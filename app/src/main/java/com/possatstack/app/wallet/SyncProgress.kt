package com.possatstack.app.wallet

/**
 * Represents the current state of a blockchain synchronisation operation.
 *
 * [Idle]    — no sync is running.
 * [FullScan] — initial full scan (indeterminate; all script public keys checked).
 * [Syncing] — incremental sync of already-revealed addresses; [percent] 0-100.
 */
sealed class SyncProgress {
    data object Idle : SyncProgress()

    data object FullScan : SyncProgress()

    data class Syncing(val percent: Int) : SyncProgress()
}
