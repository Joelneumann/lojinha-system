package de.joelneumann.lojinha.ui.utils

import java.awt.FileDialog
import java.awt.Frame

actual fun pickFolder(onSelect: (String) -> Unit) {
    try {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {}

        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("mac")) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            val dialog = FileDialog(null as Frame?, "Select Backup Destination Directory", FileDialog.LOAD)
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            System.setProperty("apple.awt.fileDialogForDirectories", "false")

            if (dir != null) {
                val path = if (file != null) {
                    val f = java.io.File(dir, file)
                    if (f.isDirectory) f.absolutePath else f.parentFile?.absolutePath ?: dir
                } else {
                    dir
                }
                onSelect(path)
            }
        } else {
            val chooser = javax.swing.JFileChooser().apply {
                dialogTitle = "Select Backup Destination Directory"
                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            val result = chooser.showOpenDialog(null)
            if (result == javax.swing.JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                onSelect(chooser.selectedFile.absolutePath)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun pickFile(title: String, extensionFilter: String?, onSelect: (PlatformFile) -> Unit) {
    try {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {}

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
