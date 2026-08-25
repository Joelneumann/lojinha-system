package de.joelneumann.lojinha.ui.utils

import kotlinx.datetime.Clock

expect class PlatformFile(name: String, absolutePath: String) {
    val name: String
    val absolutePath: String
    fun exists(): Boolean
}

fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
