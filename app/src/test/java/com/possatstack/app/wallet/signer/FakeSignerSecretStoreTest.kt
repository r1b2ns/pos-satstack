package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSignerSecretStoreTest {
    @Test
    fun `hasMnemonic returns false on fresh store`() {
        val store = FakeSignerSecretStore()
        assertFalse(store.hasMnemonic())
        assertNull(store.storedNetwork())
    }

    @Test
    fun `readMnemonic throws NoWallet when nothing saved`() =
        runTest {
            val store = FakeSignerSecretStore()
            try {
                store.readMnemonic(NoOpBiometricAuthenticator())
                org.junit.Assert.fail("Expected WalletError.NoWallet")
            } catch (error: WalletError.NoWallet) {
                assertTrue(error is WalletError)
            }
        }

    @Test
    fun `saveMnemonic then readMnemonic round-trips the value`() =
        runTest {
            val store = FakeSignerSecretStore()
            val input = "abandon abandon".toCharArray()
            store.saveMnemonic(input, WalletNetwork.SIGNET)

            val read = store.readMnemonic(NoOpBiometricAuthenticator())
            assertArrayEquals("abandon abandon".toCharArray(), read)
            assertEquals(WalletNetwork.SIGNET, store.storedNetwork())
        }

    @Test
    fun `wipe clears stored mnemonic and network`() =
        runTest {
            val store = FakeSignerSecretStore()
            store.saveMnemonic("x y z".toCharArray(), WalletNetwork.MAINNET)

            store.wipe()

            assertFalse(store.hasMnemonic())
            assertNull(store.storedNetwork())
        }

    @Test
    fun `securityPosture returns a sane default on the fake`() {
        val posture = FakeSignerSecretStore().securityPosture()
        assertTrue(posture.hardwareBacked)
        assertTrue(posture.deviceSecure)
    }
}
