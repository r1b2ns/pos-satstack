package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.UnsignedPsbt
import com.possatstack.app.wallet.WalletNetwork
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeSignerTest {
    private val context =
        SigningContext(
            network = WalletNetwork.SIGNET,
            recipients = listOf(SigningRecipient(BitcoinAddress("bc1qtest"), 1_000)),
            totalSendSats = 1_000,
            feeSats = 200,
            changeAddress = null,
        )

    @Test
    fun `signPsbt records the call`() =
        runTest {
            val signer = FakeSigner()
            val psbt = UnsignedPsbt("cHNidP//abc", "fp")

            val signed = signer.signPsbt(psbt, context)

            assertEquals(1, signer.signCount)
            assertSame(psbt, signer.lastPsbt)
            assertSame(context, signer.lastContext)
            assertEquals("cHNidP//signed", signed.base64)
        }

    @Test
    fun `signPsbt propagates injected failure`() =
        runTest {
            val signer =
                FakeSigner().apply {
                    signResult = Result.failure(RuntimeException("cancelled"))
                }
            try {
                signer.signPsbt(UnsignedPsbt("x", "fp"), context)
                fail("Expected failure")
            } catch (exception: RuntimeException) {
                assertTrue(exception.message == "cancelled")
            }
        }

    @Test
    fun `SignedPsbt carries the base64 payload verbatim`() {
        val signed = SignedPsbt("raw-base64")
        assertEquals("raw-base64", signed.base64)
    }
}
