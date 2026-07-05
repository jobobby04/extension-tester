package app.shosetsu.tester

import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.security.MessageDigest
import kotlin.io.path.*

private val ByteArray.hex: String get() = fold("") { str, it -> str + "%02x".format(it) }

private val sha256 = MessageDigest.getInstance("SHA-256")
fun ByteArray.sha256sum(): String = sha256.digest(this).hex

private const val CR = '\r'.code.toByte()
private const val LF = '\n'.code.toByte()

enum class LineEnding {
	LF,
	CRLF,
	CR,
}

fun ByteArray.detectLineEnding(): LineEnding {
	return when {
		size >= 2 && this[size - 2] == CR && this[size - 1] == LF -> LineEnding.CRLF

		isNotEmpty() && this[size - 1] == LF -> LineEnding.LF

		isNotEmpty() && this[size - 1] == CR -> LineEnding.CR

		else -> {
			for (i in 0 until size - 1) {
				when {
					this[i] == LF -> return LineEnding.LF

					// cr cannot be last, since that would have been caught by the first when
					this[i] == CR && this[i + 1] == LF -> return LineEnding.CRLF

					this[i] == CR -> return LineEnding.CR
				}
			}
			return LineEnding.LF // if no line endings are present at all, we are probably good with LF
		}
	}
}

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
						if (path.isDirectory()) {
							keys[
								path.register(
									watchService,
									ENTRY_CREATE,
									ENTRY_DELETE,
									ENTRY_MODIFY,
								),
							] = path
						}
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
