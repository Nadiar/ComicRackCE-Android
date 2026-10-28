package com.comicrack.reader.image

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import com.comicrack.reader.analysis.BitmapBorderAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A Coil Transformation that doesn't alter the image,
 * but inspects it to determine the border color.
 */
class BorderColorTransformation(
    private val onColorFound: (Int?) -> Unit,
) : Transformation() {
    override val cacheKey: String = "BorderColorTransformation"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        // Run analysis on a background thread to avoid blocking decoding
        // Note: transform is already called on a worker thread by Coil usually,
        // but analysis might be heavy so being explicit doesn't hurt.
        val color =
            withContext(Dispatchers.Default) {
                BitmapBorderAnalyzer.analyze(input)
            }

        // Report result
        // Note: Callback likely needs to land on Main thread if it updates UI directly,
        // but generally standard practice is to emit, and let consumer switch to Main.
        // Coil callbacks run on whatever thread Coil is using.
        onColorFound(color)

        // Return original bitmap unmodified
        return input
    }
}
