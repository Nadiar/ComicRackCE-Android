package com.comicrack.reader.image

/**
 * ImageAdjustmentTransformation - Image color and sharpness adjustments used by the reader.
 *
 * Notes:
 * - Keep transformations fast and allocation-light; prefer small lookup tables and
 *   avoid allocating temporary bitmaps on the UI thread.
 * - Add unit tests that validate color adjustments across a representative set of
 *   pixel values to avoid regression in color math.
 * - Document effect ranges (e.g., sharpness/saturation normalized ranges) so UI
 *   sliders can enforce correct bounds.
 */

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Coil 3 transformation that adjusts image saturation, contrast and sharpness used by the reader.
 *
 * @param saturation Multiplicative saturation factor (0.0 = grayscale, 1.0 = unchanged, 2.0 = vivid).
 * @param contrast Multiplicative contrast factor (0.0 = flat, 1.0 = unchanged, 2.0 = high contrast).
 * @param sharpness Sharpness factor where 1.0 = unchanged. Values > 1.0 apply a local
 *   sharpening kernel; values < 1.0 are not implemented and are treated as no-op.
 *
 * The transformation uses a [ColorMatrix] for saturation and contrast (fast, efficient)
 * and a small 3x3 convolution pass for sharpening when `sharpness > 1.0` (an O(N) pixel operation).
 * To avoid unnecessary allocations, the input `Bitmap` is returned unchanged when all parameters are
 * default (1.0).
 */
class ImageAdjustmentTransformation(
    private val saturation: Float = 1f,
    private val contrast: Float = 1f,
    private val sharpness: Float = 1f,
) : Transformation() {
    override val cacheKey: String = "adjust_s${saturation}_c${contrast}_sh$sharpness"

    /**
     * Apply configured adjustments to [input].
     *
     * Returns [input] unchanged when all adjustments are default (performance optimization).
     * @param input Source bitmap; may be returned directly when no changes are required.
     * @param size Target [Size] supplied by Coil (unused but included for compatibility).
     * @return A bitmap with adjustments applied, or the original [input] when unchanged.
     */
    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        // Avoid allocations and heavy processing when parameters are default; return input as-is.
        if (saturation == 1f && contrast == 1f && sharpness == 1f) {
            return input
        }

        val output = Bitmap.createBitmap(input.width, input.height, input.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 1. Combine Saturation and Contrast into a ColorMatrix
        val matrix = ColorMatrix()

        // Saturation
        if (saturation != 1f) {
            matrix.setSaturation(saturation)
        }

        // Contrast
        // Implement contrast as a multiplicative scale with a translate offset that preserves
        // perceived mid-tones (translate computed to keep mid-gray stable when scaling).
        if (contrast != 1f) {
            val scale = contrast
            val translate = (-0.5f * scale + 0.5f) * 255f
            val contrastMatrix =
                ColorMatrix(
                    floatArrayOf(
                        scale,
                        0f,
                        0f,
                        0f,
                        translate,
                        0f,
                        scale,
                        0f,
                        0f,
                        translate,
                        0f,
                        0f,
                        scale,
                        0f,
                        translate,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                    ),
                )
            matrix.postConcat(contrastMatrix)
        }

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(input, 0f, 0f, paint)

        // 2. Apply sharpness only when requested: convolution is O(N) and can be expensive on large bitmaps.
        // The implementation uses a compact 3x3 kernel (Laplacian-like) to increase local edge contrast.
        // Softening (sharpness < 1.0) is not implemented to avoid adding a separate blur pass.
        // Note: RenderScript-based options were evaluated but RenderScript is deprecated; current approach
        // favors a simple CPU kernel to keep dependencies minimal and behavior deterministic.

        if (sharpness > 1f) {
            return applySharpen(output, sharpness)
        }

        return output
    }

    /**
     * Internal helper: apply a simple 3x3 sharpening kernel.
     * `amount` is a user-facing multiplier (1.0 = identity); it is mapped to kernel weight `a`
     * to control edge emphasis. The function writes into a new bitmap to avoid mutating the input.
     */
    private fun applySharpen(
        bitmap: Bitmap,
        amount: Float,
    ): Bitmap {
        // 'amount' is mapped into kernel weight 'a' which controls the strength of the local contrast.
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val resultPixels = IntArray(width * height)

        // Simple 3x3 Sharpen Kernel:
        // [ 0 -a  0]
        // [-a 1+4a -a]
        // [ 0 -a  0]
        val a = (amount - 1f) * 0.5f // Scale factor

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                var r = 0f
                var g = 0f
                var b = 0f

                // Fast path for 3x3
                // (0,0) (0,1) (0,2)
                // (1,0) (1,1) (1,2)
                // (2,0) (2,1) (2,2)

                // Center pixel: (1+4a)
                val c = pixels[idx]
                val cr = (c shr 16 and 0xFF).toFloat()
                val cg = (c shr 8 and 0xFF).toFloat()
                val cb = (c and 0xFF).toFloat()

                r += cr * (1f + 4f * a)
                g += cg * (1f + 4f * a)
                b += cb * (1f + 4f * a)

                // Neighbors: -a
                val neighbors =
                    intArrayOf(
                        idx - 1,
                        idx + 1,
                        idx - width,
                        idx + width,
                    )

                for (nId in neighbors) {
                    val p = pixels[nId]
                    r -= (p shr 16 and 0xFF) * a
                    g -= (p shr 8 and 0xFF) * a
                    b -= (p and 0xFF) * a
                }

                val finalR = r.toInt().coerceIn(0, 255)
                val finalG = g.toInt().coerceIn(0, 255)
                val finalB = b.toInt().coerceIn(0, 255)

                resultPixels[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        // Edges are left unprocessed to avoid out-of-bounds sampling and to keep the implementation
        // performant; this is an intentional simplification.
        val adjustedBitmap = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        adjustedBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return adjustedBitmap
    }
}
