# Watch Sync v2 Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lay the inert or flag-gated groundwork for the version 2 watch and phone protocol (Track A "A-foundations", C1–C5 of the approved master plan): the wire messages, the shared state document and the Labs switch that now reaches the watch, the Room tables for marked moments and watch delivery, identity linking primitives with the X25519 key exchange, and a linked-watch profile for the shared sync engine, so the A-v2 work (D1–D10) builds on tested parts without changing what any user sees today.

**Architecture:** Eleven test-first commits on `claude/wearos-android-ux-redesign-081dc0`, each shippable on its own. The protocol is pure `commonMain` code in `:client:sync` (`app.logdate.client.sync.datalayer.v2` for messages and the gate, `app.logdate.client.sync.peer.state` for the state document), so it compiles for iOS and is shared by the phone (`:app:compose-main` `androidMain`) and the watch (`:app:wear`). Each app talks to Play services through one small adapter per app (`PhonePeerLink`, `WatchPeerLink`) behind three narrow interfaces. The phone always publishes `/logdate/state` and adds or removes its dynamic `logdate_phone_sync_v2` capability with `WATCH_EXPERIENCE_V2`; the watch mirrors only the phone-owned flag into its own `FeatureFlagStore`. Identity primitives live in `:client:device` (`IdentityKeyFingerprint`, `IdentityMintPolicy`, `LinkHandshake` with a BouncyCastle 1.84 implementation in `androidMain`). The engine changes in `:client:sync` are all behind `SyncProfile.LinkedWatch`, which nothing constructs yet (A-v2 D3 does), with `SyncProfile.Standard` keeping every phone, desktop and iOS path as it is.

**Tech Stack:** Kotlin 2.3.21 Multiplatform (Android, iOS, JVM desktop), kotlinx.serialization 1.11.0 JSON, kotlinx.coroutines 1.11.0 and Flow, kotlinx-datetime 0.7.1 with `kotlin.time.Instant`/`Clock`, Koin 4.2.1, Room KMP (schema 50 → 51, `MigrationTestHelper` on the JVM), Ktor client with `MockEngine`, BouncyCastle `bcprov-jdk18on` 1.84 (X25519, HKDF-SHA256, AES-256-GCM, HMAC-SHA256), Play services Wearable 19.0.0 (`DataClient`, `CapabilityClient`, `NodeClient`), JUnit 4 + `kotlin.test` + MockK 1.14.9 + Turbine 1.2.1, the Wear Gradle Managed Device `wearSmallRoundApi34`.

## Global Constraints

- Inert or flag-gated: nothing constructs `SyncProfile.LinkedWatch`, `RoutedSyncMetadataService`, `LinkHandshake`, `PeerDeliveryDao` or `MarkedMomentDao` outside tests; the only always-on behavior is the phone publishing its state data item and adding or removing its own dynamic capability, which older watches never subscribe to.
- v1 compatible in both directions: every new Data Layer path is outside the v1 manifest prefixes (`/logdate/notes`, `/logdate/journals`, `/logdate/associations`, `/logdate/health`, `/logdate/camera`, `/logdate/sync`), so a build without this plan never receives one.
- A newer schema is never guessed at: every v2 decoder returns `Decoded.Unsupported` for a schema it does not know and ignores unknown keys inside a schema it does.
- Logging: Napier only. Never `println`, `System.out` or `Log.*`. Logs never carry journal text, captions, tokens, keys, codes or raw JSON.
- UUIDs: `kotlin.uuid.Uuid`; never `java.util.UUID`.
- Time: `kotlin.time.Instant` and `kotlin.time.Clock` (where kotlinx-datetime 0.7 puts them); `kotlinx.datetime.TimeZone` for zones.
- Serialization: kotlinx.serialization only; wire JSON through `WearJson` (`ignoreUnknownKeys`, `encodeDefaults`, `explicitNulls = false`).
- DI: Koin constructor injection; Play services handles are created only in Koin modules and adapter secondary constructors.
- Imports: lexicographic per ktlint_official, `java.*`, `javax.*` and `kotlin.*` last, no wildcards. Run `./gradlew :<module>:ktlintFormat` when `ktlintCheck` fails, rerun the task's tests, and stage the reformatted file.
- Style: early returns and guard clauses; KDoc says why, never restates code; one parameter per line with trailing commas once a call or declaration has two or more.
- Never drop errors: a failure is logged with what failed and what happens next, and stays visible (a `Result.failure`, a `Decoded.Malformed`, a paused reason, an outbox row). No silent fallback.
- Copy: literal, never "we", never "kept", "held" or "carried"; user-visible text lives in string resources.
- Detekt: a class stays under 600 lines of code and a function under 60 (`config/detekt/detekt.yml:33-40`); the `:client:sync` baseline already lists the long `SyncUploader` functions.
- iOS: every `commonMain` change compiles with `:<module>:compileKotlinIosSimulatorArm64` and `:<module>:compileTestKotlinIosSimulatorArm64`. Kotlin/Native traps: no JVM-only okio or `Map.merge`, and backtick test names in `commonTest` carry no commas, periods, colons, slashes or parentheses.
- Gradle: run from `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`, and prefix every command with `export ANDROID_HOME=$HOME/Library/Android/sdk &&`.
- Devices: emulators and Gradle Managed Devices only. Never `connected*AndroidTest`, `install*`, `uninstall*` or any `adb` command; a physical phone may be attached. Watch instrumented tests run only as `:app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<class>`. If the managed emulator reports "Process crashed" or "failed to complete startup", rerun once with the machine otherwise idle.
- Test-first: write the test, add the API with inert bodies so it compiles and fails on its assertions, then implement. Inert bodies never reach a commit.
- Commits: Conventional Commits, title at most 72 characters, scope from `allowed-scopes.txt` (`sync`, `wear`, `database`, `device`); never the `style` type and never `build`, `android`, `ios`, `desktop` or `infra` as a scope. The body is full sentences wrapped at 72 characters; the last line is `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Commit mechanics: the message is written by its own command, a quoted heredoc to `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/<name>.txt`; the next command is the single chain `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>`. Never `git add -A`, `git add .`, `git commit -a`, `-m` or `--no-verify`.
- Every commit leaves the branch shippable: the touched modules' tests, `ktlintCheck` and `detekt` pass first.
- Order against other plans: `docs/superpowers/plans/2026-10-03-server-device-sessions-and-moments.md` (written in parallel) owns `POST /auth/devices/link`, `POST /auth/devices/link/redeem`, `DeviceLinkApiClient`, the server's `If-None-Match: *` semantics and the descriptor flag for conditional create. Tasks 8 and 9 here only add client calls, and Task 8 refuses to send a conditional create to a server that does not advertise it.

---

## What the code shows today (verified on `claude/wearos-android-ux-redesign-081dc0` at `17490e104`)

| Item | Finding | Where |
|---|---|---|
| Room version | 50, with `MIGRATION_49_50` (durable download inbox, operation ids) the newest; schemas exported to `client/database/schemas/app.logdate.client.database.LogDateDatabase/50.json`. The JVM migration tests build `MigrationTestHelper(Path.of("schemas"), …, BundledSQLiteDriver(), LogDateDatabase::class, { LogDateDatabaseConstructor.initialize() }, emptyList())`. | `client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt:159-217, 340-390`; `migrations/MIGRATION_49_50.kt`; `jvmTest/.../DownloadInboxMigrationJvmTest.kt:16-89` |
| Export coverage | `ExportCoverageTest` fails for any table in the newest schema that it does not classify. | `client/database/src/jvmTest/kotlin/app/logdate/client/database/ExportCoverageTest.kt:44-87` |
| Outbox DAO | `SyncMetadataDao` is an interface with `@Transaction` default methods (`incrementRetryIfCurrent`, `deletePendingIfCurrent`); no way to move rows between owners. Its only test double is `InMemorySyncMetadataDao` in `DatabaseSyncMetadataServiceTest`. | `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/SyncMetadataDao.kt:16-181`; `client/sync/src/commonTest/.../metadata/DatabaseSyncMetadataServiceTest.kt:213` |
| `EntityType` | `JOURNAL, NOTE, ASSOCIATION, MEDIA, HEALTH, DRAFT`; one exhaustive `when` outside `:client:sync`. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/SyncMetadataService.kt:150-157`; `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/sync/SyncStatusSheet.kt:517-528` |
| Key fingerprint | Private to `SyncPayloadCipher`: `Base64.encode(cryptoManager.hmacSha256(identityKey, "sync-payload-fingerprint").copyOf(8))`. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/crypto/SyncPayloadCipher.kt:106-119` |
| Identity keys | `IdentityKeyManager(secureStorage, cryptoManager, backupStore)` mints in `setupNewIdentity()` and `ensureIdentityKey()`; `installAccountKey` installs a key for an account. Nothing prevents minting. | `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyManager.kt:17-123` |
| Owner | `DefaultCanonicalOwnerProvider` stores `identity.canonical_owner_id` and `identity.canonical_owner_bound`; nothing resets them. 26 classes implement the interface, almost all test doubles. | `client/device/src/commonMain/kotlin/app/logdate/client/device/identity/CanonicalOwnerProvider.kt:15-98` |
| Platforms | `DevicePlatform` has no Wear OS value; `DevicesViewModel.getPlatformName` is an exhaustive `when`. | `client/device/src/commonMain/kotlin/app/logdate/client/device/models/DevicePlatform.kt:6-14`; `client/feature/core/.../settings/ui/devices/DevicesViewModel.kt:133-142` |
| BouncyCastle | `bouncycastle = "1.84"`, `bouncycastle-bcprov` → `bcprov-jdk18on`, on `:client:device` `androidMain` and `desktopMain`. Checked in the 1.84 jar: `X25519PrivateKeyParameters.generateSecret` throws `IllegalStateException("X25519 agreement failed")` for an all-zero result; `HKDFBytesGenerator`, `SHA256Digest.newInstance()`, `GCMBlockCipher.newInstance(AESEngine.newInstance())`, `AEADParameters`, `HMac` all exist. `AndroidDeviceTransfer` already uses X25519 from it. | `gradle/libs.versions.toml:81, 291`; `client/device/build.gradle.kts:64-76`; `client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/AndroidDeviceTransfer.kt:6-7` |
| Flag | `FeatureFlag.WATCH_EXPERIENCE_V2(key = "watch_experience_v2_enabled", defaultEnabled = false)`. Labs says "Applies to this phone only."; the row clamps the description to two lines. | `client/datastore/.../featureflags/FeatureFlag.kt:66`; `client/feature/core/src/commonMain/composeResources/values/strings_labs.xml:9`; `client/ui/src/commonMain/kotlin/app/logdate/ui/common/SettingsItems.kt:171-174` (`maxLines = 2`) |
| Data Layer listeners | Watch: `DATA_CHANGED` for notes, journals, associations, camera; `WearManifestFiltersTest` resolves each path against the installed manifest. Phone: `DATA_CHANGED` for notes, journals, associations, health. Both act only on `isChangeFromAnotherDevice`. | `app/wear/src/main/AndroidManifest.xml:74-118`; `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt:38-50`; `app/android-main/src/main/AndroidManifest.xml:225-269`; `app/compose-main/.../client/sync/PhoneDataLayerListenerService.kt:184-244, 466-470` |
| Capability codes | `WearableStatusCodes.DUPLICATE_CAPABILITY = 4006`, `UNKNOWN_CAPABILITY = 4007`; `CapabilityClient.addLocalCapability`/`removeLocalCapability` return `Task<Void>` (checked in the 19.0.0 AAR). | play-services-wearable 19.0.0 |
| Engine | `DefaultSyncManager` always mints (`provisionIdentityKey`, lines 191-245), always sweeps on first sync (`fullSync`, 558), always syncs drafts (561) and location history (338, 385), always hydrates media (386, `recoverMedia` 653-679). The class has about 530 lines of code. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt` |
| Download engine | `SyncDownloadEngine` applies every remote create (`existing == null` branch, 337-341) and queues media recovery for every applied note (`enqueueMedia = row.entityId !in skippedIds`, 351-352). The class has about 590 lines of code, so additions need room made. `DownloadInbox.count()` includes `MEDIA_NOTE` rows, and `fullSync` loops while it is non-zero. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloadEngine.kt:85-714`; `recovery/DownloadInbox.kt:234-352` |
| Uploads | Content and journal CREATE call `uploadNote`/`uploadJournal` (`PUT /contents/{id}`, `PUT /journals/{id}` on the server, which overwrite); UPDATE always uploads. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncUploader.kt:167-260, 330-484`; `cloud/LogDateCloudApiClient.kt:409-436, 530-557` |
| Token refresh | `SyncTokenRefresher` refreshes through `CloudAccountRepository.refreshAccessToken` (access token only), clears the session for `INVALID_REFRESH_TOKEN`/`REFRESH_TOKEN_REVOKED`, and never stores a new refresh token. `RefreshTokenDataV1Dto` reads only `accessToken`. | `client/sync/.../SyncTokenRefresher.kt:103-159`; `cloud/LogDateCloudApiClient.kt:267-298, 1160-1169` |
| Error codes | `safeCloudErrorCode` maps any code not in `SAFE_CLOUD_ERROR_CODES` to `UNKNOWN_ERROR`; there is no `DEVICE_SESSION_REVOKED`. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SafeCloudErrorCode.kt:7-107` |
| API wrapper | `DurableCloudApiClient(delegate, …) : CloudApiClient by delegate`. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/recovery/DurableCloudApiClient.kt:21-24` |
| Paused reasons | `NOT_SIGNED_IN, OFFLINE, BACKGROUND_DATA_OFF, MEDIA_WAITING_FOR_WIFI, NEEDS_RECOVERY_PHRASE`; two exhaustive `when`s each in `SyncSettingsScreen.kt` and `SyncStatusSheet.kt`. | `client/sync/.../SyncManager.kt:185-213`; `client/feature/core/.../settings/ui/SyncSettingsScreen.kt:477-501`; `client/feature/core/.../sync/SyncStatusSheet.kt:407-425` |

The master plan names migration "50" for these tables; `main` already used 50, so this plan adds **51**.

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocol.kt` | 1 | Capability names, schema numbers, limits, `WearFeatures` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt` | 1 | Every v2 path, record path parsing |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearJson.kt` | 1 | `Decoded`, the shared JSON and schema-aware decoding |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/RecordEnvelope.kt` | 1 | `RecordKind`, `RecordEnvelope`, `RecordEnvelopeCodec` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/Receipts.kt` | 1 | Receipt states, `CloudPresence`, request and response |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LinkMessages.kt` | 1 | Offer, accept, sealed bundle, bundle, confirm, refusal, `LinkNonceLedger` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LiveMessages.kt` | 1 | Live recording and playback state and commands, `LiveSequenceGate` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt` | 2 | The per-device state document and `StateDocumentCodec` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateMerger.kt` | 2 | Ownership, last-writer-wins, presence staleness |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerStateStore.kt` | 2 | Latest readable document per peer node |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerLink.kt` | 2 | `StateDocumentChannel`, `LocalCapabilities`, `PeerCapabilityPresence` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocolGate.kt` | 2 | `isV2Active()`, `isFeatureActive()` |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhonePeerLink.kt` | 3 | Phone's Play services adapter for the three interfaces |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt` | 3 | Publishes the phone document; toggles `logdate_phone_sync_v2` |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt` | 3 | Routes the watch's `/logdate/state` to `PeerStateStore` |
| `app/android-main/src/main/AndroidManifest.xml` | 3 | `/logdate/state` in the phone listener's `DATA_CHANGED` filter |
| `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt`, `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt` | 3 | Bindings and start |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchPeerLink.kt` | 4 | Watch's Play services adapter |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchStateMirror.kt` | 4 | Mirrors the phone-owned flag; publishes the watch document; toggles `logdate_watch_sync_v2` |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt`, `app/wear/src/main/AndroidManifest.xml` | 4 | Routes and receives `/logdate/state` |
| `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt`, `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` | 4 | Bindings and start |
| `client/feature/core/src/commonMain/composeResources/values/strings_labs.xml` + 4 Labs baselines | 4 | Labs copy that matches the new behavior |
| `client/database/src/commonMain/kotlin/app/logdate/client/database/entities/moments/MarkedMomentEntity.kt`, `dao/moments/MarkedMomentDao.kt` | 5 | `marked_moments` |
| `client/database/src/commonMain/kotlin/app/logdate/client/database/entities/sync/PeerDeliveryEntity.kt`, `dao/sync/PeerDeliveryDao.kt` | 5 | `peer_delivery` |
| `client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_50_51.kt`, `schemas/.../51.json` | 5 | Migration and exported schema |
| `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/SyncMetadataDao.kt` | 5 | `reassignPending`, `reassignCursors` |
| `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyFingerprint.kt` | 6 | The fingerprint, moved out of `SyncPayloadCipher` |
| `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyManager.kt` | 6 | `IdentityMintPolicy`, `installLinkedIdentityKey` |
| `client/device/src/commonMain/kotlin/app/logdate/client/device/identity/CanonicalOwnerProvider.kt` | 6 | `resetForDeviceWipe()` |
| `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/link/LinkHandshake.kt` | 7 | Transcript, keys, interface |
| `client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/link/BouncyCastleLinkHandshake.kt` | 7 | X25519, HKDF-SHA256, AES-256-GCM, HMAC |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/ConditionalCreate.kt` | 8 | `ConditionalCreateResult`, `ContentState`, the descriptor flag name |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudApiClient.kt`, `LogDateCloudApiClient.kt`, `SafeCloudTransport.kt`, `CloudContentDataSource.kt`, `CloudJournalDataSource.kt`, `CloudAssociationDataSource.kt` | 8, 9 | Conditional create, `HEAD`, `refreshSession` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SessionRefresh.kt` | 9 | `RefreshedSession`, `SessionRefreshingAccountRepository`, `DeviceSessionRevocationListener` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncTokenRefresher.kt`, `cloud/account/DefaultCloudAccountRepository.kt` | 9 | Rotated refresh tokens, revocation |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/RoutedSyncMetadataService.kt` | 10 | Upload eligibility filter over the outbox |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/RemoteApplyFilter.kt` | 10 | Which remote-only notes a device stores |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloadEngine.kt`, `SyncDownloader.kt` | 10 | Window filter and media-recovery switch |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncProfile.kt` | 11 | `SyncProfile.Standard`, `SyncProfile.LinkedWatch` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/ConditionalCreateSettler.kt` | 11 | Settles 201/412/410 outcomes |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt`, `SyncStatusPublisher.kt`, `SyncManager.kt`, `SyncUploader.kt`, `AssociationUploader.kt` | 11 | Profile wiring, `NEEDS_DEVICE_LINK` |
| `client/feature/core/.../settings/ui/SyncSettingsScreen.kt`, `.../sync/SyncStatusSheet.kt`, `composeResources/values/strings_sync.xml` | 11 | The new paused reason's line |

## Task Map

| Task | Item | Commit title |
|---|---|---|
| 1 | C1 messages | `chore(sync): define the version 2 watch protocol messages` |
| 2 | C1 state | `chore(sync): add the shared watch and phone state document` |
| 3 | C2 phone | `chore(wear): publish the phone's state for its watches` |
| 4 | C2 watch | `chore(wear): send the Labs watch switch to the watch` |
| 5 | C3 | `chore(database): add tables for marked moments and watch delivery` |
| 6 | C4 identity | `chore(device): prepare identity keys for linked watches` |
| 7 | C4 handshake | `chore(device): add the key exchange for linking a watch` |
| 8 | C5 cloud | `chore(sync): add create-if-absent requests to the cloud client` |
| 9 | C5 session | `chore(sync): keep rotated refresh tokens and report revoked devices` |
| 10 | C5 routing | `chore(sync): filter uploads and remote notes for linked devices` |
| 11 | C5 profile | `chore(sync): add a linked-watch profile to the sync engine` |

Dependencies flow forward: 2 uses 1; 3 and 4 use 2; 10 and 11 use 5 (none at runtime) and 8; 11 uses 6, 9 and 10. Line numbers are as of `17490e104`; for a file an earlier task already edited, find the code by the quoted anchor text.

---
### Task 1: Define the version 2 watch protocol messages (C1)

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocol.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearJson.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/RecordEnvelope.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/Receipts.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LinkMessages.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LiveMessages.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearPathsTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearJsonTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/RecordEnvelopeCodecTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/ReceiptsTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/LinkMessagesTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/LiveMessagesTest.kt`

**Interfaces:**
- Consumes: kotlinx.serialization (`client/sync/build.gradle.kts:9, 64` already applies the plugin and `libs.kotlinx.serialization.json`), `kotlin.uuid.Uuid`, `kotlin.time.Clock`.
- Produces:
  - `object WearProtocol { const val PHONE_CAPABILITY_V2: String; const val WATCH_CAPABILITY_V2: String; const val ENVELOPE_SCHEMA: Int; const val STATE_SCHEMA: Int; const val RECEIPTS_SCHEMA: Int; const val LINK_SCHEMA: Int; const val LIVE_SCHEMA: Int; const val MAX_STATE_BYTES: Int; const val MAX_INLINE_BODY_BYTES: Int; const val LINK_OFFER_TTL_MS: Long; const val LINK_CODE_TTL_MS: Long }`
  - `object WearFeatures { RECEIPTS, WINDOW, MOMENTS, LIVE, REMINDERS, LINK_CLOUD, PHONE_CAMERA, PHONE_PLAYBACK, PHONE_SETTINGS_LINK, PREFS: String; val ALL: Set<String> }`
  - `object WearPaths { STATE, RECEIPTS, RECEIPTS_PUSH, WINDOW_RESYNC, LINK_OFFER, LINK_BUNDLE, LIVE_RECORDING, LIVE_RECORDING_COMMAND, LIVE_PLAYBACK, LIVE_PLAYBACK_COMMAND, REMINDERS_DELIVER, REMINDERS_CLAIM, PRESENCE, DIAG_PING, FAVORITES_SET: String; fun windowItem(kind: RecordKind, id: Uuid): String; fun windowTombstone(kind: RecordKind, id: Uuid): String; fun watchRecord(kind: RecordKind, id: Uuid): String; fun bodyChannel(sha256Hex: String): String; fun parseWindowItem(path: String): RecordPath?; fun parseWindowTombstone(path: String): RecordPath?; fun parseWatchRecord(path: String): RecordPath? }`, `data class RecordPath(val kind: RecordKind, val id: Uuid)`
  - `sealed interface Decoded<out T> { data class Supported<T>(val value: T); data class Unsupported(val schema: Int, val reason: String); data class Malformed(val reason: String) }`
  - `object WearJson { val json: Json; fun <T> encode(serializer: KSerializer<T>, value: T): String; fun <T> encodeToBytes(serializer: KSerializer<T>, value: T): ByteArray; fun <T> decode(text: String, supportedSchema: Int, serializer: KSerializer<T>): Decoded<T>; fun <T> decode(bytes: ByteArray, supportedSchema: Int, serializer: KSerializer<T>): Decoded<T> }`
  - `enum class RecordKind(val pathSegment: String) { NOTE, JOURNAL, ASSOCIATION, MOMENT }`, `enum class RecordOrigin { PHONE, WATCH }`, `enum class WindowReason { RECENT, ON_THIS_DAY, REWIND, STARRED }`, `enum class DeliveryPriority { URGENT, NORMAL, BULK }`, `data class BodyRef(val sha256: String, val sizeBytes: Long)`, `data class MediaRef(val sha256: String, val sizeBytes: Long, val mimeType: String)`, `data class RecordEnvelope(schema, kind, id, contentVersion, syncVersion, origin, reasons, priority, enc, body, bodyRef, media)`, `object RecordEnvelopeCodec { fun encode(envelope: RecordEnvelope): String; fun decode(text: String): Decoded<RecordEnvelope>; fun needsChannel(body: JsonElement): Boolean }`
  - `enum class ReceiptState { ON_PHONE, BACKED_UP, DELETED_ON_PHONE, REJECTED_UNSUPPORTED }`, `enum class CloudPresence { NO_ACCOUNT, SIGNED_IN, UNKNOWN }`, `data class ReceiptQuery`, `data class ReceiptsRequest`, `data class Receipt(…) { fun covers(localContentVersion: Long): Boolean }`, `data class ReceiptsResponse`
  - `enum class LinkMode { CLOUD, PAIR }`, `data class LinkOffer(…) { fun isValidAt(nowMs: Long): Boolean }`, `data class LinkAccept`, `data class SealedLinkBundle`, `data class LinkBundle`, `data class LinkConfirm`, `enum class LinkRefusal`, `data class LinkRefused`, `class LinkNonceLedger(clock: Clock = Clock.System) { suspend fun accept(nonce: String, expiresAt: Long): Boolean }`
  - `enum class RecordingPhase`, `data class LiveRecordingState`, `enum class RecordingCommandType`, `data class LiveRecordingCommand`, `enum class PlaybackPhase`, `data class LivePlaybackState`, `enum class PlaybackCommandType`, `data class LivePlaybackCommand`, `enum class CommandFailure`, `data class LiveCommandResult`, `class LiveSequenceGate { fun admit(sessionId: String, sequence: Long): Boolean; fun end(sessionId: String) }`

- [ ] **Step 1: Write the failing path and codec tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearPathsTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests [WearPaths], the version 2 Data Layer paths both devices build and parse.
 */
class WearPathsTest {
    private val id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")

    @Test
    fun `a window item path names its kind and id`() {
        assertEquals(
            "/logdate/v2/p2w/window/note/550e8400-e29b-41d4-a716-446655440000",
            WearPaths.windowItem(RecordKind.NOTE, id),
        )
    }

    @Test
    fun `record paths read back as the kind and id they were built from`() {
        RecordKind.entries.forEach { kind ->
            assertEquals(RecordPath(kind, id), WearPaths.parseWindowItem(WearPaths.windowItem(kind, id)))
            assertEquals(RecordPath(kind, id), WearPaths.parseWindowTombstone(WearPaths.windowTombstone(kind, id)))
            assertEquals(RecordPath(kind, id), WearPaths.parseWatchRecord(WearPaths.watchRecord(kind, id)))
        }
    }

    @Test
    fun `a path of another family or an unknown kind does not parse`() {
        assertNull(WearPaths.parseWindowItem(WearPaths.windowTombstone(RecordKind.NOTE, id)))
        assertNull(WearPaths.parseWindowItem("/logdate/v2/p2w/window/postcard/$id"))
        assertNull(WearPaths.parseWindowItem("/logdate/v2/p2w/window/note/not-a-uuid"))
        assertNull(WearPaths.parseWindowItem("/logdate/v2/p2w/window/note/$id/extra"))
    }

    @Test
    fun `no version 2 path reaches a version 1 listener`() {
        val v1Prefixes =
            listOf("/logdate/notes", "/logdate/journals", "/logdate/associations", "/logdate/health", "/logdate/camera", "/logdate/sync")
        val v2Paths =
            listOf(
                WearPaths.STATE,
                WearPaths.RECEIPTS,
                WearPaths.RECEIPTS_PUSH,
                WearPaths.WINDOW_RESYNC,
                WearPaths.LINK_OFFER,
                WearPaths.LINK_BUNDLE,
                WearPaths.LIVE_RECORDING,
                WearPaths.LIVE_RECORDING_COMMAND,
                WearPaths.LIVE_PLAYBACK,
                WearPaths.LIVE_PLAYBACK_COMMAND,
                WearPaths.REMINDERS_DELIVER,
                WearPaths.REMINDERS_CLAIM,
                WearPaths.PRESENCE,
                WearPaths.DIAG_PING,
                WearPaths.FAVORITES_SET,
                WearPaths.windowItem(RecordKind.NOTE, id),
                WearPaths.windowTombstone(RecordKind.NOTE, id),
                WearPaths.watchRecord(RecordKind.NOTE, id),
                WearPaths.bodyChannel("ab".repeat(32)),
            )

        v2Paths.forEach { path -> assertTrue(v1Prefixes.none { path.startsWith(it) }, path) }
    }

    @Test
    fun `the request and live paths are the ones the master plan names`() {
        assertEquals("/logdate/state", WearPaths.STATE)
        assertEquals("/logdate/link/offer", WearPaths.LINK_OFFER)
        assertEquals("/logdate/link/bundle", WearPaths.LINK_BUNDLE)
        assertEquals("/logdate/live/recording", WearPaths.LIVE_RECORDING)
        assertEquals("/logdate/live/recording/command", WearPaths.LIVE_RECORDING_COMMAND)
        assertEquals("/logdate/live/playback", WearPaths.LIVE_PLAYBACK)
        assertEquals("/logdate/live/playback/command", WearPaths.LIVE_PLAYBACK_COMMAND)
        assertEquals("/logdate/reminders/deliver", WearPaths.REMINDERS_DELIVER)
        assertEquals("/logdate/reminders/claim", WearPaths.REMINDERS_CLAIM)
        assertEquals("/logdate/presence", WearPaths.PRESENCE)
        assertEquals("/logdate/diag/ping", WearPaths.DIAG_PING)
        assertEquals("/logdate/favorites/set", WearPaths.FAVORITES_SET)
    }

    @Test
    fun `a body channel needs a lowercase sha256`() {
        assertEquals("/logdate/v2/body/${"0f".repeat(32)}", WearPaths.bodyChannel("0f".repeat(32)))
        assertFailsWith<IllegalArgumentException> { WearPaths.bodyChannel("0F".repeat(32)) }
        assertFailsWith<IllegalArgumentException> { WearPaths.bodyChannel("abc") }
    }

    @Test
    fun `the capability and feature names are the published ones`() {
        assertEquals("logdate_phone_sync_v2", WearProtocol.PHONE_CAPABILITY_V2)
        assertEquals("logdate_watch_sync_v2", WearProtocol.WATCH_CAPABILITY_V2)
        assertEquals(
            setOf(
                "receipts.v1",
                "window.v1",
                "moments.v1",
                "live.v1",
                "reminders.v1",
                "link.cloud.v1",
                "phone.camera.v1",
                "phone.playback.v1",
                "phone.settingsLink.v1",
                "prefs.v1",
            ),
            WearFeatures.ALL,
        )
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearJsonTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Tests [WearJson], which every version 2 message goes through: unknown keys are ignored, a newer
 * schema is reported instead of guessed at, and a broken message never echoes its contents.
 */
class WearJsonTest {
    @Serializable
    private data class Probe(
        val schema: Int = 1,
        val name: String,
    )

    @Serializable
    private data class WithOptional(
        val schema: Int = 1,
        val note: String? = null,
        val count: Int = 0,
    )

    @Test
    fun `a supported document decodes`() {
        assertEquals(Decoded.Supported(Probe(name = "a")), WearJson.decode("""{"schema":1,"name":"a"}""", 1, Probe.serializer()))
    }

    @Test
    fun `keys a newer device adds are ignored`() {
        assertEquals(
            Decoded.Supported(Probe(name = "a")),
            WearJson.decode("""{"schema":1,"name":"a","later":{"x":1}}""", 1, Probe.serializer()),
        )
    }

    @Test
    fun `a newer schema is unsupported rather than guessed at`() {
        val decoded = WearJson.decode("""{"schema":2,"renamed":"a"}""", 1, Probe.serializer())

        assertEquals(2, assertIs<Decoded.Unsupported>(decoded).schema)
    }

    @Test
    fun `a document without a schema is read as schema 1`() {
        assertEquals(Decoded.Supported(Probe(name = "a")), WearJson.decode("""{"name":"a"}""", 1, Probe.serializer()))
    }

    @Test
    fun `text that is not a JSON object is malformed`() {
        assertIs<Decoded.Malformed>(WearJson.decode("not json", 1, Probe.serializer()))
        assertIs<Decoded.Malformed>(WearJson.decode("[1]", 1, Probe.serializer()))
    }

    @Test
    fun `a schema that is not a number is malformed`() {
        assertIs<Decoded.Malformed>(WearJson.decode("""{"schema":"two","name":"a"}""", 1, Probe.serializer()))
    }

    @Test
    fun `a malformed reason never repeats the input`() {
        val decoded = WearJson.decode("""{"schema":1,"name":{"secret":"my diary"}}""", 1, Probe.serializer())

        assertFalse(assertIs<Decoded.Malformed>(decoded).reason.contains("my diary"))
    }

    @Test
    fun `encoding writes defaults and leaves out nulls`() {
        assertEquals("""{"schema":1,"count":0}""", WearJson.encode(WithOptional.serializer(), WithOptional()))
    }

    @Test
    fun `bytes decode like text`() {
        val bytes = WearJson.encodeToBytes(Probe.serializer(), Probe(name = "b"))

        assertEquals(Decoded.Supported(Probe(name = "b")), WearJson.decode(bytes, 1, Probe.serializer()))
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/RecordEnvelopeCodecTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests [RecordEnvelopeCodec], the wrapper every record crosses the Data Layer in.
 */
class RecordEnvelopeCodecTest {
    private val envelope =
        RecordEnvelope(
            kind = RecordKind.NOTE,
            id = ID,
            contentVersion = 1_710_000_000_123,
            origin = RecordOrigin.PHONE,
            reasons = setOf(WindowReason.RECENT),
            body = buildJsonObject { put("text", "hi") },
        )

    @Test
    fun `an envelope has a stable wire form`() {
        assertEquals(GOLDEN, RecordEnvelopeCodec.encode(envelope))
    }

    @Test
    fun `an envelope reads back`() {
        assertEquals(Decoded.Supported(envelope), RecordEnvelopeCodec.decode(RecordEnvelopeCodec.encode(envelope)))
    }

    @Test
    fun `keys a newer phone adds are ignored`() {
        val newer = GOLDEN.replace("\"media\":[]", "\"media\":[],\"later\":true")

        assertEquals(Decoded.Supported(envelope), RecordEnvelopeCodec.decode(newer))
    }

    @Test
    fun `a newer envelope schema is unsupported`() {
        val decoded = RecordEnvelopeCodec.decode(GOLDEN.replace("\"schema\":1", "\"schema\":2"))

        assertEquals(2, assertIs<Decoded.Unsupported>(decoded).schema)
    }

    @Test
    fun `an encrypted envelope is unsupported by this version`() {
        val encrypted = GOLDEN.replace("\"media\":[]", "\"media\":[],\"enc\":\"aes-gcm\"")

        assertIs<Decoded.Unsupported>(RecordEnvelopeCodec.decode(encrypted))
    }

    @Test
    fun `an unknown kind in this schema is malformed`() {
        assertIs<Decoded.Malformed>(RecordEnvelopeCodec.decode(GOLDEN.replace("\"NOTE\"", "\"POSTCARD\"")))
    }

    @Test
    fun `an envelope with both an inline body and a reference is refused`() {
        val both = envelope.copy(bodyRef = BodyRef(sha256 = "ab".repeat(32), sizeBytes = 10))
        val wire = GOLDEN.replace("\"media\":[]", "\"bodyRef\":{\"sha256\":\"${"ab".repeat(32)}\",\"sizeBytes\":10},\"media\":[]")

        assertFailsWith<IllegalArgumentException> { RecordEnvelopeCodec.encode(both) }
        assertIs<Decoded.Malformed>(RecordEnvelopeCodec.decode(wire))
    }

    @Test
    fun `a body over 90 KB goes over a channel`() {
        val large = JsonPrimitive("x".repeat(WearProtocol.MAX_INLINE_BODY_BYTES))

        assertTrue(RecordEnvelopeCodec.needsChannel(large))
        assertFalse(RecordEnvelopeCodec.needsChannel(JsonPrimitive("small")))
        assertFailsWith<IllegalArgumentException> { RecordEnvelopeCodec.encode(envelope.copy(body = large)) }
    }

    private companion object {
        const val ID = "550e8400-e29b-41d4-a716-446655440000"
        const val GOLDEN =
            """{"schema":1,"kind":"NOTE","id":"550e8400-e29b-41d4-a716-446655440000","contentVersion":1710000000123,""" +
                """"syncVersion":0,"origin":"PHONE","reasons":["RECENT"],"priority":"NORMAL","body":{"text":"hi"},"media":[]}"""
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/ReceiptsTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests the receipts the phone gives the watch for each record.
 */
class ReceiptsTest {
    private val response =
        ReceiptsResponse(
            cloud = CloudPresence.SIGNED_IN,
            receipts = listOf(Receipt(RecordKind.NOTE, ID, contentVersion = 5, state = ReceiptState.BACKED_UP, serverVersion = 9)),
        )

    @Test
    fun `a receipts response has a stable wire form`() {
        assertEquals(GOLDEN, WearJson.encode(ReceiptsResponse.serializer(), response))
    }

    @Test
    fun `a receipts request reads back`() {
        val request = ReceiptsRequest(items = listOf(ReceiptQuery(RecordKind.MOMENT, ID, contentVersion = 7)))
        val bytes = WearJson.encodeToBytes(ReceiptsRequest.serializer(), request)

        assertEquals(Decoded.Supported(request), WearJson.decode(bytes, WearProtocol.RECEIPTS_SCHEMA, ReceiptsRequest.serializer()))
    }

    @Test
    fun `a receipt for an older version never covers a newer edit`() {
        val receipt = Receipt(RecordKind.NOTE, ID, contentVersion = 5, state = ReceiptState.ON_PHONE)

        assertTrue(receipt.covers(5))
        assertTrue(receipt.covers(4))
        assertFalse(receipt.covers(6))
    }

    @Test
    fun `an unknown receipt state in this schema is malformed`() {
        val unknown = GOLDEN.replace("BACKED_UP", "ARCHIVED")

        assertIs<Decoded.Malformed>(WearJson.decode(unknown, WearProtocol.RECEIPTS_SCHEMA, ReceiptsResponse.serializer()))
    }

    @Test
    fun `a newer receipts schema is unsupported`() {
        val newer = GOLDEN.replace("\"schema\":1", "\"schema\":2")

        assertIs<Decoded.Unsupported>(WearJson.decode(newer, WearProtocol.RECEIPTS_SCHEMA, ReceiptsResponse.serializer()))
    }

    private companion object {
        const val ID = "550e8400-e29b-41d4-a716-446655440000"
        const val GOLDEN =
            """{"schema":1,"cloud":"SIGNED_IN","receipts":[{"kind":"NOTE","id":"550e8400-e29b-41d4-a716-446655440000",""" +
                """"contentVersion":5,"state":"BACKED_UP","serverVersion":9}]}"""
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/LinkMessagesTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests the messages of the link exchange and the ledger that refuses a replayed offer.
 */
class LinkMessagesTest {
    private val offer =
        LinkOffer(
            mode = LinkMode.CLOUD,
            phoneNode = "phone-node",
            phonePublicKey = "cGs=",
            phoneNonce = "bm9uY2U=",
            issuedAt = 1_000,
            expiresAt = 61_000,
        )

    @Test
    fun `an offer reads from its wire form`() {
        val wire =
            """{"schema":1,"mode":"CLOUD","phoneNode":"phone-node","phonePublicKey":"cGs=","phoneNonce":"bm9uY2U=",""" +
                """"issuedAt":1000,"expiresAt":61000,"later":1}"""

        assertEquals(Decoded.Supported(offer), WearJson.decode(wire, WearProtocol.LINK_SCHEMA, LinkOffer.serializer()))
    }

    @Test
    fun `an offer is valid only inside its sixty seconds`() {
        assertFalse(offer.isValidAt(999))
        assertTrue(offer.isValidAt(1_000))
        assertTrue(offer.isValidAt(60_999))
        assertFalse(offer.isValidAt(61_000))
    }

    @Test
    fun `an offer that claims a longer life is never valid`() {
        assertFalse(offer.copy(expiresAt = 61_001).isValidAt(2_000))
    }

    @Test
    fun `the bundle never carries a recovery phrase`() {
        val names = LinkBundle.serializer().descriptor.elementNames.toList()

        assertEquals(
            listOf(
                "mode",
                "accountId",
                "serverOrigin",
                "serverDescriptor",
                "linkCode",
                "linkCodeExpiresAt",
                "identityKey",
                "identityKeyFingerprint",
                "mediaKey",
                "flags",
                "settings",
            ),
            names,
        )
        names.forEach { name ->
            assertFalse(listOf("phrase", "mnemonic", "recovery", "words").any { it in name.lowercase() }, name)
        }
    }

    @Test
    fun `a nonce is accepted once`() =
        runTest {
            val ledger = LinkNonceLedger(FixedClock(1_000))

            assertTrue(ledger.accept("nonce", expiresAt = 61_000))
            assertFalse(ledger.accept("nonce", expiresAt = 61_000))
            assertTrue(ledger.accept("other", expiresAt = 61_000))
        }

    @Test
    fun `the ledger forgets nonces whose offers have expired`() =
        runTest {
            val clock = FixedClock(1_000)
            val ledger = LinkNonceLedger(clock)
            ledger.accept("nonce", expiresAt = 61_000)

            clock.millis = 61_000

            assertTrue(ledger.accept("nonce", expiresAt = 121_000))
        }

    @Test
    fun `a newer link schema is unsupported`() {
        val wire = """{"schema":2,"mode":"CLOUD"}"""

        assertIs<Decoded.Unsupported>(WearJson.decode(wire, WearProtocol.LINK_SCHEMA, LinkOffer.serializer()))
    }

    private class FixedClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/LiveMessagesTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the live recording and playback messages and the gate that drops late ones.
 */
class LiveMessagesTest {
    @Test
    fun `playback state reads back`() {
        val state =
            LivePlaybackState(
                sessionId = "session",
                noteId = "550e8400-e29b-41d4-a716-446655440000",
                sequence = 3,
                phase = PlaybackPhase.PLAYING,
                positionMs = 1_200,
                speed = 1.5f,
                sentAt = 10,
            )
        val bytes = WearJson.encodeToBytes(LivePlaybackState.serializer(), state)

        assertEquals(Decoded.Supported(state), WearJson.decode(bytes, WearProtocol.LIVE_SCHEMA, LivePlaybackState.serializer()))
    }

    @Test
    fun `a recording command reads from its wire form`() {
        val wire = """{"schema":1,"sessionId":"session","command":"STOP"}"""

        assertEquals(
            Decoded.Supported(LiveRecordingCommand(sessionId = "session", command = RecordingCommandType.STOP)),
            WearJson.decode(wire, WearProtocol.LIVE_SCHEMA, LiveRecordingCommand.serializer()),
        )
    }

    @Test
    fun `states arriving out of order are dropped`() {
        val gate = LiveSequenceGate()

        assertTrue(gate.admit("session", 1))
        assertTrue(gate.admit("session", 3))
        assertFalse(gate.admit("session", 2))
        assertFalse(gate.admit("session", 3))
        assertTrue(gate.admit("session", 4))
    }

    @Test
    fun `each session keeps its own order`() {
        val gate = LiveSequenceGate()
        gate.admit("first", 5)

        assertTrue(gate.admit("second", 1))
    }

    @Test
    fun `an ended session starts its order again`() {
        val gate = LiveSequenceGate()
        gate.admit("session", 5)

        gate.end("session")

        assertTrue(gate.admit("session", 1))
    }
}
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create each of the seven production files with the full declarations from Step 3, except: `WearPaths.windowItem`, `windowTombstone`, `watchRecord` and `bodyChannel` return `""`; the three `parse…` functions return `null`; `WearJson.decode(text, …)` returns `Decoded.Malformed("")`; `RecordEnvelopeCodec.encode` returns `""`, `decode` returns `Decoded.Malformed("")` and `needsChannel` returns `false`; `Receipt.covers` and `LinkOffer.isValidAt` return `false`; `LinkNonceLedger.accept` and `LiveSequenceGate.admit` return `false`. Constants and serializable classes are written in full.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.datalayer.v2.*' --console=plain
```
Expected: FAIL. Among others, `WearPathsTest > a window item path names its kind and id` (`expected:<…/note/550e…> but was:<>`), `WearJsonTest > a supported document decodes`, `RecordEnvelopeCodecTest > an envelope has a stable wire form`, `LinkMessagesTest > a nonce is accepted once`. `the capability and feature names are the published ones`, `encoding writes defaults and leaves out nulls`, `the bundle never carries a recovery phrase`, `a receipts response has a stable wire form` and `a recording command reads from its wire form` may already pass or fail on decoding.

