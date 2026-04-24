package com.possatstack.app.wallet.payment

/**
 * User-facing payment options the merchant can accept.
 *
 * This is the *product* axis of the wallet architecture (see
 * `docs/architecture.md`): on-chain, Lightning, and bearer cards are
 * separate products that coexist in the same build. Swapping the chain
 * backend (Esplora ↔ Kyoto ↔ Floresta) is a different axis and happens
 * inside `ChainDataSource` — invisible from here.
 *
 * Lightning and SATSCARD variants are reserved now as `data object` /
 * `data class` so any UI code branching on the sealed hierarchy is
 * forced to handle them; their concrete implementation lands in Fase 5.
 */
sealed interface PaymentMethod {
    val id: String
    val displayName: String

    data object OnChain : PaymentMethod {
        override val id = "onchain"
        override val displayName = "On-chain"
    }

    /** Reserved for Fase 5 (LDK Node). */
    data object Lightning : PaymentMethod {
        override val id = "lightning"
        override val displayName = "Lightning"
    }

    /** Reserved for Fase 5 (SATSCARD). Holds the specific card serial. */
    data class BearerCard(val cardSerial: String) : PaymentMethod {
        override val id = "satscard:$cardSerial"
        override val displayName = "SATSCARD"
    }
}
