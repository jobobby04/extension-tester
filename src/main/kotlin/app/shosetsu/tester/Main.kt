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

import app.shosetsu.lib.ShosetsuSharedLib.httpClient
import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.lib.lua.ShosetsuLuaLib
import app.shosetsu.lib.lua.shosetsuGlobals
import app.shosetsu.tester.Config.CI_MODE
import app.shosetsu.tester.Config.DIRECTORY
import app.shosetsu.tester.Config.GENERATE_INDEX
import app.shosetsu.tester.Config.PRINT_REPO_INDEX
import app.shosetsu.tester.Config.SOURCES
import app.shosetsu.tester.Config.VALIDATE_INDEX
import app.shosetsu.tester.Config.WATCH
import com.github.ajalt.clikt.core.main
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import org.luaj.vm2.LuaValue
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

/*
 * shosetsu-services
 * 03 / June / 2019
 *
 * @author github.com/doomsdayrs; github.com/TechnoJo4
 */

val json = Json { prettyPrint = true }

private val globals = shosetsuGlobals()

@Throws(Exception::class)
private fun loadScript(file: File): LuaValue {
	val l = try {
		globals.load(file.readText(), "lib(${file.name})")!!
	} catch (e: Exception) {
		throw e
	}
	return l.call()!!
}

/**
 * Establish
 */
@ExperimentalTime
fun setupLibs() {
	ShosetsuLuaLib.libLoader = {
		outputTimedValue("loadScript") {
			@Suppress("CheckedExceptionsKotlin")
			loadScript(
				File("$DIRECTORY/lib/$it.lua")
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

@ExperimentalTime
fun main(args: Array<String>) {
	Config.main(args)

	setupLibs()

	if (!performIteration()) {
		exitProcess(1)
	}

	if (WATCH) {
		logger.info { "Watching for changes" }
		DirectoryWatcher(DIRECTORY/"lib", DIRECTORY/"src").apply {
			onChange { paths ->
				performIteration(paths.map { it.absolutePathString() }::contains)
			}
			watch()
		}
	}
}

class ExtensionTestException(val msg: String) : Exception(msg)

@OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
private fun performIteration(predicate: (String) -> Boolean = { true }): Boolean {
	outputTimedValue("MAIN") {
		try {
			val indexPath = DIRECTORY/"index.json"

			if (GENERATE_INDEX) {
				generateIndex(indexPath, DIRECTORY/"lib", DIRECTORY/"src")
			}

			val repoIndex: RepoIndex = indexPath.inputStream().use(RepoIndex.repositoryJsonParser::decodeFromStream)

			if (PRINT_REPO_INDEX) logger.info { outputTimedValue("RepoIndexLoad") { repoIndex.prettyPrint() } }

			if (VALIDATE_INDEX) validateRepository(repoIndex)

			// If CI mode is enabled, and repo index flag was added, simply exit, as our task was completed.
			if (PRINT_REPO_INDEX && CI_MODE) exitProcess(0)

			val e = Exception("Could not validate extensions")
			run {
				for (extensionInfo in SOURCES) {
					if (!predicate(extensionInfo.first)) continue
					try {
						testExtension(repoIndex, extensionInfo)
					} catch (ex: Exception) {
						logger.error(ex) { "(" + extensionInfo.first + ")" }
						e.addSuppressed(ex)
					}
				}
			}
			if (e.suppressed.isNotEmpty()) throw e

			logger.info { "RUN COMPLETED" }
			return true
		} catch (e: ExtensionTestException) {
			logger.error { e.msg }
			return false
		} catch (e: Exception) {
			e.printStackTrace()
			e.message?.let {
				logger.error { it.substring(it.lastIndexOf("}") + 1) }
			}
			return false
		}
	}
}