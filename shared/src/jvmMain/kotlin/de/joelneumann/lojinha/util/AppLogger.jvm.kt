package de.joelneumann.lojinha.util

actual object AppLogger {
    actual fun debug(tag: String, message: String) {
        FileRollingLogger.log("DEBUG", tag, message)
    }

    actual fun info(tag: String, message: String) {
        FileRollingLogger.log("INFO", tag, message)
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        FileRollingLogger.log("WARN", tag, message, throwable)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        FileRollingLogger.log("ERROR", tag, message, throwable)
    }
}
