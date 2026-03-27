# POS SatStack

Software for Android-based POS machines, providing a simple, secure, and private way for small merchants to charge and receive Bitcoin payments across all layers (on-chain, Lightning, etc).

## Motivation

Small merchants often face barriers to accepting Bitcoin as a payment method. POS SatStack aims to remove those barriers by offering a solution that runs directly on the Android POS machines already used day-to-day, with no additional hardware required.

## Architecture

The app is built with **Kotlin + Jetpack Compose** and follows a layered architecture:

- **UI layer** — Compose screens, Material3, follows the system theme (light/dark)
- **Wallet abstraction layer** — a `WalletRepository` interface sits between the app and any wallet library. No screen or ViewModel touches the library directly. Swapping the underlying implementation (or adding support for other cryptocurrencies) requires only a new implementation of the interface.
- **Bitcoin implementation** — powered by [BDK Android](https://github.com/bitcoindevkit/bdk-android) 2.x (BDK 1.x Rust core), using BIP-84 native SegWit descriptors.

### Wallet module structure

```
wallet/
  WalletNetwork.kt          — network enum (MAINNET, TESTNET, SIGNET, REGTEST)
  WalletDescriptor.kt       — holds external/internal descriptor strings
  BitcoinAddress.kt         — value class for Bitcoin addresses
  WalletTransaction.kt      — lightweight transaction model for the UI layer
  SyncProgress.kt           — sealed interface for sync state (Idle, FullScan, Syncing)
  WalletRepository.kt       — interface (create, load, import, sync, balance, transactions)
  bitcoin/
    BdkWalletRepository.kt  — BDK 2.x implementation
  storage/
    WalletStorage.kt        — interface for secure descriptor persistence
    SecureWalletStorage.kt   — EncryptedSharedPreferences implementation
```

### Security

- **Seed phrases and descriptors** are stored in `EncryptedSharedPreferences` (AES-256-GCM / AES-256-SIV) via Android's `MasterKey` API.
- **Wallet SQLite database** is stored in `noBackupFilesDir` to prevent cloud backup of key material.
- QR codes are generated locally using ZXing — no data leaves the device.

## Features

### Wallet management
- Create a new BIP-84 (native SegWit) wallet with a 12-word seed phrase
- Import an existing wallet from a 12 or 24-word mnemonic
- View and back up the seed phrase
- Delete the wallet from the device

### Receive
- Generate new receive addresses
- Display address as a QR code (ZXing) for easy scanning
- Copy address to clipboard

### Transaction history
- List all wallet transactions grouped by date
- Unconfirmed (pending) transactions appear in a dedicated section at the top
- Each transaction shows direction (sent/received), amount, txid, and block height
- Tap a transaction to view it on [mempool.space](https://mempool.space) (supports signet, testnet, and mainnet)

### Sync
- Electrum-based synchronization via BDK
  - **Signet**: `ssl://mempool.space:60602`
  - **Mainnet**: `ssl://electrum.blockstream.info:50002`
- Automatic sync on app start via `WalletSyncService`
- Full scan for first-time or imported wallets; incremental sync thereafter
- Global progress indicator visible across all screens
- Balance display (BTC + sats) with manual refresh

### Logging
- Custom `AppLogger` utility with info, warning, and error levels
- Logs are emitted only in debug builds — silent in production

## Requirements

- Android 8.0+ (API 26)
- Android-based POS machine or any Android device for development/testing

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose 2.8 (type-safe routes) |
| DI | Hilt 2.54 + KSP |
| Bitcoin wallet | BDK Android 2.3.1 |
| Secure storage | EncryptedSharedPreferences (security-crypto 1.1.0-alpha06) |
| QR code | ZXing Core 3.5.3 |
| Serialization | kotlinx-serialization |

## Roadmap

### Phase 1 — Proof of Concept ✅
- Android app installable on POS machines
- Home screen with Charge and Settings actions
- Wallet abstraction layer with BDK integration
- Create, import, and delete wallet
- Generate receive addresses with QR code
- Electrum sync (full scan + incremental)
- Balance display and transaction history
- Secure storage for seed phrases and descriptors

### Phase 2 — NFC + SatsCard
- NFC integration for charging via [SatsCard](https://satscard.com/)
- Tap-to-pay flow for a seamless point-of-sale experience

### Phase 3 — Field Testing
- Pilot with selected merchants
- Feedback collection and iteration on user experience

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
