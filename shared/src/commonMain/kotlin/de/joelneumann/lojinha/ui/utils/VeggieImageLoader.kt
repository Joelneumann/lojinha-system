package de.joelneumann.lojinha.ui.utils

import androidx.compose.ui.graphics.ImageBitmap

expect suspend fun loadVeggieImageBitmap(
    productId: String,
    imageFileName: String?,
    fullSize: Boolean = false
): ImageBitmap?

expect fun invalidateVeggieImageCache(productId: String? = null)
