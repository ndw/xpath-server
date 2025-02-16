package com.nwalsh.xml.xpathserver

import net.sf.saxon.s9api.Location
import net.sf.saxon.s9api.XdmNode

class BuilderLocation private constructor(): Location {
    companion object {
        val voidLocation = BuilderLocation(null, -1, -1)
    }

    private var publicId: String? = null
    private var systemId: String? = null
    private var lineNumber = -1
    private var columnNumber = -1

    constructor(systemId: String?): this() {
        this.systemId = systemId
    }

    constructor(systemId: String?, line: Int, col: Int): this() {
        this.systemId = systemId
        this.lineNumber = line
        this.columnNumber = col
    }

    constructor(node: XdmNode, overrideBaseUri: String): this(overrideBaseUri, node.lineNumber, node.columnNumber)
    constructor(node: XdmNode): this(node.baseURI?.toString(), node.lineNumber, node.columnNumber)

    override fun getSystemId(): String? {
        return systemId
    }

    override fun getPublicId(): String? {
        return publicId
    }

    override fun getLineNumber(): Int {
        return lineNumber
    }

    override fun getColumnNumber(): Int {
        return columnNumber
    }

    override fun saveLocation(): Location? {
        return BuilderLocation(systemId, lineNumber, columnNumber)
    }
}