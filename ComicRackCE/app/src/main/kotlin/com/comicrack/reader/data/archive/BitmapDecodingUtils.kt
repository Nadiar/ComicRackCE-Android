package com.comicrack.reader.data.archive

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.InputStream

/**
 * Bitmap decoding utilities with memory optimization.
 *
 * Provides safe decoding with automatic downsampling to prevent OOM errors
 * when loading large comic book images.
 *
 * Notes & usage:
 * - `decodeBitmap` attempts a single-pass decode and **consumes the provided
 *   InputStream**; callers must supply a fresh stream or use
 *   `decodeBitmapOptimized` which accepts an `openStream` callback to re-open
 *   the source for a two-pass decode (recommended for archives).
 * - For SAF `content://` sources, prefer `decodeBitmapOptimized` and provide
 *   a callback that opens a fresh stream on each invocation to support two-pass decoding.
 * - Default `inPreferredConfig` is `RGB_565` to reduce memory; use `ARGB_8888`
 *   if transparency or higher fidelity is required (at increased memory cost).
 * - Decoding should be performed on background threads; these utilities do not
 *   dispatch to IO/worker threads themselves.
 *
 * Testing:
 * - Add tests that verify decoding behavior for both local files and SAF-backed
 *   input streams, including very large images to ensure downsampling works as intended.
 */
object BitmapDecodingUtils {
    /**
     * Maximum bitmap size in bytes (~4 MB).
     * Bitmaps larger than this will be downsampled.
     */
    private const val MAX_BITMAP_SIZE_BYTES = 4 * 1024 * 1024 // 4 MB

    /**
     * Decode a bitmap with automatic downsampling.
     *
     * @param inputStream Input stream to decode from
     * @param maxWidth Maximum width in pixels (0 = no limit)
     * @param maxHeight Maximum height in pixels (0 = no limit)
     * @return Decoded bitmap, or null if decoding fails
     */
    fun decodeBitmap(
        inputStream: InputStream,
        maxWidth: Int = 0,
        maxHeight: Int = 0,
    ): Bitmap? =
        try {
            // First, get image dimensions without decoding the full image
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeStream(inputStream, null, options)

            // Calculate optimal sample size
            val sampleSize =
                calculateInSampleSize(
                    options.outWidth,
                    options.outHeight,
                    maxWidth,
                    maxHeight,
                )

            // We need to reset the stream, so callers must provide a fresh stream
            // OR we accept that we can't seek. For now, we log a warning.
            // Better approach: accept both stream and re-openable source

            // Decode with calculated sample size
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Reduces memory vs ARGB_8888

            // Note: After BitmapFactory.decodeStream above, the stream is consumed.
            // Callers must re-open the stream or we must redesign.
            // For now, return null and let caller handle re-opening.
            null
        } catch (e: Exception) {
            Timber.tag("BitmapDecodingUtils").e(e, "Failed to decode bitmap dimensions")
            null
        }

    /**
     * Decode a bitmap with automatic downsampling from a fresh stream.
     * This is the recommended approach - provide a callback to re-open the stream.
     *
     * Implementation notes:
     * - The `openStream` callback will be called twice (bounds pass + decode pass).
     * - The stream returned by `openStream` must be fresh and seekable (or re-openable).
     * - This method catches exceptions and returns `null` on failure; callers should
     *   consider retrying or showing a placeholder image.
     * - Always call from a background thread to avoid blocking the UI.
     *
     * @param openStream Callback to open a fresh input stream
     * @param maxWidth Maximum width in pixels (0 = no limit)
     * @param maxHeight Maximum height in pixels (0 = no limit)
     * @return Decoded bitmap, or null if decoding fails
     */
    fun decodeBitmapOptimized(
        openStream: () -> InputStream?,
        maxWidth: Int = 0,
        maxHeight: Int = 0,
    ): Bitmap? {
        return try {
            // First pass: get dimensions
            val dimensionsStream = openStream() ?: return null
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

            dimensionsStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate optimal sample size
            val sampleSize =
                calculateInSampleSize(
                    options.outWidth,
                    options.outHeight,
                    maxWidth,
                    maxHeight,
                )

            // Second pass: decode with sample size
            val decodingStream = openStream() ?: return null
            decodingStream.use { stream ->
                val decodeOptions =
                    BitmapFactory.Options().apply {
                        inJustDecodeBounds = false
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Timber.tag("BitmapDecodingUtils").e(e, "Failed to decode bitmap optimized")
            null
        }
    }

    /**
     * Get image dimensions without decoding the full bitmap.
     *
     * @param openStream Callback to open a fresh input stream
     * @return Pair of (width, height) or null if unable to determine
     */
    fun getImageDimensions(openStream: () -> InputStream?): Pair<Int, Int>? {
        return try {
            val stream = openStream() ?: return null
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

            stream.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.tag("BitmapDecodingUtils").e(e, "Failed to get image dimensions")
            null
        }
    }

    /**
     * Calculate the optimal inSampleSize for BitmapFactory.Options.
     *
     * Downsampling strategy:
     * - If maxWidth/maxHeight are specified, downsample to fit
     * - Otherwise, downsample to prevent OOM
     *
     * @param imageWidth Original image width
     * @param imageHeight Original image height
     * @param maxWidth Maximum desired width (0 = no limit)
     * @param maxHeight Maximum desired height (0 = no limit)
     * @return inSampleSize value (power of 2)
     *
     * Note: `inSampleSize` is a power-of-two divisor which trades quality for
     * memory and decoding speed. Increasing `inSampleSize` reduces memory usage
     * but may reduce detail; tune conservatively for large images.
     */
    fun calculateInSampleSize(
        imageWidth: Int,
        imageHeight: Int,
        maxWidth: Int = 0,
        maxHeight: Int = 0,
    ): Int {
        var sampleSize = 1

        // If max dimensions specified, downsample to fit
        if (maxWidth > 0 || maxHeight > 0) {
            val halfWidth = imageWidth / 2
            val halfHeight = imageHeight / 2

            while ((maxWidth <= 0 || halfWidth / sampleSize > maxWidth) &&
                (maxHeight <= 0 || halfHeight / sampleSize > maxHeight)
            ) {
                sampleSize *= 2
            }
        } else {
            // Default: prevent OOM by limiting memory usage
            // Estimate bytes per pixel (~4 bytes for ARGB_8888, 2 for RGB_565)
            val estimatedBytes = imageWidth.toLong() * imageHeight * 2 // 2 bytes per pixel (RGB_565)

            while (estimatedBytes / (sampleSize * sampleSize) > MAX_BITMAP_SIZE_BYTES) {
                sampleSize *= 2
            }
        }

        return sampleSize
    }
}