- [ ] **Step 4: Implement the protocol**

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocol.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

/**
 * Capability names, schema numbers and limits of the second watch and phone protocol.
 *
 * Version 2 runs beside today's protocol. A device uses it only when its Labs switch is on, the other
 * device advertises the matching capability, and that device lists the feature in its state document
 * ([WearProtocolGate]). Adding a value to any enum in this package needs a schema bump: a device that
 * meets a value it does not know reports the message as malformed.
 */
object WearProtocol {
    /** Added by the phone with `CapabilityClient.addLocalCapability` while its Labs switch is on. */
    const val PHONE_CAPABILITY_V2 = "logdate_phone_sync_v2"

    /** Added by the watch while the switch the phone sent it is on. */
    const val WATCH_CAPABILITY_V2 = "logdate_watch_sync_v2"

    const val ENVELOPE_SCHEMA = 1
    const val STATE_SCHEMA = 1
    const val RECEIPTS_SCHEMA = 1
    const val LINK_SCHEMA = 1
    const val LIVE_SCHEMA = 1

    /** A state document is one small data item; anything larger belongs in its own path. */
    const val MAX_STATE_BYTES = 8 * 1024

    /** A record body larger than this goes over a channel and is referenced by its SHA-256. */
    const val MAX_INLINE_BODY_BYTES = 90 * 1024

    const val LINK_OFFER_TTL_MS = 60_000L

    /** How long the single-use code in a link bundle can be redeemed. */
    const val LINK_CODE_TTL_MS = 120_000L
}

/** The names a device lists in its state document for each version 2 feature it supports. */
object WearFeatures {
    const val RECEIPTS = "receipts.v1"
    const val WINDOW = "window.v1"
    const val MOMENTS = "moments.v1"
    const val LIVE = "live.v1"
    const val REMINDERS = "reminders.v1"
    const val LINK_CLOUD = "link.cloud.v1"
    const val PHONE_CAMERA = "phone.camera.v1"
    const val PHONE_PLAYBACK = "phone.playback.v1"
    const val PHONE_SETTINGS_LINK = "phone.settingsLink.v1"
    const val PREFS = "prefs.v1"

    val ALL: Set<String> =
        setOf(
            RECEIPTS,
            WINDOW,
            MOMENTS,
            LIVE,
            REMINDERS,
            LINK_CLOUD,
            PHONE_CAMERA,
            PHONE_PLAYBACK,
            PHONE_SETTINGS_LINK,
            PREFS,
        )
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlin.uuid.Uuid

/**
 * The Data Layer paths of version 2.
 *
 * None starts with a prefix a version 1 listener filters on (`/logdate/notes`, `/logdate/journals`,
 * `/logdate/associations`, `/logdate/health`, `/logdate/camera`, `/logdate/sync`), so a build without
 * version 2 never wakes for one.
 */
object WearPaths {
    /** Each device's state document, one data item per node. */
    const val STATE = "/logdate/state"

    const val RECEIPTS = "/logdate/v2/receipts"
    const val RECEIPTS_PUSH = "/logdate/v2/receipts/push"
    const val WINDOW_RESYNC = "/logdate/v2/window/resync"

    const val LINK_OFFER = "/logdate/link/offer"
    const val LINK_BUNDLE = "/logdate/link/bundle"

    const val LIVE_RECORDING = "/logdate/live/recording"
    const val LIVE_RECORDING_COMMAND = "/logdate/live/recording/command"
    const val LIVE_PLAYBACK = "/logdate/live/playback"
    const val LIVE_PLAYBACK_COMMAND = "/logdate/live/playback/command"

    const val REMINDERS_DELIVER = "/logdate/reminders/deliver"
    const val REMINDERS_CLAIM = "/logdate/reminders/claim"
    const val PRESENCE = "/logdate/presence"
    const val DIAG_PING = "/logdate/diag/ping"
    const val FAVORITES_SET = "/logdate/favorites/set"

    private const val WINDOW_PREFIX = "/logdate/v2/p2w/window/"
    private const val TOMBSTONE_PREFIX = "/logdate/v2/p2w/tombstone/"
    private const val RECORD_PREFIX = "/logdate/v2/w2p/record/"
    private const val BODY_PREFIX = "/logdate/v2/body/"
    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    /** A record the phone keeps in the watch's library window. */
    fun windowItem(
        kind: RecordKind,
        id: Uuid,
    ): String = "$WINDOW_PREFIX${kind.pathSegment}/$id"

    /** A record that left the window or was deleted on the phone. */
    fun windowTombstone(
        kind: RecordKind,
        id: Uuid,
    ): String = "$TOMBSTONE_PREFIX${kind.pathSegment}/$id"

    /** A record made on the watch, on its way to the phone. */
    fun watchRecord(
        kind: RecordKind,
        id: Uuid,
    ): String = "$RECORD_PREFIX${kind.pathSegment}/$id"

    /** The channel a body too large for a data item travels on. */
    fun bodyChannel(sha256Hex: String): String {
        require(SHA256_HEX.matches(sha256Hex)) { "A body channel is named by a lowercase hex SHA-256" }
        return "$BODY_PREFIX$sha256Hex"
    }

    fun parseWindowItem(path: String): RecordPath? = parse(path, WINDOW_PREFIX)

    fun parseWindowTombstone(path: String): RecordPath? = parse(path, TOMBSTONE_PREFIX)

    fun parseWatchRecord(path: String): RecordPath? = parse(path, RECORD_PREFIX)

    private fun parse(
        path: String,
        prefix: String,
    ): RecordPath? {
        if (!path.startsWith(prefix)) return null
        val parts = path.removePrefix(prefix).split('/')
        if (parts.size != 2) return null
        val kind = RecordKind.fromPathSegment(parts[0]) ?: return null
        val id = runCatching { Uuid.parse(parts[1]) }.getOrNull() ?: return null
        return RecordPath(kind, id)
    }
}

/** The kind and id a record path names. */
data class RecordPath(
    val kind: RecordKind,
    val id: Uuid,
)
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearJson.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** The outcome of reading a version 2 message. */
sealed interface Decoded<out T> {
    data class Supported<T>(
        val value: T,
    ) : Decoded<T>

    /**
     * Written by a newer version this one cannot read. The reader keeps what it has, says so, and
     * answers with `REJECTED_UNSUPPORTED` where the message expects an answer.
     */
    data class Unsupported(
        val schema: Int,
        val reason: String,
    ) : Decoded<Nothing>

    /** Not a message of a schema this version knows. [reason] never repeats the message's contents. */
    data class Malformed(
        val reason: String,
    ) : Decoded<Nothing>
}

/**
 * The JSON every version 2 message uses.
 *
 * Unknown keys are ignored so a newer device can add fields within a schema; a newer schema is
 * reported as [Decoded.Unsupported] before anything else is read.
 */
object WearJson {
    private const val SCHEMA_KEY = "schema"
    private const val DEFAULT_SCHEMA = 1

    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
    ): String = json.encodeToString(serializer, value)

