package com.possatstack.app.wallet

/**
 * A fully-constructed but **unsigned** PSBT (BIP-174).
 *
 * Inputs have been selected, outputs and fees are set, but no signatures have
 * been applied. The PSBT is encoded as a base64 string and can be passed to an
 * external signer (hardware wallet, watch-only flow, PayJoin counterparty, …)
 * for finalisation.
 */
@JvmInline
value class UnsignedPsbt(val base64: String)
