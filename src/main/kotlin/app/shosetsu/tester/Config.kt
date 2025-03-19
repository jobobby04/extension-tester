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
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.doomsdayrs.lib.extension_tester.BuildConfig
import java.nio.file.Path
import java.util.*
import java.util.logging.Level
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.system.exitProcess

/**
 * extension-tester
 * 06 / 11 / 2021
 */
object Config : CliktCommand() {

	val VALIDATE_METADATA by option(
		"--validate-metadata",
		help = "Validate the metadata, program will end if metadata is invalid"
	).flag(default = false)

	val VALIDATE_INDEX by option(
		"--validate-index",
		help = "Validate the index, program will end if index is invalid"
	).flag(default = false)

	val GENERATE_INDEX by option(
		"--generate-index",
		help = "Generate a new index"
	).flag(default = false)

	val WATCH by option(
		"--watch",
		help = "Watch the directory for changes"
	).flag(default = false)

	val SEARCH_VALUE by option(
		"--target-query",
		help = "Target a specific query"
	).default("world")

	val PRINT_LISTINGS by option(
		"--print-listings",
		help = "Print out loaded listings"
	).flag(default = false)

	val PRINT_LIST_STATS by option(
		"--print-list-stats",
		help = "Print out stats of listings"
	).flag(default = false)

	val PRINT_NOVELS by option(
		"--print-novels",
		help = "Print out loaded novels"
	).flag(default = false)

	val PRINT_NOVEL_STATS by option(
		"--print-novel-stats",
		help = "Print out stats of loaded novels"
	).flag(default = false)

	val PRINT_PASSAGES by option(
		"--print-passages",
		help = "Print out passages"
	).flag(default = false)

	val PRINT_REPO_INDEX by option(
		"--print-index",
		help = "Print out repository index"
	).flag(default = false)

	val PRINT_METADATA by option(
		"--print-meta",
		help = "Print out meta data of an extension"
	).flag(default = false)

	val REPEAT by option(
		"--repeat",
		help = "Repeat a result, as sometimes there is an obscure error with reruns"
	).flag(default = false)

	val CI_MODE by option(
		"--ci",
		help = "Run in CI mode, modifies `print-index`"
	).flag(default = false)

	val VERBOSE by option(
		"--verbose",
		help = "Print out debug logs"
	).flag(default = false)

	/**
	 * Novel to load via the extension, useful for novel cases.
	 * default is empty, thus ignored.
	 */
	val SPECIFIC_NOVEL_URL by option(
		"--target-novel",
		help = "Target a specific novel"
	).default("")

	val SPECIFIC_CHAPTER by option(
		"--target-chapter",
		help = "Target a specific chapter of a specific novel"
	).int().default(0)

	private val rawFilters by option("--modify-filter").multiple()

	var FILTERS = emptyMap<Int, String>()
		private set

	/** Replace with the directory of the extensions you want to use*/
	val DIRECTORY by option(
		names = arrayOf("-r", "--repo", "--repository"),
		help = "Specifies repository path to use, Defaults to current directory"
	).path().default(Path.of("."))

	// Should be an array of the path of the script to the type of that script
	var SOURCES: List<Pair<String, ExtensionType>> = listOf()
		private set

	private val skipExtensions by option(
		"--skip",
		help = "Specifies which extensions to skip, via their paths"
	).path(true, canBeDir = false).multiple()

	private val extensions by argument(help = "Specifies which extensions to test")
		.path(true, canBeDir = false, mustBeReadable = true)
		.multiple()

	private val printVersion by option(
		"--version",
		help = "Print version"
	).flag(default = false)

	private val headersFile by option(
		"--headers",
		help = "Path to a headers file to read from"
	).path(true, canBeDir = false, mustBeReadable = true)

	private val userArgent by option(
		"--user-agent",
		envvar = "EXTENSION_TESTER_USER_AGENT",
		help = "Easily provide a User Agent to use"
	).default("ShosetsuExtensionTester/${BuildConfig.VERSION} Sorry for the spam!")

	init {
		completionOption()
	}

	override fun run() {
		setupLogging(if (VERBOSE) Level.FINER else null)
		if (printVersion) {
			println("Version: ${BuildConfig.VERSION}")
			exitProcess(0)
		}

		SOURCES = extensions.filterNot { skipExtensions.contains(it) }.map {
			it.absolutePathString() to when (it.extension.lowercase(Locale.getDefault())) {
				"lua" -> ExtensionType.LuaScript
				else -> {
					logger.error { "Unknown file type ${it.extension}" }
					exitProcess(1)
				}
			}
		}

		if (!(CI_MODE && VALIDATE_INDEX || PRINT_REPO_INDEX || GENERATE_INDEX) && SOURCES.isEmpty()) {
			logger.error { "No extension provided" }
			exitProcess(1)
		}

		if (CI_MODE && WATCH) {
			logger.error { "Cannot run in CI mode and watch mode" }
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

		ShosetsuSharedLib.shosetsuHeaders = arrayOf(
			"User-Agent" to userArgent
		)

		FILTERS = rawFilters.associate { rawFilter ->
			val values = rawFilter.split("=")

			val id = values.getOrNull(0)?.toIntOrNull()

			if (id == null) {
				logger.error { "`modify-filter` has not been provided a valid filter id" }
				exitProcess(1)
			}

			val value = values.getOrNull(1)

			if (value == null) {
				logger.error { "`modify-filter` has not been provided a valid state" }
				exitProcess(1)
			}

			id to value
		}
	}
}