/*
 * Extension Tester: Test Shosetsu extensions
 * Copyright (C) 2022 Doomsdayrs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package app.shosetsu.tester

import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.tester.Config.DIRECTORY
import java.io.File

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
				throw ExtensionTestException("Extension `${extension.name}` has the same id as `${otherExt.name}`: ${extension.id}")
			}
		}
		run {
			// TODO Javascript support soon
			val extFile =
				File("$DIRECTORY/src/${extension.lang}/${extension.fileName}.lua")
			if (!extFile.exists()) {
				throw ExtensionTestException("Extension `${extension.name}`(${extension.id}) is not in expected path: $extFile")
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
					throw ExtensionTestException("Library `$repoLibrary` has the same name as `$otherLib`")
				}
			}

			run {
				// TODO Javascript support soon
				val extFile =
					File("$DIRECTORY/lib/${repoLibrary.name}.lua")
				if (!extFile.exists()) {
					throw ExtensionTestException("Repo $repoLibrary is not in expected path: $extFile")
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
				throw ExtensionTestException("Style `${style.name}` has the same id as `${otherStyle.name}`: ${style.id}")
			}
		}
		run {
			val extFile =
				File("$DIRECTORY/styles/${style.fileName}.css")
			if (!extFile.exists()) {
				throw ExtensionTestException("Style `${style.name}`(${style.id}) is not in expected path: $extFile")
			}
		}
	}

	logger.info { "Index is valid" }
}