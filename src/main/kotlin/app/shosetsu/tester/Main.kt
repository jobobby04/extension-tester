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

import app.shosetsu.lib.Filter
import app.shosetsu.lib.IExtension
import app.shosetsu.lib.Novel
import app.shosetsu.lib.ShosetsuSharedLib.httpClient
import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.lib.lua.ShosetsuLuaLib
import app.shosetsu.lib.lua.shosetsuGlobals
import app.shosetsu.tester.Config.CI_MODE
import app.shosetsu.tester.Config.DIRECTORY
import app.shosetsu.tester.Config.PRINT_LISTINGS
import app.shosetsu.tester.Config.PRINT_LIST_STATS
import app.shosetsu.tester.Config.PRINT_NOVELS
import app.shosetsu.tester.Config.PRINT_NOVEL_STATS
import app.shosetsu.tester.Config.PRINT_PASSAGES
import app.shosetsu.tester.Config.PRINT_REPO_INDEX
import app.shosetsu.tester.Config.SOURCES
import app.shosetsu.tester.Config.SPECIFIC_CHAPTER
import app.shosetsu.tester.Config.VALIDATE_INDEX
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.luaj.vm2.LuaValue
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/*
 * shosetsu-services
 * 03 / June / 2019
 *
 * @author github.com/doomsdayrs; github.com/TechnoJo4
 */

val json = Json { prettyPrint = true }

private val globals = shosetsuGlobals()

val logger = KotlinLogging.logger("Extension Tester")

private fun loadScript(file: File, source_pre: String = "ext"): LuaValue {
	val l = try {
		globals.load(file.readText(), "$source_pre(${file.name})")!!
	} catch (e: Error) {
		throw e
	}
	return l.call()!!
}

@ExperimentalTime
fun showNovel(ext: IExtension, novelURL: String) {
	val novel = outputTimedValue("ext.parseNovel") {
		ext.parseNovel(novelURL, true)
	}

	while (novel.chapters.isEmpty()) {
		logger.warn("Chapters are empty")
		return
	}

	if (PRINT_NOVELS)
		logger.info(novel.toString())

	if (PRINT_NOVEL_STATS)
		logger.info("${novel.title} - ${novel.chapters.size} chapters.")

	val passage = outputTimedValue("ext.getPassage") {
		ext.getPassage(novel.chapters[SPECIFIC_CHAPTER].link)
	}

	if (PRINT_PASSAGES)
		logger.info("Passage:\t${passage.decodeToString()}")
	else
		logger.info(with(passage.decodeToString()) {
			if (length < 25) "Result: $this"
			else "$length chars long result: " +
					"${take(10)} [...] ${takeLast(10)}"
		})
}

@Throws(Exception::class)
@ExperimentalTime
fun showListing(ext: IExtension, novels: Array<Novel.Info>) {
	if (PRINT_LISTINGS) {
		logger.info { (novels.joinToString(", ") { it.toString() }) }
	}

	logger.info { "${novels.size} novels." }
	if (PRINT_LIST_STATS) {
		logger.warn { ("${novels.count { it.title.isEmpty() }} with no title, ") }
		logger.warn { ("${novels.count { it.link.isEmpty() }} with no link, ") }
		logger.warn { ("${novels.count { it.imageURL.isEmpty() }} with no image url.") }
	}

	if (novels.isEmpty())
		return

	var selectedNovel = 0
	logger.debug { novels[selectedNovel].link }
	var novel = outputTimedValue("ext.parseNovel") {
		ext.parseNovel(novels[selectedNovel].link, true)
	}

	while (novel.chapters.isEmpty() && selectedNovel < novels.lastIndex) {
		logger.warn { "Chapters are empty, trying next novel" }
		selectedNovel++
		novel = outputTimedValue("ext.parseNovel") {
			ext.parseNovel(novels[selectedNovel].link, true)
		}
	}

	if (novel.chapters.isEmpty()) {
		throw Exception("Chapters still empty after retry, likely an issue.")
	}

	if (PRINT_NOVELS)
		logger.info { novel.toString() }

	if (PRINT_NOVEL_STATS)
		logger.info { "${novel.title} - ${novel.chapters.size} chapters." }


	val passage = outputTimedValue("ext.getPassage") {
		ext.getPassage(novel.chapters[0].link)
	}


	if (PRINT_PASSAGES) {
		logger.info { "Passage:\t${passage.decodeToString()}" }
	} else {
		logger.info {
			with(passage.decodeToString()) {
				if (length < 25) "Result: $this"
				else "$length chars long result: " +
						"${take(10)} [...] ${takeLast(10)}"
			}
		}
	}
}

