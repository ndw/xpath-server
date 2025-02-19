package com.nwalsh.xml.xpathserver

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.logging.Level

class DirectoryWatcher(val app: XPathServer, val path: Path): Thread() {
    private var stop = false
    private lateinit var watchService: WatchService

    override fun start() {
        watchService = FileSystems.getDefault().newWatchService()
        path.register(watchService,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE)
        app.logger.log(Level.INFO) { "Start watching ${path} "}
        super.start()
    }

    fun stopWatching() {
        app.logger.log(Level.INFO) { "Stop watching ${path} "}
        stop = true
    }

    override fun run() {
        var key: WatchKey? = watchService.take()
        while (key != null) {
            for (event in key.pollEvents()) {
                val kind = event.kind()
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    // ???
                    continue
                }

                val filename = (event.context() as Path).toString()
                val filePath = path.resolve(filename)

                var ignore = false
                for (regex in app.config.ignore) {
                    ignore = regex.containsMatchIn(filename)
                    if (ignore) {
                        break
                    }
                }
                if (ignore) {
                    app.logger.log(Level.INFO) { "Ignoring ${filePath} " }
                    continue
                }

                when (kind) {
                    StandardWatchEventKinds.ENTRY_CREATE -> {
                        app.logger.log(Level.INFO) { "Creating ${filePath} " }
                        app.cache.load(filePath)
                    }
                    StandardWatchEventKinds.ENTRY_DELETE -> {
                        app.logger.log(Level.INFO) { "Deleting ${filePath} " }
                        app.cache.remove(filePath)
                    }
                    StandardWatchEventKinds.ENTRY_MODIFY -> {
                        app.logger.log(Level.INFO) { "Modifying ${filePath} " }
                        app.cache.load(filePath)
                    }
                }
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    // ???
                    continue
                }
            }

            key.reset()
        }


    }
}