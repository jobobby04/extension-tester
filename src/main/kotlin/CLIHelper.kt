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

import Config.CI_MODE
import Config.DIRECTORY
import Config.PRINT_LISTINGS
import Config.PRINT_LIST_STATS
import Config.PRINT_METADATA
import Config.PRINT_NOVELS
import Config.PRINT_NOVEL_STATS
import Config.PRINT_PASSAGES
import Config.PRINT_REPO_INDEX
import Config.REPEAT
import Config.SEARCH_VALUE
import Config.SOURCES
import Config.SPECIFIC_CHAPTER
import Config.SPECIFIC_NOVEL
import Config.SPECIFIC_NOVEL_URL
import Config.VALIDATE_INDEX
import app.shosetsu.lib.ExtensionType
import app.shosetsu.lib.ShosetsuSharedLib
import com.github.doomsdayrs.lib.extension_tester.BuildConfig
import java.io.File
import java.util.*
import kotlin.system.exitProcess

/*
 * extension-tester
 * 06 / 11 / 2021
 */

private const val ARG_FLAG_QUICK_HELP = "-h"
private const val ARGUMENT_HELP = "--help"
private const val ARG_FLAG_REPO = "-r"
private const val ARG_FLAG_EXT = "-e"
private const val ARGUMENT_PRINT_LISTINGS = "--print-listings"
private const val ARGUMENT_PRINT_LIST_STATS = "--print-list-stats"
private const val ARGUMENT_PRINT_NOVELS = "--print-novels"
private const val ARGUMENT_PRINT_NOVEL_STATS = "--print-novel-stats"
private const val ARGUMENT_PRINT_PASSAGES = "--print-passages"
private const val ARGUMENT_PRINT_INDEX = "--print-index"
private const val ARGUMENT_PRINT_METADATA = "--print-meta"
private const val ARGUMENT_REPEAT = "--repeat"
private const val ARGUMENT_TARGET_NOVEL = "--target-novel"
private const val ARGUMENT_TARGET_CHAPTER = "--target-chapter"
private const val ARGUMENT_TARGET_QUERY = "--target-query"
private const val ARGUMENT_VERSION = "--version"
private const val ARGUMENT_CI = "--ci"
private const val ARGUMENT_HEADERS = "--headers"
private const val ARGUMENT_USER_AGENT = "--user-agent"
private const val ARGUMENT_VALIDATE_METADATA = "--validate-metadata"
private const val ARGUMENT_VALIDATE_INDEX = "--validate-index"

/** Resets the color of a line */
const val CRESET: String = "\u001B[0m"
const val CCYAN: String = "\u001B[36m"
const val CPURPLE: String = "\u001B[35m"
const val CRED: String = "\u001B[31m"
const val CGREEN: String = "\u001B[32m"

fun printQuickHelp() {
	println("Usage: PROGRAM EXTENSION")
	println("Try 'PROGRAM $ARGUMENT_HELP' for more information.")
}

fun printHelp() {
	println("Usage: PROGRAM EXTENSION")
	println("Test a shosetsu extension")
	println("Example: PROGRAM ./extension.lua")
	println()
	println("Options:")
	println("\t$ARG_FLAG_QUICK_HELP:\tProvides a quick bit of help")
	println("\t$ARGUMENT_HELP:\tPrints this page")
	println("\t$ARG_FLAG_REPO:\tSpecifies repository path to use, Defaults to current directory")
	println("\t$ARG_FLAG_EXT:\tSpecifies which extension to use")
	println("\t$ARGUMENT_PRINT_LISTINGS:\n\t\tPrint out loaded listings")
	println("\t$ARGUMENT_PRINT_LIST_STATS:\n\t\tPrint out stats of listings")
	println("\t$ARGUMENT_PRINT_NOVELS:\n\t\tPrint out loaded novels")
	println("\t$ARGUMENT_PRINT_NOVEL_STATS:\n\t\tPrint out stats of loaded novels")
	println("\t$ARGUMENT_PRINT_PASSAGES:\n\t\tPrint out passages")
	println("\t$ARGUMENT_PRINT_INDEX:\n\t\tPrint out repository index")
	println("\t$ARGUMENT_PRINT_METADATA:\n\t\tPrint out meta data of an extension")
	println("\t$ARGUMENT_REPEAT:\n\t\tRepeat a result, as sometimes there is an obscure error with reruns")
	println("\t$ARGUMENT_TARGET_NOVEL:\n\t\tTarget a specific novel")
	println("\t$ARGUMENT_TARGET_CHAPTER:\n\t\tTarget a specific chapter of a specific novel")
	println("\t$ARGUMENT_TARGET_QUERY:\n\t\tTarget a specific query")
	println("\t$ARGUMENT_CI:\n\t\tRun in CI mode, modifies $ARGUMENT_PRINT_INDEX")
	println("\t$ARGUMENT_HEADERS:\n\t\tPath to a headers file to read from")
	println("\t$ARGUMENT_USER_AGENT:\n\t\tEasily provide a User Agent to use")
	println("\t$ARGUMENT_VALIDATE_METADATA:\n\t\tValidate the metadata, program will end if metadata is invalid")
	println("\t$ARGUMENT_VALIDATE_INDEX:\n\t\tValidate the index, program will end if index is invalid")
}

