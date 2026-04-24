package com.possatstack.app.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletBackupTest {
    @Test
    fun `Bip39 equals is content-based on mnemonic and network`() {
        val a = WalletBackup.Bip39("abandon abandon".toCharArray(), WalletNetwork.SIGNET)
        val b = WalletBackup.Bip39("abandon abandon".toCharArray(), WalletNetwork.SIGNET)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Bip39 differs when mnemonic differs`() {
        val a = WalletBackup.Bip39("abandon abandon".toCharArray(), WalletNetwork.SIGNET)
        val b = WalletBackup.Bip39("abandon ability".toCharArray(), WalletNetwork.SIGNET)
        assertNotEquals(a, b)
    }

    @Test
    fun `Bip39 differs when network differs`() {
        val a = WalletBackup.Bip39("abandon abandon".toCharArray(), WalletNetwork.SIGNET)
        val b = WalletBackup.Bip39("abandon abandon".toCharArray(), WalletNetwork.MAINNET)
        assertNotEquals(a, b)
    }

    @Test
    fun `ChannelStateScb equals is content-based on payload and network`() {
        val payloadA = byteArrayOf(1, 2, 3, 4)
        val payloadB = byteArrayOf(1, 2, 3, 4)
        val a = WalletBackup.ChannelStateScb(payloadA, WalletNetwork.SIGNET)
        val b = WalletBackup.ChannelStateScb(payloadB, WalletNetwork.SIGNET)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `XpubWatching retains fingerprint`() {
        val backup =
            WalletBackup.XpubWatching(
                xpub = "zpub...",
                fingerprint = "deadbeef",
                network = WalletNetwork.MAINNET,
            )
        assertEquals("deadbeef", backup.fingerprint)
        assertEquals(WalletNetwork.MAINNET, backup.network)
    }

    @Test
    fun `DescriptorOnly is distinct kind from Bip39`() {
        val mnemonic = WalletBackup.Bip39("a b c".toCharArray(), WalletNetwork.SIGNET)
        val descriptor = WalletBackup.DescriptorOnly("ext", "int", WalletNetwork.SIGNET)
        assertTrue(mnemonic !is WalletBackup.DescriptorOnly)
        assertTrue(descriptor !is WalletBackup.Bip39)
    }
}
