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

import app.shosetsu.lib.ShosetsuSharedLib.httpClient
import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.lib.lua.ShosetsuLuaLib
import app.shosetsu.lib.lua.shosetsuGlobals
import app.shosetsu.tester.Config.CI_MODE
import app.shosetsu.tester.Config.DIRECTORY
import app.shosetsu.tester.Config.PRINT_REPO_INDEX
import app.shosetsu.tester.Config.SOURCES
import app.shosetsu.tester.Config.VALIDATE_INDEX
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import org.luaj.vm2.LuaValue
import java.io.File
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

val logger = KotlinLogging.logger("Extension Tester")

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
				validateRepository(repoIndex)
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