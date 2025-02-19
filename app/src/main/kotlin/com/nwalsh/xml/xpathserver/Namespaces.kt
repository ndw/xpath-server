package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.NamespaceUri
import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class Namespaces(): BaseResource() {
    override fun get(variant: Variant?): Representation? {
        val app = application as XPathServer

        val sb = StringBuilder()
        for ((prefix, uri) in app.namespaces) {
            if (prefix == "") {
                sb.append("xmlns=").append(uri).append("\n")
            } else {
                sb.append("xmlns:").append(prefix).append("=").append(uri).append("\n")
            }
        }
        for ((prefix, uri) in app.userDefinedNamespaces) {
            sb.append("xmlns:").append(prefix).append("=").append(uri).append("\n")
        }

        val nslist = sb.toString()
        if (nslist == "") {
            return StringRepresentation("No bindings")
        }

        return StringRepresentation(sb.toString())
    }
}