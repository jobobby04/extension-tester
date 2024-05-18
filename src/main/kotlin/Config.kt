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
import app.shosetsu.lib.ShosetsuSharedLib
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import java.util.*
import kotlin.system.exitProcess

/**
 * extension-tester
 * 06 / 11 / 2021
 */
object Config : CliktCommand() {

	val VALIDATE_METADATA by option(
		ARGUMENT_VALIDATE_METADATA,
		help = "Validate the metadata, program will end if metadata is invalid"
	).flag(default = false)

	val VALIDATE_INDEX by option(
		ARGUMENT_VALIDATE_INDEX,
		help = "Validate the index, program will end if index is invalid"
	).flag(default = false)

	val SEARCH_VALUE by option(
		ARGUMENT_TARGET_QUERY,
		help = "Target a specific query"
	)
		.default("world")

	val PRINT_LISTINGS by option(
		ARGUMENT_PRINT_LISTINGS,
		help = "Print out loaded listings"
	).flag(default = false)

	val PRINT_LIST_STATS by option(
		ARGUMENT_PRINT_LIST_STATS,
		help = "Print out stats of listings"
	).flag(
		default = false
	)

	val PRINT_NOVELS by option(
		ARGUMENT_PRINT_NOVELS,
		help = "Print out loaded novels"
	).flag(default = false)

	val PRINT_NOVEL_STATS by option(
		ARGUMENT_PRINT_NOVEL_STATS,
		help = "Print out stats of loaded novels"
	).flag(default = false)

	val PRINT_PASSAGES by option(
		ARGUMENT_PRINT_PASSAGES,
		help = "Print out passages"
	).flag(default = false)

	val PRINT_REPO_INDEX by option(
		ARGUMENT_PRINT_INDEX,
		help = "Print out repository index"
	).flag(default = false)

	val PRINT_METADATA by option(
		ARGUMENT_PRINT_METADATA,
		help = "Print out meta data of an extension"
	).flag(default = false)

	val REPEAT by option(
		ARGUMENT_REPEAT,
		help = "Repeat a result, as sometimes there is an obscure error with reruns"
	).flag(default = false)

	val CI_MODE by option(
		ARGUMENT_CI,
		help = "Run in CI mode, modifies `print-index`"
	).flag(default = false)


	/**
	 * Novel to load via the extension, useful for novel cases.
	 * default is empty, thus ignored.
	 */
	val SPECIFIC_NOVEL_URL by option(
		ARGUMENT_TARGET_NOVEL,
		help = "Target a specific novel"
	).default("")

	val SPECIFIC_CHAPTER by option(
		ARGUMENT_TARGET_CHAPTER,
		help = "Target a specific chapter of a specific novel"
	).int().default(0)

	private val rawFilters by option(ARGUMENT_MODIFY_FILTER).multiple()

	var FILTERS = emptyMap<Int, String>()
		private set

	/** Replace with the directory of the extensions you want to use*/
	val DIRECTORY by option(
		names = arrayOf(ARG_FLAG_REPO, "--repo", "--repository"),
		help = "Specifies repository path to use, Defaults to current directory"
	).default("./")

	// Should be an array of the path of the script to the type of that script
	var SOURCES: List<Pair<String, ExtensionType>> = listOf()
		private set

	private val extensions by argument(help = "Specifies which extensions to test")
		.file(true, canBeDir = false, mustBeReadable = true)
		.multiple()

	private val printVersion by option(
		ARGUMENT_VERSION,
		help = "Print version"
	).flag(default = false)

	private val headersFile by option(
		ARGUMENT_HEADERS,
		help = "Path to a headers file to read from"
	).file(true, canBeDir = false, mustBeReadable = true)

	private val userArgent by option(
		ARGUMENT_USER_AGENT,
		envvar = "EXTENSION_TESTER_USER_AGENT",
		help = "Easily provide a User Agent to use"
	).default("")

	init {
		completionOption()
	}

	override fun run() {
		if (printVersion) {
			printVersion()
			exitProcess(0)
		}

		SOURCES = extensions.map {
			it.absolutePath to when (it.extension.lowercase(Locale.getDefault())) {
				"lua" -> ExtensionType.LuaScript
				else -> {
					printErrorln("Unknown file type ${it.extension}")
					exitProcess(1)
				}
			}
		}

		if (!(CI_MODE && VALIDATE_INDEX || PRINT_REPO_INDEX) && SOURCES.isEmpty()) {
			printErrorln("No extension provided")
			exitProcess(1)
		}

		val headersFile = headersFile

		if (headersFile != null) {
			val headersContent = headersFile.readText()
			val headerEntries = headersContent.split("\n")

			val headers = headerEntries.map { entry ->
				val key = entry.substringBefore(":")
				val value = entry.substringAfter(":")
				key to value
			}.toTypedArray()

			ShosetsuSharedLib.shosetsuHeaders = headers
		}

		if (userArgent.isNotBlank()) {
			ShosetsuSharedLib.shosetsuHeaders = arrayOf(
				"User-Agent" to userArgent
			)
		}

		FILTERS = rawFilters.associate { rawFilter ->
			val values = rawFilter.split("=")

			val id = values.getOrNull(0)?.toIntOrNull()

			if (id == null) {
				printErrorln("`modify-filter` has not been provided a valid filter id")
				exitProcess(1)
			}

			val value = values.getOrNull(1)

			if (value == null) {
				printErrorln("`modify-filter` has not been provided a valid state")
				exitProcess(1)
			}

			id to value
		}
	}
}