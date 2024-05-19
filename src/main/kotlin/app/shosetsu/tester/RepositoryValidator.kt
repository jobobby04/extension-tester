package app.shosetsu.tester

import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.tester.Config.DIRECTORY
import java.io.File
import kotlin.system.exitProcess

/**
 *  @since 2024 / 05 / 19
 */
fun validateRepository(repoIndex: RepoIndex) {
	// Validate extensions
	repoIndex.extensions.forEach { extension ->
		// Validate all extension ids are unique
		ArrayList(repoIndex.extensions).apply {
			remove(extension)
		}.forEach { otherExt ->
			if (extension.id == otherExt.id) {
				logger.error { "Extension `${extension.name}` has the same id as `${otherExt.name}`: ${extension.id}" }
				exitProcess(1)
			}
		}
		run {
			// TODO Javascript support soon
			val extFile =
				File("$DIRECTORY/src/${extension.lang}/${extension.fileName}.lua")
			if (!extFile.exists()) {
				logger.error { "Extension `${extension.name}`(${extension.id}) is not in expected path: $extFile" }
				exitProcess(1)
			}
		}
	}

	// Validate libraries
	run {
		repoIndex.libraries.forEach { repoLibrary ->
			// Validate lib is unique
			ArrayList(repoIndex.libraries).apply {
				remove(repoLibrary)
			}.forEach { otherLib ->
				if (repoLibrary.name == otherLib.name) {
					logger.error { "Library `$repoLibrary` has the same name as `$otherLib`" }
					exitProcess(1)
				}
			}

			run {
				// TODO Javascript support soon
				val extFile =
					File("$DIRECTORY/lib/${repoLibrary.name}.lua")
				if (!extFile.exists()) {
					logger.error { "Repo $repoLibrary is not in expected path: $extFile" }
					exitProcess(1)
				}
			}

		}
	}

	// Validate styles
	repoIndex.styles.forEach { style ->
		// Validate all extension ids are unique
		ArrayList(repoIndex.styles).apply {
			remove(style)
		}.forEach { otherStyle ->
			if (style.id == otherStyle.id) {
				logger.error { "Style `${style.name}` has the same id as `${otherStyle.name}`: ${style.id}" }
				exitProcess(1)
			}
		}
		run {
			val extFile =
				File("$DIRECTORY/styles/${style.fileName}.css")
			if (!extFile.exists()) {
				logger.error { "Style `${style.name}`(${style.id}) is not in expected path: $extFile" }
				exitProcess(1)
			}
		}
	}

	logger.info { "Index is valid" }
}