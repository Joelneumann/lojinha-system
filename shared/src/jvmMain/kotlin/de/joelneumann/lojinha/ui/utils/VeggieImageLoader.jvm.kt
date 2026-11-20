package de.joelneumann.lojinha.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import de.joelneumann.lojinha.data.service.VeggieImageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import java.util.concurrent.ConcurrentHashMap

private val defaultImageService by lazy { VeggieImageService() }
private val imageBitmapCache = ConcurrentHashMap<String, ImageBitmap>()

actual fun getCachedVeggieImageBitmap(
    productId: String,
    imageFileName: String?,
    fullSize: Boolean
): ImageBitmap? {
    if (imageFileName.isNullOrBlank()) return null
    val cacheKey = "$productId:$imageFileName:$fullSize"
    return imageBitmapCache[cacheKey]
}

actual suspend fun loadVeggieImageBitmap(
    productId: String,
    imageFileName: String?,
    fullSize: Boolean
): ImageBitmap? {
    if (imageFileName.isNullOrBlank()) return null
    val cacheKey = "$productId:$imageFileName:$fullSize"
    imageBitmapCache[cacheKey]?.let { return it }

    return withContext(Dispatchers.IO) {
        try {
            val bytes = if (fullSize) {
                defaultImageService.readFullImageBytes(imageFileName)
            } else {
                defaultImageService.readThumbnailBytes(imageFileName)
            }
            if (bytes != null && bytes.isNotEmpty()) {
                val bitmap = bytes.decodeToImageBitmap()
                imageBitmapCache[cacheKey] = bitmap
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

actual fun invalidateVeggieImageCache(productId: String?) {
    if (productId == null) {
        imageBitmapCache.clear()
    } else {
        val prefix = "$productId:"
        imageBitmapCache.keys.removeIf { it.startsWith(prefix) }
    }
}
