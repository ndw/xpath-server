package com.nwalsh.xml.xpathserver

import org.restlet.Component
import org.restlet.data.Protocol
import java.util.logging.Level

class Main {
    companion object {
        @JvmStatic
        fun main(vararg args: String) {
            val server = XPathServer()
            server.startService()

            val port = if (args.size > 0) {
                args[0].toInt()
            } else {
                5078 // random(ish)
            }

            val edition = server.runtime.processor.saxonEdition
            val version = server.runtime.processor.saxonProductVersion
            server.logger.log(Level.INFO) { "Starting XPath Server on port ${port}" }
            server.logger.log(Level.INFO) { "Running Saxon ${edition} version ${version}" }

            val component = Component()
            component.servers.add(Protocol.HTTP, port)
            component.defaultHost.attach(server)
            component.start()
        }
    }

}