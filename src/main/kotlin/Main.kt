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
import Config.FILTERS
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
import Config.SPECIFIC_NOVEL_URL
import Config.VALIDATE_INDEX
import Config.VALIDATE_METADATA
import app.shosetsu.lib.*
import app.shosetsu.lib.ExtensionType.LuaScript
import app.shosetsu.lib.ShosetsuSharedLib.httpClient
import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.lib.lua.LuaExtension
import app.shosetsu.lib.lua.ShosetsuLuaLib
import app.shosetsu.lib.lua.shosetsuGlobals
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.luaj.vm2.LuaValue
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS
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

private val json = Json { prettyPrint = true }

private val globals = shosetsuGlobals()

private fun loadScript(file: File, source_pre: String = "ext"): LuaValue {
	val l = try {
		globals.load(file.readText(), "$source_pre(${file.name})")!!
	} catch (e: Error) {
		throw e
	}
	return l.call()!!
}

@ExperimentalTime
private fun showNovel(ext: IExtension, novelURL: String) {
	val novel = outputTimedValue("ext.parseNovel") {
		ext.parseNovel(novelURL, true)
	}

	while (novel.chapters.isEmpty()) {
		println("$CRED Chapters are empty $CRESET")
		return
	}

	if (PRINT_NOVELS)
		println(novel)
	if (PRINT_NOVEL_STATS)
		println("${novel.title} - ${novel.chapters.size} chapters.")
	println()

	val passage = outputTimedValue("ext.getPassage") {
		ext.getPassage(novel.chapters[SPECIFIC_CHAPTER].link)
	}

	if (PRINT_PASSAGES)
		println("Passage:\t${passage.decodeToString()}")
	else
		println(with(passage.decodeToString()) {
			if (length < 25) "Result: $this"
			else "$length chars long result: " +
					"${take(10)} [...] ${takeLast(10)}"
		})
}

