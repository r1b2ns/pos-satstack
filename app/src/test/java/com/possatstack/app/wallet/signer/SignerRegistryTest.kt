package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.UnsignedPsbt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SignerRegistryTest {
    private class StubSigner(
        override val id: String,
        override val kind: SignerKind,
    ) : Signer {
        override suspend fun signPsbt(
            psbt: UnsignedPsbt,
            context: SigningContext,
        ): SignedPsbt = throw UnsupportedOperationException("stub")
    }

    @Test
    fun `software seed is returned first`() {
        val tapsigner = StubSigner("tapsigner", SignerKind.TAPSIGNER_NFC)
        val seed = StubSigner("seed", SignerKind.SOFTWARE_SEED)
        val registry = SignerRegistry(setOf(tapsigner, seed))
        assertEquals(seed, registry.default())
        assertEquals(listOf(seed, tapsigner), registry.all)
    }

    @Test
    fun `findById returns matching signer`() {
        val seed = StubSigner("seed", SignerKind.SOFTWARE_SEED)
        val tapsigner = StubSigner("tapsigner", SignerKind.TAPSIGNER_NFC)
        val registry = SignerRegistry(setOf(seed, tapsigner))
        assertSame(tapsigner, registry.findById("tapsigner"))
        assertNull(registry.findById("nope"))
    }

    @Test
    fun `firstOfKind picks any signer of the requested kind`() {
        val seed = StubSigner("seed", SignerKind.SOFTWARE_SEED)
        val tapsigner = StubSigner("tapsigner", SignerKind.TAPSIGNER_NFC)
        val registry = SignerRegistry(setOf(seed, tapsigner))
        assertSame(tapsigner, registry.firstOfKind(SignerKind.TAPSIGNER_NFC))
        assertSame(seed, registry.firstOfKind(SignerKind.SOFTWARE_SEED))
        assertNull(registry.firstOfKind(SignerKind.AIRGAP_QR))
    }

    @Test
    fun `empty registry returns null defaults`() {
        val registry = SignerRegistry(emptySet())
        assertNull(registry.default())
        assertEquals(emptyList<Signer>(), registry.all)
    }
}
