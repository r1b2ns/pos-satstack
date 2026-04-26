package com.possatstack.app.wallet.signer.tapsigner

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TapsignerApdusTest {
    @Test
    fun `select AID is well-formed`() {
        val apdu = TapsignerApdus.buildSelectAid()
        assertEquals(0x00.toByte(), apdu[0])
        assertEquals(0xA4.toByte(), apdu[1])
        assertEquals(0x04.toByte(), apdu[2])
        assertEquals(0x00.toByte(), apdu[3])
        assertEquals(15.toByte(), apdu[4])
        // AID payload (15 bytes) starts at offset 5
        val aidStart = "F0436F696E6B697465434152447631"
        val expected = aidStart.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val payload = apdu.copyOfRange(5, 5 + 15)
        assertArrayEquals(expected, payload)
        // Trailing Le
        assertEquals(0x00.toByte(), apdu[apdu.size - 1])
    }

    @Test
    fun `coinkite APDU wraps the CBOR payload`() {
        val cbor = Cbor.encode(linkedMapOf<String, Any?>("cmd" to "status"))
        val apdu = TapsignerApdus.buildCoinkiteApdu(cbor)
        assertEquals(0x00.toByte(), apdu[0])
        assertEquals(0xCB.toByte(), apdu[1])
        assertEquals(0x00.toByte(), apdu[2])
        assertEquals(0x00.toByte(), apdu[3])
        assertEquals(cbor.size.toByte(), apdu[4])
        val payload = apdu.copyOfRange(5, 5 + cbor.size)
        assertArrayEquals(cbor, payload)
        assertEquals(0x00.toByte(), apdu[apdu.size - 1])
    }

    @Test
    fun `parses ok response`() {
        val raw = byteArrayOf(0x01, 0x02, 0x03, 0x90.toByte(), 0x00)
        val parsed = TapsignerApdus.parseResponse(raw)
        assertEquals(0x9000, parsed.sw)
        assertTrue(parsed.isOk)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), parsed.data)
    }

    @Test
    fun `parses error response`() {
        val raw = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        val parsed = TapsignerApdus.parseResponse(raw)
        assertEquals(0x6A82, parsed.sw)
        assertFalse(parsed.isOk)
        assertEquals(0, parsed.data.size)
    }

    @Test
    fun `rejects oversized payload`() {
        val tooBig = ByteArray(256)
        assertThrows(IllegalArgumentException::class.java) {
            TapsignerApdus.buildCoinkiteApdu(tooBig)
        }
    }

    @Test
    fun `rejects truncated response`() {
        val raw = byteArrayOf(0x90.toByte())
        assertThrows(IllegalArgumentException::class.java) {
            TapsignerApdus.parseResponse(raw)
        }
    }

    @Test
    fun `command encode produces parseable CBOR`() {
        val cmd = TapsignerCommand.Status()
        val encoded = cmd.encode()
        val decoded = Cbor.decodeMap(encoded)
        assertEquals("status", decoded["cmd"])
    }

    @Test
    fun `sign command rejects wrong-size digest`() {
        assertThrows(IllegalArgumentException::class.java) {
            TapsignerCommand.Sign(
                digest = ByteArray(31),
                subpath = listOf(0L),
                epubkey = ByteArray(33),
                xcvc = ByteArray(6),
            )
        }
    }
}
