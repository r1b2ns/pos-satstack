# Fase 5 — Expansion plan

> Scope: everything that remains of the wallet abstraction roadmap after
> Fases 1 through 4 have shipped. Each section below is an **independent
> epic** — pick one, ignore the others, nothing in this phase forces a
> sequential order.
>
> Designed to be handed to a new session with no prior context; read this
> file plus [docs/architecture.md](architecture.md) and you should have
> everything you need.

---

## Quick status

| Fase | Subject | Status |
|---|---|---|
| 1 | Decouple app from BDK; `SignerSecretStore`; Esplora | ✅ shipped |
| 2 | Expand `OnChainWalletEngine` (PSBT / broadcast / fees / bump / backup) | ✅ shipped |
| 3 | Extract `Signer`; real `BiometricPrompt`; auth-required Keystore key for the mnemonic | ✅ shipped |
| 4 | `PaymentOrchestrator`; `ChargeScreen` + `ChargeDetailsScreen` wired through it | ✅ shipped |
| **5.1** | **TAPSIGNER NFC signer** | 🧩 scaffold shipped — secp256k1 crypto + hardware QA pending |
| **5.2** | **Lightning engine (LDK Node)** | ⏳ pending |
| **5.3** | **SATSCARD bearer method** | ⏳ pending |
| **5.4** | **Kyoto / Floresta chain backend swap** | 🛠️ Kyoto path implemented and wired (currently inactive — Esplora is the live binding) |

---

## Mandatory ground rules

These are project-wide conventions baked into the global `CLAUDE.md`. A
new session **must** honour them:

1. **Never** run `git commit` or `git push` without an explicit command
   from the user. Completing an epic does not imply permission to commit.
2. **Never** add `Co-Authored-By: Claude`, `Generated with Claude Code`,
   or any other AI attribution to commit messages, PR bodies, or code.
3. Always run `./gradlew :app:testDebugUnitTest`, `:app:ktlintCheck`, and
   `:app:assembleDebug` before declaring an epic done. Tests must pass.
4. Code style: no single-letter variable names; follow the existing
   Kotlin + Compose idioms (ktlint reformatting is already wired).
5. When creating new Android modules, mirror the structure of the
   existing `wallet/` package (contract interface + concrete impl + DI
   binding in [`WalletModule.kt`](../app/src/main/java/com/possatstack/app/di/WalletModule.kt)
   + test double in `app/src/test/...`).

---

## Architectural starting point

The wallet stack, after Fase 4, looks like this (full diagrams in
[docs/architecture.md](architecture.md)):

```
UI (Compose)
  │
  ├─ ChargeViewModel ─────┐
  ├─ ChargeDetailsViewModel
  ├─ WalletViewModel ─────┘
  │
  ▼
PaymentOrchestrator                 ← Fase 4 entry point
  │
  ├─ OnChainWalletEngine   ←── BdkOnChainEngine
  │     ├─ ChainDataSource   ← EsploraChainDataSource   [Fase 5.4 adds Kyoto / Floresta]
  │     └─ WalletStorage      ← SecureWalletStorage
  │
  ├─ LightningEngine         ← (not implemented)        [Fase 5.2]
  ├─ BearerMethodEngine      ← (not implemented)        [Fase 5.3]
  │
  └─ Signer                  ←── BdkSeedSigner          [Fase 5.1 adds TAPSIGNER]
        └─ SignerSecretStore ← AndroidKeystoreSignerSecretStore
              └─ BiometricAuthenticator ← AndroidBiometricAuthenticator
```

Key files to reference before coding anything:

