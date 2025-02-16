package com.nwalsh.xml.xpathserver

import net.sf.saxon.event.ReceiverOption
import net.sf.saxon.om.AttributeInfo
import net.sf.saxon.om.FingerprintedQName
import net.sf.saxon.om.NamespaceUri
import net.sf.saxon.s9api.Axis
import net.sf.saxon.s9api.Location
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmNode
import net.sf.saxon.s9api.XdmNodeKind
import net.sf.saxon.type.BuiltInAtomicType
import kotlin.collections.iterator

class XmlUtil {
    companion object {
        fun documentElement(node: XdmNode): XdmNode? {
            val processor = node.processor
            if (node.nodeKind == XdmNodeKind.ELEMENT) {
                return node
            }
            if (node.nodeKind == XdmNodeKind.DOCUMENT) {
                for (child in node.axisIterator(Axis.CHILD)) {
                    if (child.nodeKind == XdmNodeKind.ELEMENT) {
                        return child
                    }
                }
            }
            return null
        }

        fun namespaceBindings(node: XdmNode): Map<String, NamespaceUri> {
            val bindings = mutableMapOf<String, NamespaceUri>()
            val root = documentElement(node)
            if (root == null) {
                return bindings
            }

            for (ns in root.axisIterator(Axis.NAMESPACE)) {
                bindings[ns.nodeName?.localName ?: ""] = NamespaceUri.of(ns.stringValue)
            }

            return bindings
        }

        fun attributeInfo(name: QName, value: String, location: Location? = null): AttributeInfo {
            val fqName = FingerprintedQName(name.prefix, name.namespaceUri, name.localName)
            return AttributeInfo(fqName, BuiltInAtomicType.UNTYPED_ATOMIC, value, location, ReceiverOption.NONE)
        }
    }
}