package com.comicrack.core.storage.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [StorageType] enum and its companion parsing.
 */
class StorageTypeTest {

    @Test
    fun `fromValue returns LOCAL for LOCAL string`() {
        assertEquals(StorageType.LOCAL, StorageType.fromValue("LOCAL"))
    }

    @Test
    fun `fromValue returns SAF for SAF string`() {
        assertEquals(StorageType.SAF, StorageType.fromValue("SAF"))
    }

    @Test
    fun `fromValue returns GOOGLE_DRIVE for GOOGLE_DRIVE string`() {
        assertEquals(StorageType.GOOGLE_DRIVE, StorageType.fromValue("GOOGLE_DRIVE"))
    }

    @Test
    fun `fromValue defaults to SAF for unknown string`() {
        assertEquals(StorageType.SAF, StorageType.fromValue("UNKNOWN"))
    }

    @Test
    fun `fromValue defaults to SAF for empty string`() {
        assertEquals(StorageType.SAF, StorageType.fromValue(""))
    }

    @Test
    fun `fromValue is case sensitive`() {
        // lowercase should not match
        assertEquals(StorageType.SAF, StorageType.fromValue("local"))
        assertEquals(StorageType.SAF, StorageType.fromValue("saf"))
        assertEquals(StorageType.SAF, StorageType.fromValue("google_drive"))
    }

    @Test
    fun `value property matches enum name`() {
        assertEquals("LOCAL", StorageType.LOCAL.value)
        assertEquals("SAF", StorageType.SAF.value)
        assertEquals("GOOGLE_DRIVE", StorageType.GOOGLE_DRIVE.value)
    }
}
