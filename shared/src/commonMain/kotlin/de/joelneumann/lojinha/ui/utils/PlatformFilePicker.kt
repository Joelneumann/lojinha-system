package de.joelneumann.lojinha.ui.utils

expect fun pickFolder(onSelect: (String) -> Unit)
expect fun pickFile(title: String, extensionFilter: String?, onSelect: (PlatformFile) -> Unit)