@ExperimentalTime
@Suppress("ConstantConditionIf")
private fun showListing(ext: IExtension, novels: Array<Novel.Info>) {
	if (PRINT_LISTINGS) {
		println("$CPURPLE[")
		print(novels.joinToString(", ") { it.toString() })
		println("]$CRESET")
	}

	println("${novels.size} novels.")
	if (PRINT_LIST_STATS) {
		print("${novels.count { it.title.isEmpty() }} with no title, ")
		print("${novels.count { it.link.isEmpty() }} with no link, ")
		print("${novels.count { it.imageURL.isEmpty() }} with no image url.")
		println()
	}

	println()

	if (novels.isEmpty())
		return

	var selectedNovel = 0
	println(novels[selectedNovel].link)
	var novel = outputTimedValue("ext.parseNovel") {
		ext.parseNovel(novels[selectedNovel].link, true)
	}

	while (novel.chapters.isEmpty() && selectedNovel < novels.lastIndex) {
		println("$CRED Chapters are empty, trying next novel $CRESET")
		selectedNovel++
		novel = outputTimedValue("ext.parseNovel") {
			ext.parseNovel(novels[selectedNovel].link, true)
		}
	}

	if (novel.chapters.isEmpty())
		return

	if (PRINT_NOVELS)
		println(novel)

	if (PRINT_NOVEL_STATS)
		println("${novel.title} - ${novel.chapters.size} chapters.")

	println()


	val passage = outputTimedValue("ext.getPassage") {
		ext.getPassage(novel.chapters[0].link)
	}


	if (PRINT_PASSAGES)
		println("Passage:\t${passage.decodeToString()}")
	else
		println(with(passage.decodeToString()) {
			if (length < 25) "Result: $this"
			else "$length chars long result: " +
					"${take(10)} [...] ${takeLast(10)}"
		})
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

		println("$tabs>${name}\t[$id]\t${fName}\t={$fullName}")
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
private inline fun <T> outputTimedValue(jobName: String, block: () -> T): T {
	return measureTimedValue(block).also {
		printExecutionTime(jobName, it.duration)
	}.value
}

@ExperimentalTime
private fun printExecutionTime(job: String, time: Duration) {
	printExecutionTime(job, time.toDouble(DurationUnit.MILLISECONDS))
}

private fun printExecutionTime(job: String, timeMs: Double) {
	println("$CGREEN COMPLETED [$job] in $timeMs ms $CRED")
}

fun printErrorln(message: String) {
	println("$CRED$message$CRESET")
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
				println(request.url.toUrl().toString())
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
				println(outputTimedValue("RepoIndexLoad") {
					repoIndex.prettyPrint()
				})

			if (VALIDATE_INDEX) {
				// Validate extensions
				repoIndex.extensions.forEach { extension ->
					// Validate all extension ids are unique
					ArrayList(repoIndex.extensions).apply {
						remove(extension)
					}.forEach { otherExt ->
						if (extension.id == otherExt.id) {
							println("Extension `${extension.name}` has the same id as `${otherExt.name}`: ${extension.id}")
							exitProcess(1)
						}
					}
					run {
						// TODO Javascript support soon
						val extFile =
							File("$DIRECTORY/src/${extension.lang}/${extension.fileName}.lua")
						if (!extFile.exists()) {
							println("Extension `${extension.name}`(${extension.id}) is not in expected path: $extFile")
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
								println("Library `$repoLibrary` has the same name as `$otherLib`")
								exitProcess(1)
							}
						}

						run {
							// TODO Javascript support soon
							val extFile =
								File("$DIRECTORY/lib/${repoLibrary.name}.lua")
							if (!extFile.exists()) {
								println("Repo $repoLibrary is not in expected path: $extFile")
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
							println("Style `${style.name}` has the same id as `${otherStyle.name}`: ${style.id}")
							exitProcess(1)
						}
					}
					run {
						val extFile =
							File("$DIRECTORY/styles/${style.fileName}.css")
						if (!extFile.exists()) {
							println("Style `${style.name}`(${style.id}) is not in expected path: $extFile")
							exitProcess(1)
						}
					}
				}

				println("Index is valid")
			}

			/**
			 * If CI mode is enabled, and repo index flag was added, simply exit, as our task was completed.
			 */
			if (PRINT_REPO_INDEX && CI_MODE) {
				exitProcess(0)
			}

			run {
				for (extensionPath in SOURCES) {
					val extensionFile = File(extensionPath.first)
					val repoExtension =
						repoIndex.extensions.find {
							it.fileName == extensionFile.nameWithoutExtension
						}!!
					println("\n\n========== $extensionPath ==========")


					val extension = outputTimedValue("LuaExtension") {
						when (extensionPath.second) {
							LuaScript -> LuaExtension(extensionFile)
						}
					}

					if (SPECIFIC_NOVEL_URL.isNotBlank()) {
						showNovel(extension, SPECIFIC_NOVEL_URL)
						return@run
					}

					val settingsModel: Map<Int, *> =
						extension.settingsModel.toList().also {
							println("Settings model:")
							it.printOut()
						}.mapify()
					val searchFiltersModel: Map<Int, *> =
						extension.searchFiltersModel.toList().also {
							println("SearchFilters Model:")
							it.printOut()
						}.mapify()

					println(CCYAN)
					println("ID       : ${extension.formatterID}")
					println("Name     : ${extension.name}")
					println("BaseURL  : ${extension.baseURL}")
					println("Image    : ${extension.imageURL}")
					println("Settings : $settingsModel")
					println("Filters  : $searchFiltersModel")
					if (PRINT_METADATA)
						println(
							"MetaData : ${
								json.encodeToString(extension.exMetaData)
							}"
						)
					println(CRESET)

					if (VALIDATE_METADATA) {
						val metadata = extension.exMetaData
						when {
							extension.formatterID != metadata.id -> {
								println("Extension id does not match metadata")
								exitProcess(1)
							}

							repoExtension.version != metadata.version -> {
								println("Metadata version does not match index")
								exitProcess(1)
							}

							repoExtension.libVersion != metadata.libVersion -> {
								println("Metadata lib version does not match index")
								exitProcess(1)
							}

							else -> {
								println("Metadata is valid")
								if (CI_MODE) {
									exitProcess(0)
								}
							}
						}
					}

					if (CI_MODE && extension.hasCloudFlare) {
						print("$CRED=== CLOUDFLARE: PLEASE TEST MANUALLY ===$CRESET")
						continue
					}

					extension.listings.forEach { l ->
						with(l) {
							print("\n-------- Listing \"${name}\" ")
							print(if (isIncrementing) "(incrementing)" else "")
							println(" --------")

							var novels = getListing(
								HashMap(searchFiltersModel).apply {
									this[PAGE_INDEX] =
										if (isIncrementing) extension.startIndex else null

								}
							)

							if (isIncrementing)
								novels += getListing(HashMap(searchFiltersModel)
									.apply {
										this[PAGE_INDEX] = extension.startIndex + 1
									})

							if (REPEAT) {
								novels = getListing(
									HashMap(searchFiltersModel).apply {
										this[PAGE_INDEX] =
											if (isIncrementing) extension.startIndex else null

									}
								)

								if (isIncrementing)
									novels += getListing(HashMap(searchFiltersModel)
										.apply {
											this[PAGE_INDEX] = extension.startIndex + 1
										})
							}


							showListing(extension, novels)
							try {
								MILLISECONDS.sleep(500)
							} catch (e: InterruptedException) {
								e.printStackTrace()
							}
						}
					}

					if (extension.hasSearch) {
						println("\n-------- Search --------")
						val filters = extension.searchFiltersModel.associateBy { it.id }
						FILTERS.forEach { (id, state) ->
							val filter = filters.getOrElse(id) { null }

							when (filter) {
								is Filter.Checkbox -> filter.state = state.toBooleanStrict()
								is Filter.Dropdown -> filter.state = state.toInt()
								is Filter.Password -> filter.state = state
								is Filter.RadioGroup -> filter.state = state.toInt()
								is Filter.Switch -> filter.state = state.toBooleanStrict()
								is Filter.Text -> filter.state = state
								is Filter.TriState -> filter.state = state.toInt()

								is Filter.FList,
								is Filter.Group<*>,
								is Filter.Header,
								Filter.Separator,
								null -> Unit
							}
						}
						val filtersChanged = filters.mapValues { (_, filter) ->
							(filter as? Filter.Group<*>)?.filters?.map { it.state }
								?: (filter as? Filter.FList)?.filters?.map { it.state }
								?: filter.state
						}
						showListing(
							extension,
							outputTimedValue("ext.search") {
								extension.search(
									HashMap(searchFiltersModel).apply {
										set(QUERY_INDEX, SEARCH_VALUE)
										set(PAGE_INDEX, extension.startIndex)
										putAll(filtersChanged)
									}
								)
							}
						)
						if (extension.isSearchIncrementing) {
							showListing(
								extension,
								outputTimedValue("ext.search") {
									extension.search(
										HashMap(searchFiltersModel).apply {
											set(QUERY_INDEX, SEARCH_VALUE)
											set(PAGE_INDEX, extension.startIndex + 1)
											putAll(filtersChanged)
										}
									)
								}
							)
						}
					}

					MILLISECONDS.sleep(500)
				}
			}


			println("\n\tTESTS COMPLETE")
			exitProcess(0)
		} catch (e: Exception) {
			e.printStackTrace()
			e.message?.let {
				print(CRED)
				print(it.substring(it.lastIndexOf("}") + 1))
				println(CRESET)
			}
			exitProcess(1)
		}
	}
}