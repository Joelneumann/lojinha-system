package de.joelneumann.lojinha.ui.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual fun pickFolder(onSelect: (String) -> Unit) {
    onSelect("/backups")
}

actual fun pickFile(title: String, extensionFilter: String?, onSelect: (PlatformFile) -> Unit) {
    onSelect(PlatformFile("backup$extensionFilter", "/backups/backup$extensionFilter"))
}

@JsFun("""
(onLoaded) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/jpeg,image/png,image/webp,image/gif';
    input.onchange = (e) => {
        const file = e.target.files && e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = () => {
            const dataUrl = typeof reader.result === 'string' ? reader.result : '';
            const commaIdx = dataUrl.indexOf(',');
            const base64 = commaIdx >= 0 ? dataUrl.substring(commaIdx + 1) : '';
            if (base64.length > 0) {
                onLoaded(file.name || 'image.jpg', base64);
            }
        };
        reader.readAsDataURL(file);
    };
    input.click();
}
""")
private external fun jsPickImageFileBase64(onLoaded: (String, String) -> Unit)

@OptIn(ExperimentalEncodingApi::class)
actual fun pickImageBytes(title: String, onSelect: (fileName: String, bytes: ByteArray) -> Unit) {
    jsPickImageFileBase64 { fileName, base64 ->
        try {
            val bytes = Base64.Default.decode(base64)
            onSelect(fileName, bytes)
        } catch (e: Exception) {
            // Ignore malformed file selection
        }
    }
}