    fun <T> encodeToBytes(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray = encode(serializer, value).encodeToByteArray()

    fun <T> decode(
        bytes: ByteArray,
        supportedSchema: Int,
        serializer: KSerializer<T>,
    ): Decoded<T> = decode(bytes.decodeToString(), supportedSchema, serializer)

    fun <T> decode(
        text: String,
        supportedSchema: Int,
        serializer: KSerializer<T>,
    ): Decoded<T> {
        val name = serializer.descriptor.serialName
        val element =
            try {
                json.parseToJsonElement(text)
            } catch (_: IllegalArgumentException) {
                return Decoded.Malformed("$name is not JSON")
            }
        val document = element as? JsonObject ?: return Decoded.Malformed("$name is not a JSON object")
        val schemaElement = document[SCHEMA_KEY]
        val schema =
            if (schemaElement == null) {
                DEFAULT_SCHEMA
            } else {
                (schemaElement as? JsonPrimitive)?.intOrNull ?: return Decoded.Malformed("$name has a schema that is not a number")
            }
        if (schema > supportedSchema) {
            return Decoded.Unsupported(schema, "$name schema $schema is newer than $supportedSchema")
        }
        return try {
            Decoded.Supported(json.decodeFromJsonElement(serializer, document))
        } catch (_: IllegalArgumentException) {
            Decoded.Malformed("$name does not match schema $schema")
        }
    }
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/RecordEnvelope.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The kinds of record version 2 moves between devices. */
@Serializable
enum class RecordKind(
    val pathSegment: String,
) {
    NOTE("note"),
    JOURNAL("journal"),
    ASSOCIATION("association"),
    MOMENT("moment"),
    ;

    companion object {
        fun fromPathSegment(segment: String): RecordKind? = entries.firstOrNull { it.pathSegment == segment }
    }
}

/** The device a record was made on. */
@Serializable
enum class RecordOrigin {
    PHONE,
    WATCH,
}

/** Why a record is in the watch's library window. */
@Serializable
enum class WindowReason {
    RECENT,
    ON_THIS_DAY,
    REWIND,
    STARRED,
}

/** How soon a record should cross: URGENT for what someone is waiting on, BULK for backfill. */
@Serializable
enum class DeliveryPriority {
    URGENT,
    NORMAL,
    BULK,
}

/** A body too large for a data item, sent over [WearPaths.bodyChannel]. */
@Serializable
data class BodyRef(
    val sha256: String,
    val sizeBytes: Long,
)

/** A media file that goes with a record and is fetched on its own. */
@Serializable
data class MediaRef(
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
)

/**
 * One record on the Data Layer.
 *
 * @property contentVersion The record's `lastUpdated` in epoch milliseconds; receipts name it, so a
 *   receipt for an older version never clears a newer edit.
 * @property syncVersion The cloud server's version of the record, or 0 when it has none yet.
 * @property enc Reserved for end-to-end encrypted envelopes. Schema 1 never sets it, and a schema 1
 *   reader refuses an envelope that does.
 */
@Serializable
data class RecordEnvelope(
    val schema: Int = WearProtocol.ENVELOPE_SCHEMA,
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
    val syncVersion: Long = 0,
    val origin: RecordOrigin,
    val reasons: Set<WindowReason> = emptySet(),
    val priority: DeliveryPriority = DeliveryPriority.NORMAL,
    val enc: String? = null,
    val body: JsonElement? = null,
    val bodyRef: BodyRef? = null,
    val media: List<MediaRef> = emptyList(),
)

/** Reads and writes [RecordEnvelope]s, enforcing the inline body limit. */
object RecordEnvelopeCodec {
    /** Throws [IllegalArgumentException] for an envelope with two bodies or an inline body over the limit. */
    fun encode(envelope: RecordEnvelope): String {
        require(envelope.body == null || envelope.bodyRef == null) { "An envelope carries its body inline or by reference, not both" }
        val body = envelope.body
        require(body == null || !needsChannel(body)) { "A body over ${WearProtocol.MAX_INLINE_BODY_BYTES} bytes goes over a channel" }
        return WearJson.encode(RecordEnvelope.serializer(), envelope)
    }

    fun decode(text: String): Decoded<RecordEnvelope> {
        val decoded = WearJson.decode(text, WearProtocol.ENVELOPE_SCHEMA, RecordEnvelope.serializer())
        if (decoded !is Decoded.Supported) return decoded
        val envelope = decoded.value
        if (envelope.enc != null) {
            return Decoded.Unsupported(envelope.schema, "Encrypted envelopes need a newer version")
        }
        if (envelope.body != null && envelope.bodyRef != null) {
            return Decoded.Malformed("RecordEnvelope has both an inline body and a body reference")
        }
        return decoded
    }

    fun needsChannel(body: JsonElement): Boolean =
        WearJson.encode(JsonElement.serializer(), body).encodeToByteArray().size > WearProtocol.MAX_INLINE_BODY_BYTES
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/Receipts.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

/** What the phone has done with a record the watch sent. */
@Serializable
enum class ReceiptState {
    ON_PHONE,
    BACKED_UP,
    DELETED_ON_PHONE,

    /** The phone cannot read this kind or schema; the watch keeps the record and says why. */
    REJECTED_UNSUPPORTED,
}

/** Whether the phone backs up to the cloud, so the watch knows whether ON_PHONE is final. */
@Serializable
enum class CloudPresence {
    NO_ACCOUNT,
    SIGNED_IN,
    UNKNOWN,
}

@Serializable
data class ReceiptQuery(
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
)

/** Sent with `MessageClient.sendRequest` to [WearPaths.RECEIPTS]. */
@Serializable
data class ReceiptsRequest(
    val schema: Int = WearProtocol.RECEIPTS_SCHEMA,
    val items: List<ReceiptQuery>,
)

@Serializable
data class Receipt(
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
    val state: ReceiptState,
    val serverVersion: Long? = null,
) {
    /** Whether this receipt is about [localContentVersion] or a later version of it. */
    fun covers(localContentVersion: Long): Boolean = contentVersion >= localContentVersion
}

@Serializable
data class ReceiptsResponse(
    val schema: Int = WearProtocol.RECEIPTS_SCHEMA,
    val cloud: CloudPresence,
    val receipts: List<Receipt>,
)
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LinkMessages.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/** CLOUD links the watch to the phone's account; PAIR shares state with no account. */
@Serializable
enum class LinkMode {
    CLOUD,
    PAIR,
}

/**
 * The phone's opening message, sent with `MessageClient.sendRequest` to [WearPaths.LINK_OFFER]. Keys
 * and nonces are standard Base64. Nothing in the link exchange is ever written to a data item.
 */
@Serializable
data class LinkOffer(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val mode: LinkMode,
    val phoneNode: String,
    val phonePublicKey: String,
    val phoneNonce: String,
    val issuedAt: Long,
    val expiresAt: Long,
) {
    /** False outside the offer's own window, and always false for an offer claiming more than a minute. */
    fun isValidAt(nowMs: Long): Boolean =
        expiresAt - issuedAt <= WearProtocol.LINK_OFFER_TTL_MS && nowMs >= issuedAt && nowMs < expiresAt
}

/** The watch's answer to a [LinkOffer]. */
@Serializable
data class LinkAccept(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val watchNode: String,
    val watchPublicKey: String,
    val watchNonce: String,
)

/** A [LinkBundle] sealed with AES-256-GCM under the bundle key, with the transcript hash as associated data. */
@Serializable
data class SealedLinkBundle(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val iv: String,
    val ciphertext: String,
)

/**
 * What the phone gives the watch. It carries the derived identity and media keys, never the recovery
 * phrase: the phrase can also recover the account itself, and a watch must not hold that power.
 */
@Serializable
data class LinkBundle(
    val mode: LinkMode,
    val accountId: String? = null,
    val serverOrigin: String? = null,
    val serverDescriptor: JsonElement? = null,
    val linkCode: String? = null,
    val linkCodeExpiresAt: Long? = null,
    val identityKey: String? = null,
    val identityKeyFingerprint: String? = null,
    val mediaKey: String? = null,
    val flags: Map<String, Boolean> = emptyMap(),
    val settings: Map<String, JsonElement> = emptyMap(),
)

/** The watch's HMAC over the transcript under the confirmation key. */
@Serializable
data class LinkConfirm(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val mac: String,
)

@Serializable
enum class LinkRefusal {
    EXPIRED_OFFER,
    REPLAYED_NONCE,
    BOUND_TO_ANOTHER_ACCOUNT,
    UNSUPPORTED,
}

@Serializable
data class LinkRefused(
    val schema: Int = WearProtocol.LINK_SCHEMA,
    val reason: LinkRefusal,
)

/**
 * Remembers the nonces of offers still valid, so the same offer cannot be accepted twice. Entries go
 * once their offer has expired, and an expired offer fails [LinkOffer.isValidAt] anyway.
 */
class LinkNonceLedger(
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val expiries = mutableMapOf<String, Long>()

    suspend fun accept(
        nonce: String,
        expiresAt: Long,
    ): Boolean =
        mutex.withLock {
            val now = clock.now().toEpochMilliseconds()
            expiries.entries.removeAll { it.value <= now }
            if (nonce in expiries) return@withLock false
            expiries[nonce] = expiresAt
            true
        }
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LiveMessages.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

@Serializable
enum class RecordingPhase {
    RECORDING,
    PAUSED,
    SAVED,
    DISCARDED,
}

/** Sent as a message on [WearPaths.LIVE_RECORDING] while the watch records. */
@Serializable
data class LiveRecordingState(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val sessionId: String,
    val sequence: Long,
    val phase: RecordingPhase,
    val elapsedMs: Long,
    val sentAt: Long,
)

@Serializable
enum class RecordingCommandType {
    STOP,
    PAUSE,
    RESUME,
}

/** Sent with `MessageClient.sendRequest` to [WearPaths.LIVE_RECORDING_COMMAND]. */
@Serializable
data class LiveRecordingCommand(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val sessionId: String,
    val command: RecordingCommandType,
)

@Serializable
enum class PlaybackPhase {
    PLAYING,
    PAUSED,
    ENDED,
}

/** Sent as a message on [WearPaths.LIVE_PLAYBACK]. */
@Serializable
data class LivePlaybackState(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val sessionId: String,
    val noteId: String,
    val sequence: Long,
    val phase: PlaybackPhase,
    val positionMs: Long,
    val speed: Float = 1f,
    val sentAt: Long,
)

@Serializable
enum class PlaybackCommandType {
    PLAY,
    PAUSE,
    SEEK,
    SET_SPEED,
    CONTINUE_ON_PHONE,
    CONTINUE_ON_WATCH,
}

/** Sent with `MessageClient.sendRequest` to [WearPaths.LIVE_PLAYBACK_COMMAND]. */
@Serializable
data class LivePlaybackCommand(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val sessionId: String,
    val noteId: String,
    val command: PlaybackCommandType,
    val positionMs: Long? = null,
    val speed: Float? = null,
)

@Serializable
enum class CommandFailure {
    NO_SUCH_SESSION,
    NOT_ALLOWED,
    FAILED,
}

/** The answer to a live command request. */
@Serializable
data class LiveCommandResult(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val accepted: Boolean,
    val failure: CommandFailure? = null,
)

/**
 * Drops live state that arrives late. Messages are not ordered, so each session admits only a
 * sequence higher than the last it admitted. One gate belongs to one receiving coroutine.
 */
class LiveSequenceGate {
    private val latest = mutableMapOf<String, Long>()

    fun admit(
        sessionId: String,
        sequence: Long,
    ): Boolean {
        val previous = latest[sessionId]
        if (previous != null && sequence <= previous) return false
        latest[sessionId] = sequence
        return true
    }

    fun end(sessionId: String) {
        latest.remove(sessionId)
    }
}
```

- [ ] **Step 5: Run the tests again**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.datalayer.v2.*' --console=plain
```
Expected: PASS, 41 tests in 6 classes.

- [ ] **Step 6: Compile for iOS and run the module's checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:desktopTest :client:sync:testAndroidHostTest :client:sync:ktlintCheck :client:sync:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task01-v2-protocol.txt <<'EOF'
chore(sync): define the version 2 watch protocol messages

The redesigned watch needs a protocol that can grow without breaking
older phones and watches. This adds its messages as shared code that
both apps and iOS compile: the capability and feature names, every
path, the record envelope with its 90 KB inline body limit, receipts
that name the version they cover, the link exchange messages and the
live recording and playback messages.

Every reader ignores keys a newer device adds and reports a newer
schema as unsupported instead of guessing at it. A malformed message
is reported without repeating its contents. The link bundle has no
field for the recovery phrase, and a test pins its fields. Nothing
sends these messages yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocol.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearJson.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/RecordEnvelope.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/Receipts.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LinkMessages.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LiveMessages.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearPathsTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearJsonTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/RecordEnvelopeCodecTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/ReceiptsTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/LinkMessagesTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/LiveMessagesTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task01-v2-protocol.txt
```

---

### Task 2: Add the shared watch and phone state document (C1)

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateMerger.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerStateStore.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerLink.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocolGate.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateDocumentCodecTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateMergerTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/PeerStateStoreTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocolGateTest.kt`

**Interfaces:**
- Consumes (Task 1): `WearJson`, `Decoded`, `WearProtocol.STATE_SCHEMA`, `WearProtocol.MAX_STATE_BYTES`, `CloudPresence`.
- Produces:
  - `enum class PeerRole { PHONE, WATCH }`; `data class Stamped(val value: JsonElement, val at: Long)`; `data class CampfireSnapshot(val phase: String, val runDays: Int, val litToday: Boolean, val computedAt: Long)`; `data class PhoneState(val flags: Map<String, Boolean> = emptyMap(), val cloud: CloudPresence = CloudPresence.UNKNOWN, val campfire: CampfireSnapshot? = null, val dayStartHour: Int = 0, val zone: String? = null, val openMarks: Int = 0, val features: Set<String> = emptySet())`; `enum class PeerLinkState { UNLINKED, PAIRED, LINKED, NEEDS_RELINK }`; `enum class PresenceState { ON_WRIST, OFF_WRIST, UNKNOWN }`; `data class Presence(val state: PresenceState = PresenceState.UNKNOWN, val observedAt: Long = 0)`; `data class WatchState(val outboxCount: Int = 0, val deadLetterCount: Int = 0, val freeBytes: Long? = null, val link: PeerLinkState = PeerLinkState.UNLINKED, val presence: Presence = Presence(), val features: Set<String> = emptySet())`; `data class StateDocument(val schema: Int = WearProtocol.STATE_SCHEMA, val role: PeerRole, val writtenAt: Long, val phone: PhoneState? = null, val watch: WatchState? = null, val shared: Map<String, Stamped> = emptyMap(), val lastReminderAt: Long? = null)`
  - `object StateDocumentCodec { const val KEY = "doc"; fun encode(document: StateDocument): String; fun decode(text: String): Decoded<StateDocument>; fun toDataMap(document: StateDocument): Map<String, String>; fun fromDataMap(data: Map<String, String>): Decoded<StateDocument> }`
  - `data class MergedState(val phone: PhoneState?, val watch: WatchState?, val shared: Map<String, JsonElement>, val lastReminderAt: Long?)`; `class StateMerger(clock: Clock = Clock.System) { fun merge(phone: StateDocument?, watch: StateDocument?): MergedState; companion object { const val PRESENCE_STALE_AFTER_MS: Long } }`
  - `class PeerStateStore { fun accept(nodeId: String, decoded: Decoded<StateDocument>, expectedRole: PeerRole): StateDocument?; fun latest(role: PeerRole): Flow<StateDocument?>; fun features(role: PeerRole): Flow<Set<String>> }`
  - `data class PeerDocument(val nodeId: String, val decoded: Decoded<StateDocument>)`; `interface StateDocumentChannel { suspend fun publish(document: StateDocument): Boolean; suspend fun peerDocuments(): List<PeerDocument> }`; `interface LocalCapabilities { suspend fun add(capability: String): Boolean; suspend fun remove(capability: String): Boolean }`; `fun interface PeerCapabilityPresence { fun observe(capability: String): Flow<Boolean> }`
  - `class WearProtocolGate(flagEnabled: Flow<Boolean>, peerHasV2Capability: Flow<Boolean>, peerFeatures: Flow<Set<String>>) { fun isV2Active(): Flow<Boolean>; fun isFeatureActive(feature: String): Flow<Boolean> }`

- [ ] **Step 1: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateDocumentCodecTest.kt`:

```kotlin
package app.logdate.client.sync.peer.state

import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Tests [StateDocumentCodec], the per-device document at `/logdate/state`.
 */
class StateDocumentCodecTest {
    private val phoneDocument =
        StateDocument(
            role = PeerRole.PHONE,
            writtenAt = 1_000,
            phone =
                PhoneState(
                    flags = mapOf("watch_experience_v2_enabled" to true),
                    cloud = CloudPresence.SIGNED_IN,
                    zone = "Europe/Paris",
                    features = setOf("receipts.v1"),
                ),
        )

    private val watchDocument =
        StateDocument(
            role = PeerRole.WATCH,
            writtenAt = 1_000,
            watch =
                WatchState(
                    outboxCount = 3,
                    deadLetterCount = 1,
                    freeBytes = 1_024,
                    link = PeerLinkState.LINKED,
                    presence = Presence(PresenceState.ON_WRIST, observedAt = 900),
                    features = setOf("receipts.v1"),
                ),
            shared = mapOf("reminders.enabled" to Stamped(JsonPrimitive(true), at = 950)),
            lastReminderAt = 800,
        )

    @Test
    fun `a phone document has a stable wire form`() {
        assertEquals(PHONE_GOLDEN, StateDocumentCodec.encode(phoneDocument))
    }

    @Test
    fun `a watch document reads back`() {
        assertEquals(Decoded.Supported(watchDocument), StateDocumentCodec.decode(StateDocumentCodec.encode(watchDocument)))
    }

    @Test
    fun `keys a newer device adds are ignored at every level`() {
        val newer =
            PHONE_GOLDEN
                .replace("\"schema\":1,", "\"schema\":1,\"later\":{\"a\":1},")
                .replace("\"openMarks\":0,", "\"openMarks\":0,\"laterPhoneField\":true,")

        assertEquals(Decoded.Supported(phoneDocument), StateDocumentCodec.decode(newer))
    }

    @Test
    fun `a newer schema is unsupported`() {
        val decoded = StateDocumentCodec.decode(PHONE_GOLDEN.replace("\"schema\":1", "\"schema\":2"))

        assertEquals(2, assertIs<Decoded.Unsupported>(decoded).schema)
    }

    @Test
    fun `an unknown presence state in this schema is malformed`() {
        val wire = StateDocumentCodec.encode(watchDocument).replace("ON_WRIST", "ASLEEP")

        assertIs<Decoded.Malformed>(StateDocumentCodec.decode(wire))
    }

    @Test
    fun `a document over 8 KB is refused`() {
        val oversized = phoneDocument.copy(phone = PhoneState(flags = (1..1_000).associate { "flag_$it" to true }))

        assertFailsWith<IllegalArgumentException> { StateDocumentCodec.encode(oversized) }
    }

    @Test
    fun `the document travels under one data item key`() {
        val data = StateDocumentCodec.toDataMap(phoneDocument)

        assertEquals(setOf("doc"), data.keys)
        assertEquals(Decoded.Supported(phoneDocument), StateDocumentCodec.fromDataMap(data))
    }

    @Test
    fun `a data item without a document is malformed`() {
        assertIs<Decoded.Malformed>(StateDocumentCodec.fromDataMap(emptyMap()))
    }

    private companion object {
        const val PHONE_GOLDEN =
            """{"schema":1,"role":"PHONE","writtenAt":1000,"phone":{"flags":{"watch_experience_v2_enabled":true},""" +
                """"cloud":"SIGNED_IN","dayStartHour":0,"zone":"Europe/Paris","openMarks":0,"features":["receipts.v1"]},"shared":{}}"""
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateMergerTest.kt`:

```kotlin
package app.logdate.client.sync.peer.state

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [StateMerger]: every field has one owner, shared entries go to the later write with ties to
 * the phone, and a watch's presence goes stale after half an hour.
 */
class StateMergerTest {
    private val merger = StateMerger(FixedClock(NOW))

    @Test
    fun `phone owned fields come only from the phone document`() {
        val phone = phoneDocument(PhoneState(flags = mapOf("flag" to true)))
        val watch = watchDocument().copy(phone = PhoneState(flags = mapOf("flag" to false)))

        assertEquals(PhoneState(flags = mapOf("flag" to true)), merger.merge(phone, watch).phone)
    }

    @Test
    fun `watch owned fields come only from the watch document`() {
        val phone = phoneDocument(PhoneState()).copy(watch = WatchState(outboxCount = 99))

        assertNull(merger.merge(phone, watch = null).watch)
        assertEquals(4, merger.merge(phone, watchDocument(WatchState(outboxCount = 4))).watch?.outboxCount)
    }

    @Test
    fun `a document passed as the wrong role contributes nothing`() {
        val merged = merger.merge(phone = watchDocument(shared = mapOf("pref" to stamp("a", NOW))), watch = phoneDocument(PhoneState()))

        assertNull(merged.phone)
        assertNull(merged.watch)
        assertEquals(emptyMap(), merged.shared)
    }

    @Test
    fun `the later shared write wins`() {
        val merged =
            merger.merge(
                phoneDocument(PhoneState(), shared = mapOf("pref" to stamp("phone", NOW - 10))),
                watchDocument(shared = mapOf("pref" to stamp("watch", NOW - 5))),
            )

        assertEquals(JsonPrimitive("watch"), merged.shared["pref"])
    }

    @Test
    fun `a shared tie goes to the phone`() {
        val merged =
            merger.merge(
                phoneDocument(PhoneState(), shared = mapOf("pref" to stamp("phone", NOW - 5))),
                watchDocument(shared = mapOf("pref" to stamp("watch", NOW - 5))),
            )

        assertEquals(JsonPrimitive("phone"), merged.shared["pref"])
    }

    @Test
    fun `an entry only one device wrote is used`() {
        val merged = merger.merge(phoneDocument(PhoneState()), watchDocument(shared = mapOf("pref" to stamp("watch", NOW))))

        assertEquals(JsonPrimitive("watch"), merged.shared["pref"])
    }

    @Test
    fun `a clock running ahead cannot outlast a later write`() {
        val merged =
            merger.merge(
                phoneDocument(PhoneState(), shared = mapOf("pref" to stamp("phone", NOW))),
                watchDocument(shared = mapOf("pref" to stamp("watch", NOW + 3_600_000))),
            )

        assertEquals(JsonPrimitive("phone"), merged.shared["pref"])
    }

    @Test
    fun `a clock running ahead still beats an older write`() {
        val merged =
            merger.merge(
                phoneDocument(PhoneState(), shared = mapOf("pref" to stamp("phone", NOW - 60_000))),
                watchDocument(shared = mapOf("pref" to stamp("watch", NOW + 3_600_000))),
            )

        assertEquals(JsonPrimitive("watch"), merged.shared["pref"])
    }

    @Test
    fun `the last reminder is the latest either device recorded`() {
        assertEquals(
            900L,
            merger.merge(phoneDocument(PhoneState()).copy(lastReminderAt = 900), watchDocument().copy(lastReminderAt = 800)).lastReminderAt,
        )
        assertEquals(800L, merger.merge(phoneDocument(PhoneState()), watchDocument().copy(lastReminderAt = 800)).lastReminderAt)
        assertNull(merger.merge(phoneDocument(PhoneState()), watchDocument()).lastReminderAt)
    }

    @Test
    fun `presence older than 30 minutes is unknown`() {
        val fresh = watchDocument(WatchState(presence = Presence(PresenceState.ON_WRIST, observedAt = NOW - THIRTY_MINUTES)))
        val stale = watchDocument(WatchState(presence = Presence(PresenceState.ON_WRIST, observedAt = NOW - THIRTY_MINUTES - 1)))

        assertEquals(PresenceState.ON_WRIST, merger.merge(phone = null, watch = fresh).watch?.presence?.state)
        assertEquals(PresenceState.UNKNOWN, merger.merge(phone = null, watch = stale).watch?.presence?.state)
    }

    @Test
    fun `presence stamped in the future counts as fresh`() {
        val ahead = watchDocument(WatchState(presence = Presence(PresenceState.OFF_WRIST, observedAt = NOW + 3_600_000)))

        assertEquals(PresenceState.OFF_WRIST, merger.merge(phone = null, watch = ahead).watch?.presence?.state)
    }

    private fun phoneDocument(
        phone: PhoneState,
        shared: Map<String, Stamped> = emptyMap(),
    ) = StateDocument(role = PeerRole.PHONE, writtenAt = NOW, phone = phone, shared = shared)

    private fun watchDocument(
        watch: WatchState = WatchState(),
        shared: Map<String, Stamped> = emptyMap(),
    ) = StateDocument(role = PeerRole.WATCH, writtenAt = NOW, watch = watch, shared = shared)

    private fun stamp(
        value: String,
        at: Long,
    ) = Stamped(JsonPrimitive(value), at)

    private class FixedClock(
        private val millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val NOW = 1_710_000_000_000
        const val THIRTY_MINUTES = 30 * 60 * 1_000L
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/PeerStateStoreTest.kt`:

```kotlin
package app.logdate.client.sync.peer.state

import app.logdate.client.sync.datalayer.v2.Decoded
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [PeerStateStore], which keeps the latest readable document each peer wrote.
 */
class PeerStateStoreTest {
    private val store = PeerStateStore()

    @Test
    fun `a readable document from the expected role is stored`() =
        runTest {
            val watch = StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = WatchState(features = setOf("receipts.v1")))

            assertEquals(watch, store.accept("watch-node", Decoded.Supported(watch), PeerRole.WATCH))
            assertEquals(setOf("receipts.v1"), store.features(PeerRole.WATCH).first())
        }

    @Test
    fun `a document from the wrong role is ignored`() =
        runTest {
            val phone = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState())

            assertNull(store.accept("node", Decoded.Supported(phone), PeerRole.WATCH))
            assertNull(store.latest(PeerRole.PHONE).first())
        }

    @Test
    fun `an unsupported or malformed document is ignored`() =
        runTest {
            assertNull(store.accept("node", Decoded.Unsupported(2, "newer"), PeerRole.WATCH))
            assertNull(store.accept("node", Decoded.Malformed("broken"), PeerRole.WATCH))
            assertEquals(emptySet(), store.features(PeerRole.WATCH).first())
        }

    @Test
    fun `the newest document of a role wins`() =
        runTest {
            val older = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState(features = setOf("a")))
            val newer = StateDocument(role = PeerRole.PHONE, writtenAt = 2, phone = PhoneState(features = setOf("b")))
            store.accept("first", Decoded.Supported(newer), PeerRole.PHONE)
            store.accept("second", Decoded.Supported(older), PeerRole.PHONE)

            assertEquals(setOf("b"), store.features(PeerRole.PHONE).first())
        }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocolGateTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [WearProtocolGate]: version 2 needs the switch, the other device's capability, and for each
 * surface the feature it uses.
 */
class WearProtocolGateTest {
    private val flag = MutableStateFlow(true)
    private val capability = MutableStateFlow(true)
    private val features = MutableStateFlow(setOf(WearFeatures.RECEIPTS))
    private val gate = WearProtocolGate(flag, capability, features)

    @Test
    fun `version 2 is off while the switch is off`() =
        runTest {
            flag.value = false

            assertFalse(gate.isV2Active().first())
        }

    @Test
    fun `version 2 is off while the other device does not advertise it`() =
        runTest {
            capability.value = false

            assertFalse(gate.isV2Active().first())
        }

    @Test
    fun `version 2 is on with the switch and the capability`() =
        runTest {
            assertTrue(gate.isV2Active().first())
        }

    @Test
    fun `a feature needs the other device to list it`() =
        runTest {
            assertTrue(gate.isFeatureActive(WearFeatures.RECEIPTS).first())
            assertFalse(gate.isFeatureActive(WearFeatures.MOMENTS).first())
        }

    @Test
    fun `no feature is active while version 2 is off`() =
        runTest {
            flag.value = false

            assertFalse(gate.isFeatureActive(WearFeatures.RECEIPTS).first())
        }
}
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create the five production files from Step 4 in full, except: `StateDocumentCodec.encode` returns `""` and `decode`/`fromDataMap` return `Decoded.Malformed("")`; `StateMerger.merge` returns `MergedState(null, null, emptyMap(), null)`; `PeerStateStore.accept` returns `null`, `latest` returns `flowOf(null)` and `features` returns `flowOf(emptySet())`; `WearProtocolGate.isV2Active` and `isFeatureActive` return `flowOf(false)`.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.state.*' --tests 'app.logdate.client.sync.datalayer.v2.WearProtocolGateTest' --console=plain
```
Expected: FAIL, including `StateDocumentCodecTest > a phone document has a stable wire form`, `StateMergerTest > phone owned fields come only from the phone document`, `PeerStateStoreTest > a readable document from the expected role is stored` and `WearProtocolGateTest > version 2 is on with the switch and the capability`.

- [ ] **Step 4: Implement the state document**

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt`:

```kotlin
package app.logdate.client.sync.peer.state

import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class PeerRole {
    PHONE,
    WATCH,
}

/** A shared preference value with the time, by the writer's clock, it was written. */
@Serializable
data class Stamped(
    val value: JsonElement,
    val at: Long,
)

/** The campfire as the phone computed it; the watch's window is too short to compute a long run. */
@Serializable
data class CampfireSnapshot(
    val phase: String,
    val runDays: Int,
    val litToday: Boolean,
    val computedAt: Long,
)

/**
 * Fields only the phone writes.
 *
 * @property flags Feature flag values by [app.logdate.client.datastore.featureflags.FeatureFlag.key].
 * @property features The version 2 features this phone supports, from [app.logdate.client.sync.datalayer.v2.WearFeatures].
 */
@Serializable
data class PhoneState(
    val flags: Map<String, Boolean> = emptyMap(),
    val cloud: CloudPresence = CloudPresence.UNKNOWN,
    val campfire: CampfireSnapshot? = null,
    val dayStartHour: Int = 0,
    val zone: String? = null,
    val openMarks: Int = 0,
    val features: Set<String> = emptySet(),
)

@Serializable
enum class PeerLinkState {
    UNLINKED,
    PAIRED,
    LINKED,
    NEEDS_RELINK,
}

@Serializable
enum class PresenceState {
    ON_WRIST,
    OFF_WRIST,
    UNKNOWN,
}

@Serializable
data class Presence(
    val state: PresenceState = PresenceState.UNKNOWN,
    val observedAt: Long = 0,
)

/** Fields only the watch writes. */
@Serializable
data class WatchState(
    val outboxCount: Int = 0,
    val deadLetterCount: Int = 0,
    val freeBytes: Long? = null,
    val link: PeerLinkState = PeerLinkState.UNLINKED,
    val presence: Presence = Presence(),
    val features: Set<String> = emptySet(),
)

/**
 * One device's state, written as a data item at [app.logdate.client.sync.datalayer.v2.WearPaths.STATE].
 *
 * Every field has one owner: a phone document's [watch] and a watch document's [phone] are ignored by
 * [StateMerger]. [shared] entries are written by both and merged last-writer-wins.
 */
@Serializable
data class StateDocument(
    val schema: Int = WearProtocol.STATE_SCHEMA,
    val role: PeerRole,
    val writtenAt: Long,
    val phone: PhoneState? = null,
    val watch: WatchState? = null,
    val shared: Map<String, Stamped> = emptyMap(),
    val lastReminderAt: Long? = null,
)

/** Reads and writes [StateDocument]s, enforcing their size limit. */
object StateDocumentCodec {
    /** The data item key the document's JSON is stored under. */
    const val KEY = "doc"

    /** Throws [IllegalArgumentException] for a document over [WearProtocol.MAX_STATE_BYTES]. */
    fun encode(document: StateDocument): String {
        val text = WearJson.encode(StateDocument.serializer(), document)
        val size = text.encodeToByteArray().size
        require(size <= WearProtocol.MAX_STATE_BYTES) { "A state document is $size bytes; the limit is ${WearProtocol.MAX_STATE_BYTES}" }
        return text
    }

    fun decode(text: String): Decoded<StateDocument> = WearJson.decode(text, WearProtocol.STATE_SCHEMA, StateDocument.serializer())

    fun toDataMap(document: StateDocument): Map<String, String> = mapOf(KEY to encode(document))

    fun fromDataMap(data: Map<String, String>): Decoded<StateDocument> {
        val text = data[KEY] ?: return Decoded.Malformed("The data item has no state document")
        return decode(text)
    }
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateMerger.kt`:

```kotlin
package app.logdate.client.sync.peer.state

import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/** What both devices agree on, each field taken from its owner. */
data class MergedState(
    val phone: PhoneState?,
    val watch: WatchState?,
    val shared: Map<String, JsonElement>,
    val lastReminderAt: Long?,
)

/**
 * Combines the phone's and the watch's documents.
 *
 * A document only contributes the section its role owns. Shared entries go to the later write; a stamp
 * from the future counts as now, so a clock running ahead cannot keep a value against a later write,
 * and a tie goes to the phone. The last reminder is the later of the two, so the one-a-day limit holds
 * whichever device delivered it. A watch's presence older than [PRESENCE_STALE_AFTER_MS] is unknown.
 */
class StateMerger(
    private val clock: Clock = Clock.System,
) {
    fun merge(
        phone: StateDocument?,
        watch: StateDocument?,
    ): MergedState {
        val now = clock.now().toEpochMilliseconds()
        val phoneDocument = phone?.takeIf { it.role == PeerRole.PHONE }
        val watchDocument = watch?.takeIf { it.role == PeerRole.WATCH }
        return MergedState(
            phone = phoneDocument?.phone,
            watch = watchDocument?.watch?.let { it.copy(presence = it.presence.asOf(now)) },
            shared = mergeShared(phoneDocument?.shared.orEmpty(), watchDocument?.shared.orEmpty(), now),
            lastReminderAt = listOfNotNull(phoneDocument?.lastReminderAt, watchDocument?.lastReminderAt).maxOrNull(),
        )
    }

    private fun mergeShared(
        phone: Map<String, Stamped>,
        watch: Map<String, Stamped>,
        now: Long,
    ): Map<String, JsonElement> = (phone.keys + watch.keys).associateWith { key -> later(phone[key], watch[key], now).value }

    private fun later(
        phone: Stamped?,
        watch: Stamped?,
        now: Long,
    ): Stamped {
        if (phone == null) return requireNotNull(watch)
        if (watch == null) return phone
        return if (minOf(watch.at, now) > minOf(phone.at, now)) watch else phone
    }

    private fun Presence.asOf(now: Long): Presence {
        if (now - minOf(observedAt, now) <= PRESENCE_STALE_AFTER_MS) return this
        return copy(state = PresenceState.UNKNOWN)
    }

    companion object {
        const val PRESENCE_STALE_AFTER_MS = 30 * 60 * 1_000L
    }
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerStateStore.kt`:

```kotlin
package app.logdate.client.sync.peer.state

import app.logdate.client.sync.datalayer.v2.Decoded
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The latest readable state document each peer node wrote, for as long as this process runs. */
class PeerStateStore {
    private val documents = MutableStateFlow<Map<String, StateDocument>>(emptyMap())

    /**
     * Keeps [decoded] as [nodeId]'s document when it is readable and written by [expectedRole], and
     * returns it. Returns null, with the reason logged, for anything else.
     */
    fun accept(
        nodeId: String,
        decoded: Decoded<StateDocument>,
        expectedRole: PeerRole,
    ): StateDocument? {
        val document =
            when (decoded) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> {
                    Napier.w("A $expectedRole state document uses schema ${decoded.schema}; it is ignored until this device updates")
                    return null
                }
                is Decoded.Malformed -> {
                    Napier.e("A $expectedRole state document could not be read: ${decoded.reason}")
                    return null
                }
            }
        if (document.role != expectedRole) {
            Napier.w("Ignoring a ${document.role} state document where a $expectedRole one was expected")
            return null
        }
        documents.update { it + (nodeId to document) }
        return document
    }

    fun latest(role: PeerRole): Flow<StateDocument?> =
        documents
            .map { all -> all.values.filter { it.role == role }.maxByOrNull { it.writtenAt } }
            .distinctUntilChanged()

    /** The features the latest [role] document lists; empty until one arrives. */
    fun features(role: PeerRole): Flow<Set<String>> =
        latest(role)
            .map { document ->
                when (role) {
                    PeerRole.PHONE -> document?.phone?.features
                    PeerRole.WATCH -> document?.watch?.features
                }.orEmpty()
            }.distinctUntilChanged()
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerLink.kt`:

```kotlin
package app.logdate.client.sync.peer.state

import app.logdate.client.sync.datalayer.v2.Decoded
import kotlinx.coroutines.flow.Flow

/** A state document another node wrote, readable or not. */
data class PeerDocument(
    val nodeId: String,
    val decoded: Decoded<StateDocument>,
)

/** Where this device writes its state document and reads the other devices'. */
interface StateDocumentChannel {
    /** Writes this device's document as an urgent data item; false, with the reason logged, when it could not. */
    suspend fun publish(document: StateDocument): Boolean

    /** The documents other nodes wrote, to catch up on changes made while this process was not running. */
    suspend fun peerDocuments(): List<PeerDocument>
}

/** This device's dynamic Data Layer capabilities. */
interface LocalCapabilities {
    /** True once [capability] is advertised, including when it already was. */
    suspend fun add(capability: String): Boolean

    /** True once [capability] is not advertised, including when it never was. */
    suspend fun remove(capability: String): Boolean
}

/** Whether a reachable node advertises a capability, at once and whenever that changes. */
fun interface PeerCapabilityPresence {
    fun observe(capability: String): Flow<Boolean>
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocolGate.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Decides when this device speaks version 2.
 *
 * Version 2 is active only while this device's Labs switch is on and the other device advertises its
 * version 2 capability. A surface also needs the other device to list the feature it uses; until then
 * it does not appear, and both devices keep using version 1.
 */
class WearProtocolGate(
    private val flagEnabled: Flow<Boolean>,
    private val peerHasV2Capability: Flow<Boolean>,
    private val peerFeatures: Flow<Set<String>>,
) {
    fun isV2Active(): Flow<Boolean> =
        combine(flagEnabled, peerHasV2Capability) { enabled, capable -> enabled && capable }.distinctUntilChanged()

    fun isFeatureActive(feature: String): Flow<Boolean> =
        combine(isV2Active(), peerFeatures) { active, features -> active && feature in features }.distinctUntilChanged()
}
```

- [ ] **Step 5: Run the tests again**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.state.*' --tests 'app.logdate.client.sync.datalayer.v2.WearProtocolGateTest' --console=plain
```
Expected: PASS, 28 tests in 4 classes.

- [ ] **Step 6: Compile for iOS and run the module's checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:desktopTest :client:sync:testAndroidHostTest :client:sync:ktlintCheck :client:sync:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task02-state-document.txt <<'EOF'
chore(sync): add the shared watch and phone state document

The phone and the watch each need to tell the other what they support
and how they are set, without either one overwriting the other. Each
device will publish one small document: the phone owns its flags,
cloud status, campfire, day start, time zone, open marks and
features; the watch owns its queue counts, free space, link state,
whether it is worn and its features. Preferences both can change are
merged by the later write, with ties going to the phone, and a clock
running ahead cannot keep a value against a later change.

The protocol gate turns version 2 on only while the Labs switch is on
and the other device advertises it, and shows a surface only when the
other device lists its feature. Nothing publishes the document yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateMerger.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerStateStore.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerLink.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocolGate.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateDocumentCodecTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateMergerTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/PeerStateStoreTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocolGateTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task02-state-document.txt
```

---

### Task 3: Publish the phone's state for its watches (C2, phone)

**Files:**
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhonePeerLink.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt:215-222` (`resolveSyncDependencies`), `:233-243` (the `when` in `processSnapshotEvent`), `:466-470` (`PhoneDataLayerSyncDependencies`)
- Modify: `app/android-main/src/main/AndroidManifest.xml:228-246` (the listener's `DATA_CHANGED` filter)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:198-205` (after the `PhoneWearSyncBridge` binding) and its imports
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt:126-133` (after the dynamic shortcut block)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhonePeerLinkTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneDataLayerListenerServiceTest.kt` (`dependencies(...)` helper at :633-645; 3 new tests)

**Interfaces:**
- Consumes (Task 2): `StateDocument`, `PhoneState`, `PeerRole`, `StateDocumentCodec`, `PeerStateStore.accept`, `StateDocumentChannel`, `LocalCapabilities`, `PeerCapabilityPresence`, `PeerDocument`, `WearProtocolGate`; (Task 1) `WearPaths.STATE`, `WearProtocol.PHONE_CAPABILITY_V2`, `WearProtocol.WATCH_CAPABILITY_V2`, `Decoded`. Existing: `PhoneWearTransport.putDataItem(path, data, urgent): Boolean` (`PhoneWearSyncBridge.kt:40-56`), `FeatureFlagStore.observe(flag): Flow<Boolean>`, `FeatureFlag.WATCH_EXPERIENCE_V2`.
- Produces: `class PhonePeerLink(transport: PhoneWearTransport, dataClient: DataClient, capabilityClient: CapabilityClient, nodeClient: NodeClient) : StateDocumentChannel, LocalCapabilities, PeerCapabilityPresence`; `class PhoneStatePublisher(v2Enabled: Flow<Boolean>, channel: StateDocumentChannel, capabilities: LocalCapabilities, peerStates: PeerStateStore, clock: Clock = Clock.System, timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() }) { fun start(scope: CoroutineScope): Job; suspend fun catchUpWithWatches(); suspend fun publish(v2Enabled: Boolean) }`; `PhoneDataLayerSyncDependencies(noteIngestor, journalIngestor, healthSnapshotDao, peerStates: PeerStateStore)`; Koin singles for `PeerStateStore`, `PhonePeerLink` (bound to its three interfaces), `PhoneStatePublisher`, `WearProtocolGate`.

- [ ] **Step 1: Write the failing publisher and adapter tests**

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt`:

```kotlin
package app.logdate.client.sync.v2

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerDocument
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.WatchState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [PhoneStatePublisher]: the phone always publishes its state, and advertises version 2 only
 * while the Labs switch is on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneStatePublisherTest {
    private val enabled = MutableStateFlow(false)
    private val channel = FakeChannel()
    private val capabilities = FakeCapabilities()
    private val peerStates = PeerStateStore()
    private val publisher =
        PhoneStatePublisher(
            v2Enabled = enabled,
            channel = channel,
            capabilities = capabilities,
            peerStates = peerStates,
            clock = FixedClock(1_000),
            timeZone = { TimeZone.of("Europe/Paris") },
        )

    @Test
    fun `the phone publishes its state with the switch off`() =
        runTest {
            publisher.start(backgroundScope)
            runCurrent()

            assertEquals(
                listOf(
                    StateDocument(
                        role = PeerRole.PHONE,
                        writtenAt = 1_000,
                        phone = PhoneState(flags = mapOf(FLAG_KEY to false), zone = "Europe/Paris"),
                    ),
                ),
                channel.published,
            )
            assertEquals(listOf("remove $PHONE_V2"), capabilities.calls)
        }

    @Test
    fun `turning the switch on publishes it and advertises version 2`() =
        runTest {
            publisher.start(backgroundScope)
            runCurrent()

            enabled.value = true
            runCurrent()

            assertEquals(true, channel.published.last().phone?.flags?.get(FLAG_KEY))
            assertEquals(listOf("remove $PHONE_V2", "add $PHONE_V2"), capabilities.calls)
        }

    @Test
    fun `starting reads the state the watches already published`() =
        runTest {
            val watch = StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = WatchState(features = setOf("receipts.v1")))
            channel.peers = listOf(PeerDocument("watch-node", Decoded.Supported(watch)))

            publisher.start(backgroundScope)
            runCurrent()

            assertEquals(setOf("receipts.v1"), peerStates.features(PeerRole.WATCH).first())
        }

    @Test
    fun `a failed publish still updates the capability`() =
        runTest {
            channel.succeeds = false

            publisher.start(backgroundScope)
            runCurrent()

            assertEquals(listOf("remove $PHONE_V2"), capabilities.calls)
        }

    private class FakeChannel : StateDocumentChannel {
        val published = mutableListOf<StateDocument>()
        var peers = emptyList<PeerDocument>()
        var succeeds = true

        override suspend fun publish(document: StateDocument): Boolean {
            published += document
            return succeeds
        }

        override suspend fun peerDocuments(): List<PeerDocument> = peers
    }

    private class FakeCapabilities : LocalCapabilities {
        val calls = mutableListOf<String>()

        override suspend fun add(capability: String): Boolean {
            calls += "add $capability"
            return true
        }

        override suspend fun remove(capability: String): Boolean {
            calls += "remove $capability"
            return true
        }
    }

    private class FixedClock(
        private val millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val FLAG_KEY = "watch_experience_v2_enabled"
        const val PHONE_V2 = "logdate_phone_sync_v2"
    }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhonePeerLinkTest.kt`:

```kotlin
package app.logdate.client.sync.v2

import app.logdate.client.sync.PhoneWearTransport
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentCodec
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.WearableStatusCodes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [PhonePeerLink], the phone's Play services side of the shared state document.
 */
class PhonePeerLinkTest {
    private val transport = mockk<PhoneWearTransport>()
    private val capabilityClient = mockk<CapabilityClient>()
    private val link =
        PhonePeerLink(
            transport = transport,
            dataClient = mockk<DataClient>(),
            capabilityClient = capabilityClient,
            nodeClient = mockk<NodeClient>(),
        )

    @Test
    fun `the state goes out as one urgent data item`() =
        runTest {
            val document = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState())
            coEvery { transport.putDataItem(any(), any(), any()) } returns true

            assertTrue(link.publish(document))
            coVerify { transport.putDataItem("/logdate/state", StateDocumentCodec.toDataMap(document), true) }
        }

    @Test
    fun `adding a capability that is already advertised counts as done`() =
        runTest {
            every { capabilityClient.addLocalCapability(PHONE_V2) } returns
                Tasks.forException(ApiException(Status(WearableStatusCodes.DUPLICATE_CAPABILITY)))

            assertTrue(link.add(PHONE_V2))
        }

    @Test
    fun `removing a capability that was never advertised counts as done`() =
        runTest {
            every { capabilityClient.removeLocalCapability(PHONE_V2) } returns
                Tasks.forException(ApiException(Status(WearableStatusCodes.UNKNOWN_CAPABILITY)))

            assertTrue(link.remove(PHONE_V2))
        }

    @Test
    fun `a successful capability change is done`() =
        runTest {
            every { capabilityClient.addLocalCapability(PHONE_V2) } returns Tasks.forResult<Void>(null)

            assertTrue(link.add(PHONE_V2))
        }

    @Test
    fun `any other capability failure is not done`() =
        runTest {
            every { capabilityClient.addLocalCapability(PHONE_V2) } returns Tasks.forException(ApiException(Status(13)))
            every { capabilityClient.removeLocalCapability(PHONE_V2) } returns Tasks.forException(IllegalStateException("API_UNAVAILABLE"))

            assertFalse(link.add(PHONE_V2))
            assertFalse(link.remove(PHONE_V2))
        }

    @Test
    fun `the watch advertising version 2 is reported`() =
        runTest {
            val info = mockk<CapabilityInfo>()
            every { info.nodes } returns setOf(mockk<Node>())
            every { capabilityClient.addListener(any(), WATCH_V2) } returns Tasks.forResult<Void>(null)
            every { capabilityClient.removeListener(any(), WATCH_V2) } returns Tasks.forResult(true)
            every { capabilityClient.getCapability(WATCH_V2, CapabilityClient.FILTER_REACHABLE) } returns Tasks.forResult(info)

            assertTrue(link.observe(WATCH_V2).first())
        }

    private companion object {
        const val PHONE_V2 = "logdate_phone_sync_v2"
        const val WATCH_V2 = "logdate_watch_sync_v2"
    }
}
```

In `app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneDataLayerListenerServiceTest.kt`, replace the `dependencies(...)` helper (lines 633-645, from `private fun dependencies(` through its closing `)`) with:

```kotlin
    private fun dependencies(
        noteIngestor: WatchNoteIngestor = mockk(relaxed = true),
        journalIngestor: WatchJournalIngestor = mockk(relaxed = true),
        healthSnapshotDao: HealthSnapshotDao = mockk(relaxed = true),
        peerStates: PeerStateStore = PeerStateStore(),
    ): PhoneDataLayerSyncDependencies =
        PhoneDataLayerSyncDependencies(
            noteIngestor = noteIngestor,
            journalIngestor = journalIngestor,
            healthSnapshotDao = healthSnapshotDao,
            peerStates = peerStates,
        )
```

and add these tests before `private fun serviceWithDependencies(`:

```kotlin
    @Test
    fun `a state document from the watch is stored for the protocol gate`() =
        runTest {
            val peerStates = PeerStateStore()
            val service = TestPhoneDataLayerListenerService(serviceScope = this, syncDependencies = dependencies(peerStates = peerStates))
            val document = StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = WatchState(features = setOf(WearFeatures.RECEIPTS)))

            service.processEvents(
                PhoneDataLayerSnapshotEvent(
                    path = WearPaths.STATE,
                    data = StateDocumentCodec.toDataMap(document),
                    sourceNodeId = "watch-node",
                ),
            )

            assertEquals(setOf(WearFeatures.RECEIPTS), peerStates.features(PeerRole.WATCH).first())
        }

    @Test
    fun `the phone's own state document is never taken for a watch's`() =
        runTest {
            val peerStates = PeerStateStore()
            val service = TestPhoneDataLayerListenerService(serviceScope = this, syncDependencies = dependencies(peerStates = peerStates))
            val document = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState())

            service.processEvents(
                PhoneDataLayerSnapshotEvent(
                    path = WearPaths.STATE,
                    data = StateDocumentCodec.toDataMap(document),
                    sourceNodeId = "phone-node",
                ),
            )

            assertNull(peerStates.latest(PeerRole.PHONE).first())
            assertNull(peerStates.latest(PeerRole.WATCH).first())
        }

    @Test
    fun `a phone document from another node is not taken for a watch's`() =
        runTest {
            val peerStates = PeerStateStore()
            val service = TestPhoneDataLayerListenerService(serviceScope = this, syncDependencies = dependencies(peerStates = peerStates))
            val document = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState())

            service.processEvents(
                PhoneDataLayerSnapshotEvent(
                    path = WearPaths.STATE,
                    data = StateDocumentCodec.toDataMap(document),
                    sourceNodeId = "other-phone",
                ),
            )

            assertNull(peerStates.latest(PeerRole.WATCH).first())
            assertNull(peerStates.latest(PeerRole.PHONE).first())
        }
```

Add the imports `app.logdate.client.sync.datalayer.v2.WearFeatures`, `app.logdate.client.sync.datalayer.v2.WearPaths`, `app.logdate.client.sync.peer.state.PeerRole`, `app.logdate.client.sync.peer.state.PeerStateStore`, `app.logdate.client.sync.peer.state.PhoneState`, `app.logdate.client.sync.peer.state.StateDocument`, `app.logdate.client.sync.peer.state.StateDocumentCodec`, `app.logdate.client.sync.peer.state.WatchState`, `kotlinx.coroutines.flow.first` and `kotlin.test.assertNull`, each in its lexicographic place.

- [ ] **Step 2: Add the API surface with inert bodies**

Create `PhonePeerLink.kt` and `PhoneStatePublisher.kt` with the declarations from Step 4, with bodies: `publish`, `add` and `remove` return `false`; `peerDocuments` returns `emptyList()`; `observe` returns `flowOf(false)`; `start` returns `scope.launch { }`; `catchUpWithWatches` and `publish(v2Enabled)` are empty. In `PhoneDataLayerListenerService.kt` add only the field `val peerStates: PeerStateStore,` to `PhoneDataLayerSyncDependencies` and `peerStates = koin.get(),` to `resolveSyncDependencies`, so the tests compile.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.*' --tests 'app.logdate.client.sync.PhoneDataLayerListenerServiceTest' --console=plain
```
Expected: FAIL: `PhoneStatePublisherTest > the phone publishes its state with the switch off` (`expected:<[StateDocument(…)]> but was:<[]>`), `turning the switch on…`, `starting reads…`, `a failed publish…`; `PhonePeerLinkTest > the state goes out as one urgent data item`, `adding a capability…`, `removing a capability…`, `a successful capability change is done`, `the watch advertising version 2 is reported`; `PhoneDataLayerListenerServiceTest > a state document from the watch is stored for the protocol gate`. `any other capability failure is not done` and the two "not taken" tests pass already. Every existing listener test still passes.

- [ ] **Step 4: Implement the phone side**

`app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhonePeerLink.kt`:

```kotlin
package app.logdate.client.sync.v2

import android.net.Uri
import app.logdate.client.sync.PhoneWearTransport
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerCapabilityPresence
import app.logdate.client.sync.peer.state.PeerDocument
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.StateDocumentCodec
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.WearableStatusCodes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The phone's Play services side of the state document: publishes through [PhoneWearTransport], reads
 * the watches' documents from [DataClient], and manages this phone's dynamic capabilities.
 */
class PhonePeerLink(
    private val transport: PhoneWearTransport,
    private val dataClient: DataClient,
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
) : StateDocumentChannel,
    LocalCapabilities,
    PeerCapabilityPresence {
    override suspend fun publish(document: StateDocument): Boolean {
        val data =
            try {
                StateDocumentCodec.toDataMap(document)
            } catch (e: IllegalArgumentException) {
                Napier.e("This phone's state document is too large to publish", e)
                return false
            }
        return transport.putDataItem(WearPaths.STATE, data, urgent = true)
    }

    override suspend fun peerDocuments(): List<PeerDocument> {
        val localNode = localNodeId() ?: return emptyList()
        val buffer =
            try {
                dataClient.getDataItems(stateItems()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Could not read the watches' state documents; each is read again when it next changes", e)
                return emptyList()
            }
        return try {
            buffer.mapNotNull { item ->
                val node = item.uri.host
                if (node == null || node == localNode) return@mapNotNull null
                val text = DataMapItem.fromDataItem(item).dataMap.getString(StateDocumentCodec.KEY)
                val decoded = if (text == null) Decoded.Malformed("The data item has no state document") else StateDocumentCodec.decode(text)
                PeerDocument(node, decoded)
            }
        } finally {
            buffer.release()
        }
    }

    override suspend fun add(capability: String): Boolean =
        changeCapability(capability, adding = true) { capabilityClient.addLocalCapability(capability).await() }

    override suspend fun remove(capability: String): Boolean =
        changeCapability(capability, adding = false) { capabilityClient.removeLocalCapability(capability).await() }

    override fun observe(capability: String): Flow<Boolean> =
        callbackFlow {
            val listener = CapabilityClient.OnCapabilityChangedListener { launch { send(isAdvertised(capability)) } }
            val listening =
                try {
                    capabilityClient.addListener(listener, capability).await()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Could not follow $capability; it is reported as of now only", e)
                    false
                }
            send(isAdvertised(capability))
            awaitClose {
                if (listening) capabilityClient.removeListener(listener, capability)
            }
        }.distinctUntilChanged()

    private suspend fun isAdvertised(capability: String): Boolean =
        try {
            capabilityClient
                .getCapability(capability, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not read which watches advertise $capability; reporting none", e)
            false
        }

    private suspend fun changeCapability(
        capability: String,
        adding: Boolean,
        change: suspend () -> Unit,
    ): Boolean =
        try {
            change()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            val alreadyDone =
                e.statusCode == if (adding) WearableStatusCodes.DUPLICATE_CAPABILITY else WearableStatusCodes.UNKNOWN_CAPABILITY
            if (!alreadyDone) Napier.e("Could not ${if (adding) "add" else "remove"} the capability $capability", e)
            alreadyDone
        } catch (e: Exception) {
            Napier.e("Could not ${if (adding) "add" else "remove"} the capability $capability", e)
            false
        }

    private suspend fun localNodeId(): String? =
        try {
            nodeClient.localNode.await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not read this phone's Data Layer node; the watches' state is read when it next changes", e)
            null
        }

    private fun stateItems(): Uri =
        Uri
            .Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .authority("*")
            .path(WearPaths.STATE)
            .build()
}
```

`app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt`:

```kotlin
package app.logdate.client.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Publishes this phone's state document whenever the Labs watch switch changes, and advertises
 * [WearProtocol.PHONE_CAPABILITY_V2] only while it is on.
 *
 * The document goes out whatever the switch says, because the watch takes the switch from it. It lists
 * no version 2 features yet: each one is added by the work that ships it.
 */
class PhoneStatePublisher(
    private val v2Enabled: Flow<Boolean>,
    private val channel: StateDocumentChannel,
    private val capabilities: LocalCapabilities,
    private val peerStates: PeerStateStore,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            catchUpWithWatches()
            v2Enabled.distinctUntilChanged().collect { enabled -> publish(enabled) }
        }

    /** Reads the documents the watches published while this phone's app was not running. */
    suspend fun catchUpWithWatches() {
        channel.peerDocuments().forEach { peer -> peerStates.accept(peer.nodeId, peer.decoded, PeerRole.WATCH) }
    }

    suspend fun publish(v2Enabled: Boolean) {
        val document =
            StateDocument(
                role = PeerRole.PHONE,
                writtenAt = clock.now().toEpochMilliseconds(),
                phone =
                    PhoneState(
                        flags = mapOf(FeatureFlag.WATCH_EXPERIENCE_V2.key to v2Enabled),
                        zone = timeZone().id,
                    ),
            )
        if (!channel.publish(document)) {
            Napier.e("Could not publish this phone's state; watches keep the old Labs setting until the next change or restart")
        }
        val capabilityUpdated =
            if (v2Enabled) {
                capabilities.add(WearProtocol.PHONE_CAPABILITY_V2)
            } else {
                capabilities.remove(WearProtocol.PHONE_CAPABILITY_V2)
            }
        if (!capabilityUpdated) {
            Napier.e("Could not update ${WearProtocol.PHONE_CAPABILITY_V2}; watches keep the old protocol until the next change or restart")
        }
    }
}
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt`:

1. Add the imports `app.logdate.client.sync.datalayer.v2.WearPaths`, `app.logdate.client.sync.peer.state.PeerRole`, `app.logdate.client.sync.peer.state.PeerStateStore` and `app.logdate.client.sync.peer.state.StateDocumentCodec` in their lexicographic places.
2. In `processSnapshotEvent`, add this branch after `HealthSnapshotDataMapper.isHealthPath(path) -> handleHealthSync(event, path, dependencies.healthSnapshotDao)`:

```kotlin
                path == WearPaths.STATE -> acceptWatchState(event, dependencies.peerStates)
```

3. Add this function after `handleHealthSync`:

```kotlin
    private fun acceptWatchState(
        event: PhoneDataLayerSnapshotEvent,
        peerStates: PeerStateStore,
    ) {
        val watchNode = event.sourceNodeId
        if (watchNode == null) {
            Napier.w("A state document arrived without the node that wrote it; ignoring it")
            return
        }
        peerStates.accept(watchNode, StateDocumentCodec.fromDataMap(event.data), PeerRole.WATCH)
    }
```

The `peerStates` field and its Koin lookup from Step 2 stay.

In `app/android-main/src/main/AndroidManifest.xml`, inside the `PhoneDataLayerListenerService`'s `DATA_CHANGED` filter, after the `/logdate/health` `<data>` element (line 245), add:

```xml
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:path="/logdate/state" />
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt`, after the `single<PhoneWearSyncBridge> { … }` block, add:

```kotlin
        single { PeerStateStore() }
        single {
            PhonePeerLink(
                transport = get(),
                dataClient = Wearable.getDataClient(androidContext()),
                capabilityClient = Wearable.getCapabilityClient(androidContext()),
                nodeClient = Wearable.getNodeClient(androidContext()),
            )
        } binds arrayOf(StateDocumentChannel::class, LocalCapabilities::class, PeerCapabilityPresence::class)
        single {
            PhoneStatePublisher(
                v2Enabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                channel = get(),
                capabilities = get(),
                peerStates = get(),
            )
        }
        single {
            WearProtocolGate(
                flagEnabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                peerHasV2Capability = get<PeerCapabilityPresence>().observe(WearProtocol.WATCH_CAPABILITY_V2),
                peerFeatures = get<PeerStateStore>().features(PeerRole.WATCH),
            )
        }
```

with the imports `app.logdate.client.datastore.featureflags.FeatureFlag`, `app.logdate.client.datastore.featureflags.FeatureFlagStore`, `app.logdate.client.sync.datalayer.v2.WearProtocol`, `app.logdate.client.sync.datalayer.v2.WearProtocolGate`, `app.logdate.client.sync.peer.state.LocalCapabilities`, `app.logdate.client.sync.peer.state.PeerCapabilityPresence`, `app.logdate.client.sync.peer.state.PeerRole`, `app.logdate.client.sync.peer.state.PeerStateStore`, `app.logdate.client.sync.peer.state.StateDocumentChannel`, `app.logdate.client.sync.v2.PhonePeerLink`, `app.logdate.client.sync.v2.PhoneStatePublisher`, `com.google.android.gms.wearable.Wearable` and `org.koin.dsl.binds`.

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt`, after the `runCatching { … shortcutScheduler … }.onFailure { … }` block that ends `onCreate`, add:

```kotlin
        runCatching {
            get<PhoneStatePublisher>().start(get())
        }.onFailure { error ->
            Napier.w("Failed to start publishing this phone's state to its watches", error)
        }
```

with the import `app.logdate.client.sync.v2.PhoneStatePublisher`.

- [ ] **Step 5: Run the tests again**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.*' --tests 'app.logdate.client.sync.PhoneDataLayerListenerServiceTest' --console=plain
```
Expected: PASS: 4 publisher tests, 6 adapter tests, and every listener test including the 3 new ones.

- [ ] **Step 6: Run the phone's checks and build it**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck :app:android-main:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL. The merged manifest of `assembleDebug` lists `/logdate/state` under the listener (`app/android-main/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`).

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task03-phone-state.txt <<'EOF'
chore(wear): publish the phone's state for its watches

The phone now publishes a small state document for its watches each
time LogDate starts and whenever the Labs switch for the redesigned
watch app changes. It carries that switch and the phone's time zone.
While the switch is on, the phone also advertises the version 2 sync
capability, and it withdraws it when the switch goes off.

The phone reads the watch's own document as it arrives and when the
app starts, and ignores its own and any other phone's. A document that
cannot be published or a capability that cannot be changed is logged
with what the watch keeps doing until the next try. No watch acts on
the document yet, and builds without this change never receive it.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhonePeerLink.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt app/android-main/src/main/AndroidManifest.xml app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhonePeerLinkTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/PhoneDataLayerListenerServiceTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task03-phone-state.txt
```

---

### Task 4: Send the Labs watch switch to the watch (C2, watch)

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchPeerLink.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchStateMirror.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt:98-125` (the `when (event.path)` in `onDataChanged`) and a new private function after `handleRemoteCameraCaptureResult` (line 160)
- Modify: `app/wear/src/main/AndroidManifest.xml:81-98` (the listener's `DATA_CHANGED` filter)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt:111-122` (after `WatchSyncScheduler`) and its imports
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt:95-103` (`startSync`)
- Modify: `client/feature/core/src/commonMain/composeResources/values/strings_labs.xml:9`
- Modify: `app/compose-main/src/desktopTest/reference/labs-settings--phone.png`, `labs-settings--phone-dark.png`, `labs-settings--phone-landscape.png`, `labs-settings--tablet.png` (re-recorded)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchStateMirrorTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchPeerLinkTest.kt`
- Test: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt:39-50`
- Test: `client/feature/core/src/desktopTest/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsContentTest.kt:24`

**Interfaces:**
- Consumes (Tasks 1-2): `StateDocument`, `WatchState`, `PeerRole`, `StateDocumentCodec`, `PeerStateStore`, `StateMerger`, `StateDocumentChannel`, `LocalCapabilities`, `PeerCapabilityPresence`, `PeerDocument`, `Decoded`, `WearProtocolGate`, `WearPaths.STATE`, `WearProtocol.PHONE_CAPABILITY_V2`, `WearProtocol.WATCH_CAPABILITY_V2`. Existing: `WearDataLayerClient.putDataItem(path, data, urgent): Boolean` (`WearDataLayerClient.kt:20-24`), `FeatureFlagStore.observe/isEnabled/setEnabled`, the listener's `localNodeId()` and `resolveKoin()` (`WearDataLayerListenerService.kt:306-340`).
- Produces: `class WatchPeerLink(dataLayerClient: WearDataLayerClient, dataClient: DataClient, capabilityClient: CapabilityClient, nodeClient: NodeClient) : StateDocumentChannel, LocalCapabilities, PeerCapabilityPresence`; `class WatchStateMirror(featureFlags: FeatureFlagStore, channel: StateDocumentChannel, capabilities: LocalCapabilities, peerStates: PeerStateStore, merger: StateMerger, clock: Clock = Clock.System) { fun start(scope: CoroutineScope): Job; suspend fun catchUpWithPhone(); suspend fun onPhoneDocument(sourceNodeId: String, localNodeId: String?, decoded: Decoded<StateDocument>) }`; Koin singles for `PeerStateStore`, `StateMerger`, `WatchPeerLink`, `WatchStateMirror`, `WearProtocolGate`.

- [ ] **Step 1: Write the failing mirror and adapter tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchStateMirrorTest.kt`:

```kotlin
package app.logdate.wear.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerDocument
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.StateMerger
import app.logdate.client.sync.peer.state.WatchState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [WatchStateMirror]: the watch takes the Labs switch from the phone's document and nothing
 * else, and publishes its own document and capability.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchStateMirrorTest {
    private val flags = InMemoryFlags()
    private val channel = FakeChannel()
    private val capabilities = FakeCapabilities()
    private val mirror =
        WatchStateMirror(
            featureFlags = flags,
            channel = channel,
            capabilities = capabilities,
            peerStates = PeerStateStore(),
            merger = StateMerger(FixedClock(1_000)),
            clock = FixedClock(1_000),
        )

    @Test
    fun `the phone turning the switch on turns it on here`() =
        runTest {
            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(phoneDocument(switchOn = true)))

            assertEquals(listOf(true), flags.writes)
        }

    @Test
    fun `the phone turning the switch off turns it off here`() =
        runTest {
            flags.value.value = true

            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(phoneDocument(switchOn = false)))

            assertEquals(listOf(false), flags.writes)
        }

    @Test
    fun `a document from this watch's own node changes nothing`() =
        runTest {
            mirror.onPhoneDocument(WATCH_NODE, WATCH_NODE, Decoded.Supported(phoneDocument(switchOn = true)))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `a document from another watch changes nothing`() =
        runTest {
            val watchClaimingTheSwitch =
                StateDocument(role = PeerRole.WATCH, writtenAt = 1, phone = PhoneState(flags = mapOf(FLAG_KEY to true)))

            mirror.onPhoneDocument("other-watch", WATCH_NODE, Decoded.Supported(watchClaimingTheSwitch))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `fields the phone does not own are ignored`() =
        runTest {
            val phoneWithAWatchSection =
                StateDocument(
                    role = PeerRole.PHONE,
                    writtenAt = 1,
                    phone = PhoneState(),
                    watch = WatchState(features = setOf("receipts.v1")),
                )

            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(phoneWithAWatchSection))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `a phone on a newer schema leaves the switch as it is`() =
        runTest {
            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Unsupported(2, "newer"))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `an unchanged switch is not written again`() =
        runTest {
            flags.value.value = true

            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(phoneDocument(switchOn = true)))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `starting catches up with the phone and publishes this watch's state`() =
        runTest {
            channel.peers = listOf(PeerDocument(PHONE_NODE, Decoded.Supported(phoneDocument(switchOn = true))))

            mirror.start(backgroundScope)
            runCurrent()

            assertEquals(listOf(true), flags.writes)
            assertEquals(listOf(StateDocument(role = PeerRole.WATCH, writtenAt = 1_000, watch = WatchState())), channel.published)
            assertEquals(listOf("add $WATCH_V2"), capabilities.calls)
        }

    @Test
    fun `this watch advertises version 2 only while its switch is on`() =
        runTest {
            mirror.start(backgroundScope)
            runCurrent()

            flags.value.value = true
            runCurrent()

            assertEquals(listOf("remove $WATCH_V2", "add $WATCH_V2"), capabilities.calls)
            assertEquals(2, channel.published.size)
        }

    private fun phoneDocument(switchOn: Boolean) =
        StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState(flags = mapOf(FLAG_KEY to switchOn)))

    private class InMemoryFlags : FeatureFlagStore {
        val value = MutableStateFlow(false)
        val writes = mutableListOf<Boolean>()

        override fun observe(flag: FeatureFlag): Flow<Boolean> = value

        override suspend fun setEnabled(
            flag: FeatureFlag,
            enabled: Boolean,
        ) {
            writes += enabled
            value.value = enabled
        }
    }

    private class FakeChannel : StateDocumentChannel {
        val published = mutableListOf<StateDocument>()
        var peers = emptyList<PeerDocument>()

        override suspend fun publish(document: StateDocument): Boolean {
            published += document
            return true
        }

        override suspend fun peerDocuments(): List<PeerDocument> = peers
    }

    private class FakeCapabilities : LocalCapabilities {
        val calls = mutableListOf<String>()

        override suspend fun add(capability: String): Boolean {
            calls += "add $capability"
            return true
        }

        override suspend fun remove(capability: String): Boolean {
            calls += "remove $capability"
            return true
        }
    }

    private class FixedClock(
        private val millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val FLAG_KEY = "watch_experience_v2_enabled"
        const val PHONE_NODE = "phone-node"
        const val WATCH_NODE = "watch-node"
        const val WATCH_V2 = "logdate_watch_sync_v2"
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchPeerLinkTest.kt`:

```kotlin
package app.logdate.wear.sync.v2

import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentCodec
import app.logdate.client.sync.peer.state.WatchState
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.WearableStatusCodes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [WatchPeerLink], the watch's Play services side of the shared state document.
 */
class WatchPeerLinkTest {
    private val dataLayerClient = mockk<WearDataLayerClient>()
    private val capabilityClient = mockk<CapabilityClient>()
    private val link =
        WatchPeerLink(
            dataLayerClient = dataLayerClient,
            dataClient = mockk<DataClient>(),
            capabilityClient = capabilityClient,
            nodeClient = mockk<NodeClient>(),
        )

    @Test
    fun `the state goes out as one urgent data item`() =
        runTest {
            val document = StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = WatchState())
            coEvery { dataLayerClient.putDataItem(any(), any(), any()) } returns true

            assertTrue(link.publish(document))
            coVerify { dataLayerClient.putDataItem("/logdate/state", StateDocumentCodec.toDataMap(document), true) }
        }

    @Test
    fun `adding a capability that is already advertised counts as done`() =
        runTest {
            every { capabilityClient.addLocalCapability(WATCH_V2) } returns
                Tasks.forException(ApiException(Status(WearableStatusCodes.DUPLICATE_CAPABILITY)))

            assertTrue(link.add(WATCH_V2))
        }

    @Test
    fun `removing a capability that was never advertised counts as done`() =
        runTest {
            every { capabilityClient.removeLocalCapability(WATCH_V2) } returns
                Tasks.forException(ApiException(Status(WearableStatusCodes.UNKNOWN_CAPABILITY)))

            assertTrue(link.remove(WATCH_V2))
        }

    @Test
    fun `any other capability failure is not done`() =
        runTest {
            every { capabilityClient.addLocalCapability(WATCH_V2) } returns Tasks.forException(ApiException(Status(13)))

            assertFalse(link.add(WATCH_V2))
        }

    @Test
    fun `a phone without version 2 is reported`() =
        runTest {
            val info = mockk<CapabilityInfo>()
            every { info.nodes } returns emptySet()
            every { capabilityClient.addListener(any(), PHONE_V2) } returns Tasks.forResult<Void>(null)
            every { capabilityClient.removeListener(any(), PHONE_V2) } returns Tasks.forResult(true)
            every { capabilityClient.getCapability(PHONE_V2, CapabilityClient.FILTER_REACHABLE) } returns Tasks.forResult(info)

            assertFalse(link.observe(PHONE_V2).first())
        }

    private companion object {
        const val WATCH_V2 = "logdate_watch_sync_v2"
        const val PHONE_V2 = "logdate_phone_sync_v2"
    }
}
```

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt`, add `WearPaths.STATE,` after `RemoteCameraCaptureResultDataMapper.PATH_CAMERA_CAPTURE_RESULT,` in `every data item the phone writes reaches the listener`, and the import `app.logdate.client.sync.datalayer.v2.WearPaths` after `app.logdate.client.sync.datalayer.WearAudioRequestPaths`.

- [ ] **Step 2: Add the API surface with inert bodies**

Create `WatchPeerLink.kt` and `WatchStateMirror.kt` with the declarations from Step 4, with bodies: `publish`, `add` and `remove` return `false`; `peerDocuments` returns `emptyList()`; `observe` returns `flowOf(true)`; `start` returns `scope.launch { }`; `catchUpWithPhone` and `onPhoneDocument` are empty.

- [ ] **Step 3: Run the unit tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.v2.*' --console=plain
```
Expected: FAIL: `the phone turning the switch on turns it on here` (`expected:<[true]> but was:<[]>`), `the phone turning the switch off turns it off here`, `starting catches up with the phone and publishes this watch's state`, `this watch advertises version 2 only while its switch is on`, and every `WatchPeerLinkTest` test except `any other capability failure is not done`. The four "changes nothing" tests pass already.

- [ ] **Step 4: Implement the watch side**

`app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchPeerLink.kt`:

```kotlin
package app.logdate.wear.sync.v2

import android.net.Uri
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerCapabilityPresence
import app.logdate.client.sync.peer.state.PeerDocument
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.StateDocumentCodec
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.WearableStatusCodes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The watch's Play services side of the state document: publishes through [WearDataLayerClient], reads
 * the phone's document from [DataClient], and manages this watch's dynamic capabilities.
 */
class WatchPeerLink(
    private val dataLayerClient: WearDataLayerClient,
    private val dataClient: DataClient,
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
) : StateDocumentChannel,
    LocalCapabilities,
    PeerCapabilityPresence {
    override suspend fun publish(document: StateDocument): Boolean {
        val data =
            try {
                StateDocumentCodec.toDataMap(document)
            } catch (e: IllegalArgumentException) {
                Napier.e("This watch's state document is too large to publish", e)
                return false
            }
        return dataLayerClient.putDataItem(WearPaths.STATE, data, urgent = true)
    }

    override suspend fun peerDocuments(): List<PeerDocument> {
        val localNode = localNodeId() ?: return emptyList()
        val buffer =
            try {
                dataClient.getDataItems(stateItems()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Could not read the phone's state document; it is read again when it next changes", e)
                return emptyList()
            }
        return try {
            buffer.mapNotNull { item ->
                val node = item.uri.host
                if (node == null || node == localNode) return@mapNotNull null
                val text = DataMapItem.fromDataItem(item).dataMap.getString(StateDocumentCodec.KEY)
                val decoded = if (text == null) Decoded.Malformed("The data item has no state document") else StateDocumentCodec.decode(text)
                PeerDocument(node, decoded)
            }
        } finally {
            buffer.release()
        }
    }

    override suspend fun add(capability: String): Boolean =
        changeCapability(capability, adding = true) { capabilityClient.addLocalCapability(capability).await() }

    override suspend fun remove(capability: String): Boolean =
        changeCapability(capability, adding = false) { capabilityClient.removeLocalCapability(capability).await() }

    override fun observe(capability: String): Flow<Boolean> =
        callbackFlow {
            val listener = CapabilityClient.OnCapabilityChangedListener { launch { send(isAdvertised(capability)) } }
            val listening =
                try {
                    capabilityClient.addListener(listener, capability).await()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Could not follow $capability; it is reported as of now only", e)
                    false
                }
            send(isAdvertised(capability))
            awaitClose {
                if (listening) capabilityClient.removeListener(listener, capability)
            }
        }.distinctUntilChanged()

    private suspend fun isAdvertised(capability: String): Boolean =
        try {
            capabilityClient
                .getCapability(capability, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not read whether the phone advertises $capability; reporting that it does not", e)
            false
        }

    private suspend fun changeCapability(
        capability: String,
        adding: Boolean,
        change: suspend () -> Unit,
    ): Boolean =
        try {
            change()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            val alreadyDone =
                e.statusCode == if (adding) WearableStatusCodes.DUPLICATE_CAPABILITY else WearableStatusCodes.UNKNOWN_CAPABILITY
            if (!alreadyDone) Napier.e("Could not ${if (adding) "add" else "remove"} the capability $capability", e)
            alreadyDone
        } catch (e: Exception) {
            Napier.e("Could not ${if (adding) "add" else "remove"} the capability $capability", e)
            false
        }

    private suspend fun localNodeId(): String? =
        try {
            nodeClient.localNode.await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not read this watch's Data Layer node; the phone's state is read when it next changes", e)
            null
        }

    private fun stateItems(): Uri =
        Uri
            .Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .authority("*")
            .path(WearPaths.STATE)
            .build()
}
```

`app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchStateMirror.kt`:

```kotlin
package app.logdate.wear.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.StateMerger
import app.logdate.client.sync.peer.state.WatchState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Keeps the watch's Labs switch the same as the phone's, and publishes the watch's own state.
 *
 * The switch is phone-owned: only a document the phone wrote, read through [StateMerger], can change
 * it, and the watch never writes it back. While the switch is on the watch advertises
 * [WearProtocol.WATCH_CAPABILITY_V2].
 */
class WatchStateMirror(
    private val featureFlags: FeatureFlagStore,
    private val channel: StateDocumentChannel,
    private val capabilities: LocalCapabilities,
    private val peerStates: PeerStateStore,
    private val merger: StateMerger,
    private val clock: Clock = Clock.System,
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            catchUpWithPhone()
            featureFlags.observe(FeatureFlag.WATCH_EXPERIENCE_V2).distinctUntilChanged().collect { enabled -> publish(enabled) }
        }

    /** Reads the phone's document, for a change made while this watch's app was not running. */
    suspend fun catchUpWithPhone() {
        channel.peerDocuments().forEach { peer -> onPhoneDocument(peer.nodeId, localNodeId = null, decoded = peer.decoded) }
    }

    suspend fun onPhoneDocument(
        sourceNodeId: String,
        localNodeId: String?,
        decoded: Decoded<StateDocument>,
    ) {
        if (sourceNodeId == localNodeId) return
        val document = peerStates.accept(sourceNodeId, decoded, PeerRole.PHONE) ?: return
        val enabled = merger.merge(phone = document, watch = null).phone?.flags?.get(FeatureFlag.WATCH_EXPERIENCE_V2.key) ?: return
        if (featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) == enabled) return
        featureFlags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        Napier.i("The redesigned watch app is now ${if (enabled) "on" else "off"}, as set in Labs on the phone")
    }

    private suspend fun publish(enabled: Boolean) {
        val document = StateDocument(role = PeerRole.WATCH, writtenAt = clock.now().toEpochMilliseconds(), watch = WatchState())
        if (!channel.publish(document)) {
            Napier.e("Could not publish this watch's state; the phone sees the old one until the next change or restart")
        }
        val capabilityUpdated =
            if (enabled) {
                capabilities.add(WearProtocol.WATCH_CAPABILITY_V2)
            } else {
                capabilities.remove(WearProtocol.WATCH_CAPABILITY_V2)
            }
        if (!capabilityUpdated) {
            Napier.e("Could not update ${WearProtocol.WATCH_CAPABILITY_V2}; the phone keeps the old protocol until the next change or restart")
        }
    }
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt`:

1. Add the imports `app.logdate.client.sync.datalayer.v2.WearPaths`, `app.logdate.client.sync.peer.state.StateDocumentCodec` and `app.logdate.wear.sync.v2.WatchStateMirror` in their lexicographic places.
2. In `onDataChanged`, in `when (event.path)`, add before `else -> syncEvents.add(event)`:

```kotlin
                    WearPaths.STATE -> mirrorPhoneState(event, localNodeId)
```

3. Add after `handleRemoteCameraCaptureResult`:

```kotlin
    private suspend fun mirrorPhoneState(
        event: SnapshotDataEvent,
        localNodeId: String?,
    ) {
        val sourceNodeId = event.sourceNodeId
        if (sourceNodeId == null) {
            Napier.w("A state document arrived without the node that wrote it; ignoring it")
            return
        }
        val koin = resolveKoin()
        if (koin == null) {
            Napier.e("Koin unavailable after $MAX_KOIN_RETRIES retries; the phone's state is read again when LogDate next starts")
            return
        }
        withContext(NonCancellable) {
            koin.get<WatchStateMirror>().onPhoneDocument(sourceNodeId, localNodeId, StateDocumentCodec.fromDataMap(event.data))
        }
    }
```

In `app/wear/src/main/AndroidManifest.xml`, inside the `.sync.WearDataLayerListenerService` `DATA_CHANGED` filter, after the `/logdate/camera` `<data>` element, add:

```xml
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:path="/logdate/state" />
```

and change the service comment's first sentence to `Data Layer listener: receives notes, journals, associations, remote camera updates and the phone's state document from the paired phone.`

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt`, after the `single { WatchSyncScheduler( … ) }` block, add:

```kotlin
        single { PeerStateStore() }
        single { StateMerger() }
        single {
            WatchPeerLink(
                dataLayerClient = get(),
                dataClient = Wearable.getDataClient(get<Context>()),
                capabilityClient = Wearable.getCapabilityClient(get<Context>()),
                nodeClient = Wearable.getNodeClient(get<Context>()),
            )
        } binds arrayOf(StateDocumentChannel::class, LocalCapabilities::class, PeerCapabilityPresence::class)
        single {
            WatchStateMirror(
                featureFlags = get(),
                channel = get(),
                capabilities = get(),
                peerStates = get(),
                merger = get(),
            )
        }
        single {
            WearProtocolGate(
                flagEnabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                peerHasV2Capability = get<PeerCapabilityPresence>().observe(WearProtocol.PHONE_CAPABILITY_V2),
                peerFeatures = get<PeerStateStore>().features(PeerRole.PHONE),
            )
        }
```

with the imports `app.logdate.client.datastore.featureflags.FeatureFlag`, `app.logdate.client.datastore.featureflags.FeatureFlagStore`, `app.logdate.client.sync.datalayer.v2.WearProtocol`, `app.logdate.client.sync.datalayer.v2.WearProtocolGate`, `app.logdate.client.sync.peer.state.LocalCapabilities`, `app.logdate.client.sync.peer.state.PeerCapabilityPresence`, `app.logdate.client.sync.peer.state.PeerRole`, `app.logdate.client.sync.peer.state.PeerStateStore`, `app.logdate.client.sync.peer.state.StateDocumentChannel`, `app.logdate.client.sync.peer.state.StateMerger`, `app.logdate.wear.sync.v2.WatchPeerLink` and `app.logdate.wear.sync.v2.WatchStateMirror`.

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, replace `startSync()` (lines 95-103) with:

```kotlin
    /**
     * Puts captures an older version dropped after repeated failures back in the outbox, wakes the
     * watch whenever something in it is due, and follows the phone's state.
     */
    private fun startSync() {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        val scope = koin.get<CoroutineScope>()
        koin.get<WatchStateMirror>().start(scope)
        scope.launch {
            requeueSetAsideCaptures(koin.get())
            koin.get<WatchSyncScheduler>().start(this)
        }
    }
```

with the import `app.logdate.wear.sync.v2.WatchStateMirror`.

- [ ] **Step 5: Run the unit tests again**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.v2.*' --console=plain
```
Expected: PASS, 9 mirror tests and 5 adapter tests.

- [ ] **Step 6: Run the manifest test on the managed watch**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearManifestFiltersTest
```
Expected: PASS, 3 tests. (Before the manifest edit in Step 4 this test fails with `com.google.android.gms.wearable.DATA_CHANGED /logdate/state expected:<[app.logdate.wear.sync.WearDataLayerListenerService]> but was:<[]>`; to see that, run it once between Step 1 and Step 4.)

- [ ] **Step 7: Make the Labs description true, test first**

In `client/feature/core/src/desktopTest/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsContentTest.kt` replace line 24 with:

```kotlin
private const val WATCH_DESCRIPTION = "The new watch app and its phone screens, on this phone and your watch."
```

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.labs.LabsSettingsContentTest' --console=plain
```
Expected: FAIL: `a flag shows its title and description under the unreleased features`, because no node shows the new sentence (the screen still shows "Applies to this phone only.").

In `client/feature/core/src/commonMain/composeResources/values/strings_labs.xml` replace line 9 with:

```xml
    <string name="labs_watch_experience_description">The new watch app and its phone screens, on this phone and your watch.</string>
```

The new copy has 70 characters against the old 69, inside the row's two-line clamp at every catalog width; no `descriptionMaxLines` option is added.

Run the same command again. Expected: PASS, 6 tests.

- [ ] **Step 8: Re-record the four Labs baselines and check nothing else moved**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && LOGDATE_REQUIRE_DESKTOP_SCREENSHOTS=true ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=labs-settings :app:compose-main:updateDesktopScreenshotTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain
```
Expected: BUILD SUCCESSFUL. Open `app/compose-main/src/desktopTest/reference/labs-settings--phone.png`, `labs-settings--phone-dark.png`, `labs-settings--phone-landscape.png` and `labs-settings--tablet.png`: each shows the new sentence in full, on at most two lines, with no ellipsis.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && LOGDATE_REQUIRE_DESKTOP_SCREENSHOTS=true ./gradlew -Dlogdate.desktopScreenshots.sceneFilter=settings :app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest' --console=plain
```
Expected: PASS for every settings scene.

Run: `git status --short app/compose-main/src/desktopTest/reference app/android-main/src/screenshotTestDebug`
Expected: exactly the four `labs-settings--*.png` files, modified.

- [ ] **Step 9: Run the watch's and the feature module's checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug :client:feature:core:desktopTest :client:feature:core:ktlintCheck --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task04-watch-mirror.txt <<'EOF'
chore(wear): send the Labs watch switch to the watch

Turning on the redesigned watch app in Labs on the phone now turns it
on on the watch too, and turning it off turns it off. The watch takes
the switch from the phone's state document as soon as it changes, and
again when LogDate starts on the watch, so a change made while the
watch was away still arrives.

Only a document the phone wrote can change the switch; the watch's
own echo, another watch, and fields the phone does not own are
ignored. The watch publishes its own state and advertises version 2
only while the switch is on. Labs now says the switch applies to this
phone and the watch.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchPeerLink.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchStateMirror.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt app/wear/src/main/AndroidManifest.xml app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchStateMirrorTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchPeerLinkTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt client/feature/core/src/commonMain/composeResources/values/strings_labs.xml client/feature/core/src/desktopTest/kotlin/app/logdate/feature/core/settings/ui/labs/LabsSettingsContentTest.kt app/compose-main/src/desktopTest/reference/labs-settings--phone.png app/compose-main/src/desktopTest/reference/labs-settings--phone-dark.png app/compose-main/src/desktopTest/reference/labs-settings--phone-landscape.png app/compose-main/src/desktopTest/reference/labs-settings--tablet.png && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task04-watch-mirror.txt
```

---

### Task 5: Add tables for marked moments and watch delivery (C3)

**Files:**
- Create: `client/database/src/commonMain/kotlin/app/logdate/client/database/entities/moments/MarkedMomentEntity.kt`
- Create: `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/moments/MarkedMomentDao.kt`
- Create: `client/database/src/commonMain/kotlin/app/logdate/client/database/entities/sync/PeerDeliveryEntity.kt`
- Create: `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/PeerDeliveryDao.kt`
- Create: `client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_50_51.kt`
- Create (generated): `client/database/schemas/app.logdate.client.database.LogDateDatabase/51.json`
- Modify: `client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt:184-188, 208-215` (entities, version), `:266-268` (DAOs), `:388-389` (migrations) and imports
- Modify: `client/database/src/commonMain/kotlin/app/logdate/client/database/DaosModule.kt:30`
- Modify: `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/SyncMetadataDao.kt:176-181` (append before the closing brace)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/SyncMetadataService.kt:150-157` (`EntityType`)
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/sync/SyncStatusSheet.kt:527`
- Test: `client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncTablesMigrationJvmTest.kt`
- Test: `client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncDaoJvmTest.kt`
- Test: `client/database/src/jvmTest/kotlin/app/logdate/client/database/ExportCoverageTest.kt:44-87`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/metadata/DatabaseSyncMetadataServiceTest.kt` (fake DAO at :213; one new test)

**Interfaces:**
- Consumes: `PendingUploadEntity`, `SyncCursorEntity`, `SyncMetadataDao.getPending/insertPending/deletePending/getCursor/upsertCursor/deleteCursorsForOrigin` (`SyncMetadataDao.kt:20-110`), `UuidConverter`, `TimestampConverter`.
- Produces:
  - `data class MarkedMomentEntity(id: Uuid, capturedAt: Instant, timeZoneId: String, latitude: Double? = null, longitude: Double? = null, locationAccuracy: Float? = null, heartRateBpm: Int? = null, state: String, caption: String = "", resolvedNoteIds: String = "", originDeviceId: String, lastUpdated: Instant, syncVersion: Long = 0, deletedAt: Instant? = null)`; `object MarkedMomentState { OPEN, DISMISSED, FILLED }`
  - `interface MarkedMomentDao { suspend fun upsert(moment: MarkedMomentEntity); suspend fun getById(id: Uuid): MarkedMomentEntity?; fun observeByState(state: String): Flow<List<MarkedMomentEntity>> }`
  - `data class PeerDeliveryEntity(peerScope: String, direction: String, entityType: String, entityId: String, contentVersion: Long, state: String, attempts: Int = 0, lastAttemptAt: Long? = null, nextAttemptAt: Long? = null, lastCloudAttemptVersion: Long? = null, stateChangedAt: Long, holdReason: String? = null)`
  - `interface PeerDeliveryDao { suspend fun upsert(entry: PeerDeliveryEntity); suspend fun get(peerScope: String, direction: String, entityType: String, entityId: String): PeerDeliveryEntity?; suspend fun due(peerScope: String, direction: String, now: Long, limit: Int): List<PeerDeliveryEntity>; suspend fun delete(peerScope: String, direction: String, entityType: String, entityId: String) }`
  - `SyncMetadataDao.getPendingForOrigin(ownerId, serverOrigin): List<PendingUploadEntity>`, `getCursorsForOrigin(ownerId, serverOrigin): List<SyncCursorEntity>`, `@Transaction reassignPending(oldOwner, oldOrigin, newOwner, newOrigin): Int` (rows left behind), `@Transaction reassignCursors(oldOwner, oldOrigin, newOwner, newOrigin)`
  - `LogDateDatabase.markedMomentDao()`, `LogDateDatabase.peerDeliveryDao()`, `MIGRATION_50_51`, Room version 51
  - `EntityType.MOMENT`

- [ ] **Step 1: Write the failing migration, DAO and coverage tests**

Create `client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncTablesMigrationJvmTest.kt`:

```kotlin
package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_50_51
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates migration 51, which adds marked moments and the watch delivery ledger. */
class WatchSyncTablesMigrationJvmTest {
    private val databaseFile = Files.createTempFile("logdate-migration", ".db")

    private val helper =
        MigrationTestHelper(
            Path.of("schemas"),
            databaseFile,
            BundledSQLiteDriver(),
            LogDateDatabase::class,
            { LogDateDatabaseConstructor.initialize() },
            emptyList(),
        )

    @AfterTest
    fun tearDown() {
        Files.deleteIfExists(databaseFile)
    }

    @Test
    fun `room adds the marked moment and watch delivery tables and keeps version 50 data`() {
        helper.createDatabase(50).use { connection ->
            connection.execSQL(
                "INSERT INTO pending_uploads (ownerId, serverOrigin, entityType, entityId, operation, createdAt, retryCount, operationId) " +
                    "VALUES ('owner', 'server', 'NOTE', 'note-1', 'CREATE', 10, 2, 'operation-1')",
            )
            connection.execSQL(
                "INSERT INTO sync_cursors (ownerId, serverOrigin, entityType, lastSyncTimestamp) VALUES ('owner', 'server', 'NOTE', 500)",
            )
        }

        helper.runMigrationsAndValidate(51, listOf(MIGRATION_50_51)).use { connection ->
            connection.prepare("SELECT operation, retryCount, operationId FROM pending_uploads WHERE entityId = 'note-1'").use { row ->
                assertTrue(row.step(), "the queued upload is gone after the migration")
                assertEquals("CREATE", row.getText(0))
                assertEquals(2L, row.getLong(1))
                assertEquals("operation-1", row.getText(2))
            }
            connection.prepare("SELECT lastSyncTimestamp FROM sync_cursors WHERE entityType = 'NOTE'").use { row ->
                assertTrue(row.step(), "the download cursor is gone after the migration")
                assertEquals(500L, row.getLong(0))
            }
            listOf("marked_moments", "peer_delivery").forEach { table ->
                connection.prepare("SELECT COUNT(*) FROM $table").use { row ->
                    assertTrue(row.step())
                    assertEquals(0L, row.getLong(0), table)
                }
            }
        }
    }

    @Test
    fun `the new tables take the rows their entities describe`() {
        helper.createDatabase(50).close()

        helper.runMigrationsAndValidate(51, listOf(MIGRATION_50_51)).use { connection ->
            connection.execSQL(
                "INSERT INTO marked_moments " +
                    "(id, capturedAt, timeZoneId, state, caption, resolvedNoteIds, originDeviceId, lastUpdated, syncVersion) " +
                    "VALUES ('550e8400-e29b-41d4-a716-446655440000', 1, 'Europe/Paris', 'OPEN', '', '', 'watch', 1, 0)",
            )
            connection.execSQL(
                "INSERT INTO peer_delivery (peerScope, direction, entityType, entityId, contentVersion, state, attempts, stateChangedAt) " +
                    "VALUES ('phone', 'TO_PHONE', 'NOTE', 'note-1', 5, 'PENDING', 0, 1)",
            )
            listOf("marked_moments", "peer_delivery").forEach { table ->
                connection.prepare("SELECT COUNT(*) FROM $table").use { row ->
                    assertTrue(row.step())
                    assertEquals(1L, row.getLong(0), table)
                }
            }
        }
    }
}
```

Create `client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncDaoJvmTest.kt`:

```kotlin
package app.logdate.client.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests the marked moment and watch delivery DAOs and moving the outbox to a new owner. */
class WatchSyncDaoJvmTest {
    private val database = Room.inMemoryDatabaseBuilder<LogDateDatabase>().setDriver(BundledSQLiteDriver()).build()

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a marked moment reads back by id and by state`() =
        runTest {
            val dao = database.markedMomentDao()
            val open = moment(MarkedMomentState.OPEN, capturedAt = 2)
            val filled = moment(MarkedMomentState.FILLED, capturedAt = 3)
            dao.upsert(open)
            dao.upsert(filled)

            assertEquals(open, dao.getById(open.id))
            assertEquals(listOf(open), dao.observeByState(MarkedMomentState.OPEN).first())
        }

    @Test
    fun `a deleted marked moment is not listed`() =
        runTest {
            val dao = database.markedMomentDao()
            dao.upsert(moment(MarkedMomentState.OPEN, capturedAt = 2).copy(deletedAt = Instant.fromEpochMilliseconds(9)))

            assertEquals(emptyList(), dao.observeByState(MarkedMomentState.OPEN).first())
        }

    @Test
    fun `a delivery entry reads back and is due only once its time comes`() =
        runTest {
            val dao = database.peerDeliveryDao()
            val due = delivery("a", nextAttemptAt = 100)
            val later = delivery("b", nextAttemptAt = 300)
            val settled = delivery("c", nextAttemptAt = null)
            listOf(due, later, settled).forEach { dao.upsert(it) }

            assertEquals(due, dao.get("phone", "TO_PHONE", "NOTE", "a"))
            assertEquals(listOf(due), dao.due("phone", "TO_PHONE", now = 200, limit = 10))

            dao.delete("phone", "TO_PHONE", "NOTE", "a")

            assertNull(dao.get("phone", "TO_PHONE", "NOTE", "a"))
        }

    @Test
    fun `queued uploads move to the new owner with their operation`() =
        runTest {
            val dao = database.syncMetadataDao()
            dao.insertPending(pending("local", "", "note-1", "CREATE", operationId = "op-1", retryCount = 1))
            dao.insertPending(pending("local", "", "note-2", "DELETE", operationId = "op-2"))
            dao.insertPending(pending("account", CLOUD, "note-2", "UPDATE", operationId = "op-3"))
            dao.insertPending(pending("other", "", "note-9", "CREATE", operationId = "op-9"))

            val leftBehind = dao.reassignPending("local", "", "account", CLOUD)

            assertEquals(1, leftBehind)
            assertEquals(
                pending("account", CLOUD, "note-1", "CREATE", operationId = "op-1", retryCount = 1),
                dao.getPending("account", CLOUD, "NOTE", "note-1"),
            )
            assertNull(dao.getPending("local", "", "NOTE", "note-1"))
            assertEquals("op-3", dao.getPending("account", CLOUD, "NOTE", "note-2")?.operationId)
            assertEquals("op-2", dao.getPending("local", "", "NOTE", "note-2")?.operationId)
            assertEquals("op-9", dao.getPending("other", "", "NOTE", "note-9")?.operationId)
        }

    @Test
    fun `cursors move to the new owner keeping the earlier of two`() =
        runTest {
            val dao = database.syncMetadataDao()
            dao.upsertCursor(SyncCursorEntity("local", "", "NOTE", 500))
            dao.upsertCursor(SyncCursorEntity("local", "", "JOURNAL", 700))
            dao.upsertCursor(SyncCursorEntity("account", CLOUD, "JOURNAL", 600))

            dao.reassignCursors("local", "", "account", CLOUD)

            assertEquals(500L, dao.getCursor("account", CLOUD, "NOTE")?.lastSyncTimestamp)
            assertEquals(600L, dao.getCursor("account", CLOUD, "JOURNAL")?.lastSyncTimestamp)
            assertNull(dao.getCursor("local", "", "NOTE"))
            assertNull(dao.getCursor("local", "", "JOURNAL"))
        }

    @Test
    fun `reassigning a scope to itself changes nothing`() =
        runTest {
            val dao = database.syncMetadataDao()
            dao.insertPending(pending("local", "", "note-1", "CREATE", operationId = "op-1"))
            dao.upsertCursor(SyncCursorEntity("local", "", "NOTE", 500))

            assertEquals(0, dao.reassignPending("local", "", "local", ""))
            dao.reassignCursors("local", "", "local", "")

            assertEquals("op-1", dao.getPending("local", "", "NOTE", "note-1")?.operationId)
            assertEquals(500L, dao.getCursor("local", "", "NOTE")?.lastSyncTimestamp)
        }

    private fun moment(
        state: String,
        capturedAt: Long,
    ) = MarkedMomentEntity(
        id = Uuid.random(),
        capturedAt = Instant.fromEpochMilliseconds(capturedAt),
        timeZoneId = "Europe/Paris",
        state = state,
        originDeviceId = "watch",
        lastUpdated = Instant.fromEpochMilliseconds(capturedAt),
    )

    private fun delivery(
        entityId: String,
        nextAttemptAt: Long?,
    ) = PeerDeliveryEntity(
        peerScope = "phone",
        direction = "TO_PHONE",
        entityType = "NOTE",
        entityId = entityId,
        contentVersion = 5,
        state = "PENDING",
        nextAttemptAt = nextAttemptAt,
        stateChangedAt = 1,
    )

    private fun pending(
        ownerId: String,
        serverOrigin: String,
        entityId: String,
        operation: String,
        operationId: String,
        retryCount: Int = 0,
    ) = PendingUploadEntity(
        ownerId = ownerId,
        serverOrigin = serverOrigin,
        entityType = "NOTE",
        entityId = entityId,
        operation = operation,
        createdAt = 10,
        retryCount = retryCount,
        operationId = operationId,
    )

    private companion object {
        const val CLOUD = "https://cloud.example"
    }
}
```

In `client/database/src/jvmTest/kotlin/app/logdate/client/database/ExportCoverageTest.kt`, add after `"sync_download_checkpoints" to bookkeeping(…),`:

```kotlin
            "peer_delivery" to bookkeeping("Where each record stands between the phone and the watch."),
            "marked_moments" to planned("Moments marked on the watch; the export covers them once marked moments ship."),
```

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/metadata/DatabaseSyncMetadataServiceTest.kt`, add before `private fun service(`:

```kotlin
    @Test
    fun `a marked moment can wait in the outbox`() =
        runTest {
            val metadata = service(InMemorySyncMetadataDao(), "owner", "https://first.example")

            metadata.enqueuePending("moment", EntityType.MOMENT, PendingOperation.CREATE)

            assertEquals(PendingOperation.CREATE, metadata.getPendingUploads(EntityType.MOMENT).single().operation)
        }
```

and, in `InMemorySyncMetadataDao`, after `override suspend fun getAllPending(): List<PendingUploadEntity> = pendingRows.toList()`:

```kotlin
        override suspend fun getPendingForOrigin(
            ownerId: String,
            serverOrigin: String,
        ): List<PendingUploadEntity> = pendingRows.filter { it.ownerId == ownerId && it.serverOrigin == serverOrigin }

        override suspend fun getCursorsForOrigin(
            ownerId: String,
            serverOrigin: String,
        ): List<SyncCursorEntity> = cursors.filter { it.ownerId == ownerId && it.serverOrigin == serverOrigin }
```

- [ ] **Step 2: Add the entities, DAOs, an empty migration and the version bump**

Create the four entity and DAO files and add `getPendingForOrigin`, `getCursorsForOrigin`, `reassignPending` and `reassignCursors` exactly as in Step 4, except that `reassignPending` returns `0` and `reassignCursors` does nothing. Register both entities, both DAOs and the migration in `LogDateDatabase` and `DaosModule` and set `version = 51` exactly as in Step 4. Create `MIGRATION_50_51.kt` with an empty `migrate` body. Add `MOMENT` to `EntityType` and `EntityType.MOMENT` to the `when` in `SyncStatusSheet.kt` exactly as in Step 4.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:kspKotlinJvm --console=plain
```
Expected: BUILD SUCCESSFUL and a new `client/database/schemas/app.logdate.client.database.LogDateDatabase/51.json` whose `entities` include `marked_moments` and `peer_delivery`.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:jvmTest --tests 'app.logdate.client.database.WatchSyncTablesMigrationJvmTest' --tests 'app.logdate.client.database.WatchSyncDaoJvmTest' --tests 'app.logdate.client.database.ExportCoverageTest' --console=plain
```
Expected: FAIL: both `WatchSyncTablesMigrationJvmTest` tests with `IllegalStateException: Migration didn't properly handle: marked_moments` (or `peer_delivery`), `queued uploads move to the new owner with their operation` (`expected:<1> but was:<0>`), and `cursors move to the new owner keeping the earlier of two`. The marked moment and delivery DAO tests, `reassigning a scope to itself changes nothing` and `ExportCoverageTest` pass.

- [ ] **Step 4: Implement the tables, the migration and the reassignment**

`client/database/src/commonMain/kotlin/app/logdate/client/database/entities/moments/MarkedMomentEntity.kt`:

```kotlin
package app.logdate.client.database.entities.moments

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A moment someone marked on the watch to fill in later.
 *
 * A separate record rather than a note type: older phones read notes with a closed set of types, and
 * would drop a mark and never acknowledge it. [state] only moves forward (OPEN, then DISMISSED or
 * FILLED; FILLED wins), and [resolvedNoteIds] only grows.
 *
 * @property caption Plain text here; encrypted before it leaves the device.
 * @property resolvedNoteIds The ids of the notes that filled this mark, comma-separated; empty when none.
 */
@Entity(tableName = "marked_moments")
data class MarkedMomentEntity(
    @PrimaryKey
    val id: Uuid,
    val capturedAt: Instant,
    val timeZoneId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val heartRateBpm: Int? = null,
    val state: String,
    val caption: String = "",
    val resolvedNoteIds: String = "",
    val originDeviceId: String,
    val lastUpdated: Instant,
    val syncVersion: Long = 0,
    val deletedAt: Instant? = null,
)

/** The stored values of [MarkedMomentEntity.state]. */
object MarkedMomentState {
    const val OPEN = "OPEN"
    const val DISMISSED = "DISMISSED"
    const val FILLED = "FILLED"
}
```

`client/database/src/commonMain/kotlin/app/logdate/client/database/dao/moments/MarkedMomentDao.kt`:

```kotlin
package app.logdate.client.database.dao.moments

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface MarkedMomentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(moment: MarkedMomentEntity)

    @Query("SELECT * FROM marked_moments WHERE id = :id")
    suspend fun getById(id: Uuid): MarkedMomentEntity?

    @Query("SELECT * FROM marked_moments WHERE state = :state AND deletedAt IS NULL ORDER BY capturedAt DESC")
    fun observeByState(state: String): Flow<List<MarkedMomentEntity>>
}
```

`client/database/src/commonMain/kotlin/app/logdate/client/database/entities/sync/PeerDeliveryEntity.kt`:

```kotlin
package app.logdate.client.database.entities.sync

import androidx.room.Entity

/**
 * Where one record stands on its way between this device and its peer, so a delivery decision
 * survives the process being killed.
 *
 * @property peerScope The peer this row is about, such as the paired phone's link.
 * @property direction Which way the record travels, such as `TO_PHONE`.
 * @property contentVersion The record version this row describes; a newer edit starts over.
 * @property lastCloudAttemptVersion The version last uploaded straight to the cloud, so the same version
 *   is never sent there twice.
 * @property holdReason Why the record is waiting, when it is.
 */
@Entity(
    tableName = "peer_delivery",
    primaryKeys = ["peerScope", "direction", "entityType", "entityId"],
)
data class PeerDeliveryEntity(
    val peerScope: String,
    val direction: String,
    val entityType: String,
    val entityId: String,
    val contentVersion: Long,
    val state: String,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val nextAttemptAt: Long? = null,
    val lastCloudAttemptVersion: Long? = null,
    val stateChangedAt: Long,
    val holdReason: String? = null,
)
```

`client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/PeerDeliveryDao.kt`:

```kotlin
package app.logdate.client.database.dao.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.sync.PeerDeliveryEntity

@Dao
interface PeerDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PeerDeliveryEntity)

    @Query(
        "SELECT * FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction " +
            "AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun get(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ): PeerDeliveryEntity?

    @Query(
        "SELECT * FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction " +
            "AND nextAttemptAt IS NOT NULL AND nextAttemptAt <= :now ORDER BY nextAttemptAt ASC LIMIT :limit",
    )
    suspend fun due(
        peerScope: String,
        direction: String,
        now: Long,
        limit: Int,
    ): List<PeerDeliveryEntity>

    @Query(
        "DELETE FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction " +
            "AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun delete(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    )
}
```

`client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_50_51.kt` (each statement equals the `createSql` of its table in `51.json`, with `${TABLE_NAME}` filled in):

```kotlin
@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds marked moments and the watch delivery ledger after the durable download inbox of version 50. */
val MIGRATION_50_51 =
    object : Migration(50, 51) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `marked_moments` (
                    `id` TEXT NOT NULL,
                    `capturedAt` INTEGER NOT NULL,
                    `timeZoneId` TEXT NOT NULL,
                    `latitude` REAL,
                    `longitude` REAL,
                    `locationAccuracy` REAL,
                    `heartRateBpm` INTEGER,
                    `state` TEXT NOT NULL,
                    `caption` TEXT NOT NULL,
                    `resolvedNoteIds` TEXT NOT NULL,
                    `originDeviceId` TEXT NOT NULL,
                    `lastUpdated` INTEGER NOT NULL,
                    `syncVersion` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )""",
            )
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `peer_delivery` (
                    `peerScope` TEXT NOT NULL,
                    `direction` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `contentVersion` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `lastAttemptAt` INTEGER,
                    `nextAttemptAt` INTEGER,
                    `lastCloudAttemptVersion` INTEGER,
                    `stateChangedAt` INTEGER NOT NULL,
                    `holdReason` TEXT,
                    PRIMARY KEY(`peerScope`, `direction`, `entityType`, `entityId`)
                )""",
            )
        }
    }
```

In `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/SyncMetadataDao.kt`, add before the interface's closing brace (after `deleteLegacyPendingByType`):

```kotlin

    // --- Moving to a new owner ---

    @Query("SELECT * FROM pending_uploads WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin ORDER BY createdAt ASC")
    suspend fun getPendingForOrigin(
        ownerId: String,
        serverOrigin: String,
    ): List<PendingUploadEntity>

    @Query("SELECT * FROM sync_cursors WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin")
    suspend fun getCursorsForOrigin(
        ownerId: String,
        serverOrigin: String,
    ): List<SyncCursorEntity>

    /**
     * Moves every queued upload of one owner and server to another, keeping each operation's id and
     * retry count. A row whose entity the new scope already queues stays where it is, never merged or
     * dropped, and is counted in the result so the caller can decide about it.
     */
    @Transaction
    suspend fun reassignPending(
        oldOwner: String,
        oldOrigin: String,
        newOwner: String,
        newOrigin: String,
    ): Int {
        if (oldOwner == newOwner && oldOrigin == newOrigin) return 0
        var leftBehind = 0
        for (row in getPendingForOrigin(oldOwner, oldOrigin)) {
            if (getPending(newOwner, newOrigin, row.entityType, row.entityId) != null) {
                leftBehind++
                continue
            }
            insertPending(row.copy(ownerId = newOwner, serverOrigin = newOrigin))
            deletePending(oldOwner, oldOrigin, row.entityType, row.entityId)
        }
        return leftBehind
    }

    /**
     * Moves download cursors from one owner and server to another. Where the new scope already has a
     * cursor for the same entity type, the earlier of the two wins, so nothing is skipped.
     */
    @Transaction
    suspend fun reassignCursors(
        oldOwner: String,
        oldOrigin: String,
        newOwner: String,
        newOrigin: String,
    ) {
        if (oldOwner == newOwner && oldOrigin == newOrigin) return
        for (cursor in getCursorsForOrigin(oldOwner, oldOrigin)) {
            val existing = getCursor(newOwner, newOrigin, cursor.entityType)
            val timestamp = existing?.let { minOf(it.lastSyncTimestamp, cursor.lastSyncTimestamp) } ?: cursor.lastSyncTimestamp
            upsertCursor(SyncCursorEntity(newOwner, newOrigin, cursor.entityType, timestamp))
        }
        deleteCursorsForOrigin(oldOwner, oldOrigin)
    }
```

In `client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt`:
- After `DownloadCheckpointEntity::class,` (line 188) add `PeerDeliveryEntity::class,`.
- After `PersonResolutionDecisionEntity::class,` (line 213) add:

```kotlin
        // Moments marked on the watch
        MarkedMomentEntity::class,
```

- Change `version = 50,` (line 215) to `version = 51,`.
- After `abstract fun syncMetadataDao(): SyncMetadataDao` add:

```kotlin

    abstract fun peerDeliveryDao(): PeerDeliveryDao

    abstract fun markedMomentDao(): MarkedMomentDao
```

- After `MIGRATION_49_50,` in `getRoomDatabase` add `MIGRATION_50_51,`.
- Add the imports `app.logdate.client.database.dao.moments.MarkedMomentDao`, `app.logdate.client.database.dao.sync.PeerDeliveryDao`, `app.logdate.client.database.entities.moments.MarkedMomentEntity`, `app.logdate.client.database.entities.sync.PeerDeliveryEntity` and `app.logdate.client.database.migrations.MIGRATION_50_51` in their lexicographic places.

In `client/database/src/commonMain/kotlin/app/logdate/client/database/DaosModule.kt`, after `single { get<LogDateDatabase>().syncMetadataDao() }` add:

```kotlin
        single { get<LogDateDatabase>().peerDeliveryDao() }
        single { get<LogDateDatabase>().markedMomentDao() }
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/SyncMetadataService.kt`, replace the `EntityType` enum (lines 147-157) with:

```kotlin
/**
 * Types of entities that can be synced.
 *
 * [MOMENT] is a moment marked on the watch, stored apart from notes; nothing uploads it yet.
 */
enum class EntityType {
    JOURNAL,
    NOTE,
    ASSOCIATION,
    MEDIA,
    HEALTH,
    DRAFT,
    MOMENT,
}
```

In `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/sync/SyncStatusSheet.kt` line 527, change `EntityType.ASSOCIATION, EntityType.MEDIA, EntityType.HEALTH, null -> ""` to:

```kotlin
        EntityType.ASSOCIATION, EntityType.MEDIA, EntityType.HEALTH, EntityType.MOMENT, null -> ""
```

Regenerate the schema so `51.json` matches the final entities:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:kspKotlinJvm --console=plain
```

Open `51.json` and check that the `createSql` of `marked_moments` and `peer_delivery` list the same columns, types, `NOT NULL`s and primary keys as `MIGRATION_50_51`. If Room orders or spells a column differently, change the migration to match `51.json`, never the other way round.

- [ ] **Step 5: Run the tests again**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:jvmTest --tests 'app.logdate.client.database.WatchSyncTablesMigrationJvmTest' --tests 'app.logdate.client.database.WatchSyncDaoJvmTest' --tests 'app.logdate.client.database.ExportCoverageTest' :client:sync:desktopTest --tests 'app.logdate.client.sync.metadata.DatabaseSyncMetadataServiceTest' --console=plain
```
Expected: PASS: 2 migration tests, 6 DAO tests, 3 coverage tests, and every `DatabaseSyncMetadataServiceTest` test including `a marked moment can wait in the outbox`.

- [ ] **Step 6: Run every affected module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:jvmTest :client:database:compileKotlinIosSimulatorArm64 :client:database:compileTestKotlinIosSimulatorArm64 :client:database:ktlintCheck :client:database:detekt :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:feature:core:desktopTest :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:ktlintCheck :app:wear:compileDebugKotlin :app:android-main:compileDebugKotlin --console=plain
```
Expected: BUILD SUCCESSFUL. Every existing migration test (`DownloadInboxMigrationJvmTest`, `PendingOperationMigrationJvmTest`, …) still passes.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task05-room-51.txt <<'EOF'
chore(database): add tables for marked moments and watch delivery

Database version 51 adds two empty tables for the watch redesign:
marked moments, which are stored apart from notes so older phones never
meet a note type they cannot read, and a delivery ledger that records
where each record stands between the phone and the watch, so a
decision survives the app being closed.

The backup queue and download cursors can now move from one owner to
another in a single transaction, for when a watch is linked to an
account. A queued change the new owner already has stays where it was
and is counted, never merged or lost. Marked moments can wait in the
backup queue. Nothing writes to the new tables yet, and existing data
is untouched by the migration.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/database/src/commonMain/kotlin/app/logdate/client/database/entities/moments/MarkedMomentEntity.kt client/database/src/commonMain/kotlin/app/logdate/client/database/dao/moments/MarkedMomentDao.kt client/database/src/commonMain/kotlin/app/logdate/client/database/entities/sync/PeerDeliveryEntity.kt client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/PeerDeliveryDao.kt client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_50_51.kt client/database/schemas/app.logdate.client.database.LogDateDatabase/51.json client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt client/database/src/commonMain/kotlin/app/logdate/client/database/DaosModule.kt client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/SyncMetadataDao.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/SyncMetadataService.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/sync/SyncStatusSheet.kt client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncTablesMigrationJvmTest.kt client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncDaoJvmTest.kt client/database/src/jvmTest/kotlin/app/logdate/client/database/ExportCoverageTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/metadata/DatabaseSyncMetadataServiceTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task05-room-51.txt
```

---

### Task 6: Prepare identity keys for linked watches (C4)

**Files:**
- Create: `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyFingerprint.kt`
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyManager.kt:17-21` (constructor), `:109-110` (`setupNewIdentity`), after `:69` (`installAccountKey`), after `:262` (exceptions)
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/identity/CanonicalOwnerProvider.kt:35-37, 84-85`
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/models/DevicePlatform.kt:7`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/crypto/SyncPayloadCipher.kt:106-119`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/devices/DevicesViewModel.kt:135`
- Test: `client/device/src/androidHostTest/kotlin/app/logdate/client/device/crypto/IdentityKeyFingerprintTest.kt`
- Test: `client/device/src/commonTest/kotlin/app/logdate/client/device/crypto/IdentityKeyManagerTest.kt` (6 new tests)
- Test: `client/device/src/commonTest/kotlin/app/logdate/client/device/identity/CanonicalOwnerProviderTest.kt` (1 new test)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/crypto/SyncPayloadCipherTest.kt` (1 new test)
- Test: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/devices/DevicesViewModelTest.kt` (1 new test)

**Interfaces:**
- Consumes: `CryptoManager.hmacSha256(key, data)`, `SecureStorage.getBytes/putBytes/remove`, `KeyValueStorage.remove`.
- Produces: `object IdentityKeyFingerprint { fun of(identityKey: ByteArray, cryptoManager: CryptoManager): String }`; `enum class IdentityMintPolicy { ALLOWED, NEVER }`; `IdentityKeyManager(secureStorage, cryptoManager, backupStore = NoOpIdentityKeyBackupStore, mintPolicy: IdentityMintPolicy = IdentityMintPolicy.ALLOWED)`; `suspend fun IdentityKeyManager.installLinkedIdentityKey(key: ByteArray, expectedFingerprint: String)`; `class IdentityMintingNotAllowedException : IllegalStateException`; `class IdentityKeyFingerprintMismatchException : IllegalArgumentException`; `suspend fun CanonicalOwnerProvider.resetForDeviceWipe()`; `DevicePlatform.WEAR_OS`.

- [ ] **Step 1: Write the failing tests**

Create `client/device/src/androidHostTest/kotlin/app/logdate/client/device/crypto/IdentityKeyFingerprintTest.kt`:

```kotlin
package app.logdate.client.device.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins [IdentityKeyFingerprint] with a real HMAC-SHA256: every synced value already carries this
 * fingerprint, so its output can never change.
 */
class IdentityKeyFingerprintTest {
    private val cryptoManager = AndroidCryptoManager()

    @Test
    fun `the fingerprint is the first 8 bytes of the HMAC of its context in Base64`() {
        assertEquals("9+eXSPQZXdg=", IdentityKeyFingerprint.of(ByteArray(32) { 0x31 }, cryptoManager))
    }

    @Test
    fun `different keys have different fingerprints`() {
        assertNotEquals(
            IdentityKeyFingerprint.of(ByteArray(32) { 0x31 }, cryptoManager),
            IdentityKeyFingerprint.of(ByteArray(32) { 0x32 }, cryptoManager),
        )
    }
}
```

In `client/device/src/commonTest/kotlin/app/logdate/client/device/crypto/IdentityKeyManagerTest.kt`, add after `account key restore binds the identity to one account`:

```kotlin
    @Test
    fun `a device that never mints refuses to create a key`() =
        runTest {
            val linked = linkedManager()

            assertFailsWith<IdentityMintingNotAllowedException> { linked.ensureIdentityKey() }
            assertFailsWith<IdentityMintingNotAllowedException> { linked.setupNewIdentity() }
            assertFalse(linked.hasIdentityKey())
        }

    @Test
    fun `a linked key is installed when it matches its fingerprint`() =
        runTest {
            val linked = linkedManager()
            val key = ByteArray(32) { 0x31 }

            linked.installLinkedIdentityKey(key, IdentityKeyFingerprint.of(key, cryptoManager))

            assertTrue(linked.getIdentityKey().contentEquals(key))
            assertEquals(null, linked.getStoredRecoveryPhrase())
        }

    @Test
    fun `a linked key that does not match its fingerprint is refused`() =
        runTest {
            val linked = linkedManager()
            val key = ByteArray(32) { 0x31 }

            assertFailsWith<IdentityKeyFingerprintMismatchException> {
                linked.installLinkedIdentityKey(key, IdentityKeyFingerprint.of(ByteArray(32) { 0x32 }, cryptoManager))
            }
            assertFalse(linked.hasIdentityKey())
        }

    @Test
    fun `a linked key never replaces a different key`() =
        runTest {
            val linked = linkedManager()
            val key = ByteArray(32) { 0x31 }
            val other = ByteArray(32) { 0x32 }
            linked.installLinkedIdentityKey(key, IdentityKeyFingerprint.of(key, cryptoManager))

            assertFailsWith<IllegalStateException> { linked.installLinkedIdentityKey(other, IdentityKeyFingerprint.of(other, cryptoManager)) }
            assertTrue(linked.getIdentityKey().contentEquals(key))
        }

    @Test
    fun `installing the same linked key again is harmless`() =
        runTest {
            val linked = linkedManager()
            val key = ByteArray(32) { 0x31 }

            repeat(2) { linked.installLinkedIdentityKey(key, IdentityKeyFingerprint.of(key, cryptoManager)) }

            assertTrue(linked.getIdentityKey().contentEquals(key))
        }

    @Test
    fun `a linked key of the wrong length is refused`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { linkedManager().installLinkedIdentityKey(ByteArray(16), "unused") }
        }

    private fun linkedManager() = IdentityKeyManager(InMemorySecureStorage(), cryptoManager, mintPolicy = IdentityMintPolicy.NEVER)
```

In `client/device/src/commonTest/kotlin/app/logdate/client/device/identity/CanonicalOwnerProviderTest.kt`, add (and the import `kotlin.test.assertFalse`):

```kotlin
    @Test
    fun `a device wipe forgets the owner and its account binding`() =
        runTest {
            val provider = DefaultCanonicalOwnerProvider(InMemoryKeyValueStorage())
            val accountOwner = "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"
            provider.adoptRemoteOwnerIfUninitialized(accountOwner)

            provider.resetForDeviceWipe()

            assertFalse(provider.hasBoundOwner())
            assertNotEquals(accountOwner, provider.getCanonicalOwnerId())
        }
```

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/crypto/SyncPayloadCipherTest.kt`, add before `private suspend fun cipherFor(` (and the imports `app.logdate.client.device.crypto.IdentityKeyFingerprint`, `kotlinx.serialization.json.jsonObject` and `kotlinx.serialization.json.jsonPrimitive`):

```kotlin
    @Test
    fun `an envelope carries the identity key fingerprint`() =
        runTest {
            val cryptoManager = TestCryptoManager()
            val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), cryptoManager)
            identityKeyManager.recoverIdentity((1..12).map { "fingerprint-$it" })
            val contentEncryptionService = ContentEncryptionService(identityKeyManager, KeyDerivation(cryptoManager), cryptoManager)
            val cipher = SyncPayloadCipher(contentEncryptionService, identityKeyManager, cryptoManager)

            val envelope = Json.parseToJsonElement(cipher.encryptString("field", "hello").removePrefix("LDSE2:")).jsonObject

            assertEquals(
                IdentityKeyFingerprint.of(identityKeyManager.getIdentityKey(), cryptoManager),
                envelope.getValue("fp").jsonPrimitive.content,
            )
        }
```

In `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/devices/DevicesViewModelTest.kt`, add after `loadDevices should load devices`:

```kotlin
    @Test
    fun `a watch is listed as Wear OS`() =
        testScope.runTest {
            val watchId = Uuid.parse("523e4567-e89b-12d3-a456-426614174000")
            repository.addAssociatedDevice(deviceInfo(id = watchId, name = "Pixel Watch 3", platform = DevicePlatform.WEAR_OS))

            viewModel.loadDevices()
            advanceUntilIdle()

            assertEquals("Wear OS", viewModel.uiState.first().devices.single { it.id == watchId }.platformName)
        }
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create `IdentityKeyFingerprint.kt` with `fun of(...) = ""`. Add `IdentityMintPolicy`, the `mintPolicy` constructor parameter, both exception classes and `installLinkedIdentityKey` with an empty body to `IdentityKeyManager.kt`. Add `resetForDeviceWipe()` to `CanonicalOwnerProvider` with the default body from Step 4 and an empty override in `DefaultCanonicalOwnerProvider`. Add `WEAR_OS` to `DevicePlatform` and `DevicePlatform.WEAR_OS -> ""` to `DevicesViewModel.getPlatformName`.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:device:testAndroidHostTest --tests 'app.logdate.client.device.crypto.IdentityKeyFingerprintTest' :client:device:desktopTest --tests 'app.logdate.client.device.crypto.IdentityKeyManagerTest' --tests 'app.logdate.client.device.identity.CanonicalOwnerProviderTest' :client:sync:desktopTest --tests 'app.logdate.client.sync.crypto.SyncPayloadCipherTest' :client:feature:core:desktopTest --tests 'app.logdate.feature.core.settings.ui.devices.DevicesViewModelTest' --console=plain
```
Expected: FAIL: `the fingerprint is the first 8 bytes of the HMAC of its context in Base64` (`expected:<9+eXSPQZXdg=> but was:<>`), `different keys have different fingerprints`, `a device that never mints refuses to create a key`, `a linked key is installed when it matches its fingerprint`, `a linked key that does not match its fingerprint is refused`, `a linked key never replaces a different key`, `installing the same linked key again is harmless`, `a linked key of the wrong length is refused`, `a device wipe forgets the owner and its account binding`, `an envelope carries the identity key fingerprint` and `a watch is listed as Wear OS`.

- [ ] **Step 4: Implement**

`client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyFingerprint.kt`:

```kotlin
package app.logdate.client.device.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A short, non-reversible marker of an identity key: enough to tell two keys apart, never enough to
 * recover one. Every synced value carries it, and a linked device checks the key its phone sent
 * against it, so its output must never change.
 */
@OptIn(ExperimentalEncodingApi::class)
object IdentityKeyFingerprint {
    private val CONTEXT = "sync-payload-fingerprint".encodeToByteArray()
    private const val BYTES = 8

    fun of(
        identityKey: ByteArray,
        cryptoManager: CryptoManager,
    ): String = Base64.encode(cryptoManager.hmacSha256(identityKey, CONTEXT).copyOf(BYTES))
}
```

In `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyManager.kt`:

1. Replace the class header (lines 11-21) with:

```kotlin
/**
 * Manages the user's identity cryptographic key.
 *
 * The identity key is the root of all encryption for the user's data.
 * It's derived from a BIP-39 recovery phrase and stored securely in the device keystore.
 * A device with [IdentityMintPolicy.NEVER], a linked watch, never derives one of its own: it uses the
 * key its phone installs with [installLinkedIdentityKey].
 */
class IdentityKeyManager(
    private val secureStorage: SecureStorage,
    private val cryptoManager: CryptoManager,
    private val backupStore: IdentityKeyBackupStore = NoOpIdentityKeyBackupStore,
    private val mintPolicy: IdentityMintPolicy = IdentityMintPolicy.ALLOWED,
) {
```

2. After `installAccountKey` (ends at line 69) add:

```kotlin

    /**
     * Installs the identity key a phone sent while linking this device, after checking it against the
     * fingerprint sent with it. A device holding a different key refuses: it has to be wiped before it
     * can take another identity.
     */
    suspend fun installLinkedIdentityKey(
        key: ByteArray,
        expectedFingerprint: String,
    ) {
        require(key.size == 32) { "Invalid identity key length" }
        if (IdentityKeyFingerprint.of(key, cryptoManager) != expectedFingerprint) throw IdentityKeyFingerprintMismatchException()
        identityMutex.withLock {
            val existing = secureStorage.getBytes(KEY_IDENTITY_KEY)
            check(existing == null || existing.contentEquals(key)) { "A different identity key is already installed" }
            secureStorage.putBytes(KEY_IDENTITY_KEY, key)
            secureStorage.remove(KEY_RECOVERY_PHRASE)
            secureStorage.remove(KEY_RECOVERY_VERIFIED)
        }
    }
```

3. In `setupNewIdentity()`, replace `require(!hasIdentityKey()) { "Identity key already exists" }` with:

```kotlin
        if (mintPolicy == IdentityMintPolicy.NEVER) throw IdentityMintingNotAllowedException()
        require(!hasIdentityKey()) { "Identity key already exists" }
```

`ensureIdentityKey()` calls `setupNewIdentity()`, so it refuses too.

4. After `class IdentityKeyNotFoundException(…)` at the end of the file add:

```kotlin

/** Whether this device may derive an identity key of its own. */
enum class IdentityMintPolicy {
    ALLOWED,

    /** A linked watch: it only ever uses the key its phone installed. */
    NEVER,
}

class IdentityMintingNotAllowedException : IllegalStateException("This device never creates its own identity key; it is linked from a phone")

class IdentityKeyFingerprintMismatchException : IllegalArgumentException("The identity key does not match the fingerprint sent with it")
```

In `client/device/src/commonMain/kotlin/app/logdate/client/device/identity/CanonicalOwnerProvider.kt`:

1. After `adoptRemoteOwnerIfUninitialized` in the interface (line 35) add:

```kotlin

    /**
     * Forgets this installation's owner and its account binding, as part of erasing a device removed
     * from its account. The next [getCanonicalOwnerId] creates a new, unbound owner. A provider that
     * cannot forget its owner throws instead of pretending it did.
     */
    suspend fun resetForDeviceWipe() {
        throw UnsupportedOperationException("This owner provider cannot forget its owner")
    }
```

2. In `DefaultCanonicalOwnerProvider`, after `adoptRemoteOwnerIfUninitialized` add:

```kotlin

    override suspend fun resetForDeviceWipe() {
        mutex.withLock {
            storage.remove(CANONICAL_OWNER_ID_KEY)
            storage.remove(CANONICAL_OWNER_BOUND_KEY)
        }
    }
```

In `client/device/src/commonMain/kotlin/app/logdate/client/device/models/DevicePlatform.kt`, add after `ANDROID,`:

```kotlin
    WEAR_OS,
```

In `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/devices/DevicesViewModel.kt`, after `DevicePlatform.ANDROID -> "Android"` add:

```kotlin
            DevicePlatform.WEAR_OS -> "Wear OS"
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/crypto/SyncPayloadCipher.kt`, replace `currentKeyFingerprint()` and the companion object (lines 106-119) with:

```kotlin
    private suspend fun currentKeyFingerprint(): String = IdentityKeyFingerprint.of(identityKeyManager.getIdentityKey(), cryptoManager)

    private companion object {
        val ENVELOPE_PREFIX = Regex("^LDSE[0-9]+:")
    }
```

remove the now-unused imports `kotlin.io.encoding.Base64` and `kotlin.io.encoding.ExperimentalEncodingApi` and the class's `@OptIn(ExperimentalEncodingApi::class)`, and add `import app.logdate.client.device.crypto.IdentityKeyFingerprint`.

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS, including every existing test in the five classes.

- [ ] **Step 6: Run every affected module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:device:desktopTest :client:device:testAndroidHostTest :client:device:compileKotlinIosSimulatorArm64 :client:device:compileTestKotlinIosSimulatorArm64 :client:device:ktlintCheck :client:device:detekt :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:feature:core:desktopTest :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:ktlintCheck --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task06-linked-identity.txt <<'EOF'
chore(device): prepare identity keys for linked watches

A watch must never create an identity key of its own: anything it
encrypted with one could never be read on the phone. A device can now
be set never to create one, and it accepts the key its phone sends only
when the key matches the fingerprint sent with it, and only when it
holds no different key.

The key fingerprint that every synced value carries moves out of the
sync cipher so the link can check against it; a test pins its output
with a real HMAC, so nothing already synced changes. A wiped device can
forget its owner and account binding, and Devices names a watch as
Wear OS. Nothing links a watch yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyFingerprint.kt client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyManager.kt client/device/src/commonMain/kotlin/app/logdate/client/device/identity/CanonicalOwnerProvider.kt client/device/src/commonMain/kotlin/app/logdate/client/device/models/DevicePlatform.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/crypto/SyncPayloadCipher.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/devices/DevicesViewModel.kt client/device/src/androidHostTest/kotlin/app/logdate/client/device/crypto/IdentityKeyFingerprintTest.kt client/device/src/commonTest/kotlin/app/logdate/client/device/crypto/IdentityKeyManagerTest.kt client/device/src/commonTest/kotlin/app/logdate/client/device/identity/CanonicalOwnerProviderTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/crypto/SyncPayloadCipherTest.kt client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/devices/DevicesViewModelTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task06-linked-identity.txt
```

---

### Task 7: Add the key exchange for linking a watch (C4)

**Files:**
- Create: `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/link/LinkHandshake.kt`
- Create: `client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/link/BouncyCastleLinkHandshake.kt`
- Test: `client/device/src/commonTest/kotlin/app/logdate/client/device/crypto/link/LinkTranscriptTest.kt`
- Test: `client/device/src/androidHostTest/kotlin/app/logdate/client/device/crypto/link/BouncyCastleLinkHandshakeTest.kt`

**Interfaces:**
- Consumes: `libs.bouncycastle.bcprov` on `:client:device` `androidMain` (`client/device/build.gradle.kts:69`), version 1.84 (`gradle/libs.versions.toml:81`).
- Produces: `class LinkKeyPair(privateKey: ByteArray, publicKey: ByteArray)`; `class LinkKeys(bundleKey: ByteArray, confirmKey: ByteArray, pairKey: ByteArray)`; `class SealedBytes(iv: ByteArray, ciphertext: ByteArray)`; `class LinkHandshakeException(message: String, cause: Throwable? = null) : Exception`; `class LinkTranscript(phoneNode, watchNode, phonePublicKey, watchPublicKey, phoneNonce, watchNonce, schema) { fun encode(): ByteArray }`; `object LinkLabels { TRANSCRIPT = "LDLINK1"; BUNDLE = "LDLINK1 bundle"; CONFIRM = "LDLINK1 confirm"; PAIR = "LDLINK1 pair" }`; `interface LinkHandshake { generateKeyPair(); newNonce(); transcriptHash(transcript); deriveKeys(ownPrivateKey, peerPublicKey, transcriptHash): LinkKeys; seal(bundleKey, plaintext, transcriptHash): SealedBytes; open(bundleKey, sealed, transcriptHash): ByteArray; confirmation(confirmKey, transcriptHash): ByteArray; verifyConfirmation(confirmKey, transcriptHash, mac): Boolean }`; `class BouncyCastleLinkHandshake(random: SecureRandom = SecureRandom()) : LinkHandshake { fun x25519(privateKey, publicKey): ByteArray; fun publicKeyOf(privateKey): ByteArray; fun hkdfSha256(ikm, salt, info, length): ByteArray }`.
- Derivations: `T = SHA-256(transcript.encode())`; `K_bundle = HKDF-SHA256(IKM = X25519 secret, salt = T, info = "LDLINK1 bundle", L = 32)`; `K_confirm` with `"LDLINK1 confirm"`; `K_pair` with `"LDLINK1 pair"`; the bundle is AES-256-GCM under `K_bundle` with a random 12-byte IV, 128-bit tag and `T` as associated data; the confirmation is `HMAC-SHA256(K_confirm, "LDLINK1 confirm" ‖ T)`.

- [ ] **Step 1: Write the failing tests**

Create `client/device/src/commonTest/kotlin/app/logdate/client/device/crypto/link/LinkTranscriptTest.kt`:

```kotlin
package app.logdate.client.device.crypto.link

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests the byte layout every link key is bound to.
 */
class LinkTranscriptTest {
    @Test
    fun `the transcript starts with its label and prefixes each field with its length`() {
        val encoded = transcript().encode()

        assertEquals(151, encoded.size)
        assertEquals("LDLINK1", encoded.copyOfRange(0, 7).decodeToString())
        assertContentEquals(byteArrayOf(0, 0, 0, 10), encoded.copyOfRange(7, 11))
        assertContentEquals(byteArrayOf(0, 0, 0, 1), encoded.copyOfRange(147, 151))
    }

    @Test
    fun `moving a byte between node names changes the transcript`() {
        val first = transcript(phoneNode = "ab", watchNode = "c").encode()
        val second = transcript(phoneNode = "a", watchNode = "bc").encode()

        assertFalse(first.contentEquals(second))
    }

    private fun transcript(
        phoneNode: String = "phone-node",
        watchNode: String = "watch-node",
    ) = LinkTranscript(
        phoneNode = phoneNode,
        watchNode = watchNode,
        phonePublicKey = ByteArray(32) { 1 },
        watchPublicKey = ByteArray(32) { 2 },
        phoneNonce = ByteArray(16) { 1 },
        watchNonce = ByteArray(16) { 2 },
        schema = 1,
    )
}
```

Create `client/device/src/androidHostTest/kotlin/app/logdate/client/device/crypto/link/BouncyCastleLinkHandshakeTest.kt`:

```kotlin
package app.logdate.client.device.crypto.link

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [BouncyCastleLinkHandshake] against the RFC 7748 and RFC 5869 vectors, pins the keys and
 * confirmation derived from them, and checks that any change to the ciphertext, IV or transcript fails.
 */
class BouncyCastleLinkHandshakeTest {
    private val handshake = BouncyCastleLinkHandshake()

    @Test
    fun `x25519 matches RFC 7748 section 6 point 1`() {
        assertEquals(ALICE_PUBLIC, handshake.publicKeyOf(ALICE_PRIVATE.hex()).toHex())
        assertEquals(BOB_PUBLIC, handshake.publicKeyOf(BOB_PRIVATE.hex()).toHex())
        assertEquals(SHARED_SECRET, handshake.x25519(ALICE_PRIVATE.hex(), BOB_PUBLIC.hex()).toHex())
        assertEquals(SHARED_SECRET, handshake.x25519(BOB_PRIVATE.hex(), ALICE_PUBLIC.hex()).toHex())
    }

    @Test
    fun `hkdf matches RFC 5869 test case 1`() {
        val okm =
            handshake.hkdfSha256(
                ikm = ByteArray(22) { 0x0b },
                salt = "000102030405060708090a0b0c".hex(),
                info = "f0f1f2f3f4f5f6f7f8f9".hex(),
                length = 42,
            )

        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.toHex())
    }

    @Test
    fun `the transcript hash is pinned`() {
        assertEquals(TRANSCRIPT_HASH, handshake.transcriptHash(transcript()).toHex())
    }

    @Test
    fun `both devices derive the same pinned keys`() {
        val phone = handshake.deriveKeys(ALICE_PRIVATE.hex(), BOB_PUBLIC.hex(), TRANSCRIPT_HASH.hex())
        val watch = handshake.deriveKeys(BOB_PRIVATE.hex(), ALICE_PUBLIC.hex(), TRANSCRIPT_HASH.hex())

        assertEquals(BUNDLE_KEY, phone.bundleKey.toHex())
        assertEquals(CONFIRM_KEY, phone.confirmKey.toHex())
        assertEquals(PAIR_KEY, phone.pairKey.toHex())
        assertContentEquals(phone.bundleKey, watch.bundleKey)
        assertContentEquals(phone.confirmKey, watch.confirmKey)
        assertContentEquals(phone.pairKey, watch.pairKey)
    }

    @Test
    fun `fresh key pairs agree`() {
        val phone = handshake.generateKeyPair()
        val watch = handshake.generateKeyPair()

        val phoneKeys = handshake.deriveKeys(phone.privateKey, watch.publicKey, TRANSCRIPT_HASH.hex())
        val watchKeys = handshake.deriveKeys(watch.privateKey, phone.publicKey, TRANSCRIPT_HASH.hex())

        assertContentEquals(phoneKeys.bundleKey, watchKeys.bundleKey)
    }

    @Test
    fun `a sealed bundle opens with the same key and transcript`() {
        val sealed = handshake.seal(BUNDLE_KEY.hex(), PLAINTEXT, TRANSCRIPT_HASH.hex())

        assertEquals(12, sealed.iv.size)
        assertContentEquals(PLAINTEXT, handshake.open(BUNDLE_KEY.hex(), sealed, TRANSCRIPT_HASH.hex()))
    }

    @Test
    fun `a changed ciphertext fails`() {
        val sealed = handshake.seal(BUNDLE_KEY.hex(), PLAINTEXT, TRANSCRIPT_HASH.hex())
        val tampered = sealed.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertFailsWith<LinkHandshakeException> { handshake.open(BUNDLE_KEY.hex(), SealedBytes(sealed.iv, tampered), TRANSCRIPT_HASH.hex()) }
    }

    @Test
    fun `a changed iv fails`() {
        val sealed = handshake.seal(BUNDLE_KEY.hex(), PLAINTEXT, TRANSCRIPT_HASH.hex())
        val tampered = sealed.iv.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertFailsWith<LinkHandshakeException> { handshake.open(BUNDLE_KEY.hex(), SealedBytes(tampered, sealed.ciphertext), TRANSCRIPT_HASH.hex()) }
    }

    @Test
    fun `a different transcript fails as associated data`() {
        val sealed = handshake.seal(BUNDLE_KEY.hex(), PLAINTEXT, TRANSCRIPT_HASH.hex())

        assertFailsWith<LinkHandshakeException> { handshake.open(BUNDLE_KEY.hex(), sealed, ByteArray(32)) }
    }

    @Test
    fun `keys from a different transcript cannot open the bundle`() {
        val sealed = handshake.seal(BUNDLE_KEY.hex(), PLAINTEXT, TRANSCRIPT_HASH.hex())
        val otherKeys = handshake.deriveKeys(ALICE_PRIVATE.hex(), BOB_PUBLIC.hex(), ByteArray(32) { 7 })

        assertFailsWith<LinkHandshakeException> { handshake.open(otherKeys.bundleKey, sealed, TRANSCRIPT_HASH.hex()) }
    }

    @Test
    fun `an all zero peer key is rejected`() {
        assertFailsWith<LinkHandshakeException> { handshake.deriveKeys(ALICE_PRIVATE.hex(), ByteArray(32), TRANSCRIPT_HASH.hex()) }
    }

    @Test
    fun `keys of the wrong length are rejected`() {
        assertFailsWith<LinkHandshakeException> { handshake.x25519(ByteArray(31), BOB_PUBLIC.hex()) }
        assertFailsWith<LinkHandshakeException> { handshake.deriveKeys(ALICE_PRIVATE.hex(), BOB_PUBLIC.hex(), ByteArray(31)) }
        assertFailsWith<LinkHandshakeException> { handshake.seal(ByteArray(16), PLAINTEXT, TRANSCRIPT_HASH.hex()) }
    }

    @Test
    fun `the confirmation is pinned and verifies only for its transcript`() {
        val mac = handshake.confirmation(CONFIRM_KEY.hex(), TRANSCRIPT_HASH.hex())

        assertEquals(CONFIRMATION, mac.toHex())
        assertTrue(handshake.verifyConfirmation(CONFIRM_KEY.hex(), TRANSCRIPT_HASH.hex(), mac))
        assertFalse(handshake.verifyConfirmation(CONFIRM_KEY.hex(), ByteArray(32), mac))
    }

    @Test
    fun `nonces are 16 fresh bytes`() {
        val first = handshake.newNonce()

        assertEquals(16, first.size)
        assertFalse(first.contentEquals(handshake.newNonce()))
    }

    private fun transcript() =
        LinkTranscript(
            phoneNode = "phone-node",
            watchNode = "watch-node",
            phonePublicKey = ALICE_PUBLIC.hex(),
            watchPublicKey = BOB_PUBLIC.hex(),
            phoneNonce = ByteArray(16) { 1 },
            watchNonce = ByteArray(16) { 2 },
            schema = 1,
        )

    private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        const val ALICE_PRIVATE = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        const val ALICE_PUBLIC = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        const val BOB_PRIVATE = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
        const val BOB_PUBLIC = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
        const val SHARED_SECRET = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"

        // SHA-256 of the transcript above, computed independently with Python's hashlib.
        const val TRANSCRIPT_HASH = "0fefc1ebb2c354d393e73cabb5100755611c933cc869adb5665acfb62ff8e25c"

        // HKDF-SHA256(SHARED_SECRET, salt = TRANSCRIPT_HASH, info = label, L = 32), computed with Python's hmac.
        const val BUNDLE_KEY = "00d7bdd46947deaaa84f43334524bc0a26a66b6e1dc505bc7df316acf17a03eb"
        const val CONFIRM_KEY = "02e97ac2dbe0e691c065fca1b0f997a93a404b6cd55e5ca69401cc73addea0b4"
        const val PAIR_KEY = "34be0cd20b076e34efe276e9ac6df863783bdafd5f67dfb733c85131d80ffff7"

        // HMAC-SHA256(CONFIRM_KEY, "LDLINK1 confirm" + TRANSCRIPT_HASH).
        const val CONFIRMATION = "e95ffb79c8e0284751de230426caba0a5bc352bbbf7871a0a1e150f4b803a898"

        val PLAINTEXT = """{"mode":"CLOUD"}""".encodeToByteArray()
    }
}
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create `LinkHandshake.kt` in full except `LinkTranscript.encode()` returns `ByteArray(0)`, and create `BouncyCastleLinkHandshake.kt` with every function returning `ByteArray(0)`, `false`, `LinkKeyPair(ByteArray(0), ByteArray(0))`, `LinkKeys(ByteArray(0), ByteArray(0), ByteArray(0))` or `SealedBytes(ByteArray(0), ByteArray(0))` as its type needs.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:device:desktopTest --tests 'app.logdate.client.device.crypto.link.*' :client:device:testAndroidHostTest --tests 'app.logdate.client.device.crypto.link.*' --console=plain
```
Expected: FAIL: both `LinkTranscriptTest` tests (`expected:<151> but was:<0>`; the second because both encodings are empty), and every `BouncyCastleLinkHandshakeTest` test, for example `x25519 matches RFC 7748 section 6 point 1` (`expected:<8520f009…> but was:<>`) and `an all zero peer key is rejected` (`Expected an exception of LinkHandshakeException to be thrown, but was completed successfully`).

- [ ] **Step 4: Implement the exchange**

`client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/link/LinkHandshake.kt`:

```kotlin
package app.logdate.client.device.crypto.link

/** One side's single-use X25519 key pair for one link exchange. */
class LinkKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
)

/**
 * The keys both devices derive from one exchange.
 *
 * @property bundleKey Seals the link bundle with AES-256-GCM.
 * @property confirmKey Keys the watch's HMAC confirming it opened the bundle.
 * @property pairKey Stored by both devices after a pair-only link to authenticate later requests.
 */
class LinkKeys(
    val bundleKey: ByteArray,
    val confirmKey: ByteArray,
    val pairKey: ByteArray,
)

class SealedBytes(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

/** The exchange cannot continue: a bad key, a changed transcript, or a bundle that fails its check. */
class LinkHandshakeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object LinkLabels {
    const val TRANSCRIPT = "LDLINK1"
    const val BUNDLE = "LDLINK1 bundle"
    const val CONFIRM = "LDLINK1 confirm"
    const val PAIR = "LDLINK1 pair"
}

/**
 * Everything both devices saw in one exchange. Its hash salts every key and authenticates the bundle,
 * so keys from one exchange never open anything from another, and a changed field fails the exchange.
 */
class LinkTranscript(
    val phoneNode: String,
    val watchNode: String,
    val phonePublicKey: ByteArray,
    val watchPublicKey: ByteArray,
    val phoneNonce: ByteArray,
    val watchNonce: ByteArray,
    val schema: Int,
) {
    /**
     * [LinkLabels.TRANSCRIPT], then each field as a 4-byte big-endian length and its bytes, then
     * [schema] as a 4-byte big-endian integer. The lengths keep "ab" + "c" apart from "a" + "bc".
     */
    fun encode(): ByteArray {
        val fields =
            listOf(
                phoneNode.encodeToByteArray(),
                watchNode.encodeToByteArray(),
                phonePublicKey,
                watchPublicKey,
                phoneNonce,
                watchNonce,
            )
        val prefixed = fields.fold(LinkLabels.TRANSCRIPT.encodeToByteArray()) { encoded, field -> encoded + int32(field.size) + field }
        return prefixed + int32(schema)
    }

    private fun int32(value: Int): ByteArray =
        byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
}

/**
 * The key exchange that links a watch to its phone: X25519, then HKDF-SHA256 salted with the
 * transcript hash, AES-256-GCM for the bundle, and HMAC-SHA256 for the watch's confirmation.
 */
interface LinkHandshake {
    fun generateKeyPair(): LinkKeyPair

    fun newNonce(): ByteArray

    fun transcriptHash(transcript: LinkTranscript): ByteArray

    /** Throws [LinkHandshakeException] for a key of the wrong size or one that gives no shared secret. */
    fun deriveKeys(
        ownPrivateKey: ByteArray,
        peerPublicKey: ByteArray,
        transcriptHash: ByteArray,
    ): LinkKeys

    fun seal(
        bundleKey: ByteArray,
        plaintext: ByteArray,
        transcriptHash: ByteArray,
    ): SealedBytes

    /** Throws [LinkHandshakeException] when the key, IV, ciphertext or transcript differ from sealing. */
    fun open(
        bundleKey: ByteArray,
        sealed: SealedBytes,
        transcriptHash: ByteArray,
    ): ByteArray

    fun confirmation(
        confirmKey: ByteArray,
        transcriptHash: ByteArray,
    ): ByteArray

    fun verifyConfirmation(
        confirmKey: ByteArray,
        transcriptHash: ByteArray,
        mac: ByteArray,
    ): Boolean
}
```

`client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/link/BouncyCastleLinkHandshake.kt`:

```kotlin
package app.logdate.client.device.crypto.link

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.util.Arrays
import java.security.SecureRandom

/** [LinkHandshake] on BouncyCastle, which `AndroidDeviceTransfer` already uses for X25519. */
class BouncyCastleLinkHandshake(
    private val random: SecureRandom = SecureRandom(),
) : LinkHandshake {
    override fun generateKeyPair(): LinkKeyPair {
        val privateKey = X25519PrivateKeyParameters(random)
        return LinkKeyPair(privateKey.encoded, privateKey.generatePublicKey().encoded)
    }

    override fun newNonce(): ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)

    override fun transcriptHash(transcript: LinkTranscript): ByteArray = sha256(transcript.encode())

    override fun deriveKeys(
        ownPrivateKey: ByteArray,
        peerPublicKey: ByteArray,
        transcriptHash: ByteArray,
    ): LinkKeys {
        if (transcriptHash.size != HASH_BYTES) throw LinkHandshakeException("A transcript hash is $HASH_BYTES bytes")
        val shared = x25519(ownPrivateKey, peerPublicKey)
        try {
            return LinkKeys(
                bundleKey = hkdfSha256(shared, transcriptHash, LinkLabels.BUNDLE.encodeToByteArray(), KEY_BYTES),
                confirmKey = hkdfSha256(shared, transcriptHash, LinkLabels.CONFIRM.encodeToByteArray(), KEY_BYTES),
                pairKey = hkdfSha256(shared, transcriptHash, LinkLabels.PAIR.encodeToByteArray(), KEY_BYTES),
            )
        } finally {
            shared.fill(0)
        }
    }

    override fun seal(
        bundleKey: ByteArray,
        plaintext: ByteArray,
        transcriptHash: ByteArray,
    ): SealedBytes {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val ciphertext = runGcm(forEncryption = true, key = bundleKey, iv = iv, associatedData = transcriptHash, input = plaintext)
        return SealedBytes(iv, ciphertext)
    }

    override fun open(
        bundleKey: ByteArray,
        sealed: SealedBytes,
        transcriptHash: ByteArray,
    ): ByteArray =
        try {
            runGcm(forEncryption = false, key = bundleKey, iv = sealed.iv, associatedData = transcriptHash, input = sealed.ciphertext)
        } catch (e: InvalidCipherTextException) {
            throw LinkHandshakeException("The link bundle failed its integrity check", e)
        }

    override fun confirmation(
        confirmKey: ByteArray,
        transcriptHash: ByteArray,
    ): ByteArray = hmacSha256(confirmKey, LinkLabels.CONFIRM.encodeToByteArray() + transcriptHash)

    override fun verifyConfirmation(
        confirmKey: ByteArray,
        transcriptHash: ByteArray,
        mac: ByteArray,
    ): Boolean = Arrays.constantTimeAreEqual(confirmation(confirmKey, transcriptHash), mac)

    /** X25519 as RFC 7748 defines it. Throws [LinkHandshakeException] for a result of all zeros. */
    fun x25519(
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): ByteArray {
        if (privateKey.size != KEY_BYTES || publicKey.size != KEY_BYTES) throw LinkHandshakeException("X25519 keys are $KEY_BYTES bytes")
        val shared = ByteArray(KEY_BYTES)
        try {
            X25519PrivateKeyParameters(privateKey, 0).generateSecret(X25519PublicKeyParameters(publicKey, 0), shared, 0)
        } catch (e: IllegalStateException) {
            throw LinkHandshakeException("The other device's key gives no shared secret", e)
        }
        return shared
    }

    fun publicKeyOf(privateKey: ByteArray): ByteArray = X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded

    /** HKDF-SHA256 as RFC 5869 defines it. */
    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest.newInstance())
        generator.init(HKDFParameters(ikm, salt, info))
        return ByteArray(length).also { generator.generateBytes(it, 0, length) }
    }

    private fun runGcm(
        forEncryption: Boolean,
        key: ByteArray,
        iv: ByteArray,
        associatedData: ByteArray,
        input: ByteArray,
    ): ByteArray {
        if (key.size != KEY_BYTES || iv.size != IV_BYTES) throw LinkHandshakeException("AES-256-GCM needs a 32-byte key and a 12-byte IV")
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(forEncryption, AEADParameters(KeyParameter(key), TAG_BITS, iv, associatedData))
        val output = ByteArray(cipher.getOutputSize(input.size))
        val written = cipher.processBytes(input, 0, input.size, output, 0)
        val total = written + cipher.doFinal(output, written)
        return output.copyOf(total)
    }

    private fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = HMac(SHA256Digest.newInstance())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(mac.macSize).also { mac.doFinal(it, 0) }
    }

