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

import java.util.Stack
import org.junit.Ignore
import org.junit.Test

/**
 * extension-tester
 * 06 / 11 / 2021
 */
@Ignore
class ArgumentStackTest {

	fun takeArgs(args: Array<String>) {
		val stack = Stack<String>()
		args.reversed().forEach(stack::add)
		println(stack.pop())
	}

	@Test
	fun test() {
		takeArgs(arrayOf("-r", "/path/to/repo", "-e", "/path/to/ext"))
	}
}
