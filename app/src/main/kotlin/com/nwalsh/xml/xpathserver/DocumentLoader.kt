package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.NamespaceUri
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmAtomicValue
import net.sf.saxon.s9api.XdmDestination
import net.sf.saxon.s9api.XdmNode
import net.sf.saxon.s9api.XdmValue
import nu.validator.htmlparser.common.XmlViolationPolicy
import nu.validator.htmlparser.dom.HtmlDocumentBuilder
import org.restlet.data.MediaType
import org.restlet.data.Status
import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.Level
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXSource
import kotlin.collections.set

class DocumentLoader(val app: XPathServer) {
    var document: XdmValue? = null
    var docPath: Path? = null
    val namespaces: MutableMap<String, NamespaceUri> = mutableMapOf()
    var defaultPrefix: String? = null
    var errorMessage: String? = null
    var errorLine = -1
    var errorColumn = -1

    fun load(entity: Representation, uri: String): Representation {
        synchronized(app) {
            docPath = Paths.get(uri)
            return when (entity.mediaType) {
                MediaType.APPLICATION_XML, MediaType.APPLICATION_XHTML -> parseXml(entity)
                MediaType.TEXT_HTML -> parseHtml(entity)
                MediaType.APPLICATION_JSON -> parseJson(entity)
                MediaType.TEXT_PLAIN -> parseText(entity)
                else -> throw IllegalArgumentException("Unsupported media type ${entity.mediaType}")
            }
        }
    }

    fun load(file: Path) {
        val mediaType = MediaType.APPLICATION_XML
        synchronized(app) {
            docPath = file
            val stream = Files.newInputStream(file)
            when (mediaType) {
                MediaType.APPLICATION_XML, MediaType.APPLICATION_XHTML -> {
                    parseXml(stream)
                    app.logger.log(Level.FINE) { "Parsed XML ${file}" }
                }
                MediaType.TEXT_HTML -> parseHtml(stream)
                MediaType.APPLICATION_JSON -> parseJson(stream)
                MediaType.TEXT_PLAIN -> parseText(stream)
                else -> throw IllegalArgumentException("Unsupported media type ${mediaType}")
            }
        }
    }

    private fun parseXml(entity: Representation): Representation {
        return parseXml(entity.stream)
    }

    private fun parseXml(stream: InputStream): Representation {
        val source = InputSource(stream)
        source.systemId = docPath.toString()

        lateinit var doc: XdmNode
        synchronized(app.runtime.processor) {
            val config = app.runtime.processor.underlyingConfiguration
            val saveParseOptions = config.parseOptions
            val errorHandler = LoaderErrorHandler()
            val parseOptions = saveParseOptions.withErrorHandler(errorHandler)
            try {
                config.parseOptions = parseOptions
                val builder = app.runtime.processor.newDocumentBuilder()
                builder.isDTDValidation = false
                builder.isLineNumbering = true
                doc = builder.build(SAXSource(source))
            } finally {
                config.parseOptions = saveParseOptions
            }
        }

        document = doc

        for ((prefix, uri) in XmlUtil.namespaceBindings(doc)) {
            if (prefix != "xml") {
                namespaces[prefix] = uri
            }
        }

        val defaultNs = namespaces[""]
        if (defaultNs != null) {
            if (defaultPrefix != null) {
                namespaces.remove(defaultPrefix)
            }

            defaultPrefix = "_"
            while (defaultPrefix in namespaces) {
                defaultPrefix += "_"
            }
            namespaces[defaultPrefix!!] = defaultNs
        }

        return StringRepresentation("OK XML")
    }

    fun parseJson(entity: Representation): Representation {
        return parseJson(entity.stream)
    }

    fun parseJson(stream: InputStream): Representation {
        val vara = QName("a")
        val json = stream.readAllBytes().toString(StandardCharsets.UTF_8)
        val compiler = app.runtime.processor.newXPathCompiler()
        compiler.declareVariable(vara)
        val exec = compiler.compile("parse-json(\$a)")
        val selector = exec.load()
        selector.setVariable(vara, XdmAtomicValue(json))

        document = selector.evaluate()

        return StringRepresentation("OK JSON")
    }

    fun parseHtml(entity: Representation): Representation {
        return parseHtml(entity.stream)
    }

    fun parseHtml(stream: InputStream): Representation {
        val htmlBuilder = HtmlDocumentBuilder(XmlViolationPolicy.ALTER_INFOSET)
        val reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
        val html = htmlBuilder.parse(InputSource(reader))

        val builder = app.runtime.processor.newDocumentBuilder()
        val destination = XdmDestination()
        builder.parse(DOMSource(html), destination)

        document = destination.xdmNode

        return StringRepresentation("OK HTML")
    }

    fun parseText(entity: Representation): Representation {
        return parseText(entity.stream)
    }

    fun parseText(stream: InputStream): Representation {
        val text = stream.readAllBytes().toString(StandardCharsets.UTF_8)

        val builder = TreeBuilder(app.runtime.processor)
        builder.startDocument(docPath?.toUri())
        builder.addText(text)
        builder.endDocument()

        document = builder.result

        return StringRepresentation("OK TEXT")
    }

    inner class LoaderErrorHandler(): ErrorHandler {
        var errorCount = 0

        override fun warning(exception: SAXParseException?) {
            // nop
        }

        override fun error(exception: SAXParseException?) {
            if (errorMessage == null && exception?.message != null) {
                errorMessage = exception.message
                errorLine = exception.lineNumber
                errorColumn = exception.columnNumber
            }
            errorCount++
        }

        override fun fatalError(exception: SAXParseException?) {
            if (errorMessage == null && exception?.message != null) {
                errorMessage = exception.message
                errorLine = exception.lineNumber
                errorColumn = exception.columnNumber
            }
            errorCount++
        }
    }
}