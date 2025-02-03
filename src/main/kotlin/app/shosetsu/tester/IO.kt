package app.shosetsu.tester

import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchKey
import java.security.MessageDigest
import kotlin.io.path.*

private val ByteArray.hex: String get() = fold("") { str, it -> str + "%02x".format(it) }

private val sha256 = MessageDigest.getInstance("SHA-256")
fun Path.sha256sum(): String = sha256.digest(readBytes()).hex

@OptIn(ExperimentalPathApi::class)
class DirectoryWatcher(vararg directories: Path) {
    private val watchService = directories[0].fileSystem.newWatchService()
    private val keys: MutableMap<WatchKey, Path> = mutableMapOf()

    private val listeners = mutableListOf<(Set<Path>) -> Unit>()
    fun onChange(action: (Set<Path>) -> Unit) = listeners.add(action)

    init {
        for (directory in directories) {
            keys[directory.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)] = directory
            directory.walk(PathWalkOption.INCLUDE_DIRECTORIES)
                .filter { it.isDirectory() }
                .forEach { keys[it.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)] = it }
        }
    }

    fun watch() {
        while (true) {
            val key = watchService.take()
            val dir = keys[key] ?: continue
            val paths = mutableSetOf<Path>()
            for (event in key.pollEvents()) {
                val path = dir.resolve(event.context() as Path)
                when (event.kind()) {
                    ENTRY_CREATE -> {
                        logger.info { "File created: $path" }
                        if (path.isDirectory()) keys[path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)] = path
                        paths.add(path)
                    }
                    ENTRY_DELETE -> {
                        logger.info { "File deleted: $path" }
                        paths.add(path)
                    }
                    ENTRY_MODIFY -> {
                        logger.info { "File modified: $path" }
                        paths.add(path)
                    }
                }
            }
            listeners.forEach { it(paths) }
            if (!key.reset()) {
                logger.info { "Directory is no longer accessible" }
                key.cancel()
            }
        }
    }
}
