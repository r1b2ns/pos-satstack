package com.possatstack.app.wallet.signer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central list of every [Signer] available in the current build.
 *
 * The app grew past the "single signer" assumption in Fase 5.1 — the
 * user can now pick between the in-app seed and an external TAPSIGNER
 * card (Fase 5.1), and the same pattern will accommodate Coldcard /
 * airgap QR in later phases.
 *
 * Signers are injected as a `Set` via Hilt's multibinding so adding a new
 * one is a single `@IntoSet` entry in [com.possatstack.app.di.WalletModule] —
 * nothing else downstream needs to change.
 */
@Singleton
class SignerRegistry
    @Inject
    constructor(
        signers: Set<@JvmSuppressWildcards Signer>,
    ) {
        /** Stable ordering: seed first, then hardware signers by kind name. */
        val all: List<Signer> =
            signers.sortedWith(
                compareBy(
                    { if (it.kind == SignerKind.SOFTWARE_SEED) 0 else 1 },
                    { it.kind.name },
                    { it.id },
                ),
            )

        fun findById(id: String): Signer? = all.firstOrNull { it.id == id }

        fun firstOfKind(kind: SignerKind): Signer? = all.firstOrNull { it.kind == kind }

        /** Default signer used when the UI does not prompt for picking. */
        fun default(): Signer? = all.firstOrNull()
    }
