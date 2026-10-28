package com.comicrack.reader.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceholderPageProviderTest {
    @Test
    fun `getPageUri returns null because placeholder pages have no backing resource`() = runTest {
        val uri = PlaceholderPageProvider().getPageUri(0)

        assertNull(uri)
    }
}
