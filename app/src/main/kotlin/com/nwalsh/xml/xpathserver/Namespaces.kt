package com.nwalsh.xml.xpathserver

import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class Namespaces(): BaseResource() {
    override fun get(variant: Variant?): Representation? {
        val app = application as XPathServer

        val seen = mutableSetOf<String>()
        val sb = StringBuilder()
        for ((prefix, uri) in app.namespaces) {
            if (prefix != "xml") {
                if (prefix == "") {
                    sb.append("xmlns=").append(uri).append("\n")
                } else {
                    sb.append("xmlns:").append(prefix).append("=").append(uri).append("\n")
                }
            }
            seen.add(prefix)
        }

        for ((prefix, uri) in app.userDefinedNamespaces) {
            if (prefix !in seen) {
                sb.append("xmlns:").append(prefix).append("=").append(uri).append("\n")
            }
        }

        return StringRepresentation(sb.toString())
    }
}