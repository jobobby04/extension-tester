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

import app.shosetsu.tester.main
import org.junit.Test
import kotlin.time.ExperimentalTime

/**
 * extension-tester
 * 08 / 11 / 2021
 */
class ExtensionTesterTest {

	@Test
	@ExperimentalTime
	fun testProgram() {
		main(arrayOf(
			"-r",
			"../extensions/",
			"../extensions/src/en/NovelFull.lua",
			"../extensions/src/en/BoxNovel.lua"
		))
	}
}