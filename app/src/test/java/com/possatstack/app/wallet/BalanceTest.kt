package com.possatstack.app.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceTest {
    @Test
    fun `totalSats sums confirmed and trusted pending`() {
        val balance =
            Balance(
                confirmedSats = 1_000,
                trustedPendingSats = 500,
                untrustedPendingSats = 2_000,
            )
        assertEquals(1_500L, balance.totalSats)
    }

    @Test
    fun `totalSats excludes untrusted pending`() {
        val balance = Balance(confirmedSats = 0, trustedPendingSats = 0, untrustedPendingSats = 42)
        assertEquals(0L, balance.totalSats)
    }

    @Test
    fun `zero balance has zero totalSats`() {
        val balance = Balance(0, 0, 0)
        assertEquals(0L, balance.totalSats)
    }
}
