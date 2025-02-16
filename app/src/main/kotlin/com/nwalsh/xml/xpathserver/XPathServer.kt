package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.NamespaceUri
import net.sf.saxon.s9api.XdmValue
import org.restlet.Application
import org.restlet.Restlet
import org.restlet.routing.Router
import kotlin.system.exitProcess

class XPathServer(): Application() {
    val runtime = Runtime()
    var documentUri: String? = null
    var document: XdmValue? = null
    val userDefinedNamespaces = mutableMapOf<String, NamespaceUri>()
    val namespaces = mutableMapOf<String, NamespaceUri>()
    var defaultPrefix: String? = null

    fun startService() {
        statusService = BaseStatus(runtime)
    }

    override fun createInboundRoot(): Restlet? {
        val router = Router(context)

        router.attach("/xpath", XPath::class.java)
        router.attach("/document", Document::class.java)
        router.attach("/namespaces", Namespaces::class.java)
        router.attach("/namespace/{prefix}", Namespace::class.java)
        router.attach("/stop", Stop::class.java)

        return router
    }

    override fun stop() {
        super.stop()
        exitProcess(0)
    }
}
