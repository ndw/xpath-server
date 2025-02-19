package com.nwalsh.xml.xpathserver

import org.restlet.data.Status
import org.restlet.representation.Representation
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Variant
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.Level

class Watch(): BaseResource() {
    private var status = Status.SUCCESS_OK
    private lateinit var dirs: MutableSet<Path>

    override fun get(variant: Variant?): Representation? {
        val app = application as XPathServer
        val directory = URLDecoder.decode(request.attributes.get("path").toString(), StandardCharsets.UTF_8)
        val path = Paths.get("/${directory}")

        var count = 0
        val sb = StringBuilder()
        for (watchedPath in app.cache.documents.keys) {
            if (watchedPath.startsWith(path)) {
                count++
                if (watchedPath in app.cache.failedParses) {
                    sb.append(watchedPath).append(" (failed to parse)\n")
                } else {
                    sb.append(watchedPath).append("\n")
                }
            }
        }

        return StringRepresentation("Watching ${count} file${if (count == 1) "" else "s"}\n" + sb.toString())
    }

    override fun post(entity: Representation?, variant: Variant?): Representation? {
        val app = application as XPathServer
        val path = URLDecoder.decode(request.attributes.get("path").toString(), StandardCharsets.UTF_8)
        val root = File("/${path}")

        val response = synchronized(status) {
            checkPath(root)
        }

        if (status != Status.SUCCESS_OK) {
            return response
        }

        try {
            var tree = app.cache.watchTree(root.toPath())
            for (dir in dirs) {
                app.cache.watchDirectory(tree, dir, false)
            }
        } catch (ex: IllegalArgumentException) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
            return StringRepresentation(ex.message!!)
        }

        return StringRepresentation("Started watching ${root.absolutePath}")
    }

    override fun delete(variant: Variant?): Representation? {
        val app = application as XPathServer
        val path = URLDecoder.decode(request.attributes.get("path").toString(), StandardCharsets.UTF_8)
        val root = File("/${path}")

        val response = synchronized(status) {
            checkPath(root)
        }

        if (status != Status.SUCCESS_OK) {
            return response
        }

        try {
            var tree = app.cache.watchTree(root.toPath())
            for (dir in dirs) {
                app.cache.watchDirectory(tree, dir, true)
            }
        } catch (ex: IllegalArgumentException) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
            return StringRepresentation(ex.message!!)
        }

        return StringRepresentation("Stopped watching ${root.absolutePath}")
    }

    private fun checkPath(root: File): Representation {
        if (!root.exists()) {
            status = Status.CLIENT_ERROR_BAD_REQUEST
            setStatus(status)
            return StringRepresentation("Path must exist: ${root.absolutePath}}")
        }
        if (!root.isDirectory()) {
            status = Status.CLIENT_ERROR_BAD_REQUEST
            setStatus(status)
            return StringRepresentation("Path must identify a directory: ${root.absolutePath}}")
        }

        dirs = mutableSetOf<Path>()

        search(root, 1)

        if (dirs.isEmpty()) {
            status = Status.CLIENT_ERROR_BAD_REQUEST
            setStatus(status)
            return StringRepresentation("Path contains no XML files.")
        }

        return StringRepresentation("OK")
    }

    private fun search(root: File, depth: Int) {
        val app = application as XPathServer
        val path = root.toPath()
        dirs.add(path)
        val files = root.listFiles()
        if (files == null || depth > app.config.maxWatchDepth) {
            return
        }

        for (file in files) {
            if (file.isDirectory) {
                var ignore = false
                for (regex in app.config.ignore) {
                    ignore = regex.containsMatchIn(file.name)
                    if (ignore) {
                        break
                    }
                }
                if (ignore) {
                    app.logger.log(Level.INFO) { "Ignoring ${file.name} " }
                } else {
                    search(file, depth + 1)
                }
            }
        }
    }
}