package de.joelneumann.lojinha.ui.utils

expect fun pickFolder(onSelect: (String) -> Unit)
expect fun pickFile(title: String, extensionFilter: String?, onSelect: (PlatformFile) -> Unit)
expect fun pickImageBytes(title: String, onSelect: (fileName: String, bytes: ByteArray) -> Unit)
