package com.possatstack.app.wallet.payment

import com.possatstack.app.wallet.BitcoinAddress
import java.net.URLEncoder

/**
 * Build a BIP-21 URI (`bitcoin:<address>?amount=<btc>&label=<memo>`).
 *
 * BIP-21 specifies the amount in **BTC** (decimal, not satoshis). We keep
 * up to 8 fraction digits and trim trailing zeros so a 1 000 000 sat
 * charge renders as `amount=0.01` rather than `amount=0.01000000`.
 */
internal object Bip21 {
    fun build(
        address: BitcoinAddress,
        amountSats: Long,
        memo: String? = null,
    ): String {
        val params = mutableListOf<String>()
        if (amountSats > 0) params += "amount=${satsToBtcString(amountSats)}"
        if (!memo.isNullOrBlank()) params += "label=${memo.urlEncode()}"
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return "bitcoin:${address.value}$query"
    }

    private fun satsToBtcString(sats: Long): String {
        if (sats <= 0) return "0"
        val whole = sats / 100_000_000L
        val fraction = sats % 100_000_000L
        if (fraction == 0L) return whole.toString()
        val fractionStr = fraction.toString().padStart(8, '0').trimEnd('0')
        return "$whole.$fractionStr"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
}
