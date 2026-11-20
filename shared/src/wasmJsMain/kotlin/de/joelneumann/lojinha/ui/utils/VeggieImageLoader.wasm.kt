package de.joelneumann.lojinha.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.decodeToImageBitmap

private val imageHttpClient by lazy {
    HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 8_000L
        }
    }
}
private val imageBitmapCache = mutableMapOf<String, ImageBitmap>()
private val inFlightLoads = mutableMapOf<String, Deferred<ImageBitmap?>>()
private val imageLoadSemaphore = Semaphore(permits = 2)

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

    inFlightLoads[cacheKey]?.let { return it.await() }

    val deferred = CompletableDeferred<ImageBitmap?>()
    inFlightLoads[cacheKey] = deferred
    try {
        val result = imageLoadSemaphore.withPermit {
            imageBitmapCache[cacheKey]?.let { return@withPermit it }
            try {
                val isThumb = !fullSize
                val response = imageHttpClient.get("/api/veggie/images/${productId.encodeURLPathPart()}") {
                    parameter("thumb", isThumb)
                    parameter("v", imageFileName)
                }
                if (response.status == HttpStatusCode.OK) {
                    val bytes = response.readRawBytes()
                    if (bytes.isNotEmpty()) {
                        yield()
                        val bitmap = bytes.decodeToImageBitmap()
                        imageBitmapCache[cacheKey] = bitmap
                        yield()
                        bitmap
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
        deferred.complete(result)
        return result
    } catch (t: Throwable) {
        if (t is CancellationException) {
            // Do not fail unrelated waiters when one LazyColumn card scrolls offscreen
            deferred.complete(null)
        } else {
            deferred.completeExceptionally(t)
        }
        throw t
    } finally {
        if (inFlightLoads[cacheKey] === deferred) {
            inFlightLoads.remove(cacheKey)
        }
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
