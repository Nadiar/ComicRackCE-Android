package com.comicrack.wifi.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for ProtocolCodec - verifies big-endian binary encoding
 * that must match legacy ComicRack CE desktop protocol exactly.
 */
class ProtocolCodecTest {
    // ==========================================================================
    // Integer encoding tests (Big-endian: most significant byte first)
    // ==========================================================================

    @Test
    fun `writeInt encodes 42 as big-endian 4 bytes`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeInt(42)

        // 42 = 0x0000002A in big-endian
        val expected = byteArrayOf(0x00, 0x00, 0x00, 0x2A)
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `writeInt encodes large value correctly`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeInt(0x12345678)

        val expected = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `writeInt encodes negative value correctly`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeInt(-1)

        // -1 = 0xFFFFFFFF in two's complement
        val expected = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `readInt decodes big-endian 4 bytes to 42`() {
        val input = ByteArrayInputStream(byteArrayOf(0x00, 0x00, 0x00, 0x2A))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readInt()

        assertEquals(42, result)
    }

    @Test
    fun `readInt decodes large value correctly`() {
        val input = ByteArrayInputStream(byteArrayOf(0x12, 0x34, 0x56, 0x78))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readInt()

        assertEquals(0x12345678, result)
    }

    // ==========================================================================
    // Long encoding tests (Big-endian: 8 bytes)
    // ==========================================================================

    @Test
    fun `writeLong encodes file size as big-endian 8 bytes`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        // 10 GB file size
        val fileSize = 10L * 1024 * 1024 * 1024 // 10737418240
        codec.writeLong(fileSize)

        // 10737418240 = 0x0000000280000000 in big-endian
        val expected =
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x02,
                0x80.toByte(),
                0x00,
                0x00,
                0x00,
            )
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `writeLong encodes max value correctly`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeLong(Long.MAX_VALUE)

        // 0x7FFFFFFFFFFFFFFF
        val expected =
            byteArrayOf(
                0x7F,
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
            )
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `readLong decodes big-endian 8 bytes correctly`() {
        val input =
            ByteArrayInputStream(
                byteArrayOf(
                    0x00,
                    0x00,
                    0x00,
                    0x02,
                    0x80.toByte(),
                    0x00,
                    0x00,
                    0x00,
                ),
            )
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readLong()

        assertEquals(10L * 1024 * 1024 * 1024, result)
    }

    // ==========================================================================
    // String encoding tests (4-byte length prefix + UTF-8 bytes)
    // ==========================================================================

    @Test
    fun `writeString encodes with 4-byte length prefix`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeString("test")

        // Length 4 as big-endian int, followed by UTF-8 bytes
        val expected =
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x04, // length = 4
                0x74,
                0x65,
                0x73,
                0x74, // "test" in UTF-8
            )
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `writeString handles Unicode correctly`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeString("日本語")

        // Japanese characters are 3 bytes each in UTF-8
        val bytes = output.toByteArray()
        // First 4 bytes are length (9 bytes for 3 characters)
        assertEquals(0.toByte(), bytes[0])
        assertEquals(0.toByte(), bytes[1])
        assertEquals(0.toByte(), bytes[2])
        assertEquals(9.toByte(), bytes[3])
        // Total should be 4 (length) + 9 (UTF-8 bytes) = 13
        assertEquals(13, bytes.size)
    }

    @Test
    fun `writeString handles empty string`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeString("")

        val expected = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `readString decodes length-prefixed UTF-8`() {
        val input =
            ByteArrayInputStream(
                byteArrayOf(
                    0x00,
                    0x00,
                    0x00,
                    0x04, // length = 4
                    0x74,
                    0x65,
                    0x73,
                    0x74, // "test" in UTF-8
                ),
            )
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readString()

        assertEquals("test", result)
    }

    @Test
    fun `readString handles Unicode correctly`() {
        val utf8Bytes = "日本語".toByteArray(Charsets.UTF_8)
        val lengthBytes = byteArrayOf(0x00, 0x00, 0x00, utf8Bytes.size.toByte())
        val input = ByteArrayInputStream(lengthBytes + utf8Bytes)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readString()

        assertEquals("日本語", result)
    }

    // ==========================================================================
    // Byte encoding tests
    // ==========================================================================

    @Test
    fun `writeByte writes single byte`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeByte(0x42)

        assertArrayEquals(byteArrayOf(0x42), output.toByteArray())
    }

    @Test
    fun `readByte reads single byte as unsigned`() {
        val input = ByteArrayInputStream(byteArrayOf(0xFF.toByte()))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readByte()

        assertEquals(255, result) // Should be unsigned
    }

    // ==========================================================================
    // Byte array encoding tests
    // ==========================================================================

    @Test
    fun `writeBytes writes raw bytes without length prefix`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeBytes(byteArrayOf(0x01, 0x02, 0x03))

        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), output.toByteArray())
    }

    @Test
    fun `readBytes reads exact number of bytes`() {
        val input = ByteArrayInputStream(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readBytes(3)

        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), result)
    }

    // ==========================================================================
    // Round-trip tests
    // ==========================================================================

    @Test
    fun `int round-trip preserves value`() {
        val output = ByteArrayOutputStream()
        val writeCodec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)
        writeCodec.writeInt(12345678)

        val input = ByteArrayInputStream(output.toByteArray())
        val readCodec = ProtocolCodec(input, ByteArrayOutputStream())
        val result = readCodec.readInt()

        assertEquals(12345678, result)
    }

    @Test
    fun `long round-trip preserves value`() {
        val output = ByteArrayOutputStream()
        val writeCodec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)
        val original = 123456789012345678L
        writeCodec.writeLong(original)

        val input = ByteArrayInputStream(output.toByteArray())
        val readCodec = ProtocolCodec(input, ByteArrayOutputStream())
        val result = readCodec.readLong()

        assertEquals(original, result)
    }

    @Test
    fun `string round-trip preserves value`() {
        val output = ByteArrayOutputStream()
        val writeCodec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)
        val original = "Hello, 世界! 🌍"
        writeCodec.writeString(original)

        val input = ByteArrayInputStream(output.toByteArray())
        val readCodec = ProtocolCodec(input, ByteArrayOutputStream())
        val result = readCodec.readString()

        assertEquals(original, result)
    }

    // ==========================================================================
    // Boolean encoding tests (for protocol compatibility)
    // ==========================================================================

    @Test
    fun `writeBoolean writes 1 for true`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeBoolean(true)

        assertArrayEquals(byteArrayOf(0x01), output.toByteArray())
    }

    @Test
    fun `writeBoolean writes 0 for false`() {
        val output = ByteArrayOutputStream()
        val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

        codec.writeBoolean(false)

        assertArrayEquals(byteArrayOf(0x00), output.toByteArray())
    }

    @Test
    fun `readBoolean reads non-zero as true`() {
        val input = ByteArrayInputStream(byteArrayOf(0x01))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readBoolean()

        assertEquals(true, result)
    }

    @Test
    fun `readBoolean reads zero as false`() {
        val input = ByteArrayInputStream(byteArrayOf(0x00))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val result = codec.readBoolean()

        assertEquals(false, result)
    }
}
