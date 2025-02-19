package com.nwalsh.xml.xpathserver

import net.sf.saxon.s9api.XdmValue
import java.nio.file.Path
import java.util.logging.Level
import kotlin.io.path.name

class DocumentCache(val app: XPathServer) {
    val documents = mutableMapOf<Path, XdmValue>()
    val failedParses = mutableMapOf<Path, ErrorDetail>()
    val watchedTrees = mutableListOf<WatchedTree>()
    var lastPath: Path? = null

    fun watchTree(root: Path): WatchedTree {
        var duplicate: WatchedTree? = null
        for (tree in watchedTrees) {
            if (tree.root == root) {
                duplicate = tree
            } else {
                if (root.startsWith(tree.root) || tree.root.startsWith(root)) {
                    throw IllegalArgumentException("Watched trees may not overlap")
                }
            }
        }

        if (duplicate != null) {
            app.logger.log(Level.INFO) { "Rewatching ${root}"}
            duplicate.stopWatching()
            watchedTrees.remove(duplicate)
        }

        while (watchedTrees.size >= app.config.maxWatchDepth) {
            duplicate = watchedTrees.removeFirst()
            app.logger.log(Level.INFO) { "Discarding watch on ${duplicate.root}"}
            duplicate.stopWatching()
            watchedTrees.remove(duplicate)
        }

        val watchTree = WatchedTree(root)
        watchedTrees.add(watchTree)
        return watchTree
    }

    fun watchDirectory(tree: WatchedTree, path: Path, stop: Boolean) {
        if (!stop) {
            val watcher = DirectoryWatcher(app, path)
            watcher.start()
            tree.directories[path] = watcher

            val files = path.toFile().listFiles()
            if (files != null) {
                for (file in files.filter { it.isFile }) {
                    var parse = false
                    for (regex in app.config.parse) {
                        parse = regex.containsMatchIn(file.name)
                        if (parse) {
                            break
                        }
                    }
                    if (parse) {
                        load(file.toPath())
                    } else {
                        app.logger.log(Level.FINE) { "Ignoring ${file.name} " }
                    }
                }
            }
        }
    }

    fun load(path: Path) {
        failedParses.remove(path)
        val loader = DocumentLoader(app)
        try {
            loader.load(path)
            lastPath = loader.docPath
            documents[lastPath!!] = loader.document!!
            loader.defaultPrefix?.let { app.defaultPrefix = it }
            for ((prefix, uri) in loader.namespaces) {
                app.namespaces[prefix] = uri
            }
        } catch (ex: Exception) {
            val detail = ErrorDetail(loader.errorMessage ?: ex.message ?: "???", Math.max(loader.errorLine, 1), Math.max(loader.errorColumn, 1))

            app.logger.log(Level.WARNING) { "Error loading file ${path.name}: ${detail.message}" }
            val builder = TreeBuilder(app.runtime.processor)
            builder.startDocument(path.toUri())
            builder.addText(detail.message)
            builder.endDocument()
            documents[path] = builder.result
            failedParses[path] = detail
        }
    }

    fun remove(path: Path) {
        documents.remove(path)
    }
}