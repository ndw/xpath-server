package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.NamespaceUri

class XPathServerConfiguration() {
    var port = 5078
    var maxWatchDepth = 2
    var maxWatchCount = 2
    var ignore = mutableSetOf<Regex>()
    var parse = mutableSetOf<Regex>()
    var defaultNamespaceBindings = mutableMapOf<String, NamespaceUri>()
}