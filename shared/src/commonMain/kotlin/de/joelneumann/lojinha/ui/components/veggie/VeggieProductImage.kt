package de.joelneumann.lojinha.ui.components.veggie

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.joelneumann.lojinha.ui.theme.ColorSuccessEmerald
import de.joelneumann.lojinha.ui.theme.SurfaceContainerHighLight
import de.joelneumann.lojinha.ui.utils.getCachedVeggieImageBitmap
import de.joelneumann.lojinha.ui.utils.loadVeggieImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
fun VeggieProductImage(
    productId: String,
    imageFileName: String?,
    modifier: Modifier = Modifier,
    fullSize: Boolean = false,
    previewBytes: ByteArray? = null,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    placeholderIconSize: Dp = 24.dp,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null
) {
    val previewBitmap = remember(previewBytes) {
        if (previewBytes != null && previewBytes.isNotEmpty()) {
            runCatching { previewBytes.decodeToImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    val cachedBitmap = remember(productId, imageFileName, fullSize, previewBytes) {
        if (previewBytes == null && !imageFileName.isNullOrBlank()) {
            getCachedVeggieImageBitmap(productId, imageFileName, fullSize)
        } else {
            null
        }
    }

    val loadedBitmap by produceState<ImageBitmap?>(
        initialValue = cachedBitmap,
        productId,
        imageFileName,
        fullSize,
        previewBytes
    ) {
        value = if (cachedBitmap != null) {
            cachedBitmap
        } else if (previewBytes == null && !imageFileName.isNullOrBlank()) {
            loadVeggieImageBitmap(productId, imageFileName, fullSize)
        } else {
            null
        }
    }

    val bitmap = previewBitmap ?: cachedBitmap ?: loadedBitmap

    val clickModifier = if (onClick != null) {
        Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .then(clickModifier)
            .focusProperties { canFocus = false }
            .background(SurfaceContainerHighLight),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Eco,
                contentDescription = contentDescription,
                tint = ColorSuccessEmerald.copy(alpha = 0.65f),
                modifier = Modifier.size(placeholderIconSize)
            )
        }
    }
}