    private fun sha256(data: ByteArray): ByteArray {
        val digest = SHA256Digest.newInstance()
        digest.update(data, 0, data.size)
        return ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
    }

    private companion object {
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val HASH_BYTES = 32
        const val NONCE_BYTES = 16
    }
}
```

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS: 2 transcript tests and 14 handshake tests.

- [ ] **Step 6: Run the module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:device:desktopTest :client:device:testAndroidHostTest :client:device:compileKotlinIosSimulatorArm64 :client:device:compileTestKotlinIosSimulatorArm64 :client:device:ktlintCheck :client:device:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task07-link-handshake.txt <<'EOF'
chore(device): add the key exchange for linking a watch

Setting up a watch without a sign-in needs the phone to hand it keys
over the Data Layer, which Play services stores and relays. Both
devices will run X25519 with single-use keys, derive three keys with
HKDF-SHA256 over a hash of everything they exchanged, seal the bundle
with AES-256-GCM bound to that hash, and confirm with an HMAC.

The exchange matches the RFC 7748 and RFC 5869 test vectors, and the
keys and confirmation derived from them are pinned. A changed
ciphertext, IV or exchange fails, and a peer key that gives an all
zero secret is refused. Nothing runs the exchange yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/link/LinkHandshake.kt client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/link/BouncyCastleLinkHandshake.kt client/device/src/commonTest/kotlin/app/logdate/client/device/crypto/link/LinkTranscriptTest.kt client/device/src/androidHostTest/kotlin/app/logdate/client/device/crypto/link/BouncyCastleLinkHandshakeTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task07-link-handshake.txt
```

