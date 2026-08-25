package de.joelneumann.lojinha.ui.utils

actual fun pickFolder(onSelect: (String) -> Unit) {
    onSelect("/backups")
}

actual fun pickFile(title: String, extensionFilter: String?, onSelect: (PlatformFile) -> Unit) {
    onSelect(PlatformFile("backup$extensionFilter", "/backups/backup$extensionFilter"))
}
