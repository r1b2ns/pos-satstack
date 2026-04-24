package com.possatstack.app.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeePolicyTest {
    @Test
    fun `TargetBlocks carries confirmation target`() {
        val policy = FeePolicy.TargetBlocks(blocks = 6)
        assertTrue(policy is FeePolicy.TargetBlocks)
        assertEquals(6, policy.blocks)
    }

    @Test
    fun `SatsPerVb carries rate`() {
        val policy = FeePolicy.SatsPerVb(rate = 12.5)
        assertTrue(policy is FeePolicy.SatsPerVb)
        assertEquals(12.5, policy.rate, 0.0)
    }

    @Test
    fun `Absolute carries total sats`() {
        val policy = FeePolicy.Absolute(totalSats = 1_500)
        assertTrue(policy is FeePolicy.Absolute)
        assertEquals(1_500L, policy.totalSats)
    }

    @Test
    fun `FeeEstimate exposes rate and target`() {
        val estimate = FeeEstimate(satsPerVb = 8.0, targetBlocks = 3)
        assertEquals(8.0, estimate.satsPerVb, 0.0)
        assertEquals(3, estimate.targetBlocks)
    }
}