---

### Task 8: Add create-if-absent requests to the cloud client (C5)

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/ConditionalCreate.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudApiClient.kt:175-177` (a block before `// Draft Operations`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/LogDateCloudApiClient.kt:676` (after `uploadAssociations`) and the private DTOs at the end of the file (after line 1176)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SafeCloudTransport.kt:53-56` (after `delete`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt:18-51, 72-96`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudJournalDataSource.kt:16-49, 67-83`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudAssociationDataSource.kt:14-42, 65-79`
- Modify: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt:367-557` (`FakeCloudApiClient`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/ConditionalCreateCloudApiClientTest.kt`

**Interfaces:**
- Consumes: `ServerDescriptor.hasProtocolFeature(name)`, `LogDateConfigRepository.getCurrentServerDescriptor()`, `SafeCloudTransport.request(url, method, block)` (`SafeCloudTransport.kt:58-70`), `LogDateCloudApiClient.handleApiError` and `getBaseUrl`, `DefaultCloudContentDataSource.toUploadRequest`, `DefaultCloudJournalDataSource.toUploadRequest`, `JournalContentAssociation.toAssociation`.
- Produces:
  - `sealed interface ConditionalCreateResult { Created(serverVersion: Long, syncedAt: Instant); AlreadyExists(serverVersion: Long); Deleted(deletedAt: Instant) }`; `sealed interface ContentState { Present(serverVersion: Long); Absent; Deleted }`; `internal const val CONDITIONAL_CREATE_FEATURE = "conditionalCreateV1"`; `class ConditionalCreateUnsupportedException : UnsupportedOperationException`
  - `CloudApiClient.createContentIfAbsent(accessToken: String, content: ContentUploadRequest): Result<ConditionalCreateResult>`, `createJournalIfAbsent(accessToken: String, journal: JournalUploadRequest): Result<ConditionalCreateResult>`, `contentState(accessToken: String, contentId: String): Result<ContentState>`, `uploadAssociationsIfAbsent(accessToken: String, associations: AssociationUploadRequest): Result<AssociationUploadResponse>`, each defaulting to `Result.failure(UnsupportedOperationException(…))`
  - `CloudContentDataSource.createNoteIfAbsent(accessToken: String, note: JournalNote): Result<ConditionalCreateResult>`, `CloudJournalDataSource.createJournalIfAbsent(accessToken: String, journal: Journal): Result<ConditionalCreateResult>`, `CloudAssociationDataSource.uploadAssociationsIfAbsent(accessToken: String, associations: List<JournalContentAssociation>): Result<Instant>`, each defaulting to Unsupported, implemented by the `Default…DataSource`s
  - `SafeCloudTransport.head(url, block): HttpResponse`
  - `FakeCloudApiClient.createContentIfAbsentResponse`, `createJournalIfAbsentResponse`, `uploadAssociationsIfAbsentResponse`, `createContentIfAbsentCalls`

