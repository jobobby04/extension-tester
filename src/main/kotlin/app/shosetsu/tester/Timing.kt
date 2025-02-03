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

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 *  @since 2024 / 05 / 19
 */

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