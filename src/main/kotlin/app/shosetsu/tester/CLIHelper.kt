package app.shosetsu.tester/*
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

import com.github.doomsdayrs.lib.extension_tester.BuildConfig

/*
 * extension-tester
 * 06 / 11 / 2021
 */

const val ARG_FLAG_REPO = "-r"
const val ARGUMENT_PRINT_LISTINGS = "--print-listings"
const val ARGUMENT_PRINT_LIST_STATS = "--print-list-stats"
const val ARGUMENT_PRINT_NOVELS = "--print-novels"
const val ARGUMENT_PRINT_NOVEL_STATS = "--print-novel-stats"
const val ARGUMENT_PRINT_PASSAGES = "--print-passages"
const val ARGUMENT_PRINT_INDEX = "--print-index"
const val ARGUMENT_PRINT_METADATA = "--print-meta"
const val ARGUMENT_REPEAT = "--repeat"
const val ARGUMENT_TARGET_NOVEL = "--target-novel"
const val ARGUMENT_TARGET_CHAPTER = "--target-chapter"
const val ARGUMENT_TARGET_QUERY = "--target-query"
const val ARGUMENT_MODIFY_FILTER = "--modify-filter"
const val ARGUMENT_VERSION = "--version"
const val ARGUMENT_CI = "--ci"
const val ARGUMENT_HEADERS = "--headers"
const val ARGUMENT_USER_AGENT = "--user-agent"
const val ARGUMENT_VALIDATE_METADATA = "--validate-metadata"
const val ARGUMENT_VALIDATE_INDEX = "--validate-index"

fun printVersion() {
	println("Version: ${BuildConfig.VERSION}")
}