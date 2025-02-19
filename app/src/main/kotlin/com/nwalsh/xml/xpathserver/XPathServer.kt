package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.NamespaceUri
import net.sf.saxon.s9api.XdmValue
import org.restlet.Application
import org.restlet.Restlet
import org.restlet.routing.Router
import org.restlet.routing.Variable
import java.util.logging.Level
import kotlin.system.exitProcess

class XPathServer(val config: XPathServerConfiguration): Application() {
    val runtime = Runtime()
    val userDefinedNamespaces = mutableMapOf<String, NamespaceUri>()
    val namespaces = mutableMapOf<String, NamespaceUri>()
    var defaultPrefix: String? = null

    var documentUri: String? = null
    var document: XdmValue? = null

    val cache = DocumentCache(this)

    init {
        userDefinedNamespaces.putAll(config.defaultNamespaceBindings)
    }

    fun startService() {
        statusService = BaseStatus(runtime)
    }

    override fun createInboundRoot(): Restlet? {
        val router = Router(context)

        router.attach("/", ServerStatus::class.java)
        router.attach("/xpath", XPath::class.java)
        router.attach("/search", Search::class.java)
        router.attach("/namespaces", Namespaces::class.java)

        val nsRoute = router.attach("/namespace/{prefix}", Namespace::class.java)
        nsRoute.template.variables["prefix"] = Variable(Variable.TYPE_TOKEN)

        val documentRoute = router.attach("/document/{filename}", Document::class.java)
        documentRoute.template.variables["filename"] = Variable(Variable.TYPE_URI_PATH)

        val watchRoute = router.attach("/watch/{path}", Watch::class.java)
        watchRoute.template.variables["path"] = Variable(Variable.TYPE_URI_PATH)

        return router
    }

    override fun stop() {
        logger.log(Level.INFO, "Stopping service")
        super.stop()
        exitProcess(0)
    }
}
