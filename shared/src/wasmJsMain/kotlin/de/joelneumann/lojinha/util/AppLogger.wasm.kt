package de.joelneumann.lojinha.util

actual object AppLogger {
    actual fun debug(tag: String, message: String) {
        println("[DEBUG] [$tag] $message")
    }

    actual fun info(tag: String, message: String) {
        println("[INFO] [$tag] $message")
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        println("[WARN] [$tag] $message")
        throwable?.let { println(it.stackTraceToString()) }
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        println("[ERROR] [$tag] $message")
        throwable?.let { println(it.stackTraceToString()) }
    }
}
