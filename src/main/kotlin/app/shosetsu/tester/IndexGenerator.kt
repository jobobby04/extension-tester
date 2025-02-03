package app.shosetsu.tester

import app.shosetsu.lib.ExtensionType
import app.shosetsu.lib.json.*
import app.shosetsu.lib.lua.LuaExtension
import app.shosetsu.lib.lua.LuaLibrary
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

@OptIn(ExperimentalSerializationApi::class)
private val writerJson = Json(RepoIndex.repositoryJsonParser) {
    prettyPrint = true
    prettyPrintIndent = "  "
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalPathApi::class)
fun generateIndex(indexPath: Path, libraryPath: Path, scriptPath: Path) {
    logger.info { "Generating index" }
    val authors = mutableMapOf<String, MutableSet<String>>()
    fun addAuthor(author: String, work: String) = author.split(", ")
        .forEach { authors.getOrPut(it) { TreeSet() }.add(work) }
    val index = RepoIndex(
        libraries = libraryPath.walk()
            .map {
                if (it.extension != "lua" || !it.isRegularFile()) {
                    logger.warn { "Skipping non-lua file: $it" }
                    return@map null
                }
                val meta = LuaLibrary(it)
                addAuthor(meta.libMetaData.author, it.nameWithoutExtension)
                RepoLibrary(
                    name = it.nameWithoutExtension,
                    version = meta.libMetaData.version,
                    url = null,
                    hash = it.sha256sum()
                )
            }
            .filterNotNull()
            .sortedBy { it.name }
            .toList(),
        extensions = scriptPath.walk()
            .map {
                if (it.extension != "lua" || !it.isRegularFile()) {
                    logger.warn { "Skipping non-lua file: $it" }
                    return@map null
                }
                val relativePath = scriptPath.relativize(it)
                if (relativePath.nameCount != 2) {
                    logger.warn { "Skipping file at unexpected location: $it" }
                    return@map null
                }
                val meta = LuaExtension(it)
                addAuthor(meta.exMetaData.author, meta.name)
                RepoExtension(
                    id = meta.exMetaData.id,
                    name = meta.name,
                    fileName = it.nameWithoutExtension,
                    imageURL = meta.imageURL,
                    lang = relativePath.getName(0).toString(),
                    version = meta.exMetaData.version,
                    libVersion = meta.exMetaData.libVersion,
                    md5 = it.sha256sum(), // confusing, but we use sha256 for the hash
                    type = ExtensionType.LuaScript
                )
            }
            .filterNotNull()
            .sortedBy { it.id }
            .toList(),
        styles = emptyList(), //TODO: add styles
        scripts = emptyList(), //TODO: add scripts
        authors = authors.entries
            .sortedBy { it.key }
            .sortedByDescending { it.value.size }
            .map {
                RepoAuthor(
                    id = it.key.hashCode(),
                    name = it.key,
                    description = "Worked on ${it.value.joinToString(", ")}"
                )
            }
    )
    indexPath.outputStream().use { writerJson.encodeToStream(index, it) }
}
