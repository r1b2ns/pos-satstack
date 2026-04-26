package com.possatstack.app.wallet.signer.tapsigner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TapsignerResponseTest {
    @Test
    fun `decode parses status response`() {
        val payload =
            Cbor.encode(
                linkedMapOf<String, Any?>(
                    "proto" to 1L,
                    "birth" to 700_000L,
                    "slots" to listOf(0L, 1L),
                    "card_nonce" to ByteArray(16) { it.toByte() },
                    "pubkey" to ByteArray(33) { (it + 1).toByte() },
                    "testnet" to 1L,
                ),
            )
        val map = TapsignerResponse.decodeOrThrow(payload)
        val status = TapsignerResponse.parseStatus(map)
        assertEquals(1L, status.proto)
        assertEquals(700_000L, status.birth)
        assertEquals(listOf(0L, 1L), status.slots)
        assertEquals(true, status.isTestnet)
    }

    @Test
    fun `decode raises WrongCvc on 429`() {
        val payload =
            Cbor.encode(
                linkedMapOf<String, Any?>(
                    "error" to "bad CVC",
                    "code" to 429L,
                    "attempts_left" to 2L,
                ),
            )
        val error =
            assertThrows(TapsignerError.WrongCvc::class.java) {
                TapsignerResponse.decodeOrThrow(payload)
            }
        assertEquals(2, error.attemptsLeft)
    }

    @Test
    fun `decode raises RateLimited on 423 with auth_delay`() {
        val payload =
            Cbor.encode(
                linkedMapOf<String, Any?>(
                    "error" to "rate-limited",
                    "code" to 423L,
                    "auth_delay" to 15L,
                ),
            )
        val error =
            assertThrows(TapsignerError.RateLimited::class.java) {
                TapsignerResponse.decodeOrThrow(payload)
            }
        assertEquals(15, error.waitSeconds)
    }

    @Test
    fun `decode raises NotSetUp on 406`() {
        val payload =
            Cbor.encode(
                linkedMapOf<String, Any?>(
                    "error" to "not set up",
                    "code" to 406L,
                ),
            )
        assertThrows(TapsignerError.NotSetUp::class.java) {
            TapsignerResponse.decodeOrThrow(payload)
        }
    }

    @Test
    fun `parseStatus rejects missing fields`() {
        assertThrows(TapsignerError.ProtocolError::class.java) {
            TapsignerResponse.parseStatus(linkedMapOf("proto" to 1L))
        }
    }
}
