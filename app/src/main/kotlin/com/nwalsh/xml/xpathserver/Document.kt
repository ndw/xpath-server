package com.nwalsh.xml.xpathserver

import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.logging.Level

class Document(): BaseResource() {
    override fun get(variant: Variant?): Representation? {
        return StringRepresentation("expression get")
    }

    override fun post(entity: Representation?, variant: Variant?): Representation? {
        val app = (application as XPathServer)
        val filename = URLDecoder.decode(request.attributes.get("filename").toString(), StandardCharsets.UTF_8)
        app.documentUri = "/${filename}"

        if (entity != null) {
            val loader = DocumentLoader(app)
            try {
                val response = loader.load(entity, app.documentUri!!)
                app.documentUri = loader.docPath!!.toString()
                app.document = loader.document
                loader.defaultPrefix?.let { app.defaultPrefix = it}
                app.namespaces.putAll(loader.namespaces)
                return response
            } catch (ex: Exception) {
                app.logger.log(Level.WARNING) { "Error parsing entity: ${ex.message}" }
            }
        } else {
            app.logger.log(Level.WARNING) { "Upload entity was null" }
        }

        return StringRepresentation("FAIL ENTITY")
    }

    override fun delete(variant: Variant?): Representation? {
        val app = (application as XPathServer)
        app.documentUri = null
        app.document = null
        return StringRepresentation("OK")
    }
}