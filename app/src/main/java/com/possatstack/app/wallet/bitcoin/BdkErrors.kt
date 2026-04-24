package com.possatstack.app.wallet.bitcoin

import com.possatstack.app.wallet.WalletError
import org.bitcoindevkit.AddressParseException
import org.bitcoindevkit.Bip39Exception
import org.bitcoindevkit.CalculateFeeException
import org.bitcoindevkit.CannotConnectException
import org.bitcoindevkit.CreateTxException
import org.bitcoindevkit.ElectrumException
import org.bitcoindevkit.EsploraException
import org.bitcoindevkit.ExtractTxException
import org.bitcoindevkit.FeeRateException
import org.bitcoindevkit.PsbtException
import org.bitcoindevkit.SignerException

/**
 * Translate BDK-specific exceptions into the library-agnostic [WalletError]
 * hierarchy. Any code path inside [BdkOnChainEngine] that calls into BDK must
 * wrap itself with [runCatching].mapError { toWalletError() } so that callers
 * only see neutral errors.
 */
internal fun Throwable.toWalletError(): WalletError =
    when (this) {
        is WalletError -> this
        is Bip39Exception -> WalletError.InvalidMnemonic
        is AddressParseException -> WalletError.InvalidAddress(message ?: "unknown")
        is EsploraException -> WalletError.ChainSourceUnreachable(this)
        is ElectrumException -> WalletError.ChainSourceUnreachable(this)
        is CannotConnectException -> WalletError.ChainSourceUnreachable(this)
        is CreateTxException -> mapCreateTx(this)
        is FeeRateException -> WalletError.FeeTooLow
        is SignerException -> WalletError.SigningFailed(message ?: "signer failed")
        is PsbtException -> WalletError.SigningFailed(message ?: "PSBT malformed")
        is ExtractTxException -> WalletError.SigningFailed("Cannot extract tx from PSBT")
        is CalculateFeeException -> WalletError.Unknown(this)
        else -> WalletError.Unknown(this)
    }

private fun mapCreateTx(exception: CreateTxException): WalletError =
    when (exception) {
        is CreateTxException.InsufficientFunds -> WalletError.InsufficientFunds
        is CreateTxException.FeeTooLow -> WalletError.FeeTooLow
        is CreateTxException.FeeRateTooLow -> WalletError.FeeTooLow
        else -> WalletError.Unknown(exception)
    }
