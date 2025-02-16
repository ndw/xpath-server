package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.*
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmItem
import net.sf.saxon.s9api.XdmNode
import net.sf.saxon.type.Untyped
import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import java.nio.charset.StandardCharsets
import kotlin.collections.iterator

class XPath(): BaseResource() {
    companion object {
        val RESULT_NS = NamespaceUri.of("http://nwalsh.com/ns/xpath-server/results")
    }

    override fun post(entity: Representation?, variant: Variant?): Representation? {
        val app = application as XPathServer

        val expression = entity!!.stream.readAllBytes().toString(StandardCharsets.UTF_8)

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
        selector.contextItem = (application as XPathServer).document as XdmItem
        val resultList = mutableListOf<XdmItem>()
        for (item in selector.evaluate()) {
            resultList.add(item)
        }

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

        val resultsName = FingerprintedQName(resultPrefix, RESULT_NS, "results")
        val resultName = FingerprintedQName(resultPrefix, RESULT_NS, "result")

        var attr: AttributeMap = EmptyAttributeMap.getInstance()
        attr = attr.put(XmlUtil.attributeInfo(QName("count"), "${resultList.size}"))
        if (app.documentUri != null) {
            attr = attr.put(XmlUtil.attributeInfo(QName("href"), "${app.documentUri}"))
        }

        builder.addStartElement(resultsName, attr, Untyped.getInstance(), nsmap)

        for ((index, item) in resultList.withIndex()) {
            attr = EmptyAttributeMap.getInstance()
            attr = attr.put(XmlUtil.attributeInfo(QName("number"), "${index+1}"))
            if (item is XdmNode) {
                if (item.lineNumber > 0) {
                    attr = attr.put(XmlUtil.attributeInfo(QName("line"), "${item.lineNumber}"))
                }
                if (item.columnNumber > 0) {
                    attr = attr.put(XmlUtil.attributeInfo(QName("col"), "${item.columnNumber}"))
                }
            }

            builder.addStartElement(resultName, attr, Untyped.getInstance(), nsmap)
            builder.addSubtree(item)
            builder.addEndElement()
        }

        builder.addEndElement()
        builder.endDocument()

        val xml = builder.result.toString()

        return StringRepresentation(xml)
    }

}