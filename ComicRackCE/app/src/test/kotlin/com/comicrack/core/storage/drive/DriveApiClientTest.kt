package com.comicrack.core.storage.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [DriveApiClient] constants and [PagedFileList].
 *
 * Note: Full DriveApiClient tests require Android context (Robolectric)
 * since it depends on GoogleSignIn. These tests cover pure-logic aspects.
 */
class DriveApiClientTest {

    @Test
    fun `FOLDER_MIME_TYPE is correct`() {
        assertEquals("application/vnd.google-apps.folder", DriveApiClient.FOLDER_MIME_TYPE)
    }

    @Test
    fun `PagedFileList holds files and token`() {
        val files = listOf(
            com.google.api.services.drive.model.File().apply {
                id = "f1"
                name = "test.cbz"
            },
        )
        val paged = PagedFileList(files = files, nextPageToken = "token123")

        assertEquals(1, paged.files.size)
        assertEquals("f1", paged.files.first().id)
        assertEquals("token123", paged.nextPageToken)
    }

    @Test
    fun `PagedFileList with null token indicates last page`() {
        val paged = PagedFileList(files = emptyList(), nextPageToken = null)

        assertEquals(0, paged.files.size)
        assertNull(paged.nextPageToken)
    }
}
