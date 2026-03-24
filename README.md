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
  WalletRepository.kt       — interface (createWallet, loadWallet, getNewReceiveAddress)
  bitcoin/
    BdkWalletRepository.kt  — BDK 2.x implementation
```

## Requirements

- Android 8.0+ (API 26)
- Android-based POS machine or any Android device for development/testing

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose |
| Bitcoin wallet | BDK Android 2.3.1 |

## Roadmap

### Phase 1 — Proof of Concept ✅
- Android app installable on POS machines
- Home screen with Charge and Settings actions
- Wallet abstraction layer with BDK integration
- Create wallet, load wallet, and generate receive addresses

### Phase 2 — NFC + SatsCard
- NFC integration for charging via [SatsCard](https://satscard.com/)
- Tap-to-pay flow for a seamless point-of-sale experience

### Phase 3 — Field Testing
- Pilot with selected merchants
- Feedback collection and iteration on user experience

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
