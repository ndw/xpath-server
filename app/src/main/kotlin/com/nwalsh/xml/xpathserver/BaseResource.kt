package com.nwalsh.xml.xpathserver

import org.restlet.data.MediaType
import org.restlet.representation.Variant
import org.restlet.resource.ServerResource

open class BaseResource(): ServerResource() {
    val APPLICATION_XPATH = MediaType.register("application/xpath", "XPath query")

    init {
        variants.add(Variant(MediaType.TEXT_PLAIN))
        variants.add(Variant(MediaType.APPLICATION_XML))
        variants.add(Variant(MediaType.APPLICATION_XHTML))
        variants.add(Variant(MediaType.TEXT_HTML))
        variants.add(Variant(MediaType.APPLICATION_JSON))
        variants.add(Variant(MediaType.APPLICATION_XQUERY))
        variants.add(Variant(APPLICATION_XPATH))
    }

    val runtime: Runtime
        get() {
            return (application as XPathServer).runtime
        }

}