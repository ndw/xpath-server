package com.nwalsh.xml.xpathserver

import net.sf.saxon.Controller
import net.sf.saxon.event.NamespaceReducer
import net.sf.saxon.event.Receiver
import net.sf.saxon.om.AttributeMap
import net.sf.saxon.om.FingerprintedQName
import net.sf.saxon.om.NamespaceBinding
import net.sf.saxon.om.NamespaceMap
import net.sf.saxon.om.NamespaceUri
import net.sf.saxon.om.NodeName
import net.sf.saxon.s9api.Axis
import net.sf.saxon.s9api.Processor
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.XdmDestination
import net.sf.saxon.s9api.XdmNode
import net.sf.saxon.s9api.XdmNodeKind
import net.sf.saxon.s9api.XdmValue
import net.sf.saxon.serialize.SerializationProperties
import net.sf.saxon.str.UnicodeBuilder
import net.sf.saxon.str.UnicodeString
import net.sf.saxon.type.SchemaType
import java.net.URI

class TreeBuilder(processor: Processor) {
    private val controller = Controller(processor.underlyingConfiguration)
    private lateinit var destination: XdmDestination
    private lateinit var receiver: Receiver
    private var location: BuilderLocation? = null

    val result: XdmNode
        get() = destination.xdmNode

    private var inDocument = false
    private var seenRoot = false

    fun startDocument(baseUri: URI? = null) {
        destination = XdmDestination()
        val pipe = controller.makePipelineConfiguration()
        receiver = destination.getReceiver(pipe, SerializationProperties())
        receiver = NamespaceReducer(receiver)
        receiver.pipelineConfiguration = pipe
        baseUri?.let { receiver.systemId = it.toString() }
        receiver.open()
        receiver.startDocument(0)
    }

    fun endDocument() {
        receiver.endDocument()
        receiver.close()
    }

    fun addSubtree(value: XdmValue) {
        when (value) {
            is XdmNode -> addSubtreeNode(value)
            else -> addText(valuesToString(value))
        }
    }

    private fun addSubtreeNode(node: XdmNode) {
        location = BuilderLocation(node)
        try {
            receiver.append(node.underlyingNode)
        } catch (_: UnsupportedOperationException) {
            // Okay, do it the hard way
            addSubtreeNodeByParts(node)
        }
    }

    private fun addSubtreeNodeByParts(node: XdmNode) {
        // Okay, do it the hard way
        location = BuilderLocation(node)
        when (node.nodeKind) {
            XdmNodeKind.DOCUMENT -> writeChildren(node)
            XdmNodeKind.ELEMENT -> {
                addStartElement(node)
                writeChildren(node)
                addEndElement()
            }
            XdmNodeKind.COMMENT -> addComment(node.stringValue)
            XdmNodeKind.TEXT -> addText(node.stringValue)
            XdmNodeKind.PROCESSING_INSTRUCTION ->
                addPI(node.nodeName.localName, node.stringValue)
            else ->
                throw RuntimeException("Unexpected node kind")
        }
    }

    fun addStartElement(node: XdmNode) {
        location = BuilderLocation(node)
        addStartElement(node, node.nodeName, node.baseURI)
    }

    fun addStartElement(node: XdmNode, newName: QName, overrideBaseURI: URI) {
        location = BuilderLocation(node, overrideBaseURI.toString())
        val attrs = node.underlyingNode.attributes()
        addStartElement(node, newName, overrideBaseURI, attrs)
    }

    fun addStartElement(node: XdmNode, newName: QName, overrideBaseURI: URI, attrs: AttributeMap) {
        location = BuilderLocation(node, overrideBaseURI.toString())
        val inode = node.underlyingNode

        var inscopeNS = if (seenRoot) {
            val nslist: MutableList<NamespaceBinding> = mutableListOf()
            inode.getDeclaredNamespaces(null).forEach { ns ->
                nslist.add(ns)
            }
            NamespaceMap(nslist)
        } else {
            inode.allNamespaces
        }

        // If the newName has no prefix, then make sure we don't pass along some other
        // binding for the default namespace...
        if (newName.prefix == "" && inscopeNS.defaultNamespace.isEmpty) {
            inscopeNS = inscopeNS.remove("")
        }

        // Hack. See comment at top of file
        if (overrideBaseURI.toASCIIString() != "") {
            receiver.setSystemId(overrideBaseURI.toASCIIString())
        }

        val newNameOfNode = FingerprintedQName(newName.prefix, newName.namespaceUri, newName.localName)
        addStartElement(newNameOfNode, attrs, inode.schemaType, inscopeNS)
    }

    fun addStartElement(elemName: NodeName, attrs: AttributeMap, typeCode: SchemaType, nsmap: NamespaceMap) {
        // Sort out the namespaces...
        var newmap = updateMap(nsmap, elemName.prefix, elemName.uri)
        attrs.asList().forEach { attr ->
            if (!attr.nodeName.namespaceUri.isEmpty) {
                newmap = updateMap(newmap, attr.nodeName.prefix, attr.nodeName.uri)
            }
        }

        val loc = if (location != null) {
            location
        } else if (receiver.systemId == null) {
            BuilderLocation.voidLocation
        } else {
            BuilderLocation(receiver.systemId)
        }

        receiver.startElement(elemName, typeCode, attrs, nsmap, loc, 0)
        location = null
    }

    fun addEndElement() {
        receiver.endElement()
    }

    protected fun writeChildren(node: XdmNode) {
        location = BuilderLocation(node)
        val iter = node.axisIterator(Axis.CHILD)
        while (iter.hasNext()) {
            addSubtree(iter.next())
        }
    }

    fun addComment(comment: String) {
        receiver.comment(makeUnicodeString(comment), location, 0)
    }


    fun addText(text: String) {
        receiver.characters(makeUnicodeString(text), location, 0)
    }

    fun addPI(target: String, data: String, baseURI: String) {
        location = BuilderLocation(baseURI)
        receiver.processingInstruction(target, makeUnicodeString(data), location, 0)
    }

    fun addPI(target: String, data: String) {
        addPI(target, data, receiver.systemId)
    }

    private fun makeUnicodeString(text: String): UnicodeString {
        val buf = UnicodeBuilder()
        buf.append(text)
        return buf.toUnicodeString()
    }

    private fun valuesToString(values: XdmValue): String {
        val sb = StringBuilder()
        var sep = ""
        for (pos in 1..values.size()) {
            sb.append(sep)
            sb.append(values.itemAt(pos - 1).stringValue)
            sep = " "
        }
        return sb.toString()
    }

    private fun updateMap(nsmap: NamespaceMap, prefix: String?, uri: String?): NamespaceMap {
        if (uri == null || "" == uri) {
            return nsmap
        }

        if (prefix == null || "" == prefix) {
            if (uri != nsmap.defaultNamespace.toString()) {
                return nsmap.put("", NamespaceUri.of(uri))
            }
        }

        val curNS = nsmap.getNamespaceUri(prefix)
        if (curNS == null) {
            return nsmap.put(prefix, NamespaceUri.of(uri))
        } else if (curNS == NamespaceUri.of(uri)) {
            return nsmap
        }

        throw IllegalArgumentException("Unresolvable conflicting namespace bindings for ${prefix}")
    }

}