import app.shosetsu.lib.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlin.io.path.Path
import kotlin.io.path.inputStream

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Usage: DiffKt <old-index> <new-index>")
        return
    }
    val oldIndex: RepoIndex = Path(args[0]).inputStream().use(RepoIndex.repositoryJsonParser::decodeFromStream)
    val newIndex: RepoIndex = Path(args[1]).inputStream().use(RepoIndex.repositoryJsonParser::decodeFromStream)
    println(diff(oldIndex, newIndex))
}

// Utility method that compares two RepoIndex objects and returns a string with the differences.
// Use this when changing the IndexGenerator to ensure that the changes are correct.

fun diff(left: RepoIndex, right: RepoIndex) = buildString {
    if (left == right) return@buildString
    appendLine("Differences found:")
    if (left.authors != right.authors) {
        appendLine("  Authors differ:")
        appendLine(diffAuthors(left.authors.associateBy { it.id }, right.authors.associateBy { it.id }).trimEnd().prependIndent("    "))
    }
    if (left.libraries.toSet() != right.libraries.toSet()) {
        appendLine("  Libraries differ:")
        appendLine(diffLibrarys(left.libraries.associateBy { it.name }, right.libraries.associateBy { it.name }).trimEnd().prependIndent("    "))
    }
    if (left.extensions.toSet() != right.extensions.toSet()) {
        appendLine("  Extensions differ:")
        appendLine(diffExtensions(left.extensions.associateBy { it.id }, right.extensions.associateBy { it.id }).trimEnd().prependIndent("    "))
    }
    if (left.styles.toSet() != right.styles.toSet()) {
        appendLine("  Styles differ:")
        appendLine(diffStyles(left.styles.associateBy { it.id }, right.styles.associateBy { it.id }).trimEnd().prependIndent("    "))
    }
    if (left.scripts.toSet() != right.scripts.toSet()) {
        appendLine("  Scripts differ:")
        appendLine(diffScripts(left.scripts.associateBy { it.id }, right.scripts.associateBy { it.id }).trimEnd().prependIndent("    "))
    }
}

fun diffAuthors(left: Map<Int, RepoAuthor>, right: Map<Int, RepoAuthor>) = buildString {
    if (left.size != right.size) {
        appendLine("Size mismatch: ${left.size} != ${right.size}")
    }
    for (author in left.values) {
        val other = right[author.id]
        if (other == null) {
            appendLine("Missing author: ${author.name}")
            continue
        }
        if (author != other) {
            appendLine("Diff in author ${author.name}:")
            if (author.name != other.name) {
                appendLine("  name: ${author.name} != ${other.name}")
            }
            if (author.description != other.description) {
                appendLine("  description: ${author.description} != ${other.description}")
            }
            if (author.imageURL != other.imageURL) {
                appendLine("  imageURL: ${author.imageURL} != ${other.imageURL}")
            }
            if (author.website != other.website) {
                appendLine("  website: ${author.website} != ${other.website}")
            }
        }
    }
}

fun diffLibrarys(left: Map<String, RepoLibrary>, right: Map<String, RepoLibrary>) = buildString {
    if (left.size != right.size) {
        appendLine("Size mismatch: ${left.size} != ${right.size}")
    }
    for (library in left.values) {
        val other = right[library.name]
        if (other == null) {
            appendLine("Missing library: ${library.name}")
            continue
        }
        if (library != other) {
            appendLine("Diff in library ${library.name}:")
            if (library.version != other.version) {
                appendLine("  version: ${library.version} != ${other.version}")
            }
            if (library.url != other.url) {
                appendLine("  url: ${library.url} != ${other.url}")
            }
            if (library.hash != other.hash) {
                appendLine("  hash: ${library.hash} != ${other.hash}")
            }
        }
    }
}

