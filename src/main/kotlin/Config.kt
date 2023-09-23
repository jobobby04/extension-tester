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

import app.shosetsu.lib.ExtensionType

/**
 * extension-tester
 * 06 / 11 / 2021
 */
object Config {
	var VALIDATE_METADATA: Boolean = false
	var VALIDATE_INDEX: Boolean = false
	var SEARCH_VALUE = "world"
	var PRINT_LISTINGS = false
	var PRINT_LIST_STATS = false
	var PRINT_NOVELS = false
	var PRINT_NOVEL_STATS = false
	var PRINT_PASSAGES = false
	var PRINT_REPO_INDEX = false
	var PRINT_METADATA = false
	var REPEAT = false
	var CI_MODE = false


	/** Load only the [SPECIFIC_NOVEL_URL] to test */
	var SPECIFIC_NOVEL = false

	/** Novel to load via the extension, useful for novel cases */
	var SPECIFIC_NOVEL_URL = "/"
	var SPECIFIC_CHAPTER = 0

	var FILTERS = emptyMap<Int, Map<Int?, String>>()

	/** Replace with the directory of the extensions you want to use*/
	var DIRECTORY = "./"

	// Should be an array of the path of the script to the type of that script
	var SOURCES: Array<Pair<String, ExtensionType>> = arrayOf()
}