package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.NamespaceUri
import org.restlet.data.Form
import org.restlet.data.MediaType
import org.restlet.data.Status
import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import java.nio.charset.StandardCharsets

class Namespace(): BaseResource() {
    override fun get(variant: Variant?): Representation? {
        val app = application as XPathServer
        val prefix = request.attributes.get("prefix").toString()
        val uri = app.userDefinedNamespaces[prefix] ?: app.namespaces[prefix]
        if (uri == null) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND)
            return StringRepresentation("No such binding")
        } else {
            return StringRepresentation("xmlns:${prefix}=${uri}")
        }
    }

    override fun post(entity: Representation?, variant: Variant?): Representation? {
        val app = application as XPathServer
        val prefix = request.attributes.get("prefix").toString()

        if (prefix == "xml" || prefix == "xmlns") {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
            return StringRepresentation("Cannot change the namespace binding for ${prefix}")
        }

        val form = getQuery()
        var uri = form.getFirstValue("uri") ?: form.getFirstValue("ns") ?: form.getFirstValue("namespace")
        if (uri == null) {
            if (entity?.mediaType == MediaType.APPLICATION_WWW_FORM) {
                val form = Form(entity)
                uri = form.getFirstValue("uri") ?: form.getFirstValue("ns") ?: form.getFirstValue("namespace")
            } else {
                uri = entity!!.stream.readAllBytes().toString(StandardCharsets.UTF_8)
            }
        }

        if (uri == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
            return StringRepresentation("No URI provided")
        }

        app.userDefinedNamespaces[prefix] = NamespaceUri.of(uri)
        return StringRepresentation("xmlns:${prefix}=${uri}")
    }

    override fun delete(variant: Variant?): Representation? {
        val app = application as XPathServer
        val prefix = request.attributes.get("prefix").toString()
        app.userDefinedNamespaces.remove(prefix)
        app.namespaces.remove(prefix)
        return StringRepresentation("OK")
    }
}