fun diffExtensions(left: Map<Int, RepoExtension>, right: Map<Int, RepoExtension>) = buildString {
    if (left.size != right.size) {
        appendLine("Size mismatch: ${left.size} != ${right.size}")
    }
    for (extension in left.values) {
        val other = right[extension.id]
        if (other == null) {
            appendLine("Missing extension: ${extension.name}")
            continue
        }
        if (extension != other) {
            appendLine("Diff in extension ${extension.name}:")
            if (extension.name != other.name) {
                appendLine("  name: ${extension.name} != ${other.name}")
            }
            if (extension.fileName != other.fileName) {
                appendLine("  fileName: ${extension.fileName} != ${other.fileName}")
            }
            if (extension.imageURL != other.imageURL) {
                appendLine("  imageURL: ${extension.imageURL} != ${other.imageURL}")
            }
            if (extension.lang != other.lang) {
                appendLine("  lang: ${extension.lang} != ${other.lang}")
            }
            if (extension.version != other.version) {
                appendLine("  version: ${extension.version} != ${other.version}")
            }
            if (extension.libVersion != other.libVersion) {
                appendLine("  libVersion: ${extension.libVersion} != ${other.libVersion}")
            }
            if (extension.md5 != other.md5) {
                appendLine("  md5: ${extension.md5} != ${other.md5}")
            }
            if (extension.type != other.type) {
                appendLine("  type: ${extension.type} != ${other.type}")
            }
        }
    }
}

fun diffStyles(left: Map<Int, RepoStyle>, right: Map<Int, RepoStyle>) = buildString {
    if (left.size != right.size) {
        appendLine("Size mismatch: ${left.size} != ${right.size}")
    }
    for (style in left.values) {
        val other = right[style.id]
        if (other == null) {
            appendLine("Missing style: ${style.name}")
            continue
        }
        if (style != other) {
            appendLine("Diff in style ${style.name}:")
            if (style.name != other.name) {
                appendLine("  name: ${style.name} != ${other.name}")
            }
            if (style.fileName != other.fileName) {
                appendLine("  fileName: ${style.fileName} != ${other.fileName}")
            }
            if (style.version != other.version) {
                appendLine("  version: ${style.version} != ${other.version}")
            }
            if (style.description != other.description) {
                appendLine("  description: ${style.description} != ${other.description}")
            }
            if (style.changeLog != other.changeLog) {
                appendLine("  changeLog: ${style.changeLog} != ${other.changeLog}")
            }
            if (style.authors != other.authors) {
                appendLine("  authors: ${style.authors} != ${other.authors}")
            }
            if (style.supported != other.supported) {
                appendLine("  supported: ${style.supported} != ${other.supported}")
            }
            if (style.hasExample != other.hasExample) {
                appendLine("  hasExample: ${style.hasExample} != ${other.hasExample}")
            }
            if (style.hasJavaScript != other.hasJavaScript) {
                appendLine("  hasJavaScript: ${style.hasJavaScript} != ${other.hasJavaScript}")
            }
        }
    }
}

fun diffScripts(left: Map<Int, RepoJavaScript>, right: Map<Int, RepoJavaScript>) = buildString {
    if (left.size != right.size) {
        appendLine("Size mismatch: ${left.size} != ${right.size}")
    }
    for (js in left.values) {
        val other = right[js.id]
        if (other == null) {
            appendLine("Missing js: ${js.name}")
            continue
        }
        if (js != other) {
            appendLine("Diff in js ${js.name}:")
            if (js.name != other.name) {
                appendLine("  name: ${js.name} != ${other.name}")
            }
            if (js.fileName != other.fileName) {
                appendLine("  fileName: ${js.fileName} != ${other.fileName}")
            }
            if (js.description != other.description) {
                appendLine("  description: ${js.description} != ${other.description}")
            }
            if (js.changeLog != other.changeLog) {
                appendLine("  changeLog: ${js.changeLog} != ${other.changeLog}")
            }
            if (js.authors != other.authors) {
                appendLine("  authors: ${js.authors} != ${other.authors}")
            }
            if (js.supported != other.supported) {
                appendLine("  supported: ${js.supported} != ${other.supported}")
            }
            if (js.hasExample != other.hasExample) {
                appendLine("  hasExample: ${js.hasExample} != ${other.hasExample}")
            }
        }
    }
}