- [ ] **Step 1: Write the failing client tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/ConditionalCreateCloudApiClientTest.kt`:

```kotlin
package app.logdate.client.sync.cloud

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.Journal
import app.logdate.shared.model.ServerDescriptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests the create-if-absent requests a linked watch uses, so a repeated create never overwrites or
 * brings back a record, and nothing of the kind is sent to a server that would ignore it.
 */
class ConditionalCreateCloudApiClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `a content create asks for no match and reads the created version`() =
        runTest {
            val client = client { respondJson("""{"id":"$ID","serverVersion":7,"uploadedAt":1000}""", HttpStatusCode.Created) }

            val result = client.createContentIfAbsent("token", content())

            assertEquals(ConditionalCreateResult.Created(7, Instant.fromEpochMilliseconds(1_000)), result.getOrThrow())
            val request = requests.single()
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("$API/contents/$ID", request.url.toString())
            assertEquals("*", request.headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun `an existing record answers with its server version`() =
        runTest {
            val client = client { respondJson("""{"code":"CONTENT_EXISTS","serverVersion":9}""", HttpStatusCode.PreconditionFailed) }

            assertEquals(ConditionalCreateResult.AlreadyExists(9), client.createContentIfAbsent("token", content()).getOrThrow())
        }

    @Test
    fun `a deleted record answers with when it was deleted`() =
        runTest {
            val client = client { respondJson("""{"deletedAt":1234}""", HttpStatusCode.Gone) }

            assertEquals(
                ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(1_234)),
                client.createContentIfAbsent("token", content()).getOrThrow(),
            )
        }

    @Test
    fun `a server without conditional create is never sent one`() =
        runTest {
            val client = client(conditionalCreate = false) { respondJson("{}", HttpStatusCode.Created) }

            assertIs<ConditionalCreateUnsupportedException>(client.createContentIfAbsent("token", content()).exceptionOrNull())
            assertIs<ConditionalCreateUnsupportedException>(client.contentState("token", ID).exceptionOrNull())
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `an unreadable conflict answer is a parse error`() =
        runTest {
            val client = client { respondJson("nope", HttpStatusCode.PreconditionFailed) }

            val error = assertIs<CloudApiException>(client.createContentIfAbsent("token", content()).exceptionOrNull())
            assertEquals("PARSE_ERROR", error.errorCode)
            assertEquals(412, error.statusCode)
        }

    @Test
    fun `a journal create uses the journals path`() =
        runTest {
            val client = client { respondJson("""{"id":"$ID","serverVersion":3,"uploadedAt":5}""", HttpStatusCode.Created) }

            val result = client.createJournalIfAbsent("token", JournalUploadRequest(ID, "title", "", createdAt = 1, lastUpdated = 1))

            assertEquals(ConditionalCreateResult.Created(3, Instant.fromEpochMilliseconds(5)), result.getOrThrow())
            assertEquals("$API/journals/$ID", requests.single().url.toString())
            assertEquals("*", requests.single().headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun `content state reads the version from the entity tag`() =
        runTest {
            val client = client { respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"12\"")) }

            assertEquals(ContentState.Present(12), client.contentState("token", ID).getOrThrow())
            assertEquals(HttpMethod.Head, requests.single().method)
        }

    @Test
    fun `content state reports absent and deleted records`() =
        runTest {
            assertEquals(ContentState.Absent, client { respond("", HttpStatusCode.NotFound) }.contentState("token", ID).getOrThrow())
            assertEquals(ContentState.Deleted, client { respond("", HttpStatusCode.Gone) }.contentState("token", ID).getOrThrow())
        }

    @Test
    fun `associations created if absent ask the server to skip existing ones`() =
        runTest {
            val client = client { respondJson("""{"uploadedCount":1,"uploadedAt":5}""", HttpStatusCode.OK) }

            val request = AssociationUploadRequest(listOf(Association(journalId = ID, contentId = ID, createdAt = 1)))

            assertEquals(AssociationUploadResponse(1, 5), client.uploadAssociationsIfAbsent("token", request).getOrThrow())
            assertEquals("$API/associations?ifAbsent=true", requests.single().url.toString())
        }

    @Test
    fun `the data sources create through the client`() =
        runTest {
            val api = FakeCloudApiClient()
            api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.AlreadyExists(4))
            val note =
                JournalNote.Text(
                    uid = Uuid.parse(ID),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    content = "hi",
                )
            val link = JournalContentAssociation(Uuid.parse(ID), Uuid.parse(ID), Instant.fromEpochMilliseconds(1))

            assertEquals(ConditionalCreateResult.AlreadyExists(4), DefaultCloudContentDataSource(api).createNoteIfAbsent("token", note).getOrThrow())
            assertEquals(ID, api.createContentIfAbsentCalls.single().id)
            assertTrue(DefaultCloudJournalDataSource(api).createJournalIfAbsent("token", Journal(id = Uuid.parse(ID))).isSuccess)
            assertTrue(DefaultCloudAssociationDataSource(api).uploadAssociationsIfAbsent("token", listOf(link)).isSuccess)
            assertEquals(listOf("createContentIfAbsent", "createJournalIfAbsent", "uploadAssociationsIfAbsent"), api.methodCalls)
        }

    @Test
    fun `the durable client forwards create if absent`() =
        runTest {
            val api = FakeCloudApiClient()
            api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.AlreadyExists(3))
            val database = DownloadInboxTest.Database()
            val inbox = DownloadInbox(database, database, { DownloadScope("a", "server") }, { 1L })
            val durable = DurableCloudApiClient(api, inbox)

            assertEquals(ConditionalCreateResult.AlreadyExists(3), durable.createContentIfAbsent("token", content()).getOrThrow())
        }

    private suspend fun client(
        conditionalCreate: Boolean = true,
        handler: MockRequestHandler,
    ): LogDateCloudApiClient {
        val config = DefaultLogDateConfigRepository(initialBackendUrl = ORIGIN)
        if (conditionalCreate) {
            config.updateServerDescriptor(
                ServerDescriptor(
                    serverOrigin = ORIGIN,
                    apiBaseUrl = API,
                    deploymentKind = DeploymentKind.SELF_HOSTED,
                    displayName = "Fixture",
                    protocolFeatures = listOf(CONDITIONAL_CREATE_FEATURE),
                ),
            )
        }
        val engine =
            MockEngine { request ->
                requests += request
                handler(request)
            }
        return LogDateCloudApiClient(config, HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun content() =
        ContentUploadRequest(id = ID, type = "TEXT", content = "LDSE2:ciphertext", mediaUri = null, createdAt = 1, lastUpdated = 1)

    private companion object {
        const val ID = "550e8400-e29b-41d4-a716-446655440000"
        const val ORIGIN = "https://cloud.example"
        const val API = "https://cloud.example/api/v1"
    }
}
```

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt`, inside `FakeCloudApiClient`, after `var deleteAssociationsResponse: Result<Unit> = Result.success(Unit)` add:

```kotlin

    var createContentIfAbsentResponse: Result<ConditionalCreateResult> =
        Result.success(ConditionalCreateResult.Created(1, Clock.System.now()))

    var createJournalIfAbsentResponse: Result<ConditionalCreateResult> =
        Result.success(ConditionalCreateResult.Created(1, Clock.System.now()))

    var uploadAssociationsIfAbsentResponse: Result<AssociationUploadResponse> =
        Result.success(AssociationUploadResponse(0, Clock.System.now().toEpochMilliseconds()))

    val createContentIfAbsentCalls = mutableListOf<ContentUploadRequest>()
```

and after `override suspend fun deleteAssociations(…) { … }` add:

```kotlin

    override suspend fun createContentIfAbsent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ConditionalCreateResult> {
        methodCalls.add("createContentIfAbsent")
        createContentIfAbsentCalls.add(content)
        return createContentIfAbsentResponse
    }

    override suspend fun createJournalIfAbsent(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<ConditionalCreateResult> {
        methodCalls.add("createJournalIfAbsent")
        return createJournalIfAbsentResponse
    }

    override suspend fun uploadAssociationsIfAbsent(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse> {
        methodCalls.add("uploadAssociationsIfAbsent")
        return uploadAssociationsIfAbsentResponse
    }
```

with the import `app.logdate.client.sync.cloud.ConditionalCreateResult`.

- [ ] **Step 2: Add the API surface with inert bodies**

Create `ConditionalCreate.kt` in full (Step 4). Add the four `CloudApiClient` defaults, the three data source interface defaults and `SafeCloudTransport.head` in full (Step 4). Add the `LogDateCloudApiClient` overrides and the `Default…DataSource` overrides with bodies that return `Result.failure(UnsupportedOperationException("not yet"))`.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.cloud.ConditionalCreateCloudApiClientTest' --console=plain
```
Expected: FAIL: every test except `the durable client forwards create if absent` (which passes, because `DurableCloudApiClient` delegates to the fake by `by delegate`), for example `a content create asks for no match and reads the created version` with `UnsupportedOperationException: not yet`, and `a server without conditional create is never sent one` (`Expected value to be of type <ConditionalCreateUnsupportedException>, actual <UnsupportedOperationException>`).

- [ ] **Step 4: Implement the requests**

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/ConditionalCreate.kt`:

```kotlin
package app.logdate.client.sync.cloud

import kotlin.time.Instant

/**
 * What a create-if-absent request found on the server.
 *
 * The server answers `If-None-Match: *` with 201 when it created the record, 412 with the record's
 * version when it already exists, and 410 with the deletion time when it was deleted, so a repeated
 * create can never overwrite a record or bring a deleted one back.
 */
sealed interface ConditionalCreateResult {
    data class Created(
        val serverVersion: Long,
        val syncedAt: Instant,
    ) : ConditionalCreateResult

    data class AlreadyExists(
        val serverVersion: Long,
    ) : ConditionalCreateResult

    data class Deleted(
        val deletedAt: Instant,
    ) : ConditionalCreateResult
}

/** What the server holds for one content record, read with `HEAD /contents/{id}`. */
sealed interface ContentState {
    data class Present(
        val serverVersion: Long,
    ) : ContentState

    data object Absent : ContentState

    data object Deleted : ContentState
}

/**
 * The server descriptor flag for create-if-absent requests. A server without it ignores
 * `If-None-Match` and overwrites, so the client never sends it one.
 */
internal const val CONDITIONAL_CREATE_FEATURE = "conditionalCreateV1"

class ConditionalCreateUnsupportedException : UnsupportedOperationException("This server does not support create-if-absent requests")
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudApiClient.kt`, before `    // Draft Operations` add:

```kotlin
    // Create-if-absent operations, for linked devices. Never sent to a server that does not advertise
    // CONDITIONAL_CREATE_FEATURE: an older server ignores the condition and overwrites.

    suspend fun createContentIfAbsent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ConditionalCreateResult> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this cloud client"))

    suspend fun createJournalIfAbsent(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<ConditionalCreateResult> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this cloud client"))

    /** Reads whether the server holds a content record, without downloading it. */
    suspend fun contentState(
        accessToken: String,
        contentId: String,
    ): Result<ContentState> =
        Result.failure(UnsupportedOperationException("Content state is not supported by this cloud client"))

    /** Uploads associations the server does not have yet, never reviving one it deleted. */
    suspend fun uploadAssociationsIfAbsent(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this cloud client"))

```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SafeCloudTransport.kt`, after `delete` add:

```kotlin

    suspend fun head(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = request(url, HttpMethod.Head, block)
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/LogDateCloudApiClient.kt`, after `uploadAssociations` (ends at line 676) add:

```kotlin

    override suspend fun createContentIfAbsent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ConditionalCreateResult> =
        conditionalCreate(accessToken, "create content") { baseUrl ->
            transport.put("$baseUrl/contents/${content.id}") {
                headers.append("Authorization", "Bearer $accessToken")
                headers.append(HttpHeaders.IfNoneMatch, "*")
                contentType(ContentType.Application.Json)
                setBody(content)
            }
        }

    override suspend fun createJournalIfAbsent(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<ConditionalCreateResult> =
        conditionalCreate(accessToken, "create journal") { baseUrl ->
            transport.put("$baseUrl/journals/${journal.id}") {
                headers.append("Authorization", "Bearer $accessToken")
                headers.append(HttpHeaders.IfNoneMatch, "*")
                contentType(ContentType.Application.Json)
                setBody(journal)
            }
        }

    override suspend fun contentState(
        accessToken: String,
        contentId: String,
    ): Result<ContentState> {
        if (!supportsConditionalCreate()) return Result.failure(ConditionalCreateUnsupportedException())
        return try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.head("$baseUrl/contents/$contentId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val version = response.headers[HttpHeaders.ETag]?.removePrefix("W/")?.trim('"')?.toLongOrNull()
                    if (version == null) {
                        Result.failure(CloudApiException("PARSE_ERROR", "Content state has no version", statusCode = 200))
                    } else {
                        Result.success(ContentState.Present(version))
                    }
                }
                HttpStatusCode.NotFound -> Result.success(ContentState.Absent)
                HttpStatusCode.Gone -> Result.success(ContentState.Deleted)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to read content state")
            Result.failure(CloudApiException(errorCode = "NETWORK_ERROR", message = "Failed to read content state"))
        }
    }

    override suspend fun uploadAssociationsIfAbsent(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse> {
        if (!supportsConditionalCreate()) return Result.failure(ConditionalCreateUnsupportedException())
        return try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.post("$baseUrl/associations") {
                    parameter("ifAbsent", "true")
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(associations)
                }
            when (response.status) {
                HttpStatusCode.OK -> Result.success(response.body<AssociationUploadResponse>())
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to upload associations if absent")
            Result.failure(CloudApiException(errorCode = "NETWORK_ERROR", message = "Failed to upload associations"))
        }
    }

    private fun supportsConditionalCreate(): Boolean =
        configRepository.getCurrentServerDescriptor()?.hasProtocolFeature(CONDITIONAL_CREATE_FEATURE) == true

    private suspend fun conditionalCreate(
        accessToken: String,
        operation: String,
        send: suspend (baseUrl: String) -> HttpResponse,
    ): Result<ConditionalCreateResult> {
        if (!supportsConditionalCreate()) return Result.failure(ConditionalCreateUnsupportedException())
        return try {
            val response = send(getBaseUrl(accessToken))
            when (response.status) {
                HttpStatusCode.Created, HttpStatusCode.OK ->
                    readBody<CreatedRecordBody>(response).map {
                        ConditionalCreateResult.Created(it.serverVersion, Instant.fromEpochMilliseconds(it.uploadedAt))
                    }
                HttpStatusCode.PreconditionFailed ->
                    readBody<ExistingRecordBody>(response).map { ConditionalCreateResult.AlreadyExists(it.serverVersion) }
                HttpStatusCode.Gone ->
                    readBody<DeletedRecordBody>(response).map { ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(it.deletedAt)) }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to $operation")
            Result.failure(CloudApiException(errorCode = "NETWORK_ERROR", message = "Failed to $operation"))
        }
    }

    private suspend inline fun <reified T> readBody(response: HttpResponse): Result<T> {
        val status = response.status.value
        val text = response.bodyAsText()
        return try {
            Result.success(errorJson.decodeFromString<T>(text))
        } catch (_: IllegalArgumentException) {
            Result.failure(CloudApiException("PARSE_ERROR", "Unreadable response (HTTP $status)", statusCode = status))
        }
    }
```

and at the end of the file, after `private data class RefreshTokenDataV1Dto(…)`, add:

```kotlin

@Serializable
private data class CreatedRecordBody(
    val serverVersion: Long,
    val uploadedAt: Long,
)

@Serializable
private data class ExistingRecordBody(
    val serverVersion: Long,
)

@Serializable
private data class DeletedRecordBody(
    val deletedAt: Long,
)
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt`:

1. In the interface, after `deleteNote`, add:

```kotlin

    /** Creates the note only if the server has no record with its id; see [CloudApiClient.createContentIfAbsent]. */
    suspend fun createNoteIfAbsent(
        accessToken: String,
        note: JournalNote,
    ): Result<ConditionalCreateResult> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this data source"))
```

2. In `DefaultCloudContentDataSource`, after `uploadNote`, add:

```kotlin

    override suspend fun createNoteIfAbsent(
        accessToken: String,
        note: JournalNote,
    ): Result<ConditionalCreateResult> {
        val request =
            try {
                note.toUploadRequest()
            } catch (error: Exception) {
                return Result.failure(error)
            }
        return cloudApiClient.createContentIfAbsent(accessToken, request)
    }
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudJournalDataSource.kt`:

1. In the interface, after `deleteJournal`, add:

```kotlin

    /** Creates the journal only if the server has no record with its id; see [CloudApiClient.createJournalIfAbsent]. */
    suspend fun createJournalIfAbsent(
        accessToken: String,
        journal: Journal,
    ): Result<ConditionalCreateResult> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this data source"))
```

2. In `DefaultCloudJournalDataSource`, after `uploadJournal`, add:

```kotlin

    override suspend fun createJournalIfAbsent(
        accessToken: String,
        journal: Journal,
    ): Result<ConditionalCreateResult> {
        val request =
            try {
                journal.toUploadRequest()
            } catch (error: Exception) {
                return Result.failure(error)
            }
        return cloudApiClient.createJournalIfAbsent(accessToken, request)
    }
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudAssociationDataSource.kt`:

1. In the interface, after `uploadAssociations`, add:

```kotlin

    /** Uploads associations the server does not have yet; see [CloudApiClient.uploadAssociationsIfAbsent]. */
    suspend fun uploadAssociationsIfAbsent(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ): Result<Instant> =
        Result.failure(UnsupportedOperationException("Create-if-absent is not supported by this data source"))
```

2. In `DefaultCloudAssociationDataSource`, after `uploadAssociations`, add:

```kotlin

    override suspend fun uploadAssociationsIfAbsent(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ): Result<Instant> {
        val request = AssociationUploadRequest(associations = associations.map { it.toAssociation() })
        return cloudApiClient.uploadAssociationsIfAbsent(accessToken, request).map { Instant.fromEpochMilliseconds(it.uploadedAt) }
    }
```

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS, 11 tests.

- [ ] **Step 6: Run the module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:testAndroidHostTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt --console=plain
```
Expected: BUILD SUCCESSFUL. `LogDateCloudApiClient` is already in the `:client:sync` detekt baseline as a large class, so its growth is not a new finding.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task08-conditional-create.txt <<'EOF'
chore(sync): add create-if-absent requests to the cloud client

A watch that backs up straight to the cloud may send an entry the phone
already uploaded. Today's upload would overwrite it, or bring back one
deleted since. The cloud client can now ask the server to create an
entry, a journal or a journal link only if it has none, and reads the
answer as created, already there with its version, or deleted.

The client sends these requests only to a server that advertises
support, because an older server ignores the condition and overwrites.
It can also ask whether the server holds an entry without downloading
it. Nothing uses the requests yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/ConditionalCreate.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudApiClient.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/LogDateCloudApiClient.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SafeCloudTransport.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudJournalDataSource.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudAssociationDataSource.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/ConditionalCreateCloudApiClientTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task08-conditional-create.txt
```

---

### Task 9: Keep rotated refresh tokens and report revoked devices (C5)

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SessionRefresh.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudApiClient.kt:60-67` (after `refreshAccessToken`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/LogDateCloudApiClient.kt:260-298` (`refreshAccessToken`), `:1166-1169` (`RefreshTokenDataV1Dto`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SafeCloudErrorCode.kt:30` (the code list)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepository.kt:44-50` (supertypes), `:331-350` (after `refreshAccessToken`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncTokenRefresher.kt:24-27, 103-159`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt:111` (constructor), `:634` (`tokenRefresher`)
- Modify: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt:196-306` (`testDefaultSyncManager`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/SessionRotationTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/BasicCloudApiClientTest.kt` (3 new tests after `refresh token uses auth v1 path`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepositoryTest.kt` (`FakeCloudApiClient` at :236; 2 new tests)

**Interfaces:**
- Consumes: `CloudAccountRepository.refreshAccessToken`, `SessionStorage.getSession/saveSession/replaceSessionIfCurrent/clearSession`, `PasskeyApiErrorCodes.INVALID_REFRESH_TOKEN`/`REFRESH_TOKEN_REVOKED`, `UserSession(accessToken, refreshToken, accountId)`.
- Produces: `data class RefreshedSession(val accessToken: String, val refreshToken: String?)`; `interface SessionRefreshingAccountRepository { suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> }`; `fun interface DeviceSessionRevocationListener { suspend fun onDeviceSessionRevoked(); companion object { val None } }`; `object DeviceSessionErrorCodes { const val DEVICE_SESSION_REVOKED }`; `CloudApiClient.refreshSession(refreshToken: String): Result<RefreshedSession>` (default Unsupported); `DefaultCloudAccountRepository : SessionRefreshingAccountRepository`; `SyncTokenRefresher(sessionStorage, cloudAccountRepository, revocationListener: DeviceSessionRevocationListener = DeviceSessionRevocationListener.None)`; `DefaultSyncManager(…, deviceSessionRevocationListener: DeviceSessionRevocationListener = DeviceSessionRevocationListener.None)`; `testDefaultSyncManager(…, deviceSessionRevocationListener = …)`.

- [ ] **Step 1: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/SessionRotationTest.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DeviceSessionRevocationListener
import app.logdate.client.sync.cloud.RefreshedSession
import app.logdate.client.sync.cloud.SessionRefreshingAccountRepository
import app.logdate.client.sync.test.FakeCloudAccountRepository
import app.logdate.client.sync.test.fakeAccountRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.CloudAccountRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * A linked watch's session slides forward and its refresh token rotates, so the new one has to be
 * stored; and when the session is revoked, the watch must learn it at once to stop and erase itself.
 */
class SessionRotationTest {
    @Test
    fun `a rotated refresh token replaces the old one in the session`() =
        runTest {
            val sessionStorage = fakeSessionStorage()
            val refresher = SyncTokenRefresher(sessionStorage, RotatingAccountRepository(Result.success(RefreshedSession("new-access", "new-refresh"))))

            val result = refresher.withFreshToken(::rejectTheFirstToken, "probe")

            assertEquals("new-access", result.getOrThrow())
            assertEquals(UserSession("new-access", "new-refresh", "test-account-id"), sessionStorage.getSession())
        }

    @Test
    fun `a server that does not rotate keeps the refresh token`() =
        runTest {
            val sessionStorage = fakeSessionStorage()
            val refresher = SyncTokenRefresher(sessionStorage, fakeAccountRepository())

            refresher.withFreshToken(::rejectTheFirstToken, "probe")

            assertEquals(UserSession("new-token", "test-refresh-token", "test-account-id"), sessionStorage.getSession())
        }

    @Test
    fun `a revoked device session signs out and is reported`() =
        runTest {
            val sessionStorage = fakeSessionStorage()
            var revocations = 0
            val refresher =
                SyncTokenRefresher(
                    sessionStorage,
                    RotatingAccountRepository(Result.failure(CloudApiException(REVOKED, "revoked", statusCode = 401))),
                    DeviceSessionRevocationListener { revocations++ },
                )

            val result = refresher.withFreshToken(::rejectTheFirstToken, "probe")

            assertEquals(REVOKED, assertIs<CloudApiException>(result.exceptionOrNull()).errorCode)
            assertNull(sessionStorage.getSession())
            assertEquals(1, revocations)
        }

    @Test
    fun `an expired refresh token signs out without reporting a revoked device`() =
        runTest {
            val sessionStorage = fakeSessionStorage()
            var revocations = 0
            val refresher =
                SyncTokenRefresher(
                    sessionStorage,
                    RotatingAccountRepository(Result.failure(CloudApiException("INVALID_REFRESH_TOKEN", "expired", statusCode = 401))),
                    DeviceSessionRevocationListener { revocations++ },
                )

            refresher.withFreshToken(::rejectTheFirstToken, "probe")

            assertNull(sessionStorage.getSession())
            assertEquals(0, revocations)
        }

    @Test
    fun `a refresh that cannot reach the server keeps the session and reports nothing`() =
        runTest {
            val sessionStorage = fakeSessionStorage()
            var revocations = 0
            val refresher =
                SyncTokenRefresher(
                    sessionStorage,
                    RotatingAccountRepository(Result.failure(CloudApiException("NETWORK_ERROR", "offline"))),
                    DeviceSessionRevocationListener { revocations++ },
                )

            refresher.withFreshToken(::rejectTheFirstToken, "probe")

            assertEquals("test-refresh-token", sessionStorage.getSession()?.refreshToken)
            assertEquals(0, revocations)
        }

    @Test
    fun `the sync engine passes a revoked session to its listener`() =
        runTest {
            var revocations = 0
            val api = fakeCloudApiClient { getJournalChangesResponse = Result.failure(CloudApiException(REVOKED, "revoked", statusCode = 401)) }
            val manager =
                testDefaultSyncManager(
                    cloudJournalDataSource = DefaultCloudJournalDataSource(api),
                    cloudAccountRepository = RotatingAccountRepository(Result.failure(CloudApiException(REVOKED, "revoked", statusCode = 401))),
                    deviceSessionRevocationListener = DeviceSessionRevocationListener { revocations++ },
                )

            manager.downloadRemoteChanges()

            assertEquals(1, revocations)
        }

    private suspend fun rejectTheFirstToken(token: String): Result<String> =
        if (token == "test-access-token") {
            Result.failure(CloudApiException("TOKEN_EXPIRED", "expired", statusCode = 401))
        } else {
            Result.success(token)
        }

    private class RotatingAccountRepository(
        private val result: Result<RefreshedSession>,
    ) : CloudAccountRepository by FakeCloudAccountRepository(),
        SessionRefreshingAccountRepository {
        override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> = result
    }

    private companion object {
        const val REVOKED = "DEVICE_SESSION_REVOKED"
    }
}
```

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/BasicCloudApiClientTest.kt`, add after `refresh token uses auth v1 path`:

```kotlin
    @Test
    fun `a session refresh returns a rotated refresh token`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("$baseUrl/auth/token/refresh", request.url.toString())
                        respond(
                            content = """{"success":true,"data":{"accessToken":"new-access","refreshToken":"new-refresh"}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            assertEquals(RefreshedSession("new-access", "new-refresh"), client.refreshSession("refresh").getOrThrow())
        }

    @Test
    fun `a session refresh without rotation has no new refresh token`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine {
                        respond(
                            content = """{"success":true,"data":{"accessToken":"new-access"}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            assertEquals(RefreshedSession("new-access", null), client.refreshSession("refresh").getOrThrow())
        }

    @Test
    fun `a revoked device session keeps its error code`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine {
                        respond(
                            content = """{"error":{"code":"DEVICE_SESSION_REVOKED","message":"This device was removed"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            val error = assertIs<CloudApiException>(client.refreshSession("refresh").exceptionOrNull())
            assertEquals("DEVICE_SESSION_REVOKED", error.errorCode)
            assertEquals(401, error.statusCode)
        }
```

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepositoryTest.kt`:

1. Change the fake's header to:

```kotlin
private class FakeCloudApiClient(
    private val completeAccountCreationResult: CompleteAccountCreationResponse? = null,
    private val refreshSessionResult: Result<RefreshedSession>? = null,
) : CloudApiClient {
    override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> =
        refreshSessionResult ?: Result.failure(UnsupportedOperationException("No session refresh configured"))

```

(the existing first override, `checkUsernameAvailability`, follows unchanged).

2. Add these tests after `sign out clears persisted did and handle`:

```kotlin
    @Test
    fun `a rotated refresh token is stored for its server`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            val repository =
                DefaultCloudAccountRepository(
                    apiClient = FakeCloudApiClient(refreshSessionResult = Result.success(RefreshedSession("access-2", "refresh-2"))),
                    secureStorage = storage,
                    configRepository = config,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            assertEquals(RefreshedSession("access-2", "refresh-2"), repository.refreshSession("refresh-1").getOrThrow())
            assertEquals("access-2", storage.getString(scopedKey("cloud_access_token", config.getCurrentBackendUrl())))
            assertEquals("refresh-2", storage.getString(scopedKey("cloud_refresh_token", config.getCurrentBackendUrl())))
        }

    @Test
    fun `a server without rotation still refreshes the access token`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://test.logdate.app")
            val repository =
                DefaultCloudAccountRepository(
                    apiClient = FakeCloudApiClient(),
                    secureStorage = storage,
                    configRepository = config,
                    coroutineScope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            assertEquals(RefreshedSession("access", null), repository.refreshSession("refresh-1").getOrThrow())
            assertEquals("access", storage.getString(scopedKey("cloud_access_token", config.getCurrentBackendUrl())))
            assertNull(storage.getString(scopedKey("cloud_refresh_token", config.getCurrentBackendUrl())))
        }
```

with the import `app.logdate.client.sync.cloud.RefreshedSession`.

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt`, add the parameter `deviceSessionRevocationListener: DeviceSessionRevocationListener = DeviceSessionRevocationListener.None,` after `diagnostics: …` in `testDefaultSyncManager`, pass `deviceSessionRevocationListener = deviceSessionRevocationListener,` in both `DefaultSyncManager(…)` calls, and import `app.logdate.client.sync.cloud.DeviceSessionRevocationListener`.

- [ ] **Step 2: Add the API surface with inert bodies**

Create `SessionRefresh.kt` in full (Step 4). Add `CloudApiClient.refreshSession` with its default, an override in `LogDateCloudApiClient` returning `Result.failure(UnsupportedOperationException("not yet"))`, `SessionRefreshingAccountRepository` to `DefaultCloudAccountRepository`'s supertypes with the same inert override, the `revocationListener` parameter to `SyncTokenRefresher` (unused), and the `deviceSessionRevocationListener` parameter to `DefaultSyncManager`, passed to `SyncTokenRefresher` at line 634.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.SessionRotationTest' --tests 'app.logdate.client.sync.cloud.BasicCloudApiClientTest' --tests 'app.logdate.client.sync.cloud.account.DefaultCloudAccountRepositoryTest' --console=plain
```
Expected: FAIL: `a rotated refresh token replaces the old one in the session`, `a revoked device session signs out and is reported` and `an expired refresh token signs out without reporting a revoked device` (the refresher still calls `refreshAccessToken`, which the test repository delegates to `FakeCloudAccountRepository`, so every refresh succeeds with `new-token`), `the sync engine passes a revoked session to its listener` (`expected:<1> but was:<0>`), the three new `BasicCloudApiClientTest` tests and the two new `DefaultCloudAccountRepositoryTest` tests. `a server that does not rotate keeps the refresh token` and `a refresh that cannot reach the server keeps the session and reports nothing` pass already.

- [ ] **Step 4: Implement**

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SessionRefresh.kt`:

```kotlin
package app.logdate.client.sync.cloud

/** A refreshed session: always a new access token, and a new refresh token when the server rotates it. */
data class RefreshedSession(
    val accessToken: String,
    val refreshToken: String?,
)

/** An account repository that returns a rotated refresh token along with the new access token. */
interface SessionRefreshingAccountRepository {
    suspend fun refreshSession(refreshToken: String): Result<RefreshedSession>
}

/** Told when the server says this device's session was revoked, so the device can stop and erase what it holds. */
fun interface DeviceSessionRevocationListener {
    suspend fun onDeviceSessionRevoked()

    companion object {
        val None = DeviceSessionRevocationListener { }
    }
}

object DeviceSessionErrorCodes {
    /** This device's session was revoked: its watch was removed, or its account signed it out. */
    const val DEVICE_SESSION_REVOKED = "DEVICE_SESSION_REVOKED"
}
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudApiClient.kt`, after `refreshAccessToken` (line 67) add:

```kotlin

    /**
     * Refreshes the session, returning the refresh token the server rotated to, when it did. A linked
     * device's session slides forward on every refresh, so a rotated refresh token must be saved.
     */
    suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> =
        Result.failure(UnsupportedOperationException("Session refresh is not supported by this cloud client"))
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/LogDateCloudApiClient.kt`, replace `refreshAccessToken` (lines 260-298, KDoc included) with:

```kotlin
    /**
     * Refreshes an expired access token.
     *
     * @param refreshToken The refresh token to use.
     * @return A new access token if successful.
     */
    override suspend fun refreshAccessToken(refreshToken: String): Result<String> = refreshSession(refreshToken).map { it.accessToken }

    override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> =
        try {
            val baseUrl = getBaseUrl(refreshToken = refreshToken)
            val response =
                transport.post("$baseUrl/auth/token/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refreshToken))
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<RefreshTokenResponseV1Dto>()
                    if (responseBody.success) {
                        Result.success(RefreshedSession(responseBody.data.accessToken, responseBody.data.refreshToken))
                    } else {
                        handleApiError(response)
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to refresh access token")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to refresh access token",
                ),
            )
        }
```

and replace `RefreshTokenDataV1Dto` (lines 1166-1169) with:

```kotlin
@Serializable
private data class RefreshTokenDataV1Dto(
    val accessToken: String,
    val refreshToken: String? = null,
)
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SafeCloudErrorCode.kt`, add `"DEVICE_SESSION_REVOKED",` after `"DELETION_UNAVAILABLE",`.

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepository.kt`:

1. Change the supertypes to:

```kotlin
) : CloudAccountRepository,
    CloudRequestLocationProvider,
    SessionRefreshingAccountRepository {
```

2. After `refreshAccessToken` add:

```kotlin

    /**
     * Refreshes the session and keeps both tokens for the server it came from. Against a cloud client
     * without session refresh it falls back to refreshing the access token alone.
     */
    override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> =
        try {
            val binding = currentCoroutineContext()[CloudRequestBinding]
            if (binding != null && binding.session.refreshToken != refreshToken) {
                error("Cloud request session changed")
            }
            val origin = binding?.location?.origin ?: configRepository.getCurrentBackendUrl()
            val rotated = apiClient.refreshSession(refreshToken)
            val result =
                if (rotated.exceptionOrNull() is UnsupportedOperationException) {
                    apiClient.refreshAccessToken(refreshToken).map { RefreshedSession(it, refreshToken = null) }
                } else {
                    rotated
                }

            result.onSuccess { session ->
                check(isCurrentOrigin(origin)) { "Cloud request origin changed" }
                secureStorage.putString(scopedKey(StorageKeys.ACCESS_TOKEN, origin), session.accessToken)
                session.refreshToken?.let { secureStorage.putString(scopedKey(StorageKeys.REFRESH_TOKEN, origin), it) }
            }

            result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Error refreshing the session")
            Result.failure(e)
        }
```

with the imports `app.logdate.client.sync.cloud.RefreshedSession` and `app.logdate.client.sync.cloud.SessionRefreshingAccountRepository`.

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncTokenRefresher.kt`:

1. Replace the constructor (lines 24-27) with:

```kotlin
internal class SyncTokenRefresher(
    private val sessionStorage: SessionStorage,
    private val cloudAccountRepository: CloudAccountRepository,
    private val revocationListener: DeviceSessionRevocationListener = DeviceSessionRevocationListener.None,
) {
```

2. Replace `refreshAndRetry` (lines 103-159) with:

```kotlin
    private suspend fun <T> refreshAndRetry(
        operation: suspend (accessToken: String) -> Result<T>,
        initialResult: Result<T>,
        currentSession: UserSession,
        binding: CloudRequestBinding?,
        locationProvider: CloudRequestLocationProvider?,
    ): Result<T> {
        Napier.i("Token expired (401), attempting refresh")
        if (binding != null && !isStillBound(binding, locationProvider)) return Result.failure(scopeChanged())
        val refreshResult = runBound(binding) { refreshSession(currentSession.refreshToken) }
        refreshResult.rethrowCancellation()
        val refreshed =
            refreshResult.getOrElse { refreshError ->
                Napier.e("Token refresh failed")
                endSessionIfRefused(refreshError, currentSession)
                return Result.failure(refreshError)
            }

        // A sign-out or a new sign-in may finish while the refresh request is in flight.
        if (sessionStorage.getSession() != currentSession) return initialResult

        // Keep the refreshed tokens. The account repository persists them under its own storage keys,
        // which the session storage never reads, so without this the session keeps handing out the
        // token the server just rejected, and a linked device loses the refresh token it rotated to.
        if (binding != null && !isStillBound(binding, locationProvider)) return Result.failure(scopeChanged())
        val updatedSession =
            currentSession.copy(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken ?: currentSession.refreshToken,
            )
        if (!saveRefreshedSession(binding, currentSession, updatedSession, locationProvider)) {
            return Result.failure(scopeChanged())
        }

        Napier.d("Token refreshed successfully, retrying")
        val refreshedBinding = binding?.let { CloudRequestBinding(it.location, updatedSession) }
        val retried = runBound(refreshedBinding) { operation(refreshed.accessToken) }
        retried.rethrowCancellation()
        if (refreshedBinding != null && !isStillBound(refreshedBinding, locationProvider)) {
            return Result.failure(scopeChanged())
        }
        return retried
    }

    private suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> {
        val rotating = cloudAccountRepository as? SessionRefreshingAccountRepository
        if (rotating != null) return rotating.refreshSession(refreshToken)
        return cloudAccountRepository.refreshAccessToken(refreshToken).map { RefreshedSession(it, refreshToken = null) }
    }

    /**
     * Settings observes this same session. A refused or revoked refresh token must stop looking signed
     * in, while an outage must keep credentials for the next attempt. A revoked device session is also
     * reported, so a linked watch can stop and erase what it holds.
     */
    private suspend fun endSessionIfRefused(
        refreshError: Throwable,
        currentSession: UserSession,
    ) {
        if (refreshError !is CloudApiException || refreshError.statusCode != 401) return
        if (refreshError.errorCode !in SESSION_ENDED_CODES) return
        if (sessionStorage.getSession() != currentSession) return
        sessionStorage.clearSession()
        if (refreshError.errorCode != DeviceSessionErrorCodes.DEVICE_SESSION_REVOKED) return
        Napier.w("The server revoked this device's session")
        revocationListener.onDeviceSessionRevoked()
    }
```

3. Add at the end of the file:

```kotlin

private val SESSION_ENDED_CODES =
    setOf(
        PasskeyApiErrorCodes.INVALID_REFRESH_TOKEN,
        PasskeyApiErrorCodes.REFRESH_TOKEN_REVOKED,
        DeviceSessionErrorCodes.DEVICE_SESSION_REVOKED,
    )
```

4. Add the imports `app.logdate.client.sync.cloud.DeviceSessionErrorCodes`, `app.logdate.client.sync.cloud.DeviceSessionRevocationListener`, `app.logdate.client.sync.cloud.RefreshedSession` and `app.logdate.client.sync.cloud.SessionRefreshingAccountRepository`.

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt`, the Step 2 parameter stays as written:

```kotlin
    private val diagnosticSource: () -> app.logdate.client.sync.diagnostics.DiagnosticSource? = { null },
    /** Told when the server revokes this device's session; a linked watch erases itself on it. */
    private val deviceSessionRevocationListener: DeviceSessionRevocationListener = DeviceSessionRevocationListener.None,
) : SyncManager {
```

and line 634 becomes:

```kotlin
    private val tokenRefresher = SyncTokenRefresher(sessionStorage, cloudAccountRepository, deviceSessionRevocationListener)
```

with the import `app.logdate.client.sync.cloud.DeviceSessionRevocationListener`.

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS, including every existing test in the three classes (`refresh token uses auth v1 path` still reads `new-access`).

- [ ] **Step 6: Run the module's checks, the full sync suite and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:testAndroidHostTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt --console=plain
```
Expected: BUILD SUCCESSFUL; `RefreshedTokenReuseTest`, `SyncSessionExpiryTest`, `ExpiredTokenDownloadTest` and `CloudOriginPinningTest` still pass.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task09-session-rotation.txt <<'EOF'
chore(sync): keep rotated refresh tokens and report revoked devices

A linked watch's session will slide forward each time it refreshes,
and the server may hand back a new refresh token when it does. Backup
now keeps that new token in the session and in the account's own
storage; a server that does not rotate keeps working as before.

When the server says a device's session was revoked, backup signs the
device out, as it already does for a refused refresh token, and tells
a listener, so a removed watch can stop and erase what it holds. The
revoked code now reaches the app instead of becoming an unknown error.
A refresh that cannot reach the server still keeps the session.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SessionRefresh.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudApiClient.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/LogDateCloudApiClient.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/SafeCloudErrorCode.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepository.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncTokenRefresher.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/SessionRotationTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/BasicCloudApiClientTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepositoryTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task09-session-rotation.txt
```

---

### Task 10: Filter uploads and remote notes for linked devices (C5)

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/RoutedSyncMetadataService.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/RemoteApplyFilter.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloadEngine.kt:55-69` (`DownloadStrategy`), `:209` (`skippedIds`), `:247-250` (the pending-local check), `:351-352` (`enqueueMedia`), `:703-713` (`conflictTimestamps`, moved out of the class)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloader.kt:51` (constructor), `:164, 182` (content strategy)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/metadata/RoutedSyncMetadataServiceTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/RemoteApplyFilterTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/WindowedDownloadTest.kt`

**Interfaces:**
- Consumes: `SyncMetadataService` (all members), `PendingUpload`, `EntityType`, `DownloadStrategy`, `SyncDownloadEngine.download`, `ChangesPage`, `DownloadInbox(dao, transactions, scope, now)`, `DownloadInbox.stage/count`, `WireDownload`, `DownloadInboxTest.Database`, `JournalNote.creationTimestamp`.
- Produces: `fun interface CloudEligibility { suspend fun isEligible(entityType: EntityType, pending: PendingUpload): Boolean }`; `class RoutedSyncMetadataService(delegate: SyncMetadataService, eligibleForCloud: CloudEligibility) : SyncMetadataService by delegate` (overrides `getPendingUploads` only); `fun interface RemoteApplyFilter { fun appliesNote(note: JournalNote): Boolean; companion object { val ALL } }`; `class RecentCaptureFilter(window: Duration, clock: Clock = Clock.System) : RemoteApplyFilter`; `DownloadStrategy(…, appliesRemoteCreate: (T) -> Boolean = { true }, enqueuesMediaRecovery: Boolean = true)`; `SyncDownloader(…, downloadsMedia: Boolean = true, remoteApplyFilter: RemoteApplyFilter = RemoteApplyFilter.ALL)`.

- [ ] **Step 1: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/metadata/RoutedSyncMetadataServiceTest.kt`:

```kotlin
package app.logdate.client.sync.metadata

import app.logdate.client.sync.test.fakeSyncMetadataService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests [RoutedSyncMetadataService]: an upload the router keeps away from the cloud is hidden from the
 * upload pass only, and still counts everywhere else.
 */
class RoutedSyncMetadataServiceTest {
    private val delegate = fakeSyncMetadataService()
    private val routed = RoutedSyncMetadataService(delegate) { _, pending -> pending.operation != PendingOperation.UPDATE }

    @Test
    fun `uploads that are not eligible are hidden from the upload pass`() =
        runTest {
            delegate.enqueuePending("created", EntityType.NOTE, PendingOperation.CREATE)
            delegate.enqueuePending("edited", EntityType.NOTE, PendingOperation.UPDATE)

            assertEquals(listOf("created"), routed.getPendingUploads(EntityType.NOTE).map { it.entityId })
        }

    @Test
    fun `a hidden upload still counts as pending for downloads and the status`() =
        runTest {
            delegate.enqueuePending("edited", EntityType.NOTE, PendingOperation.UPDATE)

            assertTrue(routed.hasPending(EntityType.NOTE, "edited"))
            assertEquals(1, routed.getPendingCount())
        }

    @Test
    fun `settling goes to the real outbox`() =
        runTest {
            delegate.enqueuePending("created", EntityType.NOTE, PendingOperation.CREATE)
            val pending = routed.getPendingUploads(EntityType.NOTE).single()

            assertTrue(routed.settleIfCurrent(EntityType.NOTE, pending, Instant.fromEpochMilliseconds(1), 1))
            assertTrue(delegate.getPendingUploads(EntityType.NOTE).isEmpty())
        }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/RemoteApplyFilterTest.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests the filters that decide which notes only the server has are stored on a device.
 */
class RemoteApplyFilterTest {
    @Test
    fun `every note applies by default`() {
        assertTrue(RemoteApplyFilter.ALL.appliesNote(note(Instant.fromEpochMilliseconds(0))))
    }

    @Test
    fun `a recent capture filter keeps notes inside its window`() {
        val filter = RecentCaptureFilter(window = 14.days, clock = FixedClock(NOW))

        assertTrue(filter.appliesNote(note(NOW)))
        assertTrue(filter.appliesNote(note(NOW - 14.days)))
        assertFalse(filter.appliesNote(note(NOW - 14.days - 1.milliseconds)))
    }

    private fun note(at: Instant) = JournalNote.Text(uid = Uuid.random(), creationTimestamp = at, lastUpdated = at, content = "x")

    private class FixedClock(
        private val now: Instant,
    ) : Clock {
        override fun now(): Instant = now
    }

    private companion object {
        val NOW = Instant.fromEpochMilliseconds(1_710_000_000_000)
    }
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/WindowedDownloadTest.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.WireDownload
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.lastWriteWinsResolver
import app.logdate.client.sync.test.testSyncTransactionManager
import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.ContentChange
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A linked watch keeps only a window of the account. A record outside it that only the server has is
 * skipped, never fetched again, while the download still moves past it and anything already on the
 * device stays current.
 */
class WindowedDownloadTest {
    @Test
    fun `a remote record outside the window is skipped and the cursor moves past it`() =
        runTest {
            val metadata = fakeSyncMetadataService()
            val inside = journal(version = 2)
            val outside = journal(version = 3)
            val created = mutableListOf<Journal>()
            val engine = downloadEngine(metadata)

            val result =
                engine.download(
                    strategy(
                        page = ChangesPage(listOf(inside, outside), emptyList(), Instant.fromEpochMilliseconds(50), hasMore = false),
                        local = emptyMap(),
                        created = created,
                        appliesRemoteCreate = { it.id == inside.id },
                    ),
                    "token",
                    Instant.fromEpochMilliseconds(0),
                )

            assertTrue(result.success)
            assertEquals(1, result.downloadedItems)
            assertEquals(listOf(inside), created)
            assertEquals(Instant.fromEpochMilliseconds(50), metadata.getLastSyncTime(EntityType.JOURNAL))
        }

    @Test
    fun `a record already on this device stays current outside the window`() =
        runTest {
            val local = journal(version = 1)
            val remote = local.copy(title = "edited elsewhere", syncVersion = 4, lastUpdated = Instant.fromEpochMilliseconds(9))
            val replaced = mutableListOf<Journal>()
            val engine = downloadEngine()

            engine.download(
                strategy(
                    page = ChangesPage(listOf(remote), emptyList(), Instant.fromEpochMilliseconds(50), hasMore = false),
                    local = mapOf(local.id to local),
                    replaced = replaced,
                    appliesRemoteCreate = { false },
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )

            assertEquals(listOf(remote), replaced)
        }

    @Test
    fun `an inbox record outside the window is settled without queueing its media`() =
        runTest {
            val id = Uuid.random()
            val database = DownloadInboxTest.Database()
            val inbox = stagedImage(database, id)
            val engine = downloadEngine(database = database, inbox = inbox)

            val result =
                engine.download(
                    strategy(
                        page = ChangesPage(listOf(journal(version = 20, id = id)), emptyList(), Instant.fromEpochMilliseconds(20), hasMore = false),
                        local = emptyMap(),
                        appliesRemoteCreate = { false },
                        entityType = EntityType.NOTE,
                    ),
                    "token",
                    Instant.fromEpochMilliseconds(0),
                )

            assertTrue(result.success)
            assertEquals(0, inbox.count())
        }

    @Test
    fun `media recovery can be left out for applied records`() =
        runTest {
            val id = Uuid.random()
            val database = DownloadInboxTest.Database()
            val inbox = stagedImage(database, id)
            val created = mutableListOf<Journal>()
            val engine = downloadEngine(database = database, inbox = inbox)

            engine.download(
                strategy(
                    page = ChangesPage(listOf(journal(version = 20, id = id)), emptyList(), Instant.fromEpochMilliseconds(20), hasMore = false),
                    local = emptyMap(),
                    created = created,
                    entityType = EntityType.NOTE,
                    enqueuesMediaRecovery = false,
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )

            assertEquals(1, created.size)
            assertEquals(0, inbox.count())
        }

    private fun downloadEngine(
        metadata: SyncMetadataService = fakeSyncMetadataService(),
        database: DownloadInboxTest.Database? = null,
        inbox: DownloadInbox? = null,
    ) = SyncDownloadEngine(
        transactionManager = database ?: testSyncTransactionManager(),
        syncMetadataService = metadata,
        conflictStore = InMemorySyncConflictStore(),
        mapCloudApiError = { SyncResult(false) },
        mapException = { _, _ -> SyncResult(false) },
        downloadInbox = inbox,
    )

    private suspend fun stagedImage(
        database: DownloadInboxTest.Database,
        id: Uuid,
    ): DownloadInbox {
        val inbox = DownloadInbox(database, database, { DownloadScope("owner", "server") }, { 1L })
        val wire =
            ContentChange(
                id.toString(),
                "IMAGE",
                mediaUri = "https://example.invalid/media/file",
                createdAt = 1,
                lastUpdated = 1,
                serverVersion = 20,
            )
        inbox.stage("NOTE", 20, listOf(WireDownload(id.toString(), 20, false, Json.encodeToString(ContentChange.serializer(), wire))))
        return inbox
    }

    private fun strategy(
        page: ChangesPage<Journal>,
        local: Map<Uuid, Journal>,
        created: MutableList<Journal> = mutableListOf(),
        replaced: MutableList<Journal> = mutableListOf(),
        appliesRemoteCreate: (Journal) -> Boolean = { true },
        entityType: EntityType = EntityType.JOURNAL,
        enqueuesMediaRecovery: Boolean = true,
    ) = DownloadStrategy(
        entityType = entityType,
        logLabel = "journal",
        fetchChanges = { _, _ -> Result.success(page) },
        localItems = { local },
        idOf = { it.id },
        syncVersionOf = { it.syncVersion },
        lastUpdatedOf = { it.lastUpdated },
        conflictResolver = lastWriteWinsResolver(),
        applyCreate = { created += it },
        applyReplace = { _, replacement -> replaced += replacement },
        applyDelete = { error("Unexpected deletion") },
        appliesRemoteCreate = appliesRemoteCreate,
        enqueuesMediaRecovery = enqueuesMediaRecovery,
    )

    private fun journal(
        version: Long,
        id: Uuid = Uuid.random(),
    ) = Journal(
        id = id,
        title = "journal",
        created = Instant.fromEpochMilliseconds(1),
        lastUpdated = Instant.fromEpochMilliseconds(1),
        syncVersion = version,
    )
}
```

- [ ] **Step 2: Add the API surface with inert bodies**

Create `RemoteApplyFilter.kt` in full except `RecentCaptureFilter.appliesNote` returns `true`. Create `RoutedSyncMetadataService.kt` with `getPendingUploads` returning `delegate.getPendingUploads(entityType)` unfiltered. Add the two `DownloadStrategy` parameters (unused by the engine) and the two `SyncDownloader` parameters (unused).

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.metadata.RoutedSyncMetadataServiceTest' --tests 'app.logdate.client.sync.RemoteApplyFilterTest' --tests 'app.logdate.client.sync.WindowedDownloadTest' --console=plain
```
Expected: FAIL: `uploads that are not eligible are hidden from the upload pass` (`expected:<[created]> but was:<[created, edited]>`), `a recent capture filter keeps notes inside its window`, `a remote record outside the window is skipped and the cursor moves past it` (`expected:<1> but was:<2>`), `an inbox record outside the window is settled without queueing its media` and `media recovery can be left out for applied records` (`expected:<0> but was:<1>`, the waiting `MEDIA_NOTE` row). The other four pass already.

- [ ] **Step 4: Implement**

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/RoutedSyncMetadataService.kt`:

```kotlin
package app.logdate.client.sync.metadata

/** Whether a queued change may go straight to the cloud from this device now. */
fun interface CloudEligibility {
    suspend fun isEligible(
        entityType: EntityType,
        pending: PendingUpload,
    ): Boolean
}

/**
 * The outbox as a linked device's upload pass sees it: changes its router keeps away from the cloud,
 * such as one its phone will relay, are left out of [getPendingUploads] and nothing else. They still
 * count as pending, so downloads never overwrite them and the status still shows them.
 */
class RoutedSyncMetadataService(
    private val delegate: SyncMetadataService,
    private val eligibleForCloud: CloudEligibility,
) : SyncMetadataService by delegate {
    override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> =
        delegate.getPendingUploads(entityType).filter { eligibleForCloud.isEligible(entityType, it) }
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/RemoteApplyFilter.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Decides whether a note that only the server has is stored on this device. A note already here is
 * always updated, and the download moves past a skipped note so it is not fetched again.
 */
fun interface RemoteApplyFilter {
    fun appliesNote(note: JournalNote): Boolean

    companion object {
        val ALL = RemoteApplyFilter { true }
    }
}

/** Keeps notes captured within [window] of now, the simplest library window a watch can hold. */
class RecentCaptureFilter(
    private val window: Duration,
    private val clock: Clock = Clock.System,
) : RemoteApplyFilter {
    override fun appliesNote(note: JournalNote): Boolean = note.creationTimestamp >= clock.now() - window
}
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloadEngine.kt`:

1. In `DownloadStrategy`, after `val localItem: suspend (Uuid) -> T? = { id -> localItems()[id] },` add:

```kotlin
    /** Whether a record only the server has is stored here; a record already here is always updated. */
    val appliesRemoteCreate: (T) -> Boolean = { true },
    /** Whether an applied record's media is queued for download; off where media is fetched on demand. */
    val enqueuesMediaRecovery: Boolean = true,
```

2. After `val skippedIds = mutableSetOf<String>()` add `val outsideWindowIds = mutableSetOf<String>()`.

3. After the block

```kotlin
                                        if (existing == null && pendingLocal) {
                                            skippedIds += id.toString()
                                            return@record
                                        }
```

add:

```kotlin
                                        if (existing == null && !strategy.appliesRemoteCreate(item)) {
                                            outsideWindowIds += id.toString()
                                            return@record
                                        }
```

4. Replace

```kotlin
                                            enqueueMedia =
                                                row.entityId !in skippedIds,
```

with:

```kotlin
                                            enqueueMedia =
                                                strategy.enqueuesMediaRecovery &&
                                                    row.entityId !in skippedIds &&
                                                    row.entityId !in outsideWindowIds,
```

5. Move `conflictTimestamps` (lines 703-713) out of the class, unchanged except for `private fun` at file level, after the class's closing brace. The class shrinks by as many lines as the additions above, staying under detekt's 600-line limit.

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloader.kt`:

1. After `private val downloadInbox: app.logdate.client.sync.recovery.DownloadInbox? = null,` add:

```kotlin
    private val downloadsMedia: Boolean = true,
    private val remoteApplyFilter: RemoteApplyFilter = RemoteApplyFilter.ALL,
```

2. In `downloadContent`, replace the `hydrate = …` line with:

```kotlin
                    hydrate = { token, note ->
                        if (downloadInbox == null && downloadsMedia) mediaTransfer.downloadIfNeeded(token, note) else note
                    },
```

and after `afterDelete = { id -> mediaSyncRefStore.delete(id) },` add:

```kotlin
                    appliesRemoteCreate = remoteApplyFilter::appliesNote,
                    enqueuesMediaRecovery = downloadsMedia,
```

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS: 3 routing tests, 2 filter tests, 4 download tests.

- [ ] **Step 6: Run the full sync suite, its checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:testAndroidHostTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt --console=plain
```
Expected: BUILD SUCCESSFUL, with every existing download test (`DurableDownloadIntegrationTest`, `MediaHydrationTest`, `AutomaticDownloadIntegrationTest`, …) passing unchanged, since both new strategy options default to today's behavior.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-routing-and-window.txt <<'EOF'
chore(sync): filter uploads and remote notes for linked devices

A watch holds only part of the journal and sends some changes through
its phone instead of the cloud. Two parts of the sync engine can now
reflect that. An outbox view hides from the upload pass the changes a
router keeps away from the cloud, while they still count as waiting,
so downloads never overwrite them and the status still shows them.

Downloads can skip notes outside a device's window that it does not
already hold, without fetching them again later, and can leave media
for later instead of queueing it. Notes already on the device stay
current. Phones, iOS and the desktop app keep downloading everything.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/RoutedSyncMetadataService.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/RemoteApplyFilter.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloadEngine.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncDownloader.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/metadata/RoutedSyncMetadataServiceTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/RemoteApplyFilterTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/WindowedDownloadTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-routing-and-window.txt
```

---

### Task 11: Add a linked-watch profile to the sync engine (C5)

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncProfile.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/ConditionalCreateSettler.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt` (constructor, `statusPublisher` at :120-136, `isEnabled` at :118, `provisionIdentityKey` at :191-194, the location history calls at :260, :338, :385 and :588, `syncDrafts` at :536-553, `fullSync` at :558, `recoverMedia` at :653-657, `downloader` at :699-719, `uploader` at :721-743)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncStatusPublisher.kt:45-60, 232-234`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncManager.kt:185-213` (`SyncPausedReason`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncUploader.kt:44-73, 167-188, 330-332, 420`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/AssociationUploader.kt:21-27, 101-106`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/SyncSettingsScreen.kt:72, 477-501`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/sync/SyncStatusSheet.kt:62, 407-425`
- Modify: `client/feature/core/src/commonMain/composeResources/values/strings_sync.xml:220`
- Modify: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt` (`testDefaultSyncManager`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/LinkedWatchProfileTest.kt`

**Interfaces:**
- Consumes: Task 6 `IdentityKeyManager(…, mintPolicy = IdentityMintPolicy.NEVER)`, `installLinkedIdentityKey`, `IdentityKeyFingerprint`; Task 8 `ConditionalCreateResult`, `CloudContentDataSource.createNoteIfAbsent`, `CloudJournalDataSource.createJournalIfAbsent`, `CloudAssociationDataSource.uploadAssociationsIfAbsent`, `FakeCloudApiClient.createContentIfAbsentResponse/createContentIfAbsentCalls`; Task 10 `RemoteApplyFilter`, `SyncDownloader(downloadsMedia, remoteApplyFilter)`; existing `SyncRetryCoordinator.handleRetryFailure(entityType, pending, error, permanent = false): Boolean` and `markUploadSettled(entityType, pending, syncedAt, version)` (`SyncRetryCoordinator.kt:157-162, 354-360`), `SyncableJournalRepository.updateSyncMetadata(journalId, syncVersion, syncedAt)`, `SyncableJournalNotesRepository.updateSyncMetadata(note, syncVersion, syncedAt)`, `deleteFromSync(id)`, `MediaSyncRefStore.deleteScoped(noteId, scope)`.
- Produces: `sealed interface SyncProfile { mintsIdentityKey; sweepsOnFirstSync; downloadsMedia; uploadsUpdates; createsConditionally; syncsDrafts; syncsLocationHistory; remoteApplyFilter; data object Standard; data class LinkedWatch(remoteApplyFilter: RemoteApplyFilter) }`; `internal suspend fun bindLinkedIdentityKey(manager: IdentityKeyManager, accountId: String?, recoveryNeeded: IdentityRecoveryNeededStore)`; `internal class ConditionalCreateSettler(retryCoordinator, recordProgress) { suspend fun settle(entityType, pending, result, errors, onCreated, onDeleted): Int }`; `SyncPausedReason.NEEDS_DEVICE_LINK`; `DefaultSyncManager(…, profile: SyncProfile = SyncProfile.Standard)`; `SyncStatusPublisher(…, linkedDevice: Boolean = false, identityKeyMissing: suspend () -> Boolean = { false })`; `SyncUploader(…, profile: SyncProfile = SyncProfile.Standard)`; `AssociationUploader(…, createsIfAbsent: Boolean = false)`; string `sync_paused_needs_device_link`; `testDefaultSyncManager(…, profile = …)`.

- [ ] **Step 1: Write the failing profile tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/LinkedWatchProfileTest.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityMintPolicy
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.ContentChange
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A linked watch runs the same sync engine as the phone with a different profile: it never creates an
 * identity key, never queues what it already holds, uploads only creates (if absent) and deletions,
 * leaves media for later, keeps only a window of remote notes, and has no drafts.
 */
class LinkedWatchProfileTest {
    private val everything = SyncProfile.LinkedWatch(RemoteApplyFilter.ALL)

    @Test
    fun `a linked watch without a key pauses for its phone and creates none`() =
        runTest {
            val identity = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager(), mintPolicy = IdentityMintPolicy.NEVER)
            val api = fakeCloudApiClient()
            val manager = testDefaultSyncManager(identityKeyManager = identity, cloudApiClient = api, profile = everything)

            manager.fullSync()

            assertFalse(identity.hasIdentityKey())
            assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, manager.getSyncStatus().pausedReason)
            assertTrue(api.methodCalls.isEmpty(), "a linked watch never asks whether the account has data")
        }

    @Test
    fun `a linked watch with its phone's key is not paused`() =
        runTest {
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(InMemorySecureStorage(), crypto, mintPolicy = IdentityMintPolicy.NEVER)
            val key = ByteArray(32) { 0x31 }
            identity.installLinkedIdentityKey(key, IdentityKeyFingerprint.of(key, crypto))
            val manager = testDefaultSyncManager(identityKeyManager = identity, profile = everything)

            manager.fullSync()

            assertNull(manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `a linked watch without a session waits for its phone`() =
        runTest {
            val manager = testDefaultSyncManager(sessionStorage = fakeSessionStorage(authenticated = false), profile = everything)

            assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `a linked watch does not queue what it already holds`() =
        runTest {
            val api = fakeCloudApiClient()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = fakeJournalNotesRepository("captured before linking"),
                    profile = everything,
                )

            manager.fullSync()

            assertTrue(api.uploadContentCalls.isEmpty())
            assertTrue(api.createContentIfAbsentCalls.isEmpty())
        }

    @Test
    fun `a linked watch creates notes only if absent`() =
        runTest {
            val fixture = queuedNote(PendingOperation.CREATE)

            fixture.manager.uploadPendingChanges()

            assertEquals(listOf(fixture.noteId.toString()), fixture.api.createContentIfAbsentCalls.map { it.id })
            assertTrue(fixture.api.uploadContentCalls.isEmpty())
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `an edit made on a linked watch stays queued for the phone`() =
        runTest {
            val fixture = queuedNote(PendingOperation.UPDATE)

            val result = fixture.manager.uploadPendingChanges()

            assertTrue(result.success)
            assertTrue(fixture.api.updateContentCalls.isEmpty())
            assertEquals(PendingOperation.UPDATE, fixture.metadata.getPendingUploads(EntityType.NOTE).single().operation)
        }

    @Test
    fun `a note the server already has is settled without taking its version`() =
        runTest {
            val fixture = queuedNote(PendingOperation.CREATE)
            fixture.api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.AlreadyExists(9))

            fixture.manager.uploadPendingChanges()

            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
            assertEquals(0L, fixture.notes.getNoteById(fixture.noteId)?.syncVersion)
        }

    @Test
    fun `a note deleted on another device is removed from the watch`() =
        runTest {
            val fixture = queuedNote(PendingOperation.CREATE)
            fixture.api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(5)))

            fixture.manager.uploadPendingChanges()

            assertNull(fixture.notes.getNoteById(fixture.noteId))
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `a create that fails stays queued`() =
        runTest {
            val fixture = queuedNote(PendingOperation.CREATE)
            fixture.api.createContentIfAbsentResponse = Result.failure(CloudApiException("SERVICE_UNAVAILABLE", "down", statusCode = 503))

            val result = fixture.manager.uploadPendingChanges()

            assertFalse(result.success)
            assertEquals(1, fixture.metadata.getPendingUploads(EntityType.NOTE).size)
        }

    @Test
    fun `a linked watch never syncs drafts`() =
        runTest {
            val api = fakeCloudApiClient()
            val manager = testDefaultSyncManager(cloudDraftDataSource = DefaultCloudDraftDataSource(api), profile = everything)

            manager.fullSync()

            assertFalse("getDraftChanges" in api.methodCalls)
        }

    @Test
    fun `a linked watch leaves media for later`() =
        runTest {
            val api = fakeCloudApiClient()
            api.getContentChangesResponse = Result.success(changes(type = "IMAGE", mediaUri = "https://example.com/api/v1/media/image-media/binary"))
            val notes = FakeJournalNotesRepository()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(api),
                    journalNotesRepository = notes,
                    profile = everything,
                )

            manager.downloadRemoteChanges()

            assertEquals(1, notes.allNotesObserved.first().size)
            assertTrue(api.downloadMediaCalls.isEmpty())
        }

    @Test
    fun `remote notes outside the window are skipped while the download moves on`() =
        runTest {
            val api = fakeCloudApiClient()
            api.getContentChangesResponse = Result.success(changes(type = "TEXT", mediaUri = null))
            val notes = FakeJournalNotesRepository()
            val metadata = fakeSyncMetadataService()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    profile = SyncProfile.LinkedWatch(RemoteApplyFilter { false }),
                )

            manager.downloadRemoteChanges()

            assertTrue(notes.allNotesObserved.first().isEmpty())
            assertEquals(Instant.fromEpochMilliseconds(CHANGED_AT), metadata.getLastSyncTime(EntityType.NOTE))
        }

    private suspend fun queuedNote(operation: PendingOperation): Fixture {
        val api = fakeCloudApiClient()
        val notes = fakeJournalNotesRepository("from the watch")
        val metadata = fakeSyncMetadataService()
        val noteId = notes.allNotesObserved.first().single().uid
        metadata.enqueuePending(noteId.toString(), EntityType.NOTE, operation)
        val manager =
            testDefaultSyncManager(
                cloudContentDataSource = DefaultCloudContentDataSource(api),
                journalNotesRepository = notes,
                syncMetadataService = metadata,
                profile = everything,
            )
        return Fixture(api, notes, metadata, noteId, manager)
    }

    private fun changes(
        type: String,
        mediaUri: String?,
    ) = ContentChangesResponse(
        changes =
            listOf(
                ContentChange(
                    id = Uuid.random().toString(),
                    type = type,
                    content = if (mediaUri == null) "remote text" else null,
                    mediaUri = mediaUri,
                    createdAt = CHANGED_AT,
                    lastUpdated = CHANGED_AT,
                    serverVersion = 1,
                ),
            ),
        deletions = emptyList(),
        lastTimestamp = CHANGED_AT,
    )

    private class Fixture(
        val api: FakeCloudApiClient,
        val notes: FakeJournalNotesRepository,
        val metadata: FakeSyncMetadataService,
        val noteId: Uuid,
        val manager: DefaultSyncManager,
    )

    private companion object {
        const val CHANGED_AT = 1_710_000_000_000
    }
}
```

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt`, add the parameter `profile: SyncProfile = SyncProfile.Standard,` after `deviceSessionRevocationListener` in `testDefaultSyncManager`, pass `profile = profile,` in both `DefaultSyncManager(…)` calls, and import `app.logdate.client.sync.SyncProfile`.

- [ ] **Step 2: Add the API surface with inert bodies**

Create `SyncProfile.kt` in full and `ConditionalCreateSettler.kt` with `settle` returning `0`. Add `NEEDS_DEVICE_LINK` to `SyncPausedReason` with its branches in the two feature screens and its string (all as in Step 4). Add the `profile` parameter to `DefaultSyncManager`, `SyncUploader` and the `createsIfAbsent` parameter to `AssociationUploader`, and the two `SyncStatusPublisher` parameters, all unused.

- [ ] **Step 3: Run the tests and watch them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.LinkedWatchProfileTest' --console=plain
```
Expected: FAIL: every test except `a linked watch with its phone's key is not paused`, which already holds for a standard device. For example `a linked watch without a key pauses for its phone and creates none` (`expected:<NEEDS_DEVICE_LINK> but was:<null>`), `a linked watch does not queue what it already holds` (the first-sync sweep queues the entry and it is uploaded), `a linked watch creates notes only if absent` (`expected:<[…]> but was:<[]>`), `a linked watch leaves media for later` (one `downloadMedia` call).

- [ ] **Step 4: Implement the profile**

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncProfile.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.metadata.IdentityRecoveryNeededStore
import io.github.aakira.napier.Napier

/** How [DefaultSyncManager] behaves on one kind of device. */
sealed interface SyncProfile {
    /** Whether this device may create an identity key; one that may not waits until it is linked. */
    val mintsIdentityKey: Boolean

    /** Whether the first sync with a server queues everything already on the device. */
    val sweepsOnFirstSync: Boolean

    /** Whether media downloads with the records it belongs to. */
    val downloadsMedia: Boolean

    /** Whether edits upload from this device; a linked watch leaves them for its phone to relay. */
    val uploadsUpdates: Boolean

    /** Whether creates upload as create-if-absent, so a record the phone already uploaded is never overwritten. */
    val createsConditionally: Boolean

    val syncsDrafts: Boolean

    val syncsLocationHistory: Boolean

    val remoteApplyFilter: RemoteApplyFilter

    /** A phone, tablet or computer someone signed in on. */
    data object Standard : SyncProfile {
        override val mintsIdentityKey = true
        override val sweepsOnFirstSync = true
        override val downloadsMedia = true
        override val uploadsUpdates = true
        override val createsConditionally = false
        override val syncsDrafts = true
        override val syncsLocationHistory = true
        override val remoteApplyFilter = RemoteApplyFilter.ALL
    }

    /**
     * A watch its phone linked to the account. It never creates an identity key, starts from nothing
     * instead of queueing what it holds, keeps media for later, uploads only creates (if absent) and
     * deletions, keeps only the remote notes [remoteApplyFilter] allows, and has no drafts or location
     * history.
     */
    data class LinkedWatch(
        override val remoteApplyFilter: RemoteApplyFilter,
    ) : SyncProfile {
        override val mintsIdentityKey = false
        override val sweepsOnFirstSync = false
        override val downloadsMedia = false
        override val uploadsUpdates = false
        override val createsConditionally = true
        override val syncsDrafts = false
        override val syncsLocationHistory = false
    }
}

/**
 * A linked device only ever uses the key its phone installed. Without one, sync waits for the link;
 * with one that belongs to another account, sync stops until the device is linked again.
 */
internal suspend fun bindLinkedIdentityKey(
    manager: IdentityKeyManager,
    accountId: String?,
    recoveryNeeded: IdentityRecoveryNeededStore,
) {
    if (!manager.hasIdentityKey()) {
        Napier.w("This device has no identity key yet; sync waits for its phone to link it")
        return
    }
    if (accountId != null && !manager.bindToAccount(accountId)) {
        Napier.e("This device's identity key belongs to another account; sync waits until it is linked again")
        recoveryNeeded.setNeeded(true)
        return
    }
    if (recoveryNeeded.isNeeded()) recoveryNeeded.setNeeded(false)
}
```

`client/sync/src/commonMain/kotlin/app/logdate/client/sync/ConditionalCreateSettler.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.SyncUploadResult
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingUpload
import io.github.aakira.napier.Napier
import kotlin.time.Clock

/**
 * Settles a create-if-absent upload. Created: the server's version is recorded. Already there: the
 * create is done and the next download brings the server's copy, so the local version is left alone.
 * Deleted on another device: the record is removed here too instead of brought back. A failure is
 * counted and retried like any other upload.
 */
internal class ConditionalCreateSettler(
    private val retryCoordinator: SyncRetryCoordinator,
    private val recordProgress: (Int) -> Unit,
) {
    /** Returns 1 when the record is on the server afterwards, 0 otherwise. */
    suspend fun settle(
        entityType: EntityType,
        pending: PendingUpload,
        result: Result<ConditionalCreateResult>,
        errors: MutableList<SyncError>,
        onCreated: suspend (SyncUploadResult) -> Unit,
        onDeleted: suspend () -> Unit,
    ): Int {
        val outcome =
            result.getOrElse { error ->
                val movedToDeadLetter = retryCoordinator.handleRetryFailure(entityType, pending, error)
                errors.add(
                    SyncError(
                        SyncErrorType.SERVER_ERROR,
                        "Failed to create ${entityType.name.lowercase()} ${pending.entityId}: ${error.message}",
                        error,
                        retryable = !movedToDeadLetter,
                    ),
                )
                Napier.w("Failed to create a record if absent")
                return 0
            }
        recordProgress(1)
        return when (outcome) {
            is ConditionalCreateResult.Created -> {
                onCreated(SyncUploadResult(outcome.serverVersion, outcome.syncedAt))
                retryCoordinator.markUploadSettled(entityType, pending, outcome.syncedAt, outcome.serverVersion)
                Napier.d("Created a record")
                1
            }
            is ConditionalCreateResult.AlreadyExists -> {
                retryCoordinator.markUploadSettled(entityType, pending, Clock.System.now(), outcome.serverVersion)
                Napier.i("The record was already on the server; the next download brings its copy")
                1
            }
            is ConditionalCreateResult.Deleted -> {
                onDeleted()
                retryCoordinator.markUploadSettled(entityType, pending, outcome.deletedAt, 0L)
                Napier.w("The record was deleted on another device; it is removed here instead of uploaded")
                0
            }
        }
    }
}
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncManager.kt`, add after `NEEDS_RECOVERY_PHRASE,` in `SyncPausedReason`:

```kotlin

    /**
     * A linked watch has no session or identity key from its phone yet, or one that no longer
     * matches its account. It never makes its own; sync waits until the phone links it again.
     */
    NEEDS_DEVICE_LINK,
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncStatusPublisher.kt`:

1. After `private val pendingRecoveryCount: suspend () -> Int = { 0 },` add:

```kotlin
    private val linkedDevice: Boolean = false,
    private val identityKeyMissing: suspend () -> Boolean = { false },
```

2. Replace the first two lines of `currentPausedReason` (lines 233-234) with:

```kotlin
        if (!authenticated) return if (linkedDevice) SyncPausedReason.NEEDS_DEVICE_LINK else SyncPausedReason.NOT_SIGNED_IN
        if (linkedDevice && (identityKeyMissing() || identityRecoveryNeededStore.isNeeded())) return SyncPausedReason.NEEDS_DEVICE_LINK
        if (identityRecoveryNeededStore.isNeeded()) return SyncPausedReason.NEEDS_RECOVERY_PHRASE
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt`:

1. Before the `deviceSessionRevocationListener` parameter add:

```kotlin
    /** How this device syncs; a linked watch uses [SyncProfile.LinkedWatch]. */
    private val profile: SyncProfile = SyncProfile.Standard,
```

2. After `private var isEnabled = true` add:

```kotlin

    private val historyEngine = locationHistorySyncEngine.takeIf { profile.syncsLocationHistory }
```

and replace `locationHistorySyncEngine` with `historyEngine` in `accountAlreadyHasCloudData` (`locationHistorySyncEngine?.hasRemoteRecords(accessToken) == true`), `uploadPendingChanges` (`locationHistorySyncEngine?.upload(accessToken)`), `downloadRemoteChanges` (`locationHistorySyncEngine?.download(accessToken)`) and `isLocationHistorySyncEnabled()`.

3. In the `SyncStatusPublisher(…)` construction, after `pendingRecoveryCount = { downloadInbox?.count() ?: 0 },` add:

```kotlin
            linkedDevice = !profile.mintsIdentityKey,
            identityKeyMissing = { identityKeyManager?.hasIdentityKey() == false },
```

4. In `provisionIdentityKey`, after `val accountId = sessionStorage.getSession()?.accountId` add:

```kotlin
        if (!profile.mintsIdentityKey) return bindLinkedIdentityKey(manager, accountId, identityRecoveryNeededStore)
```

5. Replace `override suspend fun syncDrafts(): SyncResult =` and its body (lines 536-553) with:

```kotlin
    override suspend fun syncDrafts(): SyncResult {
        if (!profile.syncsDrafts) return SyncResult(success = true)
        return syncEntityType(
            entityType = EntityType.DRAFT,
            quotaContext = "draft sync",
            disabledOrUnauthenticated = {
                if (!isEnabled || !tokenRefresher.isAuthenticated()) SyncResult(success = false) else null
            },
            download = downloader::downloadDrafts,
            upload = uploader::uploadDrafts,
            updatesGlobalSyncState = false,
            onException = { e ->
                Napier.e("Draft sync failed")
                SyncResult(
                    success = false,
                    errors = listOf(SyncError(SyncErrorType.UNKNOWN_ERROR, "Draft sync failed")),
                )
            },
        )
    }
```

6. In `uploadPendingChanges`, replace `val draftResult = uploader.uploadDrafts(accessToken)` with:

```kotlin
            val draftResult = if (profile.syncsDrafts) uploader.uploadDrafts(accessToken) else SyncResult(success = true)
```

   In `fullSync`, replace `uploader.enqueueEverythingOnFirstSync()` with:

```kotlin
            if (profile.sweepsOnFirstSync) uploader.enqueueEverythingOnFirstSync()
```

7. In `recoverMedia`, before `val inbox = downloadInbox ?: return` add:

```kotlin
        if (!profile.downloadsMedia) return
```

8. In the `SyncDownloader(…)` construction, after `downloadInbox = downloadInbox,` add:

```kotlin
            downloadsMedia = profile.downloadsMedia,
            remoteApplyFilter = profile.remoteApplyFilter,
```

9. In the `SyncUploader(…)` construction, after `setMediaDeferredForNetwork = statusPublisher::setMediaDeferredForNetwork,` add:

```kotlin
            profile = profile,
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/AssociationUploader.kt`:

1. Add the constructor parameter after `private val recordProgress: (Int) -> Unit,`:

```kotlin
    /** Creates links only if the server has none, for a linked watch. */
    private val createsIfAbsent: Boolean = false,
```

2. In `uploadCreates`, replace `{ token -> cloudAssociationDataSource.uploadAssociations(token, batch.associations) },` with:

```kotlin
                { token ->
                    if (createsIfAbsent) {
                        cloudAssociationDataSource.uploadAssociationsIfAbsent(token, batch.associations)
                    } else {
                        cloudAssociationDataSource.uploadAssociations(token, batch.associations)
                    }
                },
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncUploader.kt`:

1. After `draftRepairScope: () -> DownloadScope? = { null },` add:

```kotlin
    private val profile: SyncProfile = SyncProfile.Standard,
```

2. In the `AssociationUploader(…)` construction, after `recordProgress = recordProgress,` add `createsIfAbsent = profile.createsConditionally,`, and after the `draftUploader` property add:

```kotlin

    private val conditionalCreates = ConditionalCreateSettler(retryCoordinator, recordProgress)
```

3. In `uploadJournals`, at the top of the `PendingOperation.CREATE, PendingOperation.UPDATE -> {` branch, before `val journal = journalsById[pending.entityId]`, add:

```kotlin
                        if (pending.operation == PendingOperation.UPDATE && !profile.uploadsUpdates) {
                            Napier.d("Leaving a journal edit for the phone to relay")
                            continue
                        }
```

and after that branch's `if (!retryCoordinator.beginAttempt(EntityType.JOURNAL, pending, errors)) continue` add:

```kotlin
                        if (pending.operation == PendingOperation.CREATE && profile.createsConditionally) {
                            val created =
                                tokenRefresher.withFreshToken(
                                    { token -> cloudJournalDataSource.createJournalIfAbsent(token, journal) },
                                    "createJournalIfAbsent(${journal.id})",
                                    expectedScope = pending.scope,
                                )
                            uploadedCount +=
                                conditionalCreates.settle(
                                    entityType = EntityType.JOURNAL,
                                    pending = pending,
                                    result = created,
                                    errors = errors,
                                    onCreated = { upload ->
                                        syncableRepository?.updateSyncMetadata(journalId, upload.serverVersion, upload.syncedAt)
                                    },
                                    onDeleted = { syncableRepository?.deleteFromSync(journalId) },
                                )
                            continue
                        }
```

4. In `uploadContent`, at the top of the `PendingOperation.CREATE, PendingOperation.UPDATE -> {` branch, before `val note = notesById[pending.entityId]`, add:

```kotlin
                        if (pending.operation == PendingOperation.UPDATE && !profile.uploadsUpdates) {
                            Napier.d("Leaving an entry edit for the phone to relay")
                            continue
                        }
```

and directly before `val result =` (line 420, after `uploadReadyNote` is computed) add:

```kotlin
                        if (pending.operation == PendingOperation.CREATE && profile.createsConditionally) {
                            val created =
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.createNoteIfAbsent(token, uploadReadyNote) },
                                    "createNoteIfAbsent(${note.uid})",
                                    expectedScope = pending.scope,
                                )
                            uploadedCount +=
                                conditionalCreates.settle(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    result = created,
                                    errors = errors,
                                    onCreated = { upload ->
                                        syncableRepository?.updateSyncMetadata(note, upload.serverVersion, upload.syncedAt)
                                    },
                                    onDeleted = {
                                        syncableRepository?.deleteFromSync(noteId)
                                        mediaSyncRefStore.deleteScoped(noteId, pending.scope)
                                    },
                                )
                            continue
                        }
```

In `client/feature/core/src/commonMain/composeResources/values/strings_sync.xml`, after `sync_paused_needs_recovery_phrase_fix` (line 220) add:

```xml
    <string name="sync_paused_needs_device_link">Paused — set up this device from your phone</string>
```

In `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/SyncSettingsScreen.kt` and `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/sync/SyncStatusSheet.kt`:

1. Add the import `logdate.client.feature.core.generated.resources.sync_paused_needs_device_link` before `…sync_paused_needs_recovery_phrase`.
2. In the text `when`, after the `SyncPausedReason.NEEDS_RECOVERY_PHRASE -> …` branch add:

```kotlin
                            SyncPausedReason.NEEDS_DEVICE_LINK -> stringResource(Res.string.sync_paused_needs_device_link)
```

(indented to match the surrounding branches in each file).
3. In the color `when`, add `SyncPausedReason.NEEDS_DEVICE_LINK,` after `SyncPausedReason.NEEDS_RECOVERY_PHRASE,` in the group that maps to `MaterialTheme.colorScheme.error`.

- [ ] **Step 5: Run the tests again**

Run the Step 3 command again.
Expected: PASS, 12 tests.

- [ ] **Step 6: Run every phone-side sync test, the checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:testAndroidHostTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt :client:feature:core:desktopTest :client:feature:core:compileKotlinIosSimulatorArm64 :client:feature:core:ktlintCheck :integration:server-client-e2e:test --console=plain
```
Expected: BUILD SUCCESSFUL. Every existing `:client:sync` test passes unchanged (`IdentityKeyProvisioningTest`, `FirstSyncBackfillTest`, `FirstSyncEnqueueOnceTest`, `DraftSyncIntegrationTest`, `LocationHistorySyncEngineTest`, `MediaHydrationTest`, …), because `SyncProfile.Standard` is the default everywhere; `detekt` reports nothing new for `DefaultSyncManager` (about 545 lines of code), `SyncDownloadEngine` or `SyncUploader`.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task11-linked-watch-profile.txt <<'EOF'
chore(sync): add a linked-watch profile to the sync engine

The watch will back up with the same engine as the phone, under a
profile for a linked device. It never creates an identity key: without
the key its phone installs, backup pauses and says the device needs to
be set up from the phone. It does not queue what it already holds,
leaves media for later, keeps only the remote entries its window
allows while still moving past the rest, and has no drafts or location
history.

It uploads only new records and deletions. New records go up as
create-if-absent: one the server already has is settled without taking
its version, and one deleted on another device is removed from the
watch instead of brought back. Edits wait for the phone to relay them.
Phones, iOS and the desktop app keep the standard profile and behave
exactly as before; nothing uses the new profile yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncProfile.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/ConditionalCreateSettler.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncStatusPublisher.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncManager.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncUploader.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/AssociationUploader.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/SyncSettingsScreen.kt client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/sync/SyncStatusSheet.kt client/feature/core/src/commonMain/composeResources/values/strings_sync.xml client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/LinkedWatchProfileTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task11-linked-watch-profile.txt
```

---

## Self-Review

**Spec coverage.** Every item of the master plan's "A-foundations" (C1–C5) and the matching parts of the spec's "How sync works" map to a task:

| Item | Task | Covered by |
|---|---|---|
| C1 `WearProtocol` (versions, `logdate_phone_sync_v2`/`logdate_watch_sync_v2`, the ten feature names) | 1 | `WearProtocol`, `WearFeatures`; a test pins every name |
| C1 `WearPaths` | 1 | Window, tombstone, watch-record and body-channel paths, the receipts, link, live, reminders, presence, diagnostics and favorites paths; a test proves none reaches a v1 listener |
| C1 `RecordEnvelope` (schema, kind, id, contentVersion, syncVersion, origin, reasons, priority, `enc` reserved, body/bodyRef/media) | 1 | `RecordEnvelope`, `RecordEnvelopeCodec` (90 KB inline limit, both-bodies refusal, `enc` → Unsupported); golden JSON |
| C1 `Receipts` (+ `CloudPresence`) | 1 | `ReceiptState`, `CloudPresence`, request and response, `Receipt.covers`; golden JSON |
| C1 `LinkMessages`, `LiveMessages` | 1 | Offer with 60 s validity, accept, sealed bundle, bundle (no recovery phrase field, pinned by a descriptor test), confirm, refusal, `LinkNonceLedger`; live state, commands, results, `LiveSequenceGate` |
| C1 `StateDocument`, `StateMerger` (ownership, LWW with ties to the phone, clock skew, `lastReminderAt` = max, presence stale after 30 min) | 2 | `StateDocument` (< 8 KB, enforced), `StateMerger`; 11 merger tests |
| C1 golden JSON, unknown keys ignored, newer schema → Unsupported, iOS | 1, 2 | `WearJson` and every codec test; each task compiles `:client:sync` for iOS |
| C2 `PhoneStatePublisher` (always publishes; dynamic `logdate_phone_sync_v2` per flag via `addLocalCapability`) | 3 | `PhoneStatePublisher`, `PhonePeerLink` (4006/4007 treated as done) |
| C2 `WatchStateMirror` (mirrors the flag, ignores own node and non-phone fields, publishes the watch document) | 4 | `WatchStateMirror`, `WatchPeerLink`; also advertises `logdate_watch_sync_v2` while on |
| C2 `WearProtocolGate.isV2Active()` | 2, 3, 4 | Common class; bound on both devices |
| C2 manifests for `/logdate/state` on both sides; extend `WearManifestFiltersTest` | 3, 4 | Both listeners' `DATA_CHANGED` filters; the GMD test resolves `/logdate/state` |
| C2 Labs copy | 4 | "The new watch app and its phone screens, on this phone and your watch." (70 characters, inside the existing two-line clamp, no `descriptionMaxLines` option); only the four Labs baselines change |
| C3 migration (51 here), `marked_moments`, `peer_delivery` (`peerScope, direction, entityType, entityId` PK and the listed columns), `EntityType.MOMENT`, `reassignPending`/`reassignCursors` in a transaction, schema JSON, migration and DAO tests | 5 | `MIGRATION_50_51`, both entities and DAOs, `51.json`, `WatchSyncTablesMigrationJvmTest`, `WatchSyncDaoJvmTest`, `ExportCoverageTest` classifications |
| C4 `DevicePlatform.WEAR_OS`, `IdentityKeyManager(mintPolicy)`, `installLinkedIdentityKey(key, expectedFingerprint)`, `IdentityKeyFingerprint` with identical output, `CanonicalOwnerProvider.resetForDeviceWipe()` | 6 | Pinned fingerprint (`9+eXSPQZXdg=` for 32 bytes of `0x31`, real HMAC) and a parity test against the cipher's envelope |
| C4 `LinkHandshake` + `BouncyCastleLinkHandshake` (X25519, HKDF-SHA256, AES-256-GCM; RFC 7748 §6.1, RFC 5869 case 1; tampering; all-zero secret; transcript; K_bundle/K_confirm/K_pair; no recovery phrase) | 7 (and 1 for the bundle) | 14 handshake tests with independently computed goldens |
| C5 `SyncProfile.LinkedWatch` (no minting → `NEEDS_DEVICE_LINK`; no first-sync sweep; deferred media; out-of-window skip with cursor advance; conditional CREATE with 412/410; UPDATE left for the phone; drafts and location history off) | 10, 11 | `SyncProfile`, `ConditionalCreateSettler`, `LinkedWatchProfileTest` (12 tests), `WindowedDownloadTest` |
| C5 `RoutedSyncMetadataService(delegate, eligibleForCloud)` filtering `getPendingUploads` only | 10 | `RoutedSyncMetadataService` by delegation; a test proves `hasPending` and the count still see hidden rows |
| C5 `CloudApiClient` additions defaulting to Unsupported (`createContentIfAbsent`, `createJournalIfAbsent`, `contentState` via HEAD, associations if absent, `refreshSession`) | 8, 9 | Interface defaults, `LogDateCloudApiClient` implementations, `SafeCloudTransport.head`, data source methods |
| C5 `SyncTokenRefresher` storing a rotated refresh token and surfacing revocation | 9 | `SessionRefreshingAccountRepository`, `DeviceSessionRevocationListener`, `DEVICE_SESSION_REVOKED` in the safe codes |

**Out of scope here, by design.** `DeviceLinkApiClient` and the `/auth/devices/link` endpoints belong to `2026-10-03-server-device-sessions-and-moments.md`. Constructing `SyncProfile.LinkedWatch`, `RoutedSyncMetadataService`, the link coordinators and the delivery ledger's users belongs to A-v2 (D1–D10).

**Decisions made in this plan (each can be changed before execution).**
- Migration number: 51, because `main` already used 50 (`LogDateDatabase.kt:215`).
- Package layout: messages and the gate in `app.logdate.client.sync.datalayer.v2`; state in `app.logdate.client.sync.peer.state`.
- Wire enums are closed per schema: a value an older reader does not know makes the message `Malformed`, so adding one needs a schema bump. Unknown keys are always ignored.
- The transcript encoding length-prefixes each field (4-byte big-endian) instead of plain concatenation, so "ab"+"c" and "a"+"bc" differ. HKDF labels are `"LDLINK1 bundle"`, `"LDLINK1 confirm"`, `"LDLINK1 pair"` with the transcript hash as salt; the confirmation is `HMAC-SHA256(K_confirm, "LDLINK1 confirm" ‖ T)`; AES-GCM uses a random 12-byte IV, a 128-bit tag and `T` as associated data. The master plan names these keys but not their labels.
- AES-256-GCM and HMAC also go through BouncyCastle (`GCMBlockCipher.newInstance(AESEngine.newInstance())`, `HMac`), not `javax.crypto`, so the whole exchange has one provider.
- The bundle carries `identityKeyFingerprint` next to the key, which `installLinkedIdentityKey` checks.
- Each app has its own small Play services adapter (`PhonePeerLink`, `WatchPeerLink`) rather than adding `play-services-wearable` to `:client:sync`, following the existing per-app adapters (`GooglePhoneWearTransport`, `GoogleWearDataLayerClient`). The two share about 70 lines.
- The phone publishes a state document listing no features yet, and `cloud = UNKNOWN`; each A-v2 feature adds its own name when it ships.
- `PhoneStatePublisher` takes the flag as a `Flow<Boolean>`, not a `FeatureFlagStore`, so the phone's unit tests need no datastore dependency.
- The watch mirrors only `WATCH_EXPERIENCE_V2`, and only from a document whose role is `PHONE`; the watch never writes the flag back.
- `reassignPending` never merges: a row whose entity the new scope already queues stays under the old scope and is counted in the return value. `reassignCursors` keeps the earlier of two cursors.
- `resetForDeviceWipe()` is an interface method whose default throws, because 26 test doubles implement the interface; only `DefaultCanonicalOwnerProvider` overrides it.
- `CloudAssociationDataSource` gets a separate `uploadAssociationsIfAbsent` instead of an `ifAbsent` parameter on `uploadAssociations`, because adding a parameter would break the 14 `CloudApiClient` implementations' overrides.
- The client refuses conditional requests unless the server descriptor lists `conditionalCreateV1`, because an older server ignores `If-None-Match` and overwrites.
- On 412 the watch settles its CREATE without adopting the server version, so the next download applies the server's copy; on 410 it deletes its local copy.
- `SyncPausedReason.NEEDS_DEVICE_LINK` also replaces `NOT_SIGNED_IN` for a linked device without a session, and covers a linked key that belongs to another account. Its line reads "Paused — set up this device from your phone".
- Drafts are off for a linked watch on both the download and the upload side.

**Assumed, and to reconcile with the server plan when it lands.**
- The server answers a conditional create with 201 and `{"id","serverVersion","uploadedAt"}`, 412 with a JSON body containing `serverVersion`, and 410 with a JSON body containing `deletedAt` (extra keys are ignored); `HEAD /contents/{id}` answers 200 with `ETag: "<serverVersion>"`, 404 or 410; associations take `?ifAbsent=true`; the descriptor flag is `conditionalCreateV1` (here `CONDITIONAL_CREATE_FEATURE`; switch to the server plan's `ServerProtocolFeature` constant if it adds one); refresh responses may add `data.refreshToken`; revocation is `401` with code `DEVICE_SESSION_REVOKED`.
- Kotlin interface delegation forwards members with default bodies; `RoutedSyncMetadataServiceTest` and the durable-client test fail if it does not.
- `DataClient.getDataItems` accepts the `wear://*/logdate/state` wildcard URI.

**Checked rather than assumed.** Room is at 50 (`LogDateDatabase.kt:215`, `50.json`). BouncyCastle 1.84 is in `gradle/libs.versions.toml:81` and on `:client:device` `androidMain`; its `X25519PrivateKeyParameters.generateSecret` throws `IllegalStateException` for an all-zero result, and `HKDFBytesGenerator`, `SHA256Digest.newInstance()`, `GCMBlockCipher.newInstance`, `AESEngine.newInstance()`, `AEADParameters` and `HMac` exist (javap on the 1.84 jar). Play services Wearable 19.0.0 has `DUPLICATE_CAPABILITY = 4006`, `UNKNOWN_CAPABILITY = 4007`, `addLocalCapability`/`removeLocalCapability` (javap on the AAR). The RFC 7748 §6.1 and RFC 5869 case 1 vectors, the transcript hash `0fefc1eb…`, the derived keys and the confirmation MAC were computed independently in Python. `ExportCoverageTest` classifies every table in the newest schema, `SyncStatusSheet.kt:527` and the two `SyncPausedReason` `when`s in each of `SyncSettingsScreen.kt` and `SyncStatusSheet.kt` are exhaustive, `DevicesViewModel.getPlatformName` is exhaustive, and the only `SyncMetadataDao` test double is in `DatabaseSyncMetadataServiceTest`. `DownloadInbox.count()` includes waiting `MEDIA_NOTE` rows, and `DefaultSyncManager.sync()` loops while it is non-zero, which is why media recovery must be switchable. `DefaultSyncManager` has about 530 lines of code and `SyncDownloadEngine` about 590, so Task 10 moves `conflictTimestamps` out of the engine before adding to it.

**Known limits, not fixed here.**
- `PhonePeerLink.peerDocuments()` and `WatchPeerLink.peerDocuments()` build an `android.net.Uri` and iterate a `DataItemBuffer`, which host unit tests cannot run; the catch-up logic they feed is covered through fakes, and the end-to-end read needs a paired phone and watch (A-v2's manual check).
- The phone has no instrumented manifest test like the watch's; Task 3 checks the merged manifest from `assembleDebug`.
- The phone republishes its document on every app start, because `writtenAt` changes; the watch handles it as a no-op when the switch is unchanged.
- `PeerStateStore` lives in memory; both devices catch up from the Data Layer when their app starts.
- The watch still registers itself locally as `DevicePlatform.ANDROID` (`DeviceIdentityModule.android.kt:47`); the device list the server keeps is where Wear OS appears (server plan, C10).
- `DevicesViewModel` platform names are hard-coded English, as before; "Wear OS" is added the same way, and localizing them belongs to the Devices work (C10).
- Upload status on a linked watch still counts edits waiting for the phone; A-v2's `WatchSyncFacade` combines the two outboxes.

**Placeholder scan.** No step says TBD, TODO, "add error handling" or "similar to Task N". Every code step shows the code; inert bodies exist only in each task's Step 2, so its tests fail on behavior, and Step 4 replaces them before the commit. The one instruction that depends on generated output, matching `MIGRATION_50_51` to `51.json`, names the exact columns to compare and which side changes.

**Name consistency.** `Decoded.Supported/Unsupported/Malformed` and `WearJson.encode/encodeToBytes/decode` (Task 1) are used unchanged in Tasks 2-4. `StateDocumentCodec.toDataMap/fromDataMap/KEY`, `PeerStateStore.accept(nodeId, decoded, expectedRole): StateDocument?`, `features(role)`, `latest(role)`, `StateDocumentChannel.publish/peerDocuments`, `LocalCapabilities.add/remove`, `PeerCapabilityPresence.observe` (Task 2) are used unchanged in Tasks 3 and 4. `IdentityMintPolicy.NEVER`, `installLinkedIdentityKey(key, expectedFingerprint)` and `IdentityKeyFingerprint.of(identityKey, cryptoManager)` (Task 6) are used in Task 11. `ConditionalCreateResult.Created/AlreadyExists/Deleted`, `createNoteIfAbsent`, `createJournalIfAbsent`, `uploadAssociationsIfAbsent` and `FakeCloudApiClient.createContentIfAbsentResponse/createContentIfAbsentCalls` (Task 8) are used in Task 11. `RefreshedSession`, `SessionRefreshingAccountRepository`, `DeviceSessionRevocationListener` (Task 9) and `RemoteApplyFilter`, `DownloadStrategy.appliesRemoteCreate/enqueuesMediaRecovery`, `SyncDownloader(downloadsMedia, remoteApplyFilter)` (Task 10) are used in Task 11 with the same signatures.

**Commits.** Eleven Conventional Commits, every title 52-67 characters, all `chore` with scopes `sync`, `wear`, `database` and `device` from `allowed-scopes.txt`; every body line is at most 72 characters and ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; every commit is the one `git restore --staged . && git add <paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>` chain after its message file is written in a separate step, and its paths match the task's file list.
