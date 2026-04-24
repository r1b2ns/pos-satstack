package com.possatstack.app.wallet.payment

import com.possatstack.app.wallet.BitcoinAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class Bip21Test {
    private val address = BitcoinAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh")

    @Test
    fun `build with zero amount omits the amount parameter`() {
        val uri = Bip21.build(address, amountSats = 0L)
        assertEquals("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", uri)
    }

    @Test
    fun `build encodes 1 000 000 sats as 0,01 BTC`() {
        val uri = Bip21.build(address, amountSats = 1_000_000L)
        assertEquals("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.01", uri)
    }

    @Test
    fun `build encodes whole BTC without fractional part`() {
        val uri = Bip21.build(address, amountSats = 200_000_000L)
        assertEquals("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=2", uri)
    }

    @Test
    fun `build keeps maximum precision when necessary`() {
        val uri = Bip21.build(address, amountSats = 1L)
        assertEquals("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.00000001", uri)
    }

    @Test
    fun `build appends url-encoded memo as label`() {
        val uri = Bip21.build(address, amountSats = 50_000L, memo = "Coffee & pastries")
        assertEquals(
            "bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.0005&label=Coffee+%26+pastries",
            uri,
        )
    }

    @Test
    fun `build skips blank memo`() {
        val uri = Bip21.build(address, amountSats = 1000L, memo = "   ")
        assertEquals("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.00001", uri)
    }
}
