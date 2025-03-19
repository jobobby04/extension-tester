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

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 *  @since 2024 / 05 / 19
 */
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