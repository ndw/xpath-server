package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.AttributeMap
import net.sf.saxon.om.EmptyAttributeMap
import net.sf.saxon.om.FingerprintedQName
import net.sf.saxon.om.NamespaceMap
import net.sf.saxon.om.NamespaceUri
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.Serializer
import net.sf.saxon.s9api.XdmItem
import net.sf.saxon.s9api.XdmNode
import net.sf.saxon.type.Untyped
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

class ResultsWriter(val app: XPathServer) {
    companion object {
        val RESULT_NS = NamespaceUri.of("http://nwalsh.com/ns/xpath-server/results")
    }

    private lateinit var resultPrefix: String

    fun results(resultSet: Map<Path, List<XdmItem>>, expression: String): String {
        var totalSize = 0
        for ((path, results) in resultSet) {
            if (path !in app.cache.failedParses) {
                totalSize += results.size
            }
        }

        resultPrefix = "xsr"
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

        val builder = TreeBuilder(app.runtime.processor)
        builder.startDocument()

        var attr: AttributeMap = EmptyAttributeMap.getInstance()
        attr = attr.put(XmlUtil.attributeInfo(QName("count"), "${totalSize}"))
        attr = attr.put(XmlUtil.attributeInfo(QName("xpath"), expression))
        builder.addStartElement(fq("result-set"), attr, Untyped.getInstance(), nsmap)
        builder.addText("\n")


        for ((path, results) in resultSet) {
            builder.addComment(" ************************************************************ ")
            builder.addText("\n")
            writeResultList(builder, path, results, nsmap)
        }

        builder.addComment(" ************************************************************ ")
        builder.addText("\n")
        builder.addEndElement()
        builder.addText("\n")
        builder.endDocument()

        return serialize(builder)
    }

    fun results(results: List<XdmItem>): String {
        resultPrefix = "xsr"
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

        val builder = TreeBuilder(app.runtime.processor)
        builder.startDocument()

        writeResultList(builder, Paths.get(app.documentUri ?: "/no-document-to-query"), results, nsmap)

        builder.endDocument()
        return serialize(builder)
    }

    private fun writeResultList(builder: TreeBuilder, path: Path, resultList: List<XdmItem>, nsmap: NamespaceMap) {
        var attr: AttributeMap = EmptyAttributeMap.getInstance()
        builder.addStartElement(fq("results"), attr, Untyped.getInstance(), nsmap)
        builder.addText("\n")

        val detail = app.cache.failedParses[path]

        attr = attr.put(XmlUtil.attributeInfo(QName("href"), "${path.toAbsolutePath()}"))
        if (detail == null) {
            attr = attr.put(XmlUtil.attributeInfo(QName("count"), "${resultList.size}"))
        }

        builder.addStartElement(fq("path"), attr, Untyped.getInstance(), nsmap)
        builder.addEndElement()
        builder.addText("\n")

        for ((index, item) in resultList.withIndex()) {
            attr = EmptyAttributeMap.getInstance()
            attr = attr.put(XmlUtil.attributeInfo(QName("number"), "${index+1}"))
            if (detail == null) {
                if (item is XdmNode) {
                    attr = attr.put(XmlUtil.attributeInfo(QName("line"), "${Math.max(item.lineNumber, 1)}"))
                    attr = attr.put(XmlUtil.attributeInfo(QName("column"), "${Math.max(item.columnNumber, 1)}"))
                }
            } else {
                attr = attr.put(XmlUtil.attributeInfo(QName("line"), "${Math.max(detail.line, 1)}"))
                attr = attr.put(XmlUtil.attributeInfo(QName("column"), "${Math.max(detail.column, 1)}"))
            }

            if (index > 0) {
                builder.addComment(" ============================================================ ")
                builder.addText("\n")

            }

            builder.addStartElement(fq("result"), attr, Untyped.getInstance(), nsmap)
            builder.addText("\n")
            builder.addSubtree(item)
            builder.addText("\n")
            builder.addEndElement()
            builder.addText("\n")
        }

        builder.addEndElement()
        builder.addText("\n")
    }

    private fun serialize(builder: TreeBuilder): String {
        val baos = ByteArrayOutputStream()
        val serializer = app.runtime.processor.newSerializer(baos)
        serializer.setOutputProperty(Serializer.Property.METHOD, "xml")
        serializer.setOutputProperty(Serializer.Property.INDENT, "false")
        serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "true")
        serializer.serializeXdmValue(builder.result)
        serializer.close()

        return baos.toString(StandardCharsets.UTF_8)
    }

    private fun fq(name: String): FingerprintedQName {
        return FingerprintedQName(resultPrefix, RESULT_NS, name)
    }
}