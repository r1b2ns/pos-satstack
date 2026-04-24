package com.possatstack.app.wallet

/**
 * Library-agnostic wallet backup payload.
 *
 * Modelled as a sealed hierarchy — not an enum — so each backup flavour can
 * carry a payload of the appropriate shape (e.g. a mnemonic is a char array,
 * a Lightning channel-state backup is a binary blob). This lets the same
 * contract cover future Lightning/bearer-instrument backups without breaking
 * existing on-chain call sites.
 *
 * Callers that handle the mnemonic payload MUST zero the array when done.
 */
sealed interface WalletBackup {
    val network: WalletNetwork

    /**
     * BIP-39 mnemonic as a mutable char array so the caller can zero it out
     * after use. Never store this as a [String] — strings are interned and
     * cannot be explicitly cleared from the JVM heap.
     */
    data class Bip39(
        val mnemonic: CharArray,
        override val network: WalletNetwork,
    ) : WalletBackup {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bip39) return false
            if (!mnemonic.contentEquals(other.mnemonic)) return false
            if (network != other.network) return false
            return true
        }

        override fun hashCode(): Int {
            var result = mnemonic.contentHashCode()
            result = 31 * result + network.hashCode()
            return result
        }
    }

    /** Watch-only wallet (no signing). Extended public key + derivation fingerprint. */
    data class XpubWatching(
        val xpub: String,
        val fingerprint: String,
        override val network: WalletNetwork,
    ) : WalletBackup

    /** Raw output descriptor (BIP-380). Kind `wpkh(...)` etc. */
    data class DescriptorOnly(
        val externalDescriptor: String,
        val internalDescriptor: String,
        override val network: WalletNetwork,
    ) : WalletBackup

    /**
     * Lightning channel-state static backup (SCB). Reserved for Fase 5.
     * Payload is an opaque binary blob owned by the [LightningEngine] impl.
     */
    data class ChannelStateScb(
        val payload: ByteArray,
        override val network: WalletNetwork,
    ) : WalletBackup {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChannelStateScb) return false
            if (!payload.contentEquals(other.payload)) return false
            if (network != other.network) return false
            return true
        }

        override fun hashCode(): Int {
            var result = payload.contentHashCode()
            result = 31 * result + network.hashCode()
            return result
        }
    }
}
