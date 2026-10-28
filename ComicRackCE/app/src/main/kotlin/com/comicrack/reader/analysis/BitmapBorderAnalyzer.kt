package com.comicrack.reader.analysis

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * analyzes a bitmap to determine if it has a dominant border color.
 * Used to seamlessly blend the viewer background with the comic page.
 */
object BitmapBorderAnalyzer {
    // Threshold for what we consider "the same color" (Euclidean distance in RGB)
    private const val COLOR_TOLERANCE = 15.0

    // Percentage of pixels that must match the dominant color to be accepted
    private const val DOMINANCE_THRESHOLD = 0.85f

    /**
     * Analyze the borders of the bitmap and return a background color if one is dominant.
     * Returns null if no single color is dominant enough.
     */
    fun analyze(bitmap: Bitmap): Int? {
        val width = bitmap.width
        val height = bitmap.height

        if (width < 20 || height < 20) return null

        val pixels = IntArray(width * 4) // Buffer for one edge (max dimension + safety)
        val sampledColors = mutableListOf<Int>()

        // 1. Sample Top Edge (skip very corners and very edge to avoid artifacts)
        // We'll sample a line 2 pixels in.
        val topRow = IntArray(width)
        bitmap.getPixels(topRow, 0, width, 0, 1, width, 1)
        sampleLine(topRow, sampledColors)

        // 2. Sample Bottom Edge
        val bottomRow = IntArray(width)
        bitmap.getPixels(bottomRow, 0, width, 0, height - 2, width, 1)
        sampleLine(bottomRow, sampledColors)

        // 3. Sample Left Edge
        // getPixels for columns is tricky, usually easier to just loop getPixel or grab full block
        // For performance on large bitmaps, we limit getPixel calls.
        // Let's sample 20 points along left and right edges.
        val verticalSteps = 20
        val stepY = height / (verticalSteps + 1)

        for (i in 1..verticalSteps) {
            val y = i * stepY
            sampledColors.add(bitmap.getPixel(1, y))
            sampledColors.add(bitmap.getPixel(width - 2, y))
        }

        if (sampledColors.isEmpty()) return null

        return findDominantColor(sampledColors)
    }

    private fun sampleLine(
        pixels: IntArray,
        output: MutableList<Int>,
    ) {
        // Sample every Nth pixel to save time
        val step = (pixels.size / 20).coerceAtLeast(1)
        for (i in 0 until pixels.size step step) {
            output.add(pixels[i])
        }
    }

    private fun findDominantColor(samples: List<Int>): Int? {
        // Simple heuristic: Take the first valid sample as candidate, compare others against it.
        // Or find the most frequent.
        // We generally look for Black (very common) or White (common).

        if (samples.isEmpty()) return null

        // Count occurrences (group by similarity)
        val groups = mutableListOf<ColorGroup>()

        for (color in samples) {
            val existingGroup = groups.find { it.matches(color) }
            if (existingGroup != null) {
                existingGroup.add(color)
            } else {
                groups.add(ColorGroup(color))
            }
        }

        // Find largest group
        val dominant = groups.maxByOrNull { it.count } ?: return null

        val totalSamples = samples.size
        val dominance = dominant.count.toFloat() / totalSamples

        return if (dominance >= DOMINANCE_THRESHOLD) {
            dominant.averageColor()
        } else {
            null
        }
    }

    private class ColorGroup(
        initialColor: Int,
    ) {
        var redSum: Long = Color.red(initialColor).toLong()
        var greenSum: Long = Color.green(initialColor).toLong()
        var blueSum: Long = Color.blue(initialColor).toLong()
        var count: Int = 1

        fun matches(color: Int): Boolean {
            val avgR = redSum / count
            val avgG = greenSum / count
            val avgB = blueSum / count

            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            val distance =
                sqrt(
                    (
                        (avgR - r) * (avgR - r) +
                            (avgG - g) * (avgG - g) +
                            (avgB - b) * (avgB - b)
                    ).toDouble(),
                )

            return distance <= COLOR_TOLERANCE
        }

        fun add(color: Int) {
            redSum += Color.red(color)
            greenSum += Color.green(color)
            blueSum += Color.blue(color)
            count++
        }

        fun averageColor(): Int =
            Color.rgb(
                (redSum / count).toInt(),
                (greenSum / count).toInt(),
                (blueSum / count).toInt(),
            )
    }
}
