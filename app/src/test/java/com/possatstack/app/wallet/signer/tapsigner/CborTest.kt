package com.possatstack.app.wallet.signer.tapsigner

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CborTest {
    @Test
    fun `encodes small uint in a single byte`() {
        assertArrayEquals(byteArrayOf(0x00), Cbor.encode(0))
        assertArrayEquals(byteArrayOf(0x0A), Cbor.encode(10))
        assertArrayEquals(byteArrayOf(0x17), Cbor.encode(23))
    }

    @Test
    fun `encodes uint with 1-byte extension for 24-255`() {
        assertArrayEquals(byteArrayOf(0x18, 24), Cbor.encode(24))
        assertArrayEquals(byteArrayOf(0x18, 0xFF.toByte()), Cbor.encode(255))
    }

    @Test
    fun `encodes negative int`() {
        // -1 → major 1, value 0
        assertArrayEquals(byteArrayOf(0x20), Cbor.encode(-1))
        // -10 → major 1, value 9
        assertArrayEquals(byteArrayOf(0x29), Cbor.encode(-10))
    }

    @Test
    fun `round-trips text string`() {
        val encoded = Cbor.encode("status")
        val decoded = Cbor.decode(encoded)
        assertEquals("status", decoded)
    }

    @Test
    fun `round-trips byte string`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val encoded = Cbor.encode(payload)
        val decoded = Cbor.decode(encoded) as ByteArray
        assertArrayEquals(payload, decoded)
    }

    @Test
    fun `round-trips map with mixed values`() {
        val source =
            linkedMapOf<String, Any?>(
                "cmd" to "sign",
                "subpath" to listOf(0L, 1L, 2L),
                "digest" to ByteArray(32) { it.toByte() },
                "count" to 7L,
            )
        val encoded = Cbor.encode(source)

        @Suppress("UNCHECKED_CAST")
        val decoded = Cbor.decode(encoded) as Map<String, Any?>

        assertEquals("sign", decoded["cmd"])
        assertEquals(listOf(0L, 1L, 2L), decoded["subpath"])
        assertArrayEquals(source["digest"] as ByteArray, decoded["digest"] as ByteArray)
        assertEquals(7L, decoded["count"])
    }

    @Test
    fun `decodeMap enforces map root`() {
        val encoded = Cbor.encode("not-a-map")
        assertThrows(IllegalArgumentException::class.java) { Cbor.decodeMap(encoded) }
    }

    @Test
    fun `rejects trailing bytes`() {
        val valid = Cbor.encode("hi")
        val corrupted = valid + byteArrayOf(0x00)
        assertThrows(IllegalArgumentException::class.java) { Cbor.decode(corrupted) }
    }

    @Test
    fun `encodes two-byte length when needed`() {
        val longText = "x".repeat(300)
        val encoded = Cbor.encode(longText)
        val decoded = Cbor.decode(encoded)
        assertEquals(longText, decoded)
    }
}
