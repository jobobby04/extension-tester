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

import app.shosetsu.lib.*
import app.shosetsu.lib.ExtensionType.LuaScript
import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.lib.lua.LuaExtension
import app.shosetsu.tester.Config.CI_MODE
import app.shosetsu.tester.Config.FILTERS
import app.shosetsu.tester.Config.PRINT_LISTINGS
import app.shosetsu.tester.Config.PRINT_LIST_STATS
import app.shosetsu.tester.Config.PRINT_METADATA
import app.shosetsu.tester.Config.PRINT_NOVELS
import app.shosetsu.tester.Config.PRINT_NOVEL_STATS
import app.shosetsu.tester.Config.PRINT_PASSAGES
import app.shosetsu.tester.Config.SEARCH_VALUE
import app.shosetsu.tester.Config.SPECIFIC_CHAPTER
import app.shosetsu.tester.Config.SPECIFIC_NOVEL_URL
import app.shosetsu.tester.Config.VALIDATE_METADATA
import kotlinx.serialization.encodeToString
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

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
fun showNovel(ext: IExtension, novelURL: String) {
	val novel = outputTimedValue("ext.parseNovel") {
		ext.parseNovel(novelURL, true)
	}

	while (novel.chapters.isEmpty()) {
		logger.warn { "Chapters are empty" }
		return
	}

	if (PRINT_NOVELS)
		logger.info { novel.toString() }

	if (PRINT_NOVEL_STATS)
		logger.info { "${novel.title} - ${novel.chapters.size} chapters." }

	val passage = outputTimedValue("ext.getPassage") {
		ext.getPassage(novel.chapters[SPECIFIC_CHAPTER].link)
	}

	@Suppress("CheckedExceptionsKotlin")
	if (PRINT_PASSAGES)
		logger.info { "Passage:\t${passage.decodeToString()}" }
	else
		logger.info {
			with(passage.decodeToString()) {
				if (length < 25) "Result: $this"
				else "$length chars long result: " +
					"${take(10)} [...] ${takeLast(10)}"
			}
		}
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

	verifyImageLoad(
		novels[selectedNovel].imageURL,
		{ "Initial novel image loads" },
		{ "Initial novel image does not load: ${novels[selectedNovel]}" },
		{ "Initial novel image is not provided" }
	)

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

	verifyImageLoad(
		novel.imageURL,
		{ "Parsed Novel image loads" },
		{ "Parsed Novel image does not load: ${novels[selectedNovel]}" },
		{ "Parsed novel image is not provided" }
	)

	if (PRINT_NOVELS)
		logger.info { novel.toString() }

	if (PRINT_NOVEL_STATS)
		logger.info { "${novel.title} - ${novel.chapters.size} chapters." }


	val passage = outputTimedValue("ext.getPassage") {
		ext.getPassage(novel.chapters[0].link)
	}


	@Suppress("CheckedExceptionsKotlin")
	if (PRINT_PASSAGES) {
		logger.info {
			"Passage:\t${passage.decodeToString()}"
		}
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

/**
 * Flattens out filters
 */
fun flattenFilters(filters: List<Filter<*>>): List<Filter<*>> {
	return filters.flatMap {
		when (it) {
			is Filter.FList -> flattenFilters(it.filters)
			is Filter.Group<*> -> flattenFilters(it.filters)
			else -> listOf(it)
		}
	}
}

fun verifyImageLoad(
	imageURL: String,
	successMessage: () -> String,
	errorMessage: () -> String,
	emptyMessage: () -> String
) {
	if (imageURL.isNotBlank()) {
		try {
			val request = Request.Builder().url(imageURL).build()
			val response = ShosetsuSharedLib.httpClient.newCall(request).execute()

			if (response.code == 200) {
				logger.info(successMessage)
			} else {
				logger.error(errorMessage)
			}
		} catch (e: Exception) {
			logger.error(e, errorMessage)
		}
	} else {
		logger.error(emptyMessage)
	}
}

/**
 *  @since 2024 / 05 / 18
 */
@OptIn(ExperimentalTime::class)
@Throws(Exception::class)
fun testExtension(repoIndex: RepoIndex, extensionPath: Pair<String, ExtensionType>) {
	val extensionFile = File(extensionPath.first)
	val repoExtension =
		repoIndex.extensions.find {
			it.fileName == extensionFile.nameWithoutExtension
		}!!
	logger.info { "Testing: $extensionPath" }

	val extension = outputTimedValue("LuaExtension") {
		when (extensionPath.second) {
			LuaScript -> LuaExtension(extensionFile)
		}
	}

	if (SPECIFIC_NOVEL_URL.isNotBlank()) {
		showNovel(extension, SPECIFIC_NOVEL_URL)
		return
	}

	val settingsModel: Map<Int, *> =
		extension.settingsModel.toList().also {
			logger.info { "Settings model:" }
			it.printOut()
		}.mapify()

	val searchFiltersModel: Map<Int, *> =
		extension.getCatalogueFilters(null).toList().also {
			logger.info { "SearchFilters Model:" }
			it.printOut()
		}.mapify()

	logger.info { "ID       : ${extension.formatterID}" }
	logger.info { "Name     : ${extension.name}" }
	logger.info { "BaseURL  : ${extension.baseURL}" }
	logger.info { "Image    : ${extension.imageURL}" }
	logger.info { "Settings : $settingsModel" }
	logger.info { "Filters  : $searchFiltersModel" }
	if (PRINT_METADATA)
		logger.info {
			"MetaData : ${
				json.encodeToString(extension.exMetaData)
			}"
		}

	if (VALIDATE_METADATA) {
		val metadata = extension.exMetaData
		when {
			extension.formatterID != metadata.id -> {
				throw ExtensionTestException("Extension id does not match metadata")
			}

			repoExtension.version != metadata.version -> {
				throw ExtensionTestException("Metadata version does not match index")
			}

			repoExtension.libVersion != metadata.libVersion -> {
				throw ExtensionTestException("Metadata lib version does not match index")
			}

			else -> {
				logger.info { "Metadata is valid" }
				if (CI_MODE) {
					exitProcess(0)
				}
			}
		}
	}

	if (CI_MODE && extension.hasCloudFlare) {
		throw Exception("Test Manually")
	}

	// Check if images load
	verifyImageLoad(
		repoExtension.imageURL,
		{ "Repository imageURL loaded successfully" },
		{ "Repository imageURL does not load" },
		{ "Repository imageURL is missing" }
	)

	verifyImageLoad(
		repoExtension.imageURL,
		{ "Extension imageURL loaded successfully" },
		{ "Extension imageURL does not load" },
		{ "Extension imageURL is missing" }
	)

	fun testListing(l: IExtension.Listing.Item) {
		with(l) {
			logger.info {
				"\n-------- Listing \"${name}\" " +
					if (isIncrementing) "(incrementing)" else "" +
						" --------"
			}

			var novels = getListing?.let {
				it(
					HashMap(searchFiltersModel).apply {
						this[PAGE_INDEX] =
							if (isIncrementing) extension.startIndex else null
						this[LISTING_INDEX] = link
					}
				)
			} ?: extension.getCatalogue(
				HashMap(searchFiltersModel).apply {
					this[PAGE_INDEX] =
						if (isIncrementing) extension.startIndex else null
					this[LISTING_INDEX] = link
				}
			)

			if (isIncrementing)
				novels += getListing?.let {
					it(HashMap(searchFiltersModel)
						.apply {
							this[PAGE_INDEX] = extension.startIndex + 1
							this[LISTING_INDEX] = link
						})
				} ?: extension.getCatalogue(
					HashMap(searchFiltersModel).apply {
						this[PAGE_INDEX] = extension.startIndex + 1
						this[LISTING_INDEX] = link
					}
				)

			if (Config.REPEAT) {
				novels = getListing?.let {
					it(
						HashMap(searchFiltersModel).apply {
							this[PAGE_INDEX] =
								if (isIncrementing) extension.startIndex else null
							this[LISTING_INDEX] = link
						}
					)
				} ?: extension.getCatalogue(
					HashMap(searchFiltersModel).apply {
						this[PAGE_INDEX] = if (isIncrementing) extension.startIndex else null
						this[LISTING_INDEX] = link
					}
				)

				if (isIncrementing)
					novels += getListing?.let {
						it(HashMap(searchFiltersModel)
							.apply {
								this[PAGE_INDEX] = extension.startIndex + 1
								this[LISTING_INDEX] = link
							})
					} ?: extension.getCatalogue(
						HashMap(searchFiltersModel).apply {
							this[PAGE_INDEX] = extension.startIndex + 1
							this[LISTING_INDEX] = link
						}
					)
			}


			showListing(extension, novels)
			try {
				java.util.concurrent.TimeUnit.MILLISECONDS.sleep(500)
			} catch (e: InterruptedException) {
				e.printStackTrace()
			}
		}
	}

	// Test each listing
	when (val listings = extension.listings()) {
		is IExtension.Listing.Item -> testListing(listings)
		is IExtension.Listing.List -> listings.getListings().forEach { l ->
			if (l !is IExtension.Listing.Item) return
			testListing(l)
		}
	}

	if (extension.hasSearch) {
		logger.info { "\n-------- Search --------" }

		val filters = flattenFilters(extension.getCatalogueFilters(null).toList()).associateBy { it.id }

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

		val filtersChanged = filters.values.toList().mapify()

		showListing(
			extension,
			outputTimedValue("ext.search") {
				extension.getCatalogue(
					HashMap(searchFiltersModel).apply {
						set(QUERY_INDEX, SEARCH_VALUE)
						set(PAGE_INDEX, extension.startIndex)
						set(LISTING_INDEX, null)
						putAll(filtersChanged)
					}
				)
			}
		)
		if (extension.isSearchIncrementing) {
			showListing(
				extension,
				outputTimedValue("ext.search") {
					extension.getCatalogue(
						HashMap(searchFiltersModel).apply {
							set(QUERY_INDEX, SEARCH_VALUE)
							set(PAGE_INDEX, extension.startIndex + 1)
							set(LISTING_INDEX, null)
							putAll(filtersChanged)
						}
					)
				}
			)
		}
	}

	MILLISECONDS.sleep(500)
}