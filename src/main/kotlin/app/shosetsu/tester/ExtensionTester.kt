package app.shosetsu.tester

import app.shosetsu.lib.*
import app.shosetsu.lib.ExtensionType.LuaScript
import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.lib.lua.LuaExtension
import app.shosetsu.tester.Config.CI_MODE
import app.shosetsu.tester.Config.FILTERS
import app.shosetsu.tester.Config.PRINT_METADATA
import app.shosetsu.tester.Config.SEARCH_VALUE
import app.shosetsu.tester.Config.SPECIFIC_NOVEL_URL
import app.shosetsu.tester.Config.VALIDATE_METADATA
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

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
		extension.searchFiltersModel.toList().also {
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
		logger.info(
			"MetaData : ${
				json.encodeToString(extension.exMetaData)
			}"
		)

	if (VALIDATE_METADATA) {
		val metadata = extension.exMetaData
		when {
			extension.formatterID != metadata.id -> {
				logger.info("Extension id does not match metadata")
				exitProcess(1)
			}

			repoExtension.version != metadata.version -> {
				logger.info("Metadata version does not match index")
				exitProcess(1)
			}

			repoExtension.libVersion != metadata.libVersion -> {
				logger.info("Metadata lib version does not match index")
				exitProcess(1)
			}

			else -> {
				logger.info("Metadata is valid")
				if (CI_MODE) {
					exitProcess(0)
				}
			}
		}
	}

	if (CI_MODE && extension.hasCloudFlare) {
		throw Exception("Test Manually")
	}

	extension.listings.forEach { l ->
		with(l) {
			print("\n-------- Listing \"${name}\" ")
			print(if (isIncrementing) "(incrementing)" else "")
			logger.info(" --------")

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

			if (Config.REPEAT) {
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
				java.util.concurrent.TimeUnit.MILLISECONDS.sleep(500)
			} catch (e: InterruptedException) {
				e.printStackTrace()
			}
		}
	}

	if (extension.hasSearch) {
		logger.info("\n-------- Search --------")
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