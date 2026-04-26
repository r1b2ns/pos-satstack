package com.possatstack.app.wallet.chain

import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import org.junit.Assert.assertThrows
import org.junit.Test

class KyotoChainDataSourceTest {
    /**
     * The Kyoto data source delegates configuration to its Esplora fallback.
     * Esplora has no public REGTEST endpoint, so configuring REGTEST must
     * fail with the neutral [WalletError.ChainSourceUnreachable] rather
     * than leaking the underlying [IllegalStateException].
     */
    @Test
    fun `configureFor REGTEST surfaces ChainSourceUnreachable`() {
        val esplora = EsploraChainDataSource()
        val kyoto = KyotoChainDataSource(esplora)
        assertThrows(WalletError.ChainSourceUnreachable::class.java) {
            kyoto.configureFor(WalletNetwork.REGTEST)
        }
    }

    /**
     * Every other supported network must configure the fallback without
     * raising. We don't reach into the Esplora client (that would hit the
     * network); just verify the call shape.
     */
    @Test
    fun `configureFor accepts mainnet, testnet, signet`() {
        val esplora = EsploraChainDataSource()
        val kyoto = KyotoChainDataSource(esplora)
        kyoto.configureFor(WalletNetwork.MAINNET)
        kyoto.configureFor(WalletNetwork.TESTNET)
        kyoto.configureFor(WalletNetwork.SIGNET)
    }
}
