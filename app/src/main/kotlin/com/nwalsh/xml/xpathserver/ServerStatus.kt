package com.nwalsh.xml.xpathserver

import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant

class ServerStatus(): BaseResource() {
    override fun get(variant: Variant?): Representation? {
        val app = application as XPathServer

        val edition = app.runtime.processor.saxonEdition
        val version = app.runtime.processor.saxonProductVersion
        val sb = StringBuilder()
        sb.append("XPath server version ${XPathServerBuildConfig.VERSION}\n")
        sb.append("Copyright (C) 2025 Norm Tovey-Walsh, https://norm.tovey-walsh.com/\n")
        sb.append("Running Saxon ${edition} version ${version}\n")

        val query = getQuery()
        val debug = query.getFirst("debug") != null
        if (debug) {
            sb.append("Ignore list (regexes): ${app.config.ignore.joinToString(" ")}").append("\n")
            sb.append("Parse list (regexes): ${app.config.parse.joinToString(" ")}").append("\n")
            if (app.document == null) {
                sb.append("No document available for /xpath queries\n")
            } else {
                sb.append("Ready for /xpath queries on ${app.documentUri}\n")
            }
            if (app.cache.watchedTrees.isEmpty()) {
                sb.append("Not watching any directories\n")
            } else {
                if (app.config.maxWatchDepth == 1) {
                    sb.append("Watching files in only the top-level directory\n")
                } else {
                    sb.append("Watching files to a depth of ${app.config.maxWatchDepth - 1} subdirectories\n")
                }
                var threadCount = 0
                for (tree in app.cache.watchedTrees) {
                    threadCount += tree.directories.size
                }

                sb.append("Watching ${app.cache.watchedTrees.size} of ${app.config.maxWatchCount} directories (with ${threadCount} threads):\n")
                for (tree in app.cache.watchedTrees) {
                    var count = 0
                    for (path in app.cache.documents.keys) {
                        if (path.startsWith(tree.root)) {
                            count++
                        }
                    }
                    sb.append("\t${tree.root} (${count} documents)\n")
                }
            }
        }

        return StringRepresentation(sb.toString())
    }

    override fun delete(variant: Variant?): Representation? {
        val wait = DelayedStop(application as XPathServer)
        wait.start()
        return StringRepresentation("OK")
    }

    private class DelayedStop(private val app: XPathServer): Thread() {
        override fun run() {
            super.run()
            try {
                sleep(250)
                app.stop()
            } catch (_: InterruptedException) {
                app.stop()
            }
        }
    }
}