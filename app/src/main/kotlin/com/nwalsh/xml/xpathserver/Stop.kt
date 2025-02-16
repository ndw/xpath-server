package com.nwalsh.xml.xpathserver

import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant

class Stop(): BaseResource() {
    override fun post(entity: Representation?, variant: Variant?): Representation? {
        val app = application as XPathServer
        app.application.stop()
        return StringRepresentation("OK")
    }
}