package com.nwalsh.xml.xpathserver


import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmAtomicValue
import net.sf.saxon.s9api.XdmDestination
import nu.validator.htmlparser.common.XmlViolationPolicy
import nu.validator.htmlparser.dom.HtmlDocumentBuilder
import org.restlet.data.MediaType
import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import org.xml.sax.InputSource
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXSource

class Document(): BaseResource() {
    override fun get(variant: Variant?): Representation? {
        return StringRepresentation("expression get")
    }

    override fun post(entity: Representation?, variant: Variant?): Representation? {
        val app = (application as XPathServer)

        val query = getQuery()
        app.documentUri = query.getFirstValue("uri") ?: query.getFirstValue("filename")

        application.logger.log(Level.INFO) { "URI: ${app.documentUri}" }

        return when (entity!!.mediaType) {
            MediaType.APPLICATION_XML, MediaType.APPLICATION_XHTML -> parseXml(entity)
            MediaType.TEXT_HTML -> parseHtml(entity)
            MediaType.APPLICATION_JSON -> parseJson(entity)
            MediaType.TEXT_PLAIN -> parseText(entity)
            else -> throw IllegalArgumentException("Unsupported media type ${variant}")
        }
    }

    fun parseXml(entity: Representation): Representation {
        val s = entity.stream.readAllBytes().toString(StandardCharsets.UTF_8)
        //application.logger.log(Level.INFO) { "S: ${s}" }

        val app = (application as XPathServer)

        val source = InputSource(ByteArrayInputStream(s.toByteArray(StandardCharsets.UTF_8)))
        val base = hostRef.toString()
        source.systemId = "${base}/${name}"

        val builder = runtime.processor.newDocumentBuilder()
        builder.isDTDValidation = false
        builder.isLineNumbering = true
        val doc = builder.build(SAXSource(source))

        app.document = doc

        app.namespaces.clear()
        app.namespaces.putAll(XmlUtil.namespaceBindings(doc))

        val defaultNs = app.namespaces[""]
        if (defaultNs != null) {
            app.defaultPrefix = "_"
            while (app.defaultPrefix in app.namespaces) {
                app.defaultPrefix += "_"
            }
            app.namespaces[app.defaultPrefix!!] = defaultNs
        }

        return StringRepresentation("OK XML")
    }

    fun parseJson(entity: Representation): Representation {
        val vara = QName("a")
        val json = entity.stream.readAllBytes().toString(StandardCharsets.UTF_8)
        val compiler = runtime.processor.newXPathCompiler()
        compiler.declareVariable(vara)
        val exec = compiler.compile("parse-json(\$a)")
        val selector = exec.load()
        selector.setVariable(vara, XdmAtomicValue(json))
        (application as XPathServer).document = selector.evaluate()

        return StringRepresentation("OK JSON")
    }

    fun parseHtml(entity: Representation): Representation {
        val htmlBuilder = HtmlDocumentBuilder(XmlViolationPolicy.ALTER_INFOSET)
        val reader = BufferedReader(InputStreamReader(entity.stream, StandardCharsets.UTF_8))
        val html = htmlBuilder.parse(InputSource(reader))

        val builder = runtime.processor.newDocumentBuilder()
        val destination = XdmDestination()
        builder.parse(DOMSource(html), destination)

        (application as XPathServer).document = destination.xdmNode

        application.logger.log(Level.INFO) { "${destination.xdmNode}" }

        return StringRepresentation("OK HTML")
    }

    fun parseText(entity: Representation): Representation {
        return StringRepresentation("FAIL")
    }
}