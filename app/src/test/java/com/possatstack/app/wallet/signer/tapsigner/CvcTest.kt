package com.possatstack.app.wallet.signer.tapsigner

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CvcTest {
    @Test
    fun `parses 6-digit code`() {
        val cvc = Cvc.parse("123456")
        assertEquals(6, cvc.length)
        assertArrayEquals("123456".toByteArray(Charsets.US_ASCII), cvc.asBytes())
    }

    @Test
    fun `parses 8-digit code and trims whitespace`() {
        val cvc = Cvc.parse("  12345678 ")
        assertEquals(8, cvc.length)
    }

    @Test
    fun `rejects too short`() {
        assertThrows(IllegalArgumentException::class.java) { Cvc.parse("12345") }
    }

    @Test
    fun `rejects too long`() {
        assertThrows(IllegalArgumentException::class.java) { Cvc.parse("123456789") }
    }

    @Test
    fun `rejects non-digits`() {
        assertThrows(IllegalArgumentException::class.java) { Cvc.parse("12a456") }
    }

    @Test
    fun `wipe zeroes the buffer`() {
        val cvc = Cvc.parse("654321")
        cvc.wipe()
        val bytes = cvc.asBytes()
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun `shape validator accepts and rejects correctly`() {
        assertTrue(Cvc.isValidShape("123456"))
        assertTrue(Cvc.isValidShape("12345678"))
        assertFalse(Cvc.isValidShape(""))
        assertFalse(Cvc.isValidShape("12345"))
        assertFalse(Cvc.isValidShape("123456789"))
        assertFalse(Cvc.isValidShape("abcdef"))
    }
}
