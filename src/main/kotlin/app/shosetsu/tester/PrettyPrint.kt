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

/**
 * extension-tester
 * 08 / 11 / 2021
 */
fun Any.prettyPrint(): String {

	var indentLevel = 0
	val indentWidth = 4

	fun padding() = "".padStart(indentLevel * indentWidth)

	val toString = toString()

	val stringBuilder = StringBuilder(toString.length)

	var i = 0
	while (i < toString.length) {
		when (val char = toString[i]) {
			'(', '[', '{' -> {
				indentLevel++
				stringBuilder.appendLine(char).append(padding())
			}

			')', ']', '}' -> {
				indentLevel--
				stringBuilder.appendLine().append(padding()).append(char)
			}

			',' -> {
				stringBuilder.appendLine(char).append(padding())
				// ignore space after comma as we have added a newline
				val nextChar = toString.getOrElse(i + 1) { char }
				if (nextChar == ' ') i++
			}

			else -> {
				stringBuilder.append(char)
			}
		}
		i++
	}

	return stringBuilder.toString().replace("=", " = ")
}