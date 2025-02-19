package com.nwalsh.xml.xpathserver

import net.sf.saxon.om.NamespaceUri
import org.restlet.Component
import org.restlet.data.Protocol
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties
import java.util.logging.Level
import kotlin.system.exitProcess

class Main {
    companion object {
        @JvmStatic
        fun main(vararg args: String) {
            val properties = Properties()
            try {
                val propertyFile = System.getProperty("com.nwalsh.xml.xpath-server.properties")
                val stream = if (propertyFile != null) {
                    FileInputStream(propertyFile)
                } else {
                    Main::class.java.getResourceAsStream("/com/nwalsh/xml/xpath-server.properties")
                }
                properties.load(stream)
            } catch (ex: IOException) {
                System.err.println("Failed to load properties: ${ex.message}")
                exitProcess(1)
            }

            val config = XPathServerConfiguration()

            config.port = (properties.getProperty("server.port") ?: "5078").toInt()
            config.maxWatchDepth = (properties.getProperty("watch.depth") ?: "2").toInt()
            config.maxWatchCount = (properties.getProperty("watch.count") ?: "3").toInt()

            if (config.maxWatchDepth < 1) {
                throw IllegalArgumentException("Max watch depth is less than 1")
            }
            if (config.maxWatchCount < 1) {
                throw IllegalArgumentException("Max watch count is less than 1")
            }

            if (properties.getProperty("ignore") != null) {
                for (regex in properties.getProperty("ignore").split("\\s+".toRegex())) {
                    config.ignore.add(Regex(regex))
                }
            }
            if (properties.getProperty("parse") != null) {
                for (regex in properties.getProperty("parse").split("\\s+".toRegex())) {
                    config.parse.add(Regex(regex))
                }
            }

            for ((name, value) in properties) {
                if (name.toString().startsWith("xmlns.")) {
                    val prefix = name.toString().substring("xmlns.".length)
                    val uri = value.toString()
                    config.defaultNamespaceBindings[prefix] = NamespaceUri.of(uri)
                }
            }

            val server = XPathServer(config)
            server.startService()

            val edition = server.runtime.processor.saxonEdition
            val version = server.runtime.processor.saxonProductVersion
            server.logger.log(Level.INFO) { "Starting XPath Server on port ${config.port}" }
            server.logger.log(Level.INFO) { "Running Saxon ${edition} version ${version}" }
            server.logger.log(Level.FINE) { "Watch depth: ${config.maxWatchDepth}"}
            server.logger.log(Level.FINE) { "Ignore (regex): ${config.ignore.joinToString(" ")}" }
            server.logger.log(Level.FINE) { "Parse (regex): ${config.parse.joinToString(" ")}" }

            val component = Component()
            component.servers.add(Protocol.HTTP, config.port)
            component.defaultHost.attach(server)
            component.start()
        }
    }

}