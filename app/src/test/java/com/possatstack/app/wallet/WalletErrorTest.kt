package com.possatstack.app.wallet

import com.possatstack.app.wallet.bitcoin.toWalletError
import org.bitcoindevkit.Bip39Exception
import org.bitcoindevkit.EsploraException
import org.bitcoindevkit.FeeRateException
import org.bitcoindevkit.SignerException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletErrorTest {
    @Test
    fun `WalletError is a Throwable so it can be rethrown`() {
        val error: Throwable = WalletError.NoWallet
        assertTrue(error is WalletError)
    }

    @Test
    fun `InvalidAddress carries the offending input in the message`() {
        val error = WalletError.InvalidAddress("not-a-real-address")
        assertTrue(error.message!!.contains("not-a-real-address"))
    }

    @Test
    fun `ChainSourceUnreachable preserves the original cause`() {
        val cause = RuntimeException("boom")
        val error = WalletError.ChainSourceUnreachable(cause)
        assertSame(cause, error.cause)
    }

    @Test
    fun `toWalletError maps Bip39Exception to InvalidMnemonic`() {
        val mapped = Bip39Exception.InvalidChecksum().toWalletError()
        assertEquals(WalletError.InvalidMnemonic, mapped)
    }

    @Test
    fun `toWalletError maps EsploraException to ChainSourceUnreachable`() {
        val original = EsploraException.HeaderHashNotFound()
        val mapped = original.toWalletError() as WalletError.ChainSourceUnreachable
        assertSame(original, mapped.cause)
    }

    @Test
    fun `toWalletError maps FeeRateException to FeeTooLow`() {
        val mapped = FeeRateException.ArithmeticOverflow().toWalletError()
        assertEquals(WalletError.FeeTooLow, mapped)
    }

    @Test
    fun `toWalletError passes through existing WalletError unchanged`() {
        val original: Throwable = WalletError.InsufficientFunds
        val mapped = original.toWalletError()
        assertSame(original, mapped)
    }

    @Test
    fun `toWalletError maps SignerException to SigningFailed`() {
        val original = SignerException.MissingKey()
        val mapped = original.toWalletError()
        assertTrue(mapped is WalletError.SigningFailed)
    }

    @Test
    fun `toWalletError maps unknown Throwables to Unknown`() {
        val original = RuntimeException("surprise")
        val mapped = original.toWalletError() as WalletError.Unknown
        assertSame(original, mapped.cause)
        assertFalse(mapped is WalletError.InvalidMnemonic)
    }
}
