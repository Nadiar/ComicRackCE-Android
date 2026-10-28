package com.comicrack.data.database.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ReadProgress percentage calculation and read status logic.
 *
 * Phase 1: Task 1.1 - RED phase tests that expose calculation bugs:
 * 1. Different formulas producing different percentages for same data
 * 2. Last page (index totalPages - 1) not marking as 100%
 * 3. 90%+ threshold calculation inconsistency
 * 4. Rounding errors in percentage calculation
 *
 * Expected failures with current implementation:
 * - Test with totalPages=20, currentPage=19 should show 100% but shows 95%
 * - Test with currentPage=9, totalPages=10 should be 90%+ but formula varies
 * - First page (index 0) should show progress but shows 0%
 */
class ReadProgressCalculationTest {

    /**
     * Test that first page (index 0) shows correct percentage.
     * With 0-indexed pages, page 0 is the first page (1 of N pages read).
     *
     * Expected: ~5% for 20-page comic
     * Current bug: Shows 0% (currentPage * 100 / totalPages = 0 * 100 / 20 = 0)
     */
    @Test
    fun `first page index 0 shows 5 percent in 20-page comic`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 0,
            totalPages = 20,
        )

        val percent = progress.getPercentComplete()

        assertEquals("First page should be 5% (1 of 20 pages)", 5, percent)
    }

    /**
     * Test that last page (index totalPages - 1) shows 100%.
     * This is the critical bug - last page should mark as complete.
     *
     * Expected: 100%
     * Current bug: Shows 95% (19 * 100 / 20 = 95)
     */
    @Test
    fun `last page index 19 shows 100 percent in 20-page comic`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 19, // Last page (0-indexed)
            totalPages = 20,
        )

        val percent = progress.getPercentComplete()

        assertEquals("Last page should be 100%", 100, percent)
    }

    /**
     * Test middle page calculation.
     * Page 9 (10th page) in a 20-page comic should be 50%.
     *
     * Expected: 50%
     * Current bug: Shows 45% (9 * 100 / 20 = 45)
     */
    @Test
    fun `page 9 shows 50 percent in 20-page comic`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 9, // 10th page (50% through)
            totalPages = 20,
        )

        val percent = progress.getPercentComplete()

        assertEquals("Page 10 of 20 should be 50%", 50, percent)
    }

    /**
     * Test 90% threshold for auto-mark-as-read.
     * Page 17 (18th page) in 20-page comic = 18/20 = 90% exactly.
     *
     * Expected: isNearlyComplete() = true
     * Current bug: Shows 85% (17 * 100 / 20 = 85), isNearlyComplete() = false
     */
    @Test
    fun `page 17 in 20-page comic reaches 90 percent threshold`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 17, // 18th page = 90%
            totalPages = 20,
        )

        val percent = progress.getPercentComplete()
        val nearlyComplete = progress.isNearlyComplete()

        assertEquals("18 of 20 pages should be 90%", 90, percent)
        assertTrue("Should be nearly complete at 90%", nearlyComplete)
    }

    /**
     * Test that 89% is NOT nearly complete.
     * Page 16 (17th page) in 20-page comic = 17/20 = 85%.
     *
     * Expected: isNearlyComplete() = false
     */
    @Test
    fun `page 16 in 20-page comic is below 90 percent threshold`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 16, // 17th page = 85%
            totalPages = 20,
        )

        val percent = progress.getPercentComplete()
        val nearlyComplete = progress.isNearlyComplete()

        assertEquals("17 of 20 pages should be 85%", 85, percent)
        assertFalse("Should NOT be nearly complete at 85%", nearlyComplete)
    }

    /**
     * Test 10-page comic last page.
     * Page 9 (10th page) should be 100%.
     *
     * Expected: 100%
     * Current bug: Shows 90% (9 * 100 / 10 = 90)
     */
    @Test
    fun `last page index 9 shows 100 percent in 10-page comic`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 9, // Last page
            totalPages = 10,
        )

        val percent = progress.getPercentComplete()

        assertEquals("Last page should be 100%", 100, percent)
    }

    /**
     * Test single-page comic.
     * Page 0 (only page) should be 100%.
     *
     * Expected: 100%
     * Current bug: Shows 0% (0 * 100 / 1 = 0)
     */
    @Test
    fun `single page comic shows 100 percent when on page 0`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 0,
            totalPages = 1,
        )

        val percent = progress.getPercentComplete()

        assertEquals("Single page comic should be 100% on page 0", 100, percent)
    }

    /**
     * Test edge case: 2-page comic.
     * Page 0 (first page) = 50%, Page 1 (second page) = 100%.
     */
    @Test
    fun `two page comic - first page is 50 percent`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 0,
            totalPages = 2,
        )

        val percent = progress.getPercentComplete()

        assertEquals("First of 2 pages should be 50%", 50, percent)
    }

    @Test
    fun `two page comic - last page is 100 percent`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 1,
            totalPages = 2,
        )

        val percent = progress.getPercentComplete()

        assertEquals("Last of 2 pages should be 100%", 100, percent)
    }

    /**
     * Test zero totalPages edge case (shouldn't happen but must handle gracefully).
     */
    @Test
    fun `zero total pages returns 0 percent`() {
        val progress = ReadProgress(
            comicId = 1,
            currentPage = 0,
            totalPages = 0,
        )

        val percent = progress.getPercentComplete()

        assertEquals("Zero pages should return 0%", 0, percent)
    }

    /**
     * Test formula consistency across different comic sizes.
     * All of these should be exactly 50%:
     * - Page 49 of 100-page comic (50th page)
     * - Page 24 of 50-page comic (25th page)
     * - Page 4 of 10-page comic (5th page)
     */
    @Test
    fun `formula consistency - 50 percent at midpoint for various sizes`() {
        val testCases = listOf(
            Triple(49, 100, "100-page comic"),
            Triple(24, 50, "50-page comic"),
            Triple(4, 10, "10-page comic"),
        )

        for ((page, total, description) in testCases) {
            val progress = ReadProgress(
                comicId = 1,
                currentPage = page,
                totalPages = total,
            )

            val percent = progress.getPercentComplete()

            assertEquals(
                "Midpoint of $description should be 50%",
                50,
                percent,
            )
        }
    }

    /**
     * Test formula consistency - 100% at last page for various sizes.
     * Current implementation will fail for all of these.
     */
    @Test
    fun `formula consistency - 100 percent at last page for various sizes`() {
        val testCases = listOf(
            Triple(99, 100, "100-page comic"),
            Triple(49, 50, "50-page comic"),
            Triple(9, 10, "10-page comic"),
            Triple(4, 5, "5-page comic"),
        )

        for ((page, total, description) in testCases) {
            val progress = ReadProgress(
                comicId = 1,
                currentPage = page,
                totalPages = total,
            )

            val percent = progress.getPercentComplete()

            assertEquals(
                "Last page of $description should be 100%",
                100,
                percent,
            )
        }
    }
}
