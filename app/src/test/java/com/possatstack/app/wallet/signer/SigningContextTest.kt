package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.WalletNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SigningContextTest {
    @Test
    fun `SigningContext carries recipients, totals, fee, and change address`() {
        val context =
            SigningContext(
                network = WalletNetwork.SIGNET,
                recipients =
                    listOf(
                        SigningRecipient(BitcoinAddress("bc1qalpha"), 10_000),
                        SigningRecipient(BitcoinAddress("bc1qbeta"), 5_000),
                    ),
                totalSendSats = 15_000,
                feeSats = 250,
                changeAddress = BitcoinAddress("bc1qchange"),
            )

        assertEquals(WalletNetwork.SIGNET, context.network)
        assertEquals(2, context.recipients.size)
        assertEquals("bc1qalpha", context.recipients.first().address.value)
        assertEquals(10_000L, context.recipients.first().amountSats)
        assertEquals(15_000L, context.totalSendSats)
        assertEquals(250L, context.feeSats)
        assertEquals("bc1qchange", context.changeAddress?.value)
    }

    @Test
    fun `changeAddress may be null for drain-wallet transactions`() {
        val context =
            SigningContext(
                network = WalletNetwork.MAINNET,
                recipients = listOf(SigningRecipient(BitcoinAddress("bc1q"), 1_000)),
                totalSendSats = 1_000,
                feeSats = 100,
                changeAddress = null,
            )
        assertNull(context.changeAddress)
    }

    @Test
    fun `SignerKind enumerates expected flavours`() {
        val kinds = SignerKind.entries.toSet()
        assertEquals(
            setOf(
                SignerKind.SOFTWARE_SEED,
                SignerKind.TAPSIGNER_NFC,
                SignerKind.COLDCARD_AIRGAP,
                SignerKind.AIRGAP_QR,
            ),
            kinds,
        )
    }
}
