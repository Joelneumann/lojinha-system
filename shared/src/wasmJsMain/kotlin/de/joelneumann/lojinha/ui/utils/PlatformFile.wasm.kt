package de.joelneumann.lojinha.ui.utils

actual class PlatformFile actual constructor(
    actual val name: String,
    actual val absolutePath: String
) {
    actual fun exists(): Boolean = false
}
