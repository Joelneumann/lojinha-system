package de.joelneumann.lojinha.data.service

import de.joelneumann.lojinha.ui.utils.removeAccents
import de.joelneumann.lojinha.util.AppLogger
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.min
import kotlin.math.roundToInt

class VeggieImageService(
    private val imageDirectory: File = File(System.getProperty("user.home"), ".lojinha/veggie_images")
) {
    companion object {
        private const val TAG = "VeggieImageService"
        const val THUMB_SIZE_PX = 96
        const val FULL_MAX_DIMENSION_PX = 800
        private const val MAX_DECODE_DIMENSION_PX = 8000
        private const val MAX_DECODE_PIXELS = 40_000_000L
        private const val THUMB_QUALITY = 0.82f
        private const val FULL_QUALITY = 0.85f

        fun sanitizeProductNameToImageKey(productName: String?): String {
            if (productName.isNullOrBlank()) return ""
            return productName
                .removeAccents()
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
        }
    }

    init {
        if (!imageDirectory.exists()) {
            imageDirectory.mkdirs()
        }
    }

    fun getImageDirectory(): File = imageDirectory

    /**
     * Determines an image key based on [productName].
     * If [productName] is available, generates a clean key (e.g. `tomate`).
     * If there are multiple products/files with the same name, appends `_2`, `_3`, etc.
     */
    fun generateUniqueImageKey(
        productId: String,
        productName: String? = null,
        previousImageFileName: String? = null,
        existingImageKeys: Collection<String>? = null
    ): String {
        val cleanPrev = sanitizeFileName(previousImageFileName)
        val base = sanitizeProductNameToImageKey(productName).ifBlank {
            productId.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "product" }
        }

        var index = 1
        while (true) {
            val candidate = if (index == 1) base else "${base}_$index"

            // If candidate matches the product's own previous image file name, it's not a collision
            if (cleanPrev != null && candidate.equals(cleanPrev, ignoreCase = true)) {
                return candidate
            }

            // Check if any other product has this key
            val inExistingKeys = existingImageKeys?.any { key ->
                val cleanKey = sanitizeFileName(key)
                cleanKey != null && cleanKey.equals(candidate, ignoreCase = true)
            } == true

            // Check if files exist on disk for this candidate
            val fileExistsOnDisk = isAnyImageFileExisting(candidate)

            if (!inExistingKeys && !fileExistsOnDisk) {
                return candidate
            }
            index++
        }
    }

    private fun isAnyImageFileExisting(candidate: String): Boolean {
        val suffixes = listOf("_thumb.jpg", "_full.jpg", ".jpg", ".jpeg", ".png", ".webp")
        return suffixes.any { suffix ->
            val f = File(imageDirectory, "$candidate$suffix")
            f.exists() && f.isFile
        }
    }

    /**
     * Decodes [rawImageBytes], generates both `<imageKey>_thumb.jpg` (`96x96` square center-cropped)
     * and `<imageKey>_full.jpg` (up to `800x800` preserving aspect ratio) inside [imageDirectory],
     * removes older image files for [previousImageFileName] (unless still in use by another product),
     * and returns the new `imageFileName` key.
     */
    fun saveProductImage(
        productId: String,
        rawImageBytes: ByteArray,
        previousImageFileName: String? = null,
        productName: String? = null,
        existingImageKeys: Collection<String>? = null
    ): String {
        require(rawImageBytes.isNotEmpty()) { "Image byte array cannot be empty" }
        if (!imageDirectory.exists()) {
            imageDirectory.mkdirs()
        }

        validateImageDimensions(rawImageBytes)
        val sourceImage = ImageIO.read(ByteArrayInputStream(rawImageBytes))
            ?: throw IllegalArgumentException("Unsupported or corrupted image format")

        val imageKey = generateUniqueImageKey(
            productId = productId,
            productName = productName,
            previousImageFileName = previousImageFileName,
            existingImageKeys = existingImageKeys
        )

        val thumbImg = createSquareThumbnail(sourceImage, THUMB_SIZE_PX)
        val fullImg = createScaledImagePreservingAspect(sourceImage, FULL_MAX_DIMENSION_PX)

        val thumbFile = File(imageDirectory, "${imageKey}_thumb.jpg")
        val fullFile = File(imageDirectory, "${imageKey}_full.jpg")

        writeJpegFile(thumbImg, thumbFile, THUMB_QUALITY)
        writeJpegFile(fullImg, fullFile, FULL_QUALITY)

        if (!previousImageFileName.isNullOrBlank() && previousImageFileName != imageKey) {
            deleteProductImages(previousImageFileName, existingImageKeys)
        }

        AppLogger.info(TAG, "Saved veggie images for '$productId' ($productName) as '$imageKey' (thumb: ${thumbFile.length()} B, full: ${fullFile.length()} B)")
        return imageKey
    }

    fun getThumbnailFile(imageFileName: String?): File? {
        val clean = sanitizeFileName(imageFileName) ?: return null
        val thumbFile = File(imageDirectory, "${clean}_thumb.jpg")
        if (thumbFile.exists() && thumbFile.isFile) return thumbFile
        val fullFile = File(imageDirectory, "${clean}_full.jpg")
        if (fullFile.exists() && fullFile.isFile) return fullFile
        for (ext in listOf("jpg", "jpeg", "png", "webp")) {
            val f = File(imageDirectory, "$clean.$ext")
            if (f.exists() && f.isFile) return f
        }
        return null
    }

    fun getFullImageFile(imageFileName: String?): File? {
        val clean = sanitizeFileName(imageFileName) ?: return null
        val fullFile = File(imageDirectory, "${clean}_full.jpg")
        if (fullFile.exists() && fullFile.isFile) return fullFile
        val thumbFile = File(imageDirectory, "${clean}_thumb.jpg")
        if (thumbFile.exists() && thumbFile.isFile) return thumbFile
        for (ext in listOf("jpg", "jpeg", "png", "webp")) {
            val f = File(imageDirectory, "$clean.$ext")
            if (f.exists() && f.isFile) return f
        }
        return null
    }

    fun readThumbnailBytes(imageFileName: String?): ByteArray? {
        return getThumbnailFile(imageFileName)?.readBytes()
    }

    fun readFullImageBytes(imageFileName: String?): ByteArray? {
        return getFullImageFile(imageFileName)?.readBytes()
    }

    fun deleteProductImages(imageFileName: String?, existingImageKeys: Collection<String>? = null) {
        val clean = sanitizeFileName(imageFileName) ?: return
        val isStillUsedByOther = existingImageKeys?.any { key ->
            val cleanKey = sanitizeFileName(key)
            cleanKey != null && cleanKey.equals(clean, ignoreCase = true)
        } == true
        if (isStillUsedByOther) return
        listOf("_thumb.jpg", "_full.jpg", ".jpg", ".jpeg", ".png", ".webp").forEach { suffix ->
            File(imageDirectory, "$clean$suffix").takeIf { it.exists() }?.delete()
        }
    }

    private fun validateImageDimensions(rawImageBytes: ByteArray) {
        ImageIO.createImageInputStream(ByteArrayInputStream(rawImageBytes)).use { iis ->
            if (iis == null) throw IllegalArgumentException("Unsupported or corrupted image format")
            val readers = ImageIO.getImageReaders(iis)
            if (!readers.hasNext()) throw IllegalArgumentException("Unsupported or corrupted image format")
            val reader = readers.next()
            try {
                reader.input = iis
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width in 1..MAX_DECODE_DIMENSION_PX && height in 1..MAX_DECODE_DIMENSION_PX) {
                    "Image dimensions (${width}x${height}) exceed maximum allowed (${MAX_DECODE_DIMENSION_PX}x${MAX_DECODE_DIMENSION_PX})"
                }
                require(width.toLong() * height.toLong() <= MAX_DECODE_PIXELS) {
                    "Image pixel count exceeds maximum allowed"
                }
            } finally {
                reader.dispose()
            }
        }
    }

    private fun sanitizeFileName(imageFileName: String?): String? {
        if (imageFileName.isNullOrBlank()) return null
        val trimmed = imageFileName.trim()
            .removeSuffix("_thumb.jpg")
            .removeSuffix("_full.jpg")
            .removeSuffix(".jpg")
            .removeSuffix(".jpeg")
            .removeSuffix(".png")
            .removeSuffix(".webp")
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            return null
        }
        return trimmed.ifEmpty { null }
    }

    private fun createSquareThumbnail(source: BufferedImage, targetSize: Int): BufferedImage {
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        val cropSide = min(srcW, srcH)
        val cropX = (srcW - cropSide) / 2
        val cropY = (srcH - cropSide) / 2
        val cropped = source.getSubimage(cropX, cropY, cropSide, cropSide)
        return scaleMultiStep(cropped, targetSize, targetSize)
    }

    private fun createScaledImagePreservingAspect(source: BufferedImage, maxDimension: Int): BufferedImage {
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        if (srcW <= maxDimension && srcH <= maxDimension) {
            return ensureRgbBufferedImage(source)
        }
        val scale = maxDimension.toDouble() / maxOf(srcW, srcH).toDouble()
        val targetW = (srcW * scale).roundToInt().coerceAtLeast(1)
        val targetH = (srcH * scale).roundToInt().coerceAtLeast(1)
        return scaleMultiStep(source, targetW, targetH)
    }

    private fun ensureRgbBufferedImage(source: BufferedImage): BufferedImage {
        val rgb = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, source.width, source.height)
            g.drawImage(source, 0, 0, null)
        } finally {
            g.dispose()
        }
        return rgb
    }

    private fun scaleMultiStep(source: BufferedImage, targetW: Int, targetH: Int): BufferedImage {
        var current = ensureRgbBufferedImage(source)
        var curW = current.width
        var curH = current.height

        while (curW / 2 >= targetW && curH / 2 >= targetH) {
            curW /= 2
            curH /= 2
            val stepImg = BufferedImage(curW, curH, BufferedImage.TYPE_INT_RGB)
            val g = stepImg.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.drawImage(current, 0, 0, curW, curH, null)
            } finally {
                g.dispose()
            }
            current = stepImg
        }

        if (curW != targetW || curH != targetH) {
            val finalImg = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
            val g = finalImg.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.drawImage(current, 0, 0, targetW, targetH, null)
            } finally {
                g.dispose()
            }
            current = finalImg
        }

        return current
    }

    private fun writeJpegFile(image: BufferedImage, outputFile: File, quality: Float) {
        val writers = ImageIO.getImageWritersByFormatName("jpg")
        check(writers.hasNext()) { "No JPEG ImageWriter available in JVM" }
        val writer = writers.next()
        try {
            val param = writer.defaultWriteParam
            if (param.canWriteCompressed()) {
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                param.compressionQuality = quality.coerceIn(0.1f, 1.0f)
            }
            val bos = ByteArrayOutputStream()
            ImageIO.createImageOutputStream(bos).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(image, null, null), param)
            }
            outputFile.writeBytes(bos.toByteArray())
        } finally {
            writer.dispose()
        }
    }
}
