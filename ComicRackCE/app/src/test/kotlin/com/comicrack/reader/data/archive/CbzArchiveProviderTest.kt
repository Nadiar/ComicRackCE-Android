package com.comicrack.reader.data.archive

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for CbzArchiveProvider resource management and functionality.
 */
class CbzArchiveProviderTest {
    private lateinit var testArchive: File
    private var provider: CbzArchiveProvider? = null

    /** Helper to get provider with clear error if not initialized. */
    private fun requireProvider(): CbzArchiveProvider =
        checkNotNull(provider) { "Provider not initialized - call setup or create provider first" }

    @Before
    fun setup() {
        testArchive = File.createTempFile("test_comic", ".cbz")
        createTestZipFile(testArchive)
    }

    @After
    fun tearDown() {
        provider?.close()
        if (testArchive.exists()) {
            testArchive.delete()
        }
    }

    private fun createTestZipFile(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            // Add some test image files
            addZipEntry(zip, "page001.jpg", ByteArray(100))
            addZipEntry(zip, "page002.png", ByteArray(100))
            addZipEntry(zip, "page003.jpg", ByteArray(100))
            // Add a non-image file (should be filtered)
            addZipEntry(zip, "metadata.xml", ByteArray(50))
            // Add a macOS system file (should be filtered)
            addZipEntry(zip, "__MACOSX/._page001.jpg", ByteArray(10))
        }
    }

    private fun addZipEntry(
        zip: ZipOutputStream,
        name: String,
        data: ByteArray,
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    // ==========================================================================
    // Initialization tests
    // ==========================================================================

    @Test
    fun `CbzArchiveProvider initializes successfully`() {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test Series",
            issueNumber = "1",
        )

        assertNotNull(provider)
    }

    @Test
    fun `CbzArchiveProvider sets series name`() {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test Series",
            issueNumber = "1",
        )

        assertEquals("Test Series", provider?.seriesName)
    }

    @Test
    fun `CbzArchiveProvider sets issue number`() {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test Series",
            issueNumber = "42",
        )

        assertEquals("42", provider?.issueNumber)
    }

    @Test(expected = RuntimeException::class)
    fun `CbzArchiveProvider throws on invalid archive`() {
        val invalidFile = File.createTempFile("invalid", ".cbz")
        try {
            provider = CbzArchiveProvider(
                archivePath = invalidFile.absolutePath,
                seriesName = "Test",
                issueNumber = "1",
            )
        } finally {
            invalidFile.delete()
        }
    }

    // ==========================================================================
    // Page count tests
    // ==========================================================================

    @Test
    fun `getPageCount returns correct number of image files`() = runTest {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test Series",
            issueNumber = "1",
        )

        val count = requireProvider().getPageCount()

        // Should have 3 images (filtered out metadata.xml and __MACOSX)
        assertEquals(3, count)
    }

    @Test
    fun `getPageCount filters non-image files`() = runTest {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        )

        val pageFilenames = requireProvider().getPageFilenames()

        assertEquals(3, pageFilenames.size)
        assertFalse(pageFilenames.any { it.contains("metadata.xml") })
        assertFalse(pageFilenames.any { it.contains("__MACOSX") })
    }

    private fun assertFalse(condition: Boolean) {
        org.junit.Assert.assertFalse(condition)
    }

    // ==========================================================================
    // Page metadata tests
    // ==========================================================================

    @Test
    fun `getPageMetadata returns correct page number`() = runTest {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test Series",
            issueNumber = "1",
        )

        val metadata = requireProvider().getPageMetadata(0)

        assertEquals(1, metadata.pageNumber) // 0-indexed to 1-indexed
    }

    @Test
    fun `getPageMetadata returns correct total pages`() = runTest {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test Series",
            issueNumber = "1",
        )

        val metadata = requireProvider().getPageMetadata(0)

        assertEquals(3, metadata.totalPages)
    }

    @Test
    fun `getPageMetadata includes series info`() = runTest {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Amazing Comics",
            issueNumber = "42",
        )

        val metadata = requireProvider().getPageMetadata(0)

        assertEquals("Amazing Comics", metadata.seriesName)
        assertEquals("42", metadata.issueNumber)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `getPageMetadata throws on invalid index`() = runTest {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        )

        requireProvider().getPageMetadata(999)
    }

    // ==========================================================================
    // Page data access tests
    // ==========================================================================

    @Test
    fun `getPageUri returns null for archive-based provider`() = runTest {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        )

        val uri = requireProvider().getPageUri(0)

        assertNull(uri)
    }

    @Test
    fun `getPageInputStream returns valid stream`() {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        )

        val stream = requireProvider().getPageInputStream(0)

        assertNotNull(stream)
        stream?.close()
    }

    @Test
    fun `getPageInputStream returns null for invalid index`() {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        )

        val stream = requireProvider().getPageInputStream(999)

        assertNull(stream)
    }

    // ==========================================================================
    // AutoCloseable tests
    // ==========================================================================

    @Test
    fun `close method does not throw exception`() {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        )

        // Should not throw
        requireProvider().close()
    }

    @Test
    fun `close can be called multiple times safely`() {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        )

        requireProvider().close()
        requireProvider().close() // Should not throw
    }

    @Test
    fun `provider can be used with try-with-resources pattern`() {
        CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        ).use { archiveProvider ->
            assertNotNull(archiveProvider)
        }
        // Close should have been called automatically
    }

    // ==========================================================================
    // Image filtering tests
    // ==========================================================================

    @Test
    fun `getPageFilenames returns sorted list`() {
        provider = CbzArchiveProvider(
            archivePath = testArchive.absolutePath,
            seriesName = "Test",
            issueNumber = "1",
        )

        val filenames = requireProvider().getPageFilenames()

        // Should be alphabetically sorted
        assertEquals("page001.jpg", filenames[0])
        assertEquals("page002.png", filenames[1])
        assertEquals("page003.jpg", filenames[2])
    }

    @Test
    fun `supports multiple image formats`() = runTest {
        val multiFormatArchive = File.createTempFile("multi", ".cbz")
        try {
            ZipOutputStream(FileOutputStream(multiFormatArchive)).use { zip ->
                addZipEntry(zip, "page01.jpg", ByteArray(10))
                addZipEntry(zip, "page02.jpeg", ByteArray(10))
                addZipEntry(zip, "page03.png", ByteArray(10))
                addZipEntry(zip, "page04.gif", ByteArray(10))
                addZipEntry(zip, "page05.webp", ByteArray(10))
            }

            provider = CbzArchiveProvider(
                archivePath = multiFormatArchive.absolutePath,
                seriesName = "Test",
                issueNumber = "1",
            )

            val count = requireProvider().getPageCount()
            assertEquals(5, count)
        } finally {
            multiFormatArchive.delete()
        }
    }
}