@Suppress("UNCHECKED_CAST")
fun List<Filter<*>>.printOut(indent: Int = 0) {
	forEach { filter ->
		val id = filter.id
		val fName = filter.name

		val tabs = StringBuilder("\t").apply {
			for (i in 0 until indent)
				this.append("\t")
		}
		val name = filter.javaClass.simpleName.let {
			if (it.length > 7)
				it.substring(0, 6)
			else it
		}
		val fullName = filter.state?.javaClass?.simpleName

		logger.info { "$tabs>${name}\t[$id]\t${fName}\t={$fullName}" }
		when (filter) {
			is Filter.FList -> {
				filter.filters.printOut(indent + 1)
			}

			is Filter.Group<*> -> {
				filter.filters.printOut(indent + 1)
			}

			else -> {
			}
		}
	}
}

@ExperimentalTime
inline fun <T> outputTimedValue(jobName: String, block: () -> T): T {
	return measureTimedValue(block).also {
		printExecutionTime(jobName, it.duration)
	}.value
}

@ExperimentalTime
fun printExecutionTime(job: String, time: Duration) {
	printExecutionTime(job, time.toDouble(DurationUnit.MILLISECONDS))
}

private fun printExecutionTime(job: String, timeMs: Double) {
	logger.debug { "COMPLETED [$job] in $timeMs ms" }
}

fun printErrorln(message: String) {
	logger.error { message }
}

object Cookies : CookieJar {
	private val cookieJar = mutableMapOf<String, MutableList<Cookie>>()

	override fun loadForRequest(url: HttpUrl): List<Cookie> {
		return cookieJar[url.host].orEmpty()
	}

	override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
		val list = cookieJar.getOrPut(url.host) { mutableListOf() }
		list.removeAll { cookie ->
			cookies.any { it.name == cookie.name }
		}
		list.addAll(cookies)
	}
}

/**
 * Establish
 */
@ExperimentalTime
fun setupLibs() {
	ShosetsuLuaLib.libLoader = {
		outputTimedValue("loadScript") {
			loadScript(
				File("$DIRECTORY/lib/$it.lua"),
				"lib"
			)
		}
	}
	httpClient = OkHttpClient.Builder().cookieJar(Cookies).addInterceptor {
		outputTimedValue("Time till response") {
			it.proceed(it.request().also { request ->
				logger.debug { request.url.toUrl().toString() }
			})
		}
	}.build()
}

@OptIn(ExperimentalSerializationApi::class)
@ExperimentalTime
fun main(args: Array<String>) {
	Config.main(args)

	setupLibs()

	outputTimedValue("MAIN") {
		try {
			val repoIndex: RepoIndex =
				RepoIndex.repositoryJsonParser.decodeFromStream(File("$DIRECTORY/index.json").inputStream())

			if (PRINT_REPO_INDEX)
				logger.info {
					outputTimedValue("RepoIndexLoad") {
						repoIndex.prettyPrint()
					}
				}

			if (VALIDATE_INDEX) {
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

			/**
			 * If CI mode is enabled, and repo index flag was added, simply exit, as our task was completed.
			 */
			if (PRINT_REPO_INDEX && CI_MODE) {
				exitProcess(0)
			}

			run {
				for (extensionInfo in SOURCES) {
					try {
						testExtension(repoIndex, extensionInfo)
					} catch (e: Exception) {
						logger.error(e) { "(" + extensionInfo.first + ")" }
					}
				}
			}

			logger.info { "\n\tTESTS COMPLETE" }
			exitProcess(0)
		} catch (e: Exception) {
			e.printStackTrace()
			e.message?.let {
				logger.error { it.substring(it.lastIndexOf("}") + 1) }
			}
			exitProcess(1)
		}
	}
}