fun printVersion() {
	println("Version: ${BuildConfig.VERSION}")
}

private fun Array<String>.toStack(): Stack<String> {
	val stack = Stack<String>()
	reversed().forEach(stack::add)
	return stack
}

/**
 * Parse arguments provided to the program
 */
fun parseConfig(args: Array<String>) {
	fun quit(status: Int = 1) {
		exitProcess(status)
	}
	if (args.isEmpty()) {
		printErrorln("This program requires arguments")
		quit()
	}
	var extensionSet = false

	val argumentStack = args.toStack()
	do {
		when (val argument = argumentStack.pop()) {
			ARGUMENT_HEADERS -> {
				val headersPath = argumentStack.pop()
				val headersFile = File(headersPath)

				val headersContent = headersFile.readText()
				val headerEntries = headersContent.split("\n")

				val headers = headerEntries.map { entry ->
					val key = entry.substringBefore(":")
					val value = entry.substringAfter(":")
					key to value
				}.toTypedArray()

				ShosetsuSharedLib.shosetsuHeaders = headers
			}

			ARGUMENT_USER_AGENT -> {
				ShosetsuSharedLib.shosetsuHeaders = arrayOf(
					"User-Agent" to argumentStack.pop()
				)
			}

			ARGUMENT_VALIDATE_METADATA -> {
				Config.VALIDATE_METADATA = true
			}

			ARGUMENT_VALIDATE_INDEX -> {
				Config.VALIDATE_INDEX = true
			}

			ARGUMENT_CI -> {
				Config.CI_MODE = true
			}

			ARG_FLAG_QUICK_HELP -> {
				printQuickHelp()
				quit(0)
			}

			ARGUMENT_HELP -> {
				printHelp()
				quit(0)
			}

			ARG_FLAG_REPO -> {
				if (argumentStack.isNotEmpty()) {
					DIRECTORY = argumentStack.pop()
				} else {
					printErrorln("$ARG_FLAG_REPO has not been provided a path")
					quit()
				}
			}

			ARG_FLAG_EXT -> {
				if (argumentStack.isNotEmpty()) {
					val path = argumentStack.pop()
					val fileExt = path.substringAfterLast(".")
					val type = when (fileExt.lowercase(Locale.getDefault())) {
						"lua" -> ExtensionType.LuaScript
						else -> {
							printErrorln("Unknown file type $fileExt")
							quit()
							return
						}
					}

					SOURCES = arrayOf(path to type)
					extensionSet = true
				} else {
					printErrorln("$ARG_FLAG_EXT has not been provided an extension")
					quit()
				}
			}

			ARGUMENT_PRINT_LISTINGS -> PRINT_LISTINGS = true
			ARGUMENT_PRINT_LIST_STATS -> PRINT_LIST_STATS = true
			ARGUMENT_PRINT_NOVELS -> PRINT_NOVELS = true
			ARGUMENT_PRINT_NOVEL_STATS -> PRINT_NOVEL_STATS = true
			ARGUMENT_PRINT_PASSAGES -> PRINT_PASSAGES = true
			ARGUMENT_PRINT_INDEX -> PRINT_REPO_INDEX = true
			ARGUMENT_PRINT_METADATA -> PRINT_METADATA = true
			ARGUMENT_REPEAT -> REPEAT = true
			ARGUMENT_TARGET_NOVEL -> {
				if (argumentStack.isNotEmpty()) {
					SPECIFIC_NOVEL = true
					SPECIFIC_NOVEL_URL = argumentStack.pop()
				} else {
					printErrorln("$ARGUMENT_TARGET_NOVEL requires a URL")
					quit()
				}
			}

			ARGUMENT_TARGET_CHAPTER -> {
				if (argumentStack.isNotEmpty()) {
					val chapter = argumentStack.pop().toIntOrNull()
					if (chapter != null) {
						SPECIFIC_CHAPTER = chapter
					} else {
						printErrorln("$ARGUMENT_TARGET_CHAPTER has not been provided a valid chapter #")
						quit()
					}
				} else {
					printErrorln("$ARGUMENT_TARGET_CHAPTER requires a chapter #")
					quit()
				}
			}

			ARGUMENT_TARGET_QUERY -> {
				if (argumentStack.isNotEmpty()) {
					val query = argumentStack.pop()
					if (query != null) {
						SEARCH_VALUE = query
					} else {
						printErrorln("$ARGUMENT_TARGET_QUERY has not been provided a valid query")
						quit()
					}
				} else {
					printErrorln("$ARGUMENT_TARGET_QUERY requires a query")
					quit()
				}
			}

			ARGUMENT_VERSION -> {
				printVersion()
				quit(0)
			}

			else -> {
				val fileExt = argument.substringAfterLast(".")
				val type = when (fileExt.lowercase(Locale.getDefault())) {
					"lua" -> ExtensionType.LuaScript
					else -> {
						printErrorln("Unknown file type $fileExt")
						quit()
						return
					}
				}

				SOURCES = arrayOf(argument to type)
				extensionSet = true
			}
		}
	} while (argumentStack.isNotEmpty())

	if (!(CI_MODE && VALIDATE_INDEX || PRINT_REPO_INDEX) && !extensionSet) {
		printErrorln("No extension provided")
		quit()
	}
}