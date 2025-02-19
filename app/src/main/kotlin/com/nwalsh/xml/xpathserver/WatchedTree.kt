package com.nwalsh.xml.xpathserver

import java.nio.file.Path

class WatchedTree(val root: Path) {
    val directories = mutableMapOf<Path, DirectoryWatcher>()

    fun stopWatching() {
        for ((_, watcher) in directories) {
            watcher.stopWatching()
        }
        directories.clear()
    }

}