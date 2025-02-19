package com.nwalsh.xml.xpathserver

import net.sf.saxon.s9api.XdmItem
import org.restlet.data.Status
import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import java.nio.charset.StandardCharsets

class XPath(): BaseResource() {
    override fun get(variant: Variant?): Representation? {
        val form = getQuery()
        val xpath = form.getFirstValue("xpath")
        if (xpath == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
            return StringRepresentation("No XPath")
        }
        return evaluateXPath(xpath)
    }

    override fun post(entity: Representation?, variant: Variant?): Representation? {
        val expression = entity!!.stream.readAllBytes().toString(StandardCharsets.UTF_8)
        return evaluateXPath(expression)
    }

    private fun evaluateXPath(expression: String): Representation {
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
        val resultList = mutableListOf<XdmItem>()
        if (app.document != null) {
            val exec = compiler.compile(expression)
            val selector = exec.load()
            selector.contextItem = app.document as XdmItem
            for (item in selector.evaluate()) {
                resultList.add(item)
            }
        }

        val writer = ResultsWriter(app)
        val xml = writer.results(resultList)
        return StringRepresentation(xml)
    }
}