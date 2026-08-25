package de.joelneumann.lojinha.ui.utils

import java.awt.FileDialog
import java.awt.Frame

actual fun pickFolder(onSelect: (String) -> Unit) {
    try {
        val dialog = FileDialog(null as Frame?, "Select Backup Destination Directory", FileDialog.LOAD)
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        System.setProperty("apple.awt.fileDialogForDirectories", "false")
        if (dir != null && file != null) {
            val f = java.io.File(dir, file)
            val path = if (f.isDirectory) f.absolutePath else f.parentFile?.absolutePath ?: f.absolutePath
            onSelect(path)
        } else if (dir != null) {
            onSelect(dir)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun pickFile(title: String, extensionFilter: String?, onSelect: (PlatformFile) -> Unit) {
    try {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        if (extensionFilter != null) {
            dialog.file = "*$extensionFilter"
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val selected = java.io.File(dir, file)
            if (selected.exists()) {
                onSelect(PlatformFile(selected.name, selected.absolutePath))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