- [`OnChainWalletEngine`](../app/src/main/java/com/possatstack/app/wallet/OnChainWalletEngine.kt) — contract shape.
- [`BdkOnChainEngine`](../app/src/main/java/com/possatstack/app/wallet/bitcoin/BdkOnChainEngine.kt) — reference concrete impl.
- [`Signer`](../app/src/main/java/com/possatstack/app/wallet/signer/Signer.kt) — signing contract + `SigningContext`.
- [`BdkSeedSigner`](../app/src/main/java/com/possatstack/app/wallet/signer/BdkSeedSigner.kt) — reference Signer impl.
- [`SignerSecretStore`](../app/src/main/java/com/possatstack/app/wallet/signer/SignerSecretStore.kt) — already exposes `readSeedBytes(auth, passphrase)` for LDK Node.
- [`PaymentOrchestrator`](../app/src/main/java/com/possatstack/app/wallet/payment/PaymentOrchestrator.kt) and [`DefaultPaymentOrchestrator`](../app/src/main/java/com/possatstack/app/wallet/payment/DefaultPaymentOrchestrator.kt).
- [`PaymentMethod`](../app/src/main/java/com/possatstack/app/wallet/payment/PaymentMethod.kt) — **already reserves** `Lightning` and `BearerCard(cardSerial)`.
- [`ChargePayload`](../app/src/main/java/com/possatstack/app/wallet/payment/Charge.kt) — **already reserves** `LightningInvoice` and `BearerSlot`.
- [`WalletBackup`](../app/src/main/java/com/possatstack/app/wallet/WalletBackup.kt) — **already reserves** `ChannelStateScb`.
- [`WalletError`](../app/src/main/java/com/possatstack/app/wallet/WalletError.kt) — neutral error hierarchy; add new cases here if needed.
- [`WalletModule`](../app/src/main/java/com/possatstack/app/di/WalletModule.kt) — one `@Binds` per contract. Extend here.
- [`MainActivity`](../app/src/main/java/com/possatstack/app/MainActivity.kt) — already a `FragmentActivity`, already attaches/detaches to `ActivityHolder`.
- [`libs.versions.toml`](../gradle/libs.versions.toml) — commented targets for `kyoto` and `floresta` already sit here as reminders.
- [`build.gradle.kts`](../app/build.gradle.kts) — `CHAIN_BACKEND` buildConfig field drives the cache wipe-on-swap.

---

## Fase 5.1 — TAPSIGNER NFC signer

