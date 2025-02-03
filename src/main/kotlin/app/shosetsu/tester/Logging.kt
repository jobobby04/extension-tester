package app.shosetsu.tester

import java.util.logging.Level

// this being inline ensures that the level is set before any loggers are created
@Suppress("NOTHING_TO_INLINE")
inline fun setupLogging(level: Level? = Level.INFO) {
    // java.util.logging is the default backend for System.Logger.
    // If you use a different backend, setting the level is up to you.
    if (level != null) System.setProperty("java.util.logging.ConsoleHandler.level", level.name)
    val formatKey = "java.util.logging.SimpleFormatter.format"
    if (System.getProperty(formatKey) == null) System.setProperty(formatKey, "%4\$s - %5\$s %n")
}

val logger: System.Logger = System.getLogger("Extension Tester")

fun System.Logger.debug(message: () -> String) = this.log(System.Logger.Level.DEBUG, message)
fun System.Logger.info(message: () -> String) = this.log(System.Logger.Level.INFO, message)
fun System.Logger.warn(message: () -> String) = this.log(System.Logger.Level.WARNING, message)
fun System.Logger.error(message: () -> String) = this.log(System.Logger.Level.ERROR, message)

fun System.Logger.error(e: Throwable, message: () -> String) = this.log(System.Logger.Level.ERROR, message, e)