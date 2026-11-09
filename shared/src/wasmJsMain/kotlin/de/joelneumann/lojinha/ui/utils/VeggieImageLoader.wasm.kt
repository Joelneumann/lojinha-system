package de.joelneumann.lojinha.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jetbrains.compose.resources.decodeToImageBitmap

private val imageHttpClient by lazy { HttpClient() }
private val imageBitmapCache = mutableMapOf<String, ImageBitmap>()

actual suspend fun loadVeggieImageBitmap(
    productId: String,
    imageFileName: String?,
    fullSize: Boolean
): ImageBitmap? {
    if (imageFileName.isNullOrBlank()) return null
    val cacheKey = "$productId:$imageFileName:$fullSize"
    imageBitmapCache[cacheKey]?.let { return it }

    return try {
        val sizeParam = if (fullSize) "full" else "thumb"
        val response = imageHttpClient.get("/api/veggie/images/$productId?size=$sizeParam&v=$imageFileName")
        if (response.status == HttpStatusCode.OK) {
            val bytes = response.readRawBytes()
            if (bytes.isNotEmpty()) {
                val bitmap = bytes.decodeToImageBitmap()
                imageBitmapCache[cacheKey] = bitmap
                bitmap
            } else {
                null
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

actual fun invalidateVeggieImageCache(productId: String?) {
    if (productId == null) {
        imageBitmapCache.clear()
    } else {
        val prefix = "$productId:"
        val keysToRemove = imageBitmapCache.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { imageBitmapCache.remove(it) }
    }
}
