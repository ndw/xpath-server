package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.*
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.Serializer
import net.sf.saxon.s9api.XdmItem
import net.sf.saxon.s9api.XdmNode
import net.sf.saxon.type.Untyped
import org.restlet.data.MediaType
import org.restlet.data.Status
import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.collections.iterator

class Search(): BaseResource() {
    companion object {
        val RESULT_NS = NamespaceUri.of("http://nwalsh.com/ns/xpath-server/results")
    }

    override fun get(variant: Variant?): Representation? {
        val query = getQuery()
        val expression = query.getFirstValue("xpath")
        if (expression == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
            return StringRepresentation("No xpath query provided")
        }
        return search(expression)
    }

    override fun post(entity: Representation?, variant: Variant?): Representation? {
        val stream = entity?.stream
        val expression = if (stream != null) {
            stream.readAllBytes().toString(StandardCharsets.UTF_8)
        } else {
            val query = getQuery()
            query.getFirstValue("xpath")
        }

        if (expression == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
            return StringRepresentation("No xpath query provided")
        }

        return search(expression)
    }

    private fun search(expression: String): Representation? {
        val app = application as XPathServer

        val compiler = runtime.processor.newXPathCompiler()
        val seen = mutableSetOf<String>()
        for ((prefix, uri) in app.namespaces) {
            compiler.declareNamespace(prefix, "${uri}")
            seen.add(prefix)
        }
        for ((prefix, uri) in app.userDefinedNamespaces) {
            if (prefix !in seen) {
                compiler.declareNamespace(prefix, "${uri}")
            }
        }
        val exec = compiler.compile(expression)
        val selector = exec.load()

        val builder = TreeBuilder(runtime.processor)
        builder.startDocument()

        var resultPrefix = "xsr"
        while (resultPrefix in app.namespaces) {
            if (app.namespaces[resultPrefix] == RESULT_NS) {
                break
            }
            resultPrefix += "_"
        }

        var nsmap: NamespaceMap = NamespaceMap.emptyMap()
        for ((prefix, uri) in app.namespaces) {
            if (prefix != app.defaultPrefix) {
                nsmap = nsmap.put(prefix, uri)
            }
            if (uri == RESULT_NS) {
                resultPrefix = prefix
            }
        }

        nsmap = nsmap.put(resultPrefix, RESULT_NS)

        val resultSet = mutableMapOf<Path, List<XdmItem>>()

        // Sort the paths for a consistent order
        val pathList = app.cache.documents.keys.toList().sorted()
        for (path in pathList) {
            val value = app.cache.documents[path]!!
            val resultList = mutableListOf<XdmItem>()

            if (path in app.cache.failedParses) {
                val builder = TreeBuilder(app.runtime.processor)
                builder.startDocument(path.toUri())
                builder.addComment(" " + value.underlyingValue.stringValue + " ")
                builder.endDocument()
                resultList.add(builder.result)
            } else if (value is XdmItem) {
                selector.contextItem = value
                for (item in selector.evaluate()) {
                    resultList.add(item)
                }
            }

            if (resultList.isNotEmpty()) {
                resultSet[path] = resultList
            }
        }

        val writer = ResultsWriter(app)
        val xml = writer.results(resultSet, expression)
        return StringRepresentation(xml, MediaType.APPLICATION_XML)
    }
}