**Goal.** Add a `Signer` implementation that talks to a
[TAPSIGNER](https://tapsigner.com/) card over NFC. The card holds the
private key and signs PSBTs on-tap; the app never sees the seed.

### Prerequisites already in place

- `Signer` contract exposes `suspend fun signPsbt(psbt, context)` — the
  same shape `BdkSeedSigner` uses. TAPSIGNER is a second implementation.
- `SigningContext` already carries recipients, totals, fee, and change
  address — enough for the signer to cross-check before applying
  signatures (defence against a rogue process swapping outputs).
- `MainActivity` extends `FragmentActivity`; `ActivityHolder` already
  surfaces the current activity for components that need dialogs. Reuse
  both for the NFC foreground dispatch.

### Design sketch

1. **Protocol layer** (`wallet/signer/tapsigner/`):
   - `TapsignerClient` — thin wrapper over `android.nfc.tech.IsoDep`.
     Implements the TAPSIGNER APDU commands we need:
     `status`, `new`, `xpub`, `sign`, `wait`. CBOR encoding.
   - `Cvc` — value class for the 6-digit CVC printed on the card. Never
     stored on disk; user types it per session.
   - Concrete errors: `TapsignerError` (sealed) — `RateLimited`,
     `WrongCvc`, `CardRemoved`, `ProtocolError(cause)`, `Timeout`.

2. **State machine** (`wallet/signer/tapsigner/TapsignerSessionState.kt`):
   A tap is interactive and multi-step. Expose a `Flow<TapsignerStep>`
   to the UI so it can render a dedicated dialog (NOT the existing
   `BiometricPrompt` flow).

   ```kotlin
   sealed interface TapsignerStep {
       data object AwaitingTap : TapsignerStep
       data object AwaitingCvc : TapsignerStep
       data object Signing : TapsignerStep
       data class Done(val signed: SignedPsbt) : TapsignerStep
       data class Failed(val error: TapsignerError) : TapsignerStep
   }
   ```

3. **Signer impl** (`wallet/signer/TapsignerNfcSigner.kt`):
   - Implements `Signer`.
   - `signPsbt(psbt, context)` collects the state-machine flow until a
     `Done` or `Failed`. Returns `SignedPsbt` on success; throws
     `WalletError.SigningFailed` on failure.
   - Needs an `NfcSessionLauncher` abstraction that knows how to enable
     `NfcAdapter.enableReaderMode(...)` on the currently-resumed
     `FragmentActivity` (via `ActivityHolder`).

4. **DI** (`WalletModule.kt`):
   - Today `@Binds Signer → BdkSeedSigner` is a single binding. For
     multi-signer, switch to a qualifier pattern:
     ```kotlin
     @Qualifier @Retention(BINARY) annotation class SeedSignerQualifier
     @Qualifier @Retention(BINARY) annotation class TapsignerSignerQualifier

     @Binds @Singleton @SeedSignerQualifier
     abstract fun seedSigner(impl: BdkSeedSigner): Signer
     @Binds @Singleton @TapsignerSignerQualifier
     abstract fun tapsignerSigner(impl: TapsignerNfcSigner): Signer
     ```
   - Or simpler: provide a `SignerRegistry` singleton that returns
     `List<Signer>` and let the UI pick by kind. Recommend the registry
     approach — cleaner growth path to Coldcard / airgap.

5. **UI** (new):
   - Signer picker sheet (reached from the Send / Charge flow when
     multiple signers are registered).
   - `TapsignerDialog` observes the `Flow<TapsignerStep>` and renders
     tap instructions, CVC entry, spinner, success / error.

### Files to create

```
app/src/main/java/com/possatstack/app/wallet/signer/tapsigner/
  TapsignerClient.kt
  TapsignerApdus.kt           — APDU byte templates
  TapsignerError.kt
  Cvc.kt
  TapsignerSessionState.kt
  NfcSessionLauncher.kt
  AndroidNfcSessionLauncher.kt — uses ActivityHolder

app/src/main/java/com/possatstack/app/wallet/signer/
  TapsignerNfcSigner.kt
  SignerRegistry.kt            — if picking the registry pattern
  SeedSignerQualifier.kt       — if picking qualifiers
  TapsignerSignerQualifier.kt

app/src/main/java/com/possatstack/app/ui/signer/
  TapsignerDialog.kt
  SignerPickerSheet.kt
```

### Files to modify

- `WalletModule.kt` — add tapsigner binding(s).
- `AndroidManifest.xml` — `<uses-feature android:name="android.hardware.nfc" android:required="false"/>` + NFC tech-discovered intent filter on `MainActivity`.
- `ChargeDetailsScreen.kt` / Send screen — launch signer picker when a
  signer is needed.
- `SigningContext` — no change expected; it already carries enough.

### Testing

- Unit-testable (no device) — covered today:
  - [`CvcTest`](../app/src/test/java/com/possatstack/app/wallet/signer/tapsigner/CvcTest.kt)
    — parsing, length bounds, digit-only check, defensive wipe.
  - [`CborTest`](../app/src/test/java/com/possatstack/app/wallet/signer/tapsigner/CborTest.kt)
    — encoder/decoder round-trips for the subset used by the
    tap-protocol (uints, neg-ints, byte strings, text strings, arrays,
    string-keyed maps); rejects trailing bytes.
  - [`TapsignerApdusTest`](../app/src/test/java/com/possatstack/app/wallet/signer/tapsigner/TapsignerApdusTest.kt)
    — `SELECT AID` byte layout, INS=0xCB envelope, status-word parsing,
    oversized payload rejection, command-encoding round-trip.
  - [`TapsignerResponseTest`](../app/src/test/java/com/possatstack/app/wallet/signer/tapsigner/TapsignerResponseTest.kt)
    — error-code mapping (429 → `WrongCvc`, 423 → `RateLimited`, 406 →
    `NotSetUp`) and `parseStatus` shape enforcement.
  - [`SignerRegistryTest`](../app/src/test/java/com/possatstack/app/wallet/signer/SignerRegistryTest.kt)
    — multibinding ordering, `findById`, `firstOfKind`, empty-set
    behaviour.

- Instrumented / manual (requires a real TAPSIGNER + an NFC-capable
  device):
  1. Install the debug build on a device with NFC enabled.
  2. Boot to the charge / wallet screen and trigger a flow that calls
     `signer.signPsbt(...)` (the wallet send flow lands alongside the
     hardware QA pass — until then, exercise via a debug entry point or
     a Compose preview that calls the signer directly).
  3. The `SignerPickerSheet` must list both "In-app seed" and
     "TAPSIGNER (NFC)"; pick the latter.
  4. The `TapsignerDialog` shows `AwaitingCvc`. Type the 6–8 digit code
     printed on the back of the card and tap *Continue*.
  5. The dialog moves to `AwaitingTap`. Hold the card flat against the
     back of the phone, NFC antenna aligned. Expect `Exchanging` for
     1–2s and then `Done` with a signed PSBT.
  6. Negative paths to verify by hand:
     - **Wrong CVC** — should show `WrongCvc` and allow retry.
     - **Card removed mid-tap** — should fail with `CardRemoved`.
     - **NFC disabled** — should fail with a clear `HostError` before
       reaching `AwaitingTap`.
     - **Repeated wrong CVC** — after the third miss, expect
       `RateLimited(waitSeconds = N)` with N seconds of cooldown.

### What ships in this scaffold

- **Architectural shell**: `Signer` multibinding via Hilt
  (`SignerRegistry`); `SignerPickerSheet` + `TapsignerDialog` Compose
  components; `NfcSessionLauncher` abstraction with an Android impl
  reading `NfcAdapter.enableReaderMode(...)` through `ActivityHolder`.
- **Wire format**: full APDU framing (`SELECT AID`, INS=0xCB envelope,
  status-word parsing) and a small CBOR encoder/decoder covering the
  protocol subset.
- **Commands modelled**: `status`, `xpub`, `sign`, `wait` with their
  CBOR shapes plus error-code mapping for `WrongCvc`, `RateLimited`,
  `NotSetUp`.
- **State machine**: `TapsignerStep` (`AwaitingCvc` → `AwaitingTap` →
  `Exchanging` → `Done` / `Failed` / `RateLimited`) published as a
  `Flow` from `TapsignerNfcSigner`; the dialog observes it.
- **Manifest**: `android.permission.NFC` and an optional
  `<uses-feature android:hardware.nfc>` so devices without NFC still
  install the app (the picker just hides TAPSIGNER on those builds).

### What is intentionally *not* shipped here (hardware-QA follow-up)

`TapsignerSession.run` deliberately fails at the secp256k1 ECDH
handshake with `TapsignerError.HostError("…requires hardware-QA
follow-up…")`. Reasons and the bounded follow-up:

1. **secp256k1 ECDH on Android is non-trivial.** Stock JCE filters the
   curve; we need a dedicated dep (e.g. `secp256k1-kmp`,
   `bitcoinj-core`, or pulling secp256k1 from BDK's native side).
   Picking and validating one belongs in the same PR as the manual QA
   pass, so the dep choice is informed by what actually flashes on
   hardware. The integration point is the
   `TapsignerCrypto` interface — swap
   `UnavailableTapsignerCrypto` for the real impl in
   [`WalletModule.companion.provideTapsignerCrypto`](../app/src/main/java/com/possatstack/app/di/WalletModule.kt).
2. **PSBT digest extraction / signature splicing.** The session has the
   client + session key + `xcvc` ready; the missing logic is "for each
   PSBT input, build the segwit / BIP-143 sighash, send `sign`, splice
   the returned `(r, s)` back into the PSBT, finalise". This requires
   touching BDK directly to reuse its sighash builder — also gated on
   hardware so the choice of finaliser matches what the card actually
   produces.
3. **Coldcard / airgap QR signers.** Out of scope for 5.1; the
   `SignerRegistry` accepts them via a single `@IntoSet` line each.

### Risks

- **No test card on CI** — you'll need at least one manual QA pass per
  release. Budget for it.
- **Rate limiting**: TAPSIGNER throttles after 3 wrong CVCs with
  increasing backoff. Surface this clearly in UI.
- **User experience**: NFC positioning on POS devices varies wildly.
  Provide visual guidance.

---

## Fase 5.2 — Lightning engine (LDK Node)

**Goal.** Add a `LightningEngine` implementation based on
[LDK Node](https://github.com/lightningdevkit/ldk-node), so the POS can
receive (and eventually send) Lightning payments alongside on-chain.

### Prerequisites already in place

- `LightningMethod` is already a case in the sealed `PaymentMethod`.
- `ChargePayload.LightningInvoice(bolt11)` is already reserved.
- `WalletBackup.ChannelStateScb(payload, network)` is already a
  polymorphic branch — exactly what SCB-style backups need.
- `SignerSecretStore.readSeedBytes(auth)` returns the 64-byte BIP-39
  seed that LDK Node consumes directly.
- `noBackupFilesDir/bdk/` is segregated from the reserved
  `noBackupFilesDir/ldk/` — the wipe-on-swap logic in
  `BdkOnChainEngine.migrateLegacyDbPath` only ever touches `bdk/`.

### Design sketch

1. **Contract** (`wallet/lightning/LightningEngine.kt`). Mirror the
   sketch in the architecture doc:
   ```kotlin
   interface LightningEngine {
       suspend fun hasNode(): Boolean
       suspend fun start()
       suspend fun stop()

       suspend fun createInvoice(amountSats: Long, memo: String, expirySeconds: Int): LnInvoice
       fun invoiceStatus(paymentHash: String): Flow<LnInvoiceStatus>
       suspend fun payInvoice(bolt11: String): LnPayment
       suspend fun getBalance(): LnBalance
       suspend fun backup(): WalletBackup   // kind = ChannelStateScb
       suspend fun restoreFromBackup(backup: WalletBackup)
   }
   ```

2. **Implementation** (`wallet/lightning/LdkNodeEngine.kt`):
   - Uses `LDKNode.start()` during app boot (gated behind the foreground
     service — see step 3).
   - Seed → derived from `SignerSecretStore.readSeedBytes(auth)`. Same
     biometric gate as BDK signing; store the 64 bytes in memory only
     while the node is running.
   - Chain source — LDK Node accepts Esplora URL on construction. When
     `BuildConfig.CHAIN_BACKEND == "esplora"` reuse the same URL the
     on-chain engine uses; otherwise accept a dedicated config for
     Lightning only (Kyoto doesn't provide full txs that LDK needs).
   - Persistence: `noBackupFilesDir/ldk/`. LDK Node handles its own
     schema; just point it at that path.

3. **Foreground service** (`service/LightningNodeService.kt`):
   - Mirror `WalletSyncService` but with a different lifecycle:
     Lightning needs the node running whenever the app is "live for
     payments". Start on `MainActivity.onResume`, keep alive until
     merchant explicitly disables LN or app is killed.
   - Use `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (API 34+) with the
     justification "Bitcoin payments" or equivalent. POS devices
     typically have doze-whitelisting — validate.

4. **Orchestrator wiring** (`DefaultPaymentOrchestrator`):
   - Today `createCharge(Lightning, ...)` throws. Replace with:
     ```kotlin
     is PaymentMethod.Lightning -> createLightningCharge(amountSats, memo)
     ```
     Produces a `ChargePayload.LightningInvoice(bolt11)`.
   - Status monitor: observe `lightningEngine.invoiceStatus(paymentHash)`
     and translate `Paid` → `ChargeStatus.Confirmed(txid = null, blockHeight = null)`,
     `Expired` → `ChargeStatus.Expired`.
   - `availableMethods()` adds `Lightning` when `lightningEngine.hasNode()`.

5. **LSP decision** (product, not architectural):
   - **Breez SDK** (custodial via LSP) — easiest UX, trust trade-off.
   - **LDK Node + LSPS1/LSPS2** (self-custodial with JIT channels) —
     recommended long-term, heavier engineering.
   - **Voltage / Olympus / Blocktank** — pick one LSPS provider and
     wire via `ldk-node`'s built-in LSPS client.
   - Document the choice in `docs/lightning.md` when deciding.

6. **Backup strategy** (product, not optional):
   - Mnemonic alone is **insufficient** to recover channels. Implement
     at least one of:
     a) SCB export on each open/update (merchant emails to themselves);
     b) VSS integration with an LDK-compatible server;
     c) LSP with state-replication (Phoenix style).
   - `LightningEngine.backup()` already returns a `WalletBackup`, so
     the output shape exists — fill in the payload.

### Files to create

```
app/src/main/java/com/possatstack/app/wallet/lightning/
  LightningEngine.kt
  LnInvoice.kt / LnInvoiceStatus.kt / LnPayment.kt / LnBalance.kt
  LdkNodeEngine.kt
  LdkNodeErrors.kt             — node.NodeError → WalletError
  LightningBackupPolicy.kt     — whichever strategy you pick

app/src/main/java/com/possatstack/app/service/
  LightningNodeService.kt

app/src/main/java/com/possatstack/app/ui/lightning/
  LightningOnboardingScreen.kt — one-time LSP / channel setup
  LnInvoiceDetails.kt           — rendered inside ChargeDetailsScreen
```

### Files to modify

- `DefaultPaymentOrchestrator.kt` — wire Lightning branch.
- `ChargeDetailsScreen.kt` — render `ChargePayload.LightningInvoice`
  for real (today it shows a placeholder).
- `WalletModule.kt` — bind `LightningEngine` + provide LN config.
- `libs.versions.toml` + `build.gradle.kts` — add
  `ldk-node` dep when coordinates are chosen.
- `AndroidManifest.xml` — declare `LightningNodeService` +
  foreground-service permission.

### Testing

- `LdkNodeEngine` is heavy to unit-test (FFI + network). Prefer
  integration tests against regtest.
- `DefaultPaymentOrchestratorTest` — add a `FakeLightningEngine`
  mirroring the existing `FakeOnChainWalletEngine` pattern, then cover
  `createCharge(Lightning, ...)` happy path + status flow.
- Manual QA against signet + public LSP.

### Risks

- **Custody model**: non-negotiable UX transparency. Merchants must
  understand who holds the keys.
- **Inbound liquidity**: first payment will fail without LSPS
  integration. Either ship onboarding that proactively opens a channel,
  or default to a custodial provider.
- **Device sleep**: Lightning fundamentally needs the node online; POS
  devices that sleep aggressively will drop HTLCs. Budget for
  foreground-service hardening + doze allowlisting.

---

## Fase 5.3 — SATSCARD bearer method

**Goal.** Accept payments that arrive on a
[SATSCARD](https://satscard.com/) — a 10-slot bearer instrument. The
merchant reads the current slot's deposit address and tracks it like any
on-chain receive, then optionally sweeps it into the POS wallet.

### Prerequisites already in place

- `PaymentMethod.BearerCard(cardSerial)` is a case of the sealed type.
- `ChargePayload.BearerSlot(address)` is reserved.
- `MainActivity` already supports NFC (after Fase 5.1 lands). If Fase
  5.1 hasn't shipped, this epic needs to add the NFC foreground
  dispatch too — see Fase 5.1 notes.

### Design sketch

1. **Contract** (`wallet/bearer/BearerMethodEngine.kt`):
   ```kotlin
   interface BearerMethodEngine {
       suspend fun readCard(session: NfcSession): SatscardState
       suspend fun currentDepositAddress(card: SatscardState): BitcoinAddress
       suspend fun sweepToOnchain(
           card: SatscardState,
           destination: BitcoinAddress,
           onChain: OnChainWalletEngine,
       ): Txid
   }

   data class SatscardState(
       val serial: String,
       val activeSlot: Int,
       val slots: List<SatscardSlot>,
   )
   data class SatscardSlot(
       val index: Int,
       val address: BitcoinAddress,
       val state: SlotState,  // UNUSED | SEALED | REVEALED
   )
   ```

2. **Implementation** (`wallet/bearer/SatscardBearerEngine.kt`):
   - Uses the same `TapsignerClient` scaffolding from Fase 5.1
     (SATSCARD and TAPSIGNER share the same base protocol — `status`,
     `read`, `unseal`, `derive`, `dump`).
   - Reveal flow calls `unseal` on the active slot → exposes the
     private key → sweep into the merchant's on-chain wallet via
     `OnChainWalletEngine.createUnsignedPsbt` + signer.

3. **Orchestrator + UI**:
   - Do **not** try to fit SATSCARD into the standard keypad flow. The
     product is different — "scan a card that already has funds" rather
     than "create an invoice". Expose as a separate entry on the home
     screen ("Accept SATSCARD").
   - `DefaultPaymentOrchestrator.createCharge(BearerCard(...), ...)`
     either throws "use the SATSCARD entry point" or produces a
     specific charge type whose status is simply "the address
     received ≥ amount".

4. **Security note**: revealing a SATSCARD slot is irreversible. The
   UI must require explicit confirmation and show the amount the card
   holds before revealing.

### Files to create

```
app/src/main/java/com/possatstack/app/wallet/bearer/
  BearerMethodEngine.kt
  SatscardState.kt / SatscardSlot.kt
  SatscardBearerEngine.kt

app/src/main/java/com/possatstack/app/ui/bearer/
  SatscardHomeEntry.kt
  SatscardReadScreen.kt
  SatscardRevealConfirmDialog.kt
  SatscardSweepScreen.kt
```

### Files to modify

- `AppDestination.kt` — new routes for the bearer flow.
- `NavGraph.kt` — wire the routes.
- `SettingsScreen.kt` or home — add a "SATSCARD" entry.
- `WalletModule.kt` — bind `BearerMethodEngine`.

### Testing

- `SatscardState` equality / slot transitions — unit-testable.
- Protocol logic — same approach as Fase 5.1.
- End-to-end requires a physical SATSCARD with testnet funds.

### Risks

- **One-way reveal**: design the UI to make "unseal" a deliberate
  two-step confirmation.
- **Lost cards**: the merchant can't recover the funds if the card is
  lost before sweep. Consider adding a reminder to sweep immediately.

---

## Fase 5.4 — Kyoto / Floresta chain backend swap

**Goal.** Replace the current Esplora-backed `ChainDataSource` with
either [Kyoto](https://github.com/rustaceanrob/kyoto) (BIP-157/158
light client) or [Floresta](https://github.com/vinteumorg/Floresta)
(Utreexo full node, typically via its Electrum-compatible surface).

### Prerequisites already in place

- `ChainDataSource` interface already has `configureFor(network)`,
  `broadcastRaw(rawTx)`, `estimateFees(target)`, `getBlockHeight()`.
- `EsploraChainDataSource` is the reference implementation.
- `BuildConfig.CHAIN_BACKEND` drives `BdkOnChainEngine.sync()` to wipe
  the `bdk/` cache and force a fresh full scan when the value changes
  between builds. Mnemonic and (future) LDK state are untouched.
- `libs.versions.toml` already has commented placeholder entries for
  `kyoto` and `floresta` deps.
- `WalletModule.bindChainDataSource` is a single `@Binds` call —
  swapping it is one line.

### Design sketch — Kyoto path (🛠️ implemented, currently inactive)

The Kyoto path is fully wired and compiles into the build, but the live
DI binding is back to Esplora today. To activate Kyoto, swap two
`@Binds` lines in [`WalletModule`](../app/src/main/java/com/possatstack/app/di/WalletModule.kt)
and flip `CHAIN_BACKEND` in [`app/build.gradle.kts`](../app/build.gradle.kts)
to `"kyoto"` — the engine detects the change on next boot, wipes the
`bdk/` cache, and runs a fresh scan via the CBF light client.

What was actually built (matches the design sketch with one
simplification — no new dependency was needed because `bdk-android:2.3.x`
already exposes the CBF types):

1. **No new gradle dep.** `bdk-android:2.3.1` ships
   [`CbfBuilder`](https://bitcoindevkit.org/), `CbfClient`, `CbfNode`,
   `CbfComponents`, `CbfException`, `Peer`, `ScanType`, `Info`, and
   `Warning`. The same artifact serves both the Esplora and the Kyoto
   paths; the swap lives in DI + `CHAIN_BACKEND`, not in the module
   coordinates.
2. **`ChainSyncProvider` abstraction added** —
   [`ChainSyncProvider.kt`](../app/src/main/java/com/possatstack/app/wallet/chain/ChainSyncProvider.kt).
   `BdkOnChainEngine.sync()` no longer constructs any chain client
   directly; it forwards `(wallet, network, fullScan, onProgress)` to
   the injected provider, then persists. Two concrete impls:
   - [`EsploraChainSyncProvider`](../app/src/main/java/com/possatstack/app/wallet/chain/EsploraChainSyncProvider.kt)
     — original `EsploraClient.fullScan / sync` logic, lifted out as-is.
   - [`KyotoChainSyncProvider`](../app/src/main/java/com/possatstack/app/wallet/chain/KyotoChainSyncProvider.kt)
     — bootstraps `CbfBuilder.dataDir(noBackupFilesDir/kyoto/).scanType(...).build(wallet)`
     per call, runs `CbfNode.run()` in a child coroutine, drains
     `CbfClient.update()` and applies each `Update` to the wallet,
     publishes `Info.Progress` events as `SyncProgress.Syncing(percent)`,
     and tears the node down via `CbfClient.shutdown()` when the update
     stream goes silent for `SETTLE_TIMEOUT_MS` (8s).
3. **`KyotoChainDataSource`** —
   [`KyotoChainDataSource.kt`](../app/src/main/java/com/possatstack/app/wallet/chain/KyotoChainDataSource.kt).
   Implements `ChainDataSource` for broadcast / fee / height by
   delegating to the existing Esplora HTTP client (the doc explicitly
   accepts this fallback: "fall back to a tiny mempool.space HTTP call
   for fees"). Once an always-on CBF foreground service lands,
   `broadcastRaw` can route through `CbfClient.broadcast` directly and
   `estimateFees` can combine `minBroadcastFeerate` with a target-aware
   oracle.
4. **DI swap** — both bindings (`ChainDataSource` and
   `ChainSyncProvider`) now resolve through `WalletModule.@Binds` and
   the active backend is selected by which two lines are uncommented.
   The wipe-on-swap heuristic in `BdkOnChainEngine` reads
   `BuildConfig.CHAIN_BACKEND`, so changing the binding without bumping
   that string is a silent footgun — the comment in `WalletModule`
   spells out the two-step swap. Today the binding is Esplora and
   `CHAIN_BACKEND = "esplora"`; the Kyoto entries are present in source
   but inactive, ready to flip when an always-on CBF foreground service
   lands.

### Known limitations (intentionally out of scope here)

- **Bootstrap-per-call** — every `engine.sync()` re-builds a `CbfNode`
  and tears it down. CBF is naturally a long-running process; moving
  the node to a foreground service (similar to `WalletSyncService`
  today) is the right next step but not blocking.
- **DNS-seed only** — no curated peer list is shipped. The CBF builder
  falls back to the BDK-bundled DNS seeds. Deployments that need
  deterministic peering should call `CbfBuilder.peers(List<Peer>)`
  inside `KyotoChainSyncProvider` (one-line change).
- **Settle heuristic** — the scan is considered done when no new
  `Update` arrives within `SETTLE_TIMEOUT_MS = 8s`. Acceptable for
  incremental syncs; mainnet full scans need this bumped or the
  `ScanType.Recovery(checkpoint)` variant once the engine surfaces a
  `RecoveryPoint`.
- **Fees and broadcast still hit Esplora HTTP** by design (see
  `KyotoChainDataSource`). The architecture already isolates that
  fallback so we can swap it later without touching upstream callers.

### Design sketch — Floresta path

Floresta exposes an Electrum-compatible protocol, so adaptation is
simpler:

1. Add the dep (or an Electrum client that points at a local florestad).
2. `FlorestaChainDataSource` = lightly-tweaked copy of
   `EsploraChainDataSource` that uses BDK's `ElectrumClient` with the
   local florestad URL.
3. Engine sync path — BDK's `ElectrumClient.fullScan` / `sync` still
   works; less churn than Kyoto. May even reuse the Esplora path
   largely unchanged.
4. Swap the `@Binds` + `CHAIN_BACKEND = "floresta"`.

### Files to create

```
app/src/main/java/com/possatstack/app/wallet/chain/
  KyotoChainDataSource.kt        — if going Kyoto
  FlorestaChainDataSource.kt     — if going Floresta
  ChainSyncProvider.kt           — optional sync abstraction
  EsploraChainSyncProvider.kt    — moved from BdkOnChainEngine if introduced
  KyotoChainSyncProvider.kt      — if going Kyoto
```

### Files to modify

- `libs.versions.toml` — uncomment the target dep.
- `app/build.gradle.kts` — update `implementation` line and
  `buildConfigField("CHAIN_BACKEND", ...)`.
- `WalletModule.kt` — swap the `@Binds ChainDataSource` line.
- `BdkOnChainEngine.kt` — refactor `sync()` to use
  `ChainSyncProvider` or rewrite internal logic for the new client.
- `README.md` / `docs/architecture.md` — update the "active backend"
  labels.

### Testing

- Existing `EsploraChainDataSource` has no unit test (network-heavy).
  Same stands for Kyoto / Floresta.
- Keep `FakeChainDataSource` in tests; nothing about the orchestrator
  tests changes.
- Manual validation:
  - Do a wallet creation + receive + send round-trip on signet with the
    new backend.
  - Verify the `bdk/` cache was wiped on first boot after the swap
    (check logs).
  - Verify the mnemonic is untouched (descriptor file and mnemonic
    secret prefs both intact).

### Risks

- **Kyoto fee estimation** is awkward — P2P has no fee oracle. Either
  bolt on a separate HTTP fee source or accept a degraded UX (default
  rates, let the user override).
- **Floresta maturity** — as of early 2026 it's still pre-1.0. Budget
  for breaking-change upgrades.
- **Persistence compatibility**: in theory the BDK SQLite schema is
  backend-agnostic, but in practice the wipe-on-swap behaviour is
  defensive — keep it.

---

## Definition of done for Fase 5

Each epic ships when:

1. The new contract / implementation exists, with neutral types (no
   library classes leaking through the public surface).
2. DI wiring is swapped in `WalletModule` and documented in
   [docs/architecture.md](architecture.md).
3. Unit tests cover anything library-independent; manual QA notes exist
   for anything that requires hardware.
4. `./gradlew :app:testDebugUnitTest :app:ktlintCheck :app:assembleDebug`
   is green.
5. README and architecture diagrams are updated.

When all four epics are in, the wallet abstraction roadmap is complete —
new payment methods, chain backends, or signers can be added by dropping
a new `@Binds` into `WalletModule` and nothing else.
