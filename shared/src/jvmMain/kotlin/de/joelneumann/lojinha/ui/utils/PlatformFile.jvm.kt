package de.joelneumann.lojinha.ui.utils

import java.io.File

actual class PlatformFile actual constructor(
    actual val name: String,
    actual val absolutePath: String
) {
    val file: File = File(absolutePath)
    actual fun exists(): Boolean = file.exists()
}
