# Watch Sync v2 Link and Window Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first half of Track A "A-v2" (D1 to D5) behind `WATCH_EXPERIENCE_V2`: the phone pairs its watch with no account and links it to the phone's account with no sign-in on the wrist, the watch runs its own cloud engine, every watch capture is routed through a durable delivery ledger with receipts that say whether it is on the phone or backed up (falling back to a direct, create-only upload that leaves exactly one server version), and the phone publishes a library window that the watch applies and reconciles.

**Architecture:** Fourteen test-first commits on `claude/wearos-android-ux-redesign-081dc0`. Pure logic that both devices share lives in `commonMain` (`:client:sync` packages `peer.link` and `peer.delivery`, `:client:domain` package `watch` for the window policy), so it compiles for iOS and is unit tested on the JVM. Each app has a thin Play services layer: the phone in `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/{link,receipts,window}` with Koin in a new `phoneWatchSyncV2Module`, the watch in `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/{link,delivery,window}` with Koin in new `wearSyncV2Module`, `wearIdentityModule` and `wearCloudSyncModule`. The phone always initiates linking with `MessageClient.sendRequest` (the shipped `LinkOffer` says so), the watch answers in `onRequest`; records the watch made go out as `RecordEnvelope` data items and come back as `Receipt`s; the phone's library window goes to the watch as one data item per record. Version 1 stays the path whenever `WearProtocolGate` says version 2 or the feature is not active, in both directions.

**Tech Stack:** Kotlin 2.3.21 Multiplatform (Android, iOS, JVM desktop), kotlinx.serialization 1.11.0 JSON through `WearJson`, kotlinx.coroutines 1.11.0, kotlinx-datetime 0.7.1 with `kotlin.time.Instant`/`Clock`, Koin 4.2.1, Room KMP (schema 53, no migration in this plan), Ktor client 3.4 (`MockEngine` not needed; the end-to-end test uses the real server harness), BouncyCastle `bcprov-jdk18on` 1.84 through `BouncyCastleLinkHandshake`, Play services Wearable 19.0.0 (`MessageClient.sendRequest`, `WearableListenerService.onRequest`/`onNodeMigrated`, `DataClient`, `ChannelClient`, `CapabilityClient`), WorkManager, JUnit 4 + `kotlin.test` + MockK 1.14.9, the Wear Gradle Managed Device `wearSmallRoundApi34`.

## Global Constraints

- Flag-gated: every version 2 path runs only while `WearProtocolGate.isV2Active()` is true, and each surface only while `isFeatureActive(<feature>)` is true; with the flag off both devices behave exactly as they do at `17bcbc04b`.
- Version 1 still works in both directions: no version 1 path, payload or manifest filter is removed or changed in meaning, and a new path never starts with `/logdate/notes`, `/logdate/journals`, `/logdate/associations`, `/logdate/health`, `/logdate/camera` or `/logdate/sync`.
- Feature names are advertised only by a build that implements them: the watch lists `link.cloud.v1` (Task 4), `receipts.v1` (Task 11), `window.v1` (Task 14); the phone lists `link.cloud.v1` (Task 5), `receipts.v1` (Task 9), `window.v1` (Task 13).
- Never drop errors: every failure is logged with Napier saying what failed and what happens next, and stays visible as a typed state (`PhoneLinkFailure`, `LinkProblem`, `HoldReason`, a `Receipt`, an outbox row, a dead letter), never a silent fallback.
- Logging: Napier only. Never `println`, `System.out` or `Log.*`. Logs never contain journal text, link codes, keys, tokens or raw JSON.
- UUIDs: `kotlin.uuid.Uuid`. Time: `kotlin.time.Instant` and `kotlin.time.Clock`; zones and dates: `kotlinx.datetime`.
- Serialization: kotlinx.serialization; wire JSON through `WearJson`; enums inside a version 2 schema are closed, so this plan adds no value to `LinkRefusal`, `ReceiptState`, `WindowReason`, `RecordKind` or `PeerLinkState`; new fields are optional with defaults.
- DI: Koin constructor injection; Play services handles are created only inside Koin modules and the existing adapters.
- Imports: lexicographic per ktlint_official, `java.*`, `javax.*` and `kotlin.*` last, no wildcards; when `ktlintCheck` fails run `./gradlew :<module>:ktlintFormat`, rerun the tests and stage the reformatted file.
- Style: early returns and guard clauses; KDoc says why, never restates code; one parameter per line with trailing commas once a call or declaration has two or more; code lines at most 140 characters (ktlint_official's limit, enforced by `ktlintCheck`).
- Copy: literal, never "we", never "kept", "held" or "carried" (or other vague metaphor verbs); user-visible text lives in string resources.
- Detekt: classes under 600 lines of code and functions under 60 (`config/detekt/detekt.yml:33-40`); never add a baseline entry.
- iOS: every `commonMain` change compiles with `:<module>:compileKotlinIosSimulatorArm64` and `:<module>:compileTestKotlinIosSimulatorArm64`; backtick test names in `commonTest` contain no commas, periods, colons, slashes or parentheses; no JVM-only APIs in `commonMain`.
- Room is at version 53 (`client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt:228`); this plan adds DAO queries only and no migration.
- Gradle: run from the worktree root and prefix every command with `export ANDROID_HOME=$HOME/Library/Android/sdk &&`. Every task name used below was listed by `./gradlew <project>:tasks --all` on 2026-10-10.
- Devices: emulators and Gradle Managed Devices only. Never `connected*AndroidTest`, `install*`, `uninstall*` or any `adb` command, and never start, stop or kill an emulator by hand. Watch instrumented tests: `./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<class>`; phone: `./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=<class>`. If a managed device reports "Process crashed" or "failed to complete startup", rerun once with the machine otherwise idle.
- Test-first: each task's Step 1 writes the tests, Step 2 runs them and sees them fail (compilation errors naming the missing classes, or assertion failures), Step 3 implements, Step 4 sees them pass.
- Commits: Conventional Commits, title at most 72 characters, type `chore` while the flag is off, scope from `allowed-scopes.txt` (`sync`, `wear`, `device`, `database`, `networking`, `data`, `datastore`); never the `style` type and never `build`, `android`, `ios`, `desktop` or `infra` as a scope. Body: full sentences wrapped at 72 characters; last line `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Commit mechanics: the message is written by its own step, a quoted heredoc to `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/<name>.txt`; the next step is the single chain `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <file>`. Never `git add -A`, `git add .`, `git commit -a`, `-m` or `--no-verify`.
- Every commit leaves the branch shippable: the touched modules' tests, `ktlintCheck` and `detekt` pass first.

---

## What the code shows today (verified on `claude/wearos-android-ux-redesign-081dc0` at `17bcbc04b`)

| Item | Finding | Where |
|---|---|---|
| Link messages | The phone opens: `LinkOffer` is "The phone's opening message, sent with `MessageClient.sendRequest` to `WearPaths.LINK_OFFER`"; the watch answers `LinkAccept`; then `SealedLinkBundle` to `LINK_BUNDLE`, answered by `LinkConfirm`. `LinkRefused(reason: LinkRefusal)` with `EXPIRED_OFFER, REPLAYED_NONCE, BOUND_TO_ANOTHER_ACCOUNT, UNSUPPORTED`. `LinkBundle` has no recovery phrase field. `LinkNonceLedger(clock).accept(nonce, expiresAt)`. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/LinkMessages.kt:16-129` |
| Handshake | `LinkHandshake` (`generateKeyPair`, `newNonce`, `transcriptHash(LinkTranscript)`, `deriveKeys` throwing `LinkHandshakeException`, `seal`, `open`, `confirmation`, `verifyConfirmation`); `LinkKeys(bundleKey, confirmKey, pairKey)`; `BouncyCastleLinkHandshake(random)` in `androidMain` only. | `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/link/LinkHandshake.kt:1-116`; `client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/link/BouncyCastleLinkHandshake.kt:17` |
| Paths | `WearPaths.STATE, RECEIPTS ("/logdate/v2/receipts"), RECEIPTS_PUSH, WINDOW_RESYNC ("/logdate/v2/window/resync"), LINK_OFFER, LINK_BUNDLE`; `windowItem/windowTombstone` under `/logdate/v2/p2w/`, `watchRecord` under `/logdate/v2/w2p/record/`, `bodyChannel(sha256Hex)` under `/logdate/v2/body/`; `parse*` helpers. No path for watch media in version 2. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt:12-84` |
| Envelope and receipts | `RecordEnvelope(schema, kind, id, contentVersion, syncVersion, origin, reasons, priority, enc, body, bodyRef, media)`; `RecordEnvelopeCodec.encode/decode/needsChannel` (90 KB); no data-map helper. `ReceiptsRequest(items: List<ReceiptQuery>)`, `ReceiptsResponse(cloud, receipts)`, `Receipt.covers(local)`. | `datalayer/v2/RecordEnvelope.kt:63-107`; `datalayer/v2/Receipts.kt:1-60` |
| State documents | `PhoneState(flags, cloud, campfire, dayStartHour, zone, openMarks, features)`, `WatchState(outboxCount, deadLetterCount, freeBytes, link: PeerLinkState, presence, features)`; `PeerLinkState { UNLINKED, PAIRED, LINKED, NEEDS_RELINK }`. `PeerStateStore` stores one document per node but exposes only `latest(role)` and `features(role)`. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt:36-82`; `PeerStateStore.kt:14-57` |
| Gate | `WearProtocolGate(flagEnabled, peerHasV2Capability, peerFeatures).isV2Active()/isFeatureActive(feature)`, bound on both devices. | `datalayer/v2/WearProtocolGate.kt:15-24`; `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt:154-160`; `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:236-242` |
| Phone publisher | `PhoneStatePublisher(v2Enabled, channel, capabilities, peerStates, clock, timeZone)` publishes `PhoneState(flags, zone)` only, no features, `cloud = UNKNOWN`; started in `LogdateApplication.startPhoneStatePublishing()`. | `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt:26-71`; `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt:139-145` |
| Watch mirror | `WatchStateMirror` publishes `WatchState()` (link `UNLINKED`, no features) whenever the flag changes. | `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchStateMirror.kt:37-83` |
| Listeners | Watch: `onMessageReceived` → `WearPhoneMessageHandler`, `onCapabilityChanged`, `onDataChanged` (acts only on `TYPE_CHANGED` from another node); no `onRequest`, no `onNodeMigrated`. Phone: `onMessageReceived` (baselined `LongMethod`), `onChannelOpened` for `/logdate/notes`, `onDataChanged`; no `onRequest`, no `onCapabilityChanged`. | `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt:57-395`; `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt:52-489`; `app/compose-main/detekt-baseline.xml:5` |
| Manifests | Watch listener: `DATA_CHANGED` for notes, journals, associations, camera, `/logdate/state`; `MESSAGE_RECEIVED` for `/logdate/notes`, `/logdate/sync`; `CAPABILITY_CHANGED` for `/logdate_phone_app`. Phone listener: `DATA_CHANGED` notes, journals, associations, health, `/logdate/state`; `MESSAGE_RECEIVED`; `CHANNEL_EVENT` for `/logdate/notes`. Play services 19.0.0 actions: `com.google.android.gms.wearable.REQUEST_RECEIVED`, `com.google.android.gms.wearable.NODE_MIGRATED` (javap). | `app/wear/src/main/AndroidManifest.xml:75-121`; `app/android-main/src/main/AndroidManifest.xml:224-270` |
| Watch v1 outbox | `WearDataLayerSyncManager` reads `SyncMetadataService` and settles a note when its put succeeds (text) or when the phone acknowledges it (audio); `uploadPendingChangesLocked()` returns `notConnectedResult()` when no phone is connected; bound as `SyncManager`, `WearNoteAckHandler`, `WearNoteRemovalNotifier`. | `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt:57-131, 146-165, 677-727`; `WearDataModule.kt:106-122` |
| Phone v1 ingest | `WatchNoteIngestor(notesRepository, audioStore, acknowledger, notifier, noteDataMapper)` stores audio only once bytes and digest match; it acknowledges audio through `WatchNoteAcknowledger`, not text. | `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/WatchNoteIngestor.kt:85-208` |
| Owner and outbox | The outbox is scoped by `(canonical owner, current backend URL)`; `SyncMetadataDao.reassignPending/reassignCursors` move rows between scopes; `HistoryOwnerAdoptionModule` moves history rows with `dao.adopt(old, new, origin, device) { it.rewriteHistoryOwner(new) }`. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/DatabaseSyncMetadataService.kt:352-354`; `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/SyncMetadataDao.kt:265-308`; `client/data/src/commonMain/kotlin/app/logdate/client/data/account/HistoryOwnerAdoptionModule.kt:12-27` |
| Delivery ledger | `PeerDeliveryEntity(peerScope, direction, entityType, entityId, contentVersion, state, attempts, lastAttemptAt, nextAttemptAt, lastCloudAttemptVersion, stateChangedAt, holdReason)`; DAO `upsert/get/due/delete`. | `client/database/src/commonMain/kotlin/app/logdate/client/database/entities/sync/PeerDeliveryEntity.kt:16-33`; `dao/sync/PeerDeliveryDao.kt:9-46` |
| Engine profile | `SyncProfile.LinkedWatch(remoteApplyFilter)`, `RoutedSyncMetadataService(delegate, CloudEligibility)`, `RecentCaptureFilter(window)`; nothing constructs them. `DefaultSyncManager` takes `profile` and `deviceSessionRevocationListener`. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncProfile.kt:48-58`; `metadata/RoutedSyncMetadataService.kt:16-22`; `RemoteApplyFilter.kt:20-25`; `DefaultSyncManager.kt:62-116` |
| Watch Koin | The watch has no `CryptoManager`, `IdentityKeyManager`, `PrivacyScopeEpoch`, `HttpClient`, `CloudApiClient`, `MediaManager` or `JournalContentRepository` binding, and `:app:wear` does not depend on `:client:networking` or `:client:sensor`. | `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt:77-213`; `app/wear/build.gradle.kts:134-148` |
| Phone create upload | A plain CREATE sends `If-None-Match: *`; a record that exists answers `412 CONTENT_EXISTS`, which `SyncUploader` counts as a failure and retries until it dead-letters. | `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentRequests.kt:14-44`; `SyncUploader.kt:493-540`; `server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt:349-376` |
| Network type | `NetworkConnectionType { NONE, CELLULAR, WIFI, ETHERNET, OTHER }`; the Bluetooth proxy maps to `OTHER`; `NetworkSaverState` has no "validated" flag. | `client/networking/src/commonMain/kotlin/app/logdate/client/networking/saver/NetworkSaverState.kt:6-21`; `client/sensor/src/androidMain/kotlin/app/logdate/client/sensor/network/AndroidNetworkSaverModeProvider.kt:121-133` |
| Day start | `Instant.toStreakDay(timeZone, dayStartHour)` and `DEFAULT_STREAK_DAY_START_HOUR = 4` in `:client:domain`; the phone reads the hour from `app.logdate.client.health.util.LogdatePreferencesDataSource`; the watch's `WatchDayBoundaryPreferences(phoneDayStartHour = { null })` waits for the phone's state. | `client/domain/src/commonMain/kotlin/app/logdate/client/domain/streak/StreakDay.kt:20-38`; `client/domain/.../di/DomainModule.kt:200-202`; `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt:45-52` |

## Boundaries with the later plans

| Concern | This plan (D1 to D5) | Later step that owns the rest |
|---|---|---|
| Linking | PAIR and CLOUD exchanges, the install journal, owner adoption, typed problems, one confirmation notification, the "other account" and "device limit" notifications | D10: revocation, sign-out unlinks watches, the watch wipe, the "Erase watch" action; C7: Remove watch |
| Bundle `flags` and `settings` | Sent (`flags` contains `WATCH_EXPERIENCE_V2`, `settings` is empty); the watch ignores both because the state document is the source of truth for flags | C2: shared preferences fill `settings` and read them |
| Watch cloud engine | Constructed, profile `LinkedWatch`, its own stores; it uploads only the notes the router released (creates if absent and removals) and is never asked to download, so the phone's library window stays the watch's library | D7: `MOMENT` records; D10: `DeviceRevocationHandler` (this plan only records `SESSION_REVOKED`); downloading from the cloud while the phone is away is not assigned (see Self-Review) |
| Media | Watch audio reaches the phone over a new version 2 channel during delivery; window envelopes contain the phone's note body as version 1 did and `media = []` | D6: `WatchMediaCache`, `MediaStreamClient`, `ChannelDataSource`, `PhoneMediaServer`, `WatchMediaPrefetchWorker`, media refs in window envelopes |
| Record kinds | Version 2 delivery moves `NOTE` only; journals, associations, health stay on version 1; a `MOMENT` envelope gets `REJECTED_UNSUPPORTED` and a `MOMENT` window item is ignored with a log line | D7: marked-moment sync and the window carrying moment state |
| Window inputs | `RECENT` (14 logical days) and `ON_THIS_DAY` from the phone's notes with the policy's date rules; `REWIND` and `STARRED` come from a `WindowExtras` source whose default is empty | D11 (Track C): Rewind note IDs, starred IDs, and optionally replacing the On this day source with `GetMemoryRecallUseCase` |
| Campfire and day start | The phone publishes `PhoneState.campfire` and `dayStartHour`; the watch's `WatchDayBoundaryPreferences` reads the hour | B2: the watch's Today UI reading the campfire snapshot |
| Status surfaces | `PhoneWatchLinkStore.observe()`, `WatchLinkStore.observe()`, `PeerDeliveryLedger.observe()` (state and `HoldReason` per note), the version 1 manager's `syncStatusFlow` | C3b `WatchStatusResolver`, C4 Watch page, B6 watch status card, B2 "Connected to your LogDate" line |
| Reminders, presence, live, favorites | Nothing | D8, D9, R-track |

---

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/LinkWire.kt` | 1 | `LinkExchangeFailure`, `LinkStep`, `LinkAnswer`, `LinkWire` (Base64 keys, read and write link messages) |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PhoneLinkExchange.kt` | 1 | The phone's half of the exchange |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/WatchLinkExchange.kt` | 1 | The watch's half of the exchange |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PairKeyVault.kt` | 1 | Pair keys in `SecureStorage` |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkStore.kt` | 1, 4 | The watch's link record |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinator.kt` | 1, 4 | Answers link requests; node migration; install outcomes |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchOwnState.kt` | 1, 4, 11 | The watch's own section of its state document |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/LocalNode.kt` | 1 | `NodeClient.localNodeIdOrNull()` |
| `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt` | 1, 4, 8, 11, 14 | Koin for the watch's version 2 sync |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerStateStore.kt` | 2 | `documents(role)` |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/*.kt` | 2, 5 | `LinkRequester`, `LinkTargets`, `PhoneWatchLinkStore`, `PhoneLinkDecision`, `PhoneLinkInitiator`, `LinkBundleSource`, `WatchLinkNotifier`, `CloudLinkBundleSource`, `LinkCodeMinter` |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/LocalNode.kt` | 2 | `NodeClient.localNodeIdOrNull()` on the phone |
| `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt` | 2, 5, 9, 10, 13 | Koin for the phone's version 2 sync |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/LinkedDeviceInstaller.kt` | 3 | Journaled install of keys, session, owner and server |
| `client/data/src/commonMain/kotlin/app/logdate/client/data/account/OwnerAdoptionMover.kt` | 3 | Moves outbox, cursors and history rows to the linked owner |
| `app/wear/src/main/kotlin/app/logdate/wear/di/WearIdentityModule.kt` | 4 | Crypto, `IdentityKeyManager(NEVER)`, media key, privacy epoch, installer |
| `client/networking/.../saver/NetworkSaverState.kt`, `DefaultDataUsagePolicy.kt`; `client/sensor/.../network/AndroidNetworkSaverModeProvider.kt` | 6 | `BLUETOOTH_PROXY`, `isValidated` |
| `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/PrefixedKeyValueStorage.kt` | 7 | Separate key space for the watch's cloud stores |
| `app/wear/src/main/kotlin/app/logdate/wear/di/WearCloudSyncModule.kt` | 7, 11, 14 | The `named("watchCloud")` engine and its stores |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/*.kt` | 8 | `PeerDeliveryLedger`, `WatchSyncRouter`, `EnvelopeDataMap`, `NoteEnvelopeBody` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt` | 8 | `recordMedia(kind, id)` |
| `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/PeerDeliveryDao.kt` | 8 | `all`, `observeAll`, `earliestNextAttempt` |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/*.kt` | 9, 10 | `WatchRecordIngestorV2`, `PhoneReceiptBook`, `WatchReceiptResponder`, `WatchReceiptNotifier`, `WatchCaptureCollisionPolicy` |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/CreateCollision.kt` | 10 | `CreateCollisionPolicy`, `CreateCollisionResolver`, `CreateCollisionSettler` |
| `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/WatchFallbackBackupE2ETest.kt` | 10 | Exactly one server version |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/*.kt` | 11 | `DataLayerTransportV2`, `WatchReceiptApplier`, `WatchRouteConditions`, `ReleasedToCloud`, `WatchNoteDelivery` (with the `WatchNotesV2` seam into the version 1 manager) |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchWindowPolicy.kt` | 12 | Day start, DST, Feb 29, budget order |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WindowMessages.kt` | 13 | `WindowResyncRequest`, `WindowMemberRef`, `WindowResyncResponse`, `WindowTombstoneDataMap` (and `WearProtocol.WINDOW_SCHEMA`) |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/*.kt` | 13 | `WatchWindowPublisher`, `WindowExtras`, `WindowManifestStore`, `WindowDataItems`, `WindowResyncResponder` |
| `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/*.kt` | 14 | `WindowApplier`, `WindowReconciler`, `WindowMembershipStore`, `WindowItemSource` |

## Task Map

| Task | Step | Commit title |
|---|---|---|
| 1 | D1 watch | `chore(wear): answer the phone's pairing offer on the watch` |
| 2 | D1 phone | `chore(wear): pair the phone with its watch without an account` |
| 3 | D2 core | `chore(sync): install a linked watch's keys and session in steps` |
| 4 | D2 watch | `chore(wear): finish linking the watch to the phone's account` |
| 5 | D2 phone | `chore(wear): send the account link from the phone to the watch` |
| 6 | D3 network | `chore(networking): treat the watch's Bluetooth link as metered` |
| 7 | D3 engine | `chore(wear): add the watch's own cloud sync engine` |
| 8 | D4 common | `chore(sync): route each watch change and record its delivery` |
| 9 | D4 phone | `chore(wear): store watch captures on the phone and send receipts` |
| 10 | D4 fallback | `chore(sync): settle a capture the watch already backed up` |
| 11 | D4 watch | `chore(wear): send watch captures with receipts and back them up` |
| 12 | D5 policy | `chore(wear): choose what the watch's library window contains` |
| 13 | D5 phone | `chore(wear): publish the watch's library window from the phone` |
| 14 | D5 watch | `chore(wear): apply the phone's library window on the watch` |

Dependencies flow forward: 2 uses 1; 3 uses 1; 4 uses 1 and 3; 5 uses 2 and 4; 7 uses 4 and 6; 9 uses 8; 10 uses 8 and 9; 11 uses 7, 8 and 9; 13 uses 8 and 12; 14 uses 11 and 13. Line numbers are as of `17bcbc04b`; for a file an earlier task already edited, find the code by the quoted anchor text.

---
### Task 1: Answer the phone's pairing offer on the watch (D1, watch)

The common exchange both devices run, and the watch's side of it. The phone opens every exchange (`LinkOffer` KDoc, `LinkMessages.kt:16-19`), so the watch answers in `WearableListenerService.onRequest`. This task pairs only (`LinkMode.PAIR`); a `CLOUD` offer is refused as `UNSUPPORTED` until Task 4 installs one. Moving the watch to another phone (`onNodeMigrated`) marks the pairing stale, which the watch reports as `NEEDS_RELINK`.

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/LinkWire.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PhoneLinkExchange.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/WatchLinkExchange.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PairKeyVault.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/LocalNode.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkStore.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinator.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchOwnState.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchStateMirror.kt:29-83`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt` (after `onCapabilityChanged`, `:73-78`)
- Modify: `app/wear/src/main/AndroidManifest.xml` (after the `CAPABILITY_CHANGED` filter, `:116-122`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt:145-153`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt:45-50`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/link/LinkWireTest.kt`
- Test: `client/sync/src/androidHostTest/kotlin/app/logdate/client/sync/peer/link/LinkExchangeTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/MemoryKeyValueStorage.kt` (test helper, also `MemoryPairKeyVault`)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinatorTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchOwnStateTest.kt`
- Test: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt` (2 new tests)

**Interfaces:**
- Consumes: `LinkHandshake`, `LinkTranscript`, `LinkKeys`, `LinkKeyPair`, `SealedBytes`, `LinkHandshakeException`, `BouncyCastleLinkHandshake` (`:client:device`); `LinkOffer`, `LinkAccept`, `SealedLinkBundle`, `LinkBundle`, `LinkConfirm`, `LinkRefused`, `LinkRefusal`, `LinkMode`, `LinkNonceLedger`, `WearJson`, `Decoded`, `WearProtocol.LINK_SCHEMA`, `WearProtocol.LINK_OFFER_TTL_MS`, `WearPaths.LINK_OFFER`, `WearPaths.LINK_BUNDLE` (`:client:sync`); `SecureStorage.getBytes/putBytes`; `KeyValueStorage`; `FeatureFlagStore.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)`; `WatchState`, `PeerLinkState`.
- Produces:
  - `enum class LinkExchangeFailure { MALFORMED, UNSUPPORTED_SCHEMA, EXPIRED_OFFER, REPLAYED_NONCE, BAD_KEY, BUNDLE_UNREADABLE }`
  - `sealed interface LinkStep<out T> { data class Ok<T>(val value: T); data class Failed(val failure: LinkExchangeFailure) }`, `fun <T> LinkStep<T>.valueOrNull(): T?`
  - `sealed interface LinkAnswer<out T> { data class Answered<T>(val value: T); data class Refused(val reason: LinkRefusal); data class Unreadable(val failure: LinkExchangeFailure) }`
  - `object LinkWire { fun encodeKey(bytes: ByteArray): String; fun decodeKey(text: String): ByteArray?; fun <T> write(serializer: KSerializer<T>, value: T): ByteArray; fun refusal(reason: LinkRefusal): ByteArray; fun <T> read(bytes: ByteArray, serializer: KSerializer<T>): LinkAnswer<T> }`
  - `class PhoneLinkExchange(handshake: LinkHandshake, clock: Clock = Clock.System) { fun start(mode: LinkMode, phoneNode: String): Started; fun seal(started: Started, accept: LinkAccept, watchNode: String, bundle: LinkBundle): LinkStep<Sealed>; fun verify(sealed: Sealed, confirm: LinkConfirm): Boolean }`, `class PhoneLinkExchange.Started { val offer: LinkOffer }`, `class PhoneLinkExchange.Sealed { val bundle: SealedLinkBundle; val pairKey: ByteArray }`
  - `class WatchLinkExchange(handshake: LinkHandshake, nonces: LinkNonceLedger, clock: Clock = Clock.System) { suspend fun accept(offer: LinkOffer, watchNode: String, phoneNode: String): LinkStep<Accepted>; fun open(accepted: Accepted, sealed: SealedLinkBundle): LinkStep<Opened> }`, `class Accepted { val reply: LinkAccept; val offer: LinkOffer }`, `class Opened { val bundle: LinkBundle; val confirm: LinkConfirm; val pairKey: ByteArray }`
  - `interface PairKeyVault { suspend fun put(peer: String, key: ByteArray); suspend fun get(peer: String): ByteArray?; suspend fun remove(peer: String) }`, `class SecureStoragePairKeyVault(secureStorage: SecureStorage, prefix: String) : PairKeyVault`
  - Watch: `@Serializable data class WatchLinkRecord(val mode: LinkMode, val phoneNode: String, val linkedAt: Long, val stale: Boolean = false, val accountId: String? = null)`, `fun WatchLinkRecord?.peerLinkState(): PeerLinkState`, `class WatchLinkStore(storage: KeyValueStorage, pairKeys: PairKeyVault, clock: Clock = Clock.System) { fun observe(): Flow<WatchLinkRecord?>; suspend fun current(): WatchLinkRecord?; suspend fun savePairing(phoneNode: String, pairKey: ByteArray); suspend fun markStale(phoneNode: String): Boolean }`
  - Watch: `class WatchLinkCoordinator(exchange: WatchLinkExchange, store: WatchLinkStore, v2Enabled: suspend () -> Boolean, localNodeId: suspend () -> String?) { suspend fun answer(sourceNodeId: String, path: String, payload: ByteArray): ByteArray?; suspend fun onNodeMigrated(oldNodeId: String); companion object { fun handles(path: String): Boolean } }`
  - Watch: `data class WatchCounts(val outbox: Int = 0, val deadLetters: Int = 0, val freeBytes: Long? = null)`, `class WatchOwnState(links: Flow<WatchLinkRecord?>, features: Set<String> = emptySet(), counts: Flow<WatchCounts> = flowOf(WatchCounts())) { fun observe(): Flow<WatchState> }`
  - Watch: `WatchStateMirror(featureFlags, channel, capabilities, peerStates, merger, ownState: Flow<WatchState> = flowOf(WatchState()), clock)`; `internal suspend fun NodeClient.localNodeIdOrNull(): String?`; `val wearSyncV2Module: Module`

**Failure modes and how they surface:**
- An expired or replayed offer, a key of the wrong size, a bundle that does not open (wrong node, tampered ciphertext, changed transcript), or an unreadable message: the watch answers `LinkRefused(EXPIRED_OFFER | REPLAYED_NONCE | UNSUPPORTED)` and logs which check failed; the phone records it (Task 2). Nothing is stored on the watch.
- The redesigned app is off on the watch, or the watch cannot read its own node ID: refused as `UNSUPPORTED` with the reason logged.
- A bundle without an open offer from the same phone (the process died between the two requests, or another phone sent it): refused as `EXPIRED_OFFER`; the phone starts over.
- Koin not ready when the request arrives: the request's `Task` fails with an exception the phone receives as a failed `sendRequest`, and the failure is logged.
- A corrupted link record: logged as an error and read as no record (`UNLINKED`), so the phone pairs again.

- [ ] **Step 1: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/link/LinkWireTest.kt`:

```kotlin
package app.logdate.client.sync.peer.link

import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests [LinkWire]: answers and refusals read back as what they are, and keys travel as Base64. */
class LinkWireTest {
    @Test
    fun `an accept reads back as an answer`() {
        val accept = LinkAccept(watchNode = "watch", watchPublicKey = "a2V5", watchNonce = "bm9uY2U=")

        assertEquals(
            LinkAnswer.Answered(accept),
            LinkWire.read(LinkWire.write(LinkAccept.serializer(), accept), LinkAccept.serializer()),
        )
    }

    @Test
    fun `a refusal reads back as a refusal whatever answer was expected`() {
        val bytes = LinkWire.refusal(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT)

        assertEquals(LinkAnswer.Refused(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT), LinkWire.read(bytes, LinkConfirm.serializer()))
    }

    @Test
    fun `bytes that are not a link message are unreadable`() {
        assertEquals(
            LinkAnswer.Unreadable(LinkExchangeFailure.MALFORMED),
            LinkWire.read("nope".encodeToByteArray(), LinkConfirm.serializer()),
        )
    }

    @Test
    fun `an answer written by a newer version is unsupported`() {
        val bytes = """{"schema":2,"mac":"bWFj"}""".encodeToByteArray()

        assertEquals(LinkAnswer.Unreadable(LinkExchangeFailure.UNSUPPORTED_SCHEMA), LinkWire.read(bytes, LinkConfirm.serializer()))
    }

    @Test
    fun `keys round trip through Base64`() {
        val key = ByteArray(32) { it.toByte() }

        assertContentEquals(key, LinkWire.decodeKey(LinkWire.encodeKey(key)))
    }

    @Test
    fun `text that is not Base64 is no key`() {
        assertNull(LinkWire.decodeKey("not base64!"))
    }
}
```

Create `client/sync/src/androidHostTest/kotlin/app/logdate/client/sync/peer/link/LinkExchangeTest.kt`:

```kotlin
package app.logdate.client.sync.peer.link

import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [PhoneLinkExchange] and [WatchLinkExchange] against each other with the real BouncyCastle
 * handshake: both devices end with one pair key, and anything changed on the way stops the exchange.
 */
class LinkExchangeTest {
    private val clock = MutableClock(1_000_000)
    private val handshake = BouncyCastleLinkHandshake()
    private val phone = PhoneLinkExchange(handshake, clock)
    private val watch = WatchLinkExchange(handshake, LinkNonceLedger(clock), clock)

    @Test
    fun `a pairing gives both devices one pair key and a confirmation the phone accepts`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            val opened = assertNotNull(watch.open(accepted, sealed.bundle).valueOrNull())

            assertEquals(LinkMode.PAIR, opened.bundle.mode)
            assertContentEquals(sealed.pairKey, opened.pairKey)
            assertTrue(phone.verify(sealed, opened.confirm))
        }

    @Test
    fun `the bundle the watch opens is the one the phone sealed`() =
        runTest {
            val bundle = LinkBundle(mode = LinkMode.CLOUD, accountId = "account", linkCode = "code", linkCodeExpiresAt = 9)
            val started = phone.start(LinkMode.CLOUD, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, WATCH, bundle).valueOrNull())

            assertEquals(bundle, assertNotNull(watch.open(accepted, sealed.bundle).valueOrNull()).bundle)
        }

    @Test
    fun `an offer older than a minute is refused`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            clock.millis += 60_000

            assertEquals(LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER), watch.accept(started.offer, WATCH, PHONE))
        }

    @Test
    fun `the same offer cannot be accepted twice`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            watch.accept(started.offer, WATCH, PHONE)

            assertEquals(LinkStep.Failed(LinkExchangeFailure.REPLAYED_NONCE), watch.accept(started.offer, WATCH, PHONE))
        }

    @Test
    fun `a bundle sealed for another watch does not open`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, "another-watch", LinkBundle(mode = LinkMode.PAIR)).valueOrNull())

            assertEquals(LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE), watch.open(accepted, sealed.bundle))
        }

    @Test
    fun `a changed ciphertext does not open`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            val sealed = assertNotNull(phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            val bytes = assertNotNull(LinkWire.decodeKey(sealed.bundle.ciphertext))
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            val tampered = SealedLinkBundle(iv = sealed.bundle.iv, ciphertext = LinkWire.encodeKey(bytes))

            assertEquals(LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE), watch.open(accepted, tampered))
        }

    @Test
    fun `a confirmation from another exchange is not accepted`() =
        runTest {
            val first = phone.start(LinkMode.PAIR, PHONE)
            val firstAccepted = assertNotNull(watch.accept(first.offer, WATCH, PHONE).valueOrNull())
            val firstSealed = assertNotNull(phone.seal(first, firstAccepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            val second = phone.start(LinkMode.PAIR, PHONE)
            val secondAccepted = assertNotNull(watch.accept(second.offer, WATCH, PHONE).valueOrNull())
            val secondSealed =
                assertNotNull(phone.seal(second, secondAccepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            val secondOpened = assertNotNull(watch.open(secondAccepted, secondSealed.bundle).valueOrNull())

            assertFalse(phone.verify(firstSealed, secondOpened.confirm))
        }

    @Test
    fun `the phone does not seal a bundle once its offer has expired`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accepted = assertNotNull(watch.accept(started.offer, WATCH, PHONE).valueOrNull())
            clock.millis += 60_000

            assertEquals(
                LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER),
                phone.seal(started, accepted.reply, WATCH, LinkBundle(mode = LinkMode.PAIR)),
            )
        }

    private class MutableClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val PHONE = "phone-node"
        const val WATCH = "watch-node"
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/MemoryKeyValueStorage.kt`:

```kotlin
package app.logdate.wear.sync.v2

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.peer.link.PairKeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Strings in memory, observable, for the watch's version 2 tests; other value types read back their defaults. */
class MemoryKeyValueStorage : KeyValueStorage {
    private val strings = MutableStateFlow<Map<String, String>>(emptyMap())

    val values: Map<String, String> get() = strings.value

    override suspend fun getString(key: String): String? = strings.value[key]

    override fun getStringSync(key: String): String? = strings.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        strings.update { it + (key to value) }
    }

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) = Unit

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = Unit

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = Unit

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = Unit

    override suspend fun remove(key: String) {
        strings.update { it - key }
    }

    override suspend fun contains(key: String): Boolean = key in strings.value

    override suspend fun clear() {
        strings.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = strings.map { it[key] }.distinctUntilChanged()

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = flowOf(defaultValue)

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = flowOf(defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = flowOf(defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = flowOf(defaultValue)
}

/** Pair keys in memory. */
class MemoryPairKeyVault : PairKeyVault {
    val keys = mutableMapOf<String, ByteArray>()

    override suspend fun put(
        peer: String,
        key: ByteArray,
    ) {
        keys[peer] = key
    }

    override suspend fun get(peer: String): ByteArray? = keys[peer]

    override suspend fun remove(peer: String) {
        keys.remove(peer)
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinatorTest.kt`:

```kotlin
package app.logdate.wear.sync.v2.link

import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.link.LinkAnswer
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.client.sync.peer.link.valueOrNull
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.MemoryPairKeyVault
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [WatchLinkCoordinator] against the phone's half of the exchange: a pairing is stored with its
 * key, every refusal names a reason the phone can act on, and a move to another phone marks it stale.
 */
class WatchLinkCoordinatorTest {
    private val clock = MutableClock(1_000_000)
    private val handshake = BouncyCastleLinkHandshake()
    private val phone = PhoneLinkExchange(handshake, clock)
    private val pairKeys = MemoryPairKeyVault()
    private val store = WatchLinkStore(MemoryKeyValueStorage(), pairKeys, clock)
    private var enabled = true
    private val coordinator =
        WatchLinkCoordinator(
            exchange = WatchLinkExchange(handshake, LinkNonceLedger(clock), clock),
            store = store,
            v2Enabled = { enabled },
            localNodeId = { WATCH },
        )

    @Test
    fun `an offer and its bundle pair the watch with the phone`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())

            val confirm = answered(coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed)), LinkConfirm.serializer())

            assertTrue(phone.verify(sealed, confirm))
            assertEquals(PeerLinkState.PAIRED, store.current().peerLinkState())
            assertContentEquals(sealed.pairKey, pairKeys.keys[PHONE])
        }

    @Test
    fun `with the redesigned app off the watch refuses an offer`() =
        runTest {
            enabled = false

            val answer = coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(phone.start(LinkMode.PAIR, PHONE)))

            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), LinkWire.read(assertNotNull(answer), LinkAccept.serializer()))
            assertNull(store.current())
        }

    @Test
    fun `a bundle with no open offer is refused as expired`() =
        runTest {
            val bundle = SealedLinkBundle(iv = "aXY=", ciphertext = "Y2lwaGVy")

            val answer = coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, LinkWire.write(SealedLinkBundle.serializer(), bundle))

            assertEquals(LinkAnswer.Refused(LinkRefusal.EXPIRED_OFFER), LinkWire.read(assertNotNull(answer), LinkConfirm.serializer()))
        }

    @Test
    fun `a bundle from another phone than the offer's is refused`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())

            val answer = coordinator.answer("another-phone", WearPaths.LINK_BUNDLE, bundleBytes(sealed))

            assertEquals(LinkAnswer.Refused(LinkRefusal.EXPIRED_OFFER), LinkWire.read(assertNotNull(answer), LinkConfirm.serializer()))
            assertNull(store.current())
        }

    @Test
    fun `an account link is refused until this watch can install one`() =
        runTest {
            val answer = coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(phone.start(LinkMode.CLOUD, PHONE)))

            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), LinkWire.read(assertNotNull(answer), LinkAccept.serializer()))
        }

    @Test
    fun `an offer that is not a link offer is refused`() =
        runTest {
            val answer = coordinator.answer(PHONE, WearPaths.LINK_OFFER, "{}".encodeToByteArray())

            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), LinkWire.read(assertNotNull(answer), LinkAccept.serializer()))
        }

    @Test
    fun `moving to another phone marks the pairing stale`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed))

            coordinator.onNodeMigrated(PHONE)

            assertEquals(PeerLinkState.NEEDS_RELINK, store.current().peerLinkState())
        }

    @Test
    fun `a path that is not a link request has no answer`() =
        runTest {
            assertNull(coordinator.answer(PHONE, "/logdate/v2/receipts", ByteArray(0)))
            assertTrue(WatchLinkCoordinator.handles(WearPaths.LINK_OFFER))
        }

    private fun offerBytes(started: PhoneLinkExchange.Started): ByteArray = LinkWire.write(LinkOffer.serializer(), started.offer)

    private fun bundleBytes(sealed: PhoneLinkExchange.Sealed): ByteArray = LinkWire.write(SealedLinkBundle.serializer(), sealed.bundle)

    private fun <T> answered(
        bytes: ByteArray?,
        serializer: KSerializer<T>,
    ): T {
        val answer = LinkWire.read(assertNotNull(bytes), serializer)
        if (answer !is LinkAnswer.Answered) fail("Expected an answer but the watch sent $answer")
        return answer.value
    }

    private class MutableClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val PHONE = "phone-node"
        const val WATCH = "watch-node"
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchOwnStateTest.kt`:

```kotlin
package app.logdate.wear.sync.v2

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.WatchState
import app.logdate.wear.sync.v2.link.WatchLinkRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [WatchOwnState]: the watch reports its link, counts and features as one section. */
class WatchOwnStateTest {
    @Test
    fun `no link record is reported as unlinked`() =
        runTest {
            assertEquals(WatchState(), WatchOwnState(links = flowOf(null)).observe().first())
        }

    @Test
    fun `a pairing is reported with the features this build has`() =
        runTest {
            val own = WatchOwnState(links = flowOf(WatchLinkRecord(LinkMode.PAIR, "phone", 1)), features = setOf("prefs.v1"))

            assertEquals(WatchState(link = PeerLinkState.PAIRED, features = setOf("prefs.v1")), own.observe().first())
        }

    @Test
    fun `a stale pairing asks to be linked again`() =
        runTest {
            val own = WatchOwnState(links = flowOf(WatchLinkRecord(LinkMode.PAIR, "phone", 1, stale = true)))

            assertEquals(PeerLinkState.NEEDS_RELINK, own.observe().first().link)
        }

    @Test
    fun `the outbox counts are reported`() =
        runTest {
            val own = WatchOwnState(links = flowOf(null), counts = flowOf(WatchCounts(outbox = 3, deadLetters = 1, freeBytes = 9)))

            assertEquals(WatchState(outboxCount = 3, deadLetterCount = 1, freeBytes = 9), own.observe().first())
        }
}
```

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt`, add `import com.google.android.gms.wearable.NodeClient` and, after the test `the phone coming into reach reaches the listener`, add:

```kotlin
    @Test
    fun `every request the phone sends reaches the listener`() {
        listOf(
            WearPaths.LINK_OFFER,
            WearPaths.LINK_BUNDLE,
        ).forEach { path -> assertResolvesToListener(MessageClient.ACTION_REQUEST_RECEIVED, path) }
    }

    @Test
    fun `moving the watch to another phone reaches the listener`() {
        assertResolvesToListener(NodeClient.ACTION_NODE_MIGRATED, "")
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.link.LinkWireTest' :client:sync:testAndroidHostTest --tests 'app.logdate.client.sync.peer.link.LinkExchangeTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'LinkWire'", "Unresolved reference 'PhoneLinkExchange'", "Unresolved reference 'WatchLinkExchange'".

- [ ] **Step 3: Write the common exchange**

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/LinkWire.kt`:

```kotlin
package app.logdate.client.sync.peer.link

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.LinkRefused
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import kotlinx.serialization.KSerializer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Why one side stopped a link exchange. Each is logged and answered, never retried in a loop. */
enum class LinkExchangeFailure {
    MALFORMED,
    UNSUPPORTED_SCHEMA,
    EXPIRED_OFFER,
    REPLAYED_NONCE,
    BAD_KEY,
    BUNDLE_UNREADABLE,
}

/** One step of an exchange: the value it produced, or why it stopped. */
sealed interface LinkStep<out T> {
    data class Ok<T>(
        val value: T,
    ) : LinkStep<T>

    data class Failed(
        val failure: LinkExchangeFailure,
    ) : LinkStep<Nothing>
}

fun <T> LinkStep<T>.valueOrNull(): T? = (this as? LinkStep.Ok<T>)?.value

/** What the other device answered a link request with. */
sealed interface LinkAnswer<out T> {
    data class Answered<T>(
        val value: T,
    ) : LinkAnswer<T>

    data class Refused(
        val reason: LinkRefusal,
    ) : LinkAnswer<Nothing>

    data class Unreadable(
        val failure: LinkExchangeFailure,
    ) : LinkAnswer<Nothing>
}

/** Reads and writes link messages. Keys, nonces, IVs and MACs travel as standard Base64. */
@OptIn(ExperimentalEncodingApi::class)
object LinkWire {
    fun encodeKey(bytes: ByteArray): String = Base64.encode(bytes)

    fun decodeKey(text: String): ByteArray? =
        try {
            Base64.decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }

    fun <T> write(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray = WearJson.encodeToBytes(serializer, value)

    fun refusal(reason: LinkRefusal): ByteArray = write(LinkRefused.serializer(), LinkRefused(reason = reason))

    /** A refusal is read first: it has only `reason`, so no answer ever reads as one. */
    fun <T> read(
        bytes: ByteArray,
        serializer: KSerializer<T>,
    ): LinkAnswer<T> {
        val refused = WearJson.decode(bytes, WearProtocol.LINK_SCHEMA, LinkRefused.serializer())
        if (refused is Decoded.Supported) return LinkAnswer.Refused(refused.value.reason)
        return when (val decoded = WearJson.decode(bytes, WearProtocol.LINK_SCHEMA, serializer)) {
            is Decoded.Supported -> LinkAnswer.Answered(decoded.value)
            is Decoded.Unsupported -> LinkAnswer.Unreadable(LinkExchangeFailure.UNSUPPORTED_SCHEMA)
            is Decoded.Malformed -> LinkAnswer.Unreadable(LinkExchangeFailure.MALFORMED)
        }
    }
}
```

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PhoneLinkExchange.kt`:

```kotlin
package app.logdate.client.sync.peer.link

import app.logdate.client.device.crypto.link.LinkHandshake
import app.logdate.client.device.crypto.link.LinkHandshakeException
import app.logdate.client.device.crypto.link.LinkKeyPair
import app.logdate.client.device.crypto.link.LinkTranscript
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearProtocol
import kotlin.time.Clock

/**
 * The phone's half of a link exchange: it opens with an offer, seals the bundle under the key both
 * devices derive, and checks the watch's confirmation.
 */
class PhoneLinkExchange(
    private val handshake: LinkHandshake,
    private val clock: Clock = Clock.System,
) {
    /** An offer on its way. The single-use private key stays inside and is never written anywhere. */
    class Started internal constructor(
        val offer: LinkOffer,
        internal val keyPair: LinkKeyPair,
        internal val nonce: ByteArray,
    )

    /** A sealed bundle, with what the phone needs to check the confirmation and store after a pairing. */
    class Sealed internal constructor(
        val bundle: SealedLinkBundle,
        val pairKey: ByteArray,
        internal val confirmKey: ByteArray,
        internal val transcriptHash: ByteArray,
    )

    fun start(
        mode: LinkMode,
        phoneNode: String,
    ): Started {
        val keyPair = handshake.generateKeyPair()
        val nonce = handshake.newNonce()
        val now = clock.now().toEpochMilliseconds()
        val offer =
            LinkOffer(
                mode = mode,
                phoneNode = phoneNode,
                phonePublicKey = LinkWire.encodeKey(keyPair.publicKey),
                phoneNonce = LinkWire.encodeKey(nonce),
                issuedAt = now,
                expiresAt = now + WearProtocol.LINK_OFFER_TTL_MS,
            )
        return Started(offer, keyPair, nonce)
    }

    /**
     * Seals [bundle] for the watch at [watchNode], the node the offer was sent to. The transcript names
     * that node rather than the one the answer claims, so an answer relayed from another node cannot open it.
     */
    fun seal(
        started: Started,
        accept: LinkAccept,
        watchNode: String,
        bundle: LinkBundle,
    ): LinkStep<Sealed> {
        if (clock.now().toEpochMilliseconds() >= started.offer.expiresAt) return LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER)
        val watchKey = LinkWire.decodeKey(accept.watchPublicKey) ?: return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
        val watchNonce = LinkWire.decodeKey(accept.watchNonce) ?: return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
        val hash =
            handshake.transcriptHash(
                LinkTranscript(
                    phoneNode = started.offer.phoneNode,
                    watchNode = watchNode,
                    phonePublicKey = started.keyPair.publicKey,
                    watchPublicKey = watchKey,
                    phoneNonce = started.nonce,
                    watchNonce = watchNonce,
                    schema = WearProtocol.LINK_SCHEMA,
                ),
            )
        val keys =
            try {
                handshake.deriveKeys(started.keyPair.privateKey, watchKey, hash)
            } catch (_: LinkHandshakeException) {
                return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
            }
        val plaintext = LinkWire.write(LinkBundle.serializer(), bundle)
        val sealed =
            try {
                handshake.seal(keys.bundleKey, plaintext, hash)
            } finally {
                plaintext.fill(0)
            }
        val wire = SealedLinkBundle(iv = LinkWire.encodeKey(sealed.iv), ciphertext = LinkWire.encodeKey(sealed.ciphertext))
        return LinkStep.Ok(Sealed(wire, keys.pairKey, keys.confirmKey, hash))
    }

    fun verify(
        sealed: Sealed,
        confirm: LinkConfirm,
    ): Boolean {
        val mac = LinkWire.decodeKey(confirm.mac) ?: return false
        return handshake.verifyConfirmation(sealed.confirmKey, sealed.transcriptHash, mac)
    }
}
```

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/WatchLinkExchange.kt`:

```kotlin
package app.logdate.client.sync.peer.link

import app.logdate.client.device.crypto.link.LinkHandshake
import app.logdate.client.device.crypto.link.LinkHandshakeException
import app.logdate.client.device.crypto.link.LinkKeys
import app.logdate.client.device.crypto.link.LinkTranscript
import app.logdate.client.device.crypto.link.SealedBytes
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import kotlin.time.Clock

/**
 * The watch's half of a link exchange: it accepts a fresh offer once, opens the bundle sealed for it,
 * and confirms with an HMAC over the transcript.
 */
class WatchLinkExchange(
    private val handshake: LinkHandshake,
    private val nonces: LinkNonceLedger,
    private val clock: Clock = Clock.System,
) {
    /** An offer this watch accepted, waiting for its bundle. */
    class Accepted internal constructor(
        val reply: LinkAccept,
        val offer: LinkOffer,
        internal val keys: LinkKeys,
        internal val transcriptHash: ByteArray,
    )

    class Opened internal constructor(
        val bundle: LinkBundle,
        val confirm: LinkConfirm,
        val pairKey: ByteArray,
    )

    /** [phoneNode] is the node the offer came from; the transcript binds it instead of the offer's own claim. */
    suspend fun accept(
        offer: LinkOffer,
        watchNode: String,
        phoneNode: String,
    ): LinkStep<Accepted> {
        if (!offer.isValidAt(clock.now().toEpochMilliseconds())) return LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER)
        val phoneKey = LinkWire.decodeKey(offer.phonePublicKey) ?: return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
        val phoneNonce = LinkWire.decodeKey(offer.phoneNonce) ?: return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
        if (!nonces.accept(offer.phoneNonce, offer.expiresAt)) return LinkStep.Failed(LinkExchangeFailure.REPLAYED_NONCE)
        val keyPair = handshake.generateKeyPair()
        val nonce = handshake.newNonce()
        val hash =
            handshake.transcriptHash(
                LinkTranscript(
                    phoneNode = phoneNode,
                    watchNode = watchNode,
                    phonePublicKey = phoneKey,
                    watchPublicKey = keyPair.publicKey,
                    phoneNonce = phoneNonce,
                    watchNonce = nonce,
                    schema = WearProtocol.LINK_SCHEMA,
                ),
            )
        val keys =
            try {
                handshake.deriveKeys(keyPair.privateKey, phoneKey, hash)
            } catch (_: LinkHandshakeException) {
                return LinkStep.Failed(LinkExchangeFailure.BAD_KEY)
            } finally {
                keyPair.privateKey.fill(0)
            }
        val reply =
            LinkAccept(
                watchNode = watchNode,
                watchPublicKey = LinkWire.encodeKey(keyPair.publicKey),
                watchNonce = LinkWire.encodeKey(nonce),
            )
        return LinkStep.Ok(Accepted(reply, offer, keys, hash))
    }

    fun open(
        accepted: Accepted,
        sealed: SealedLinkBundle,
    ): LinkStep<Opened> {
        if (clock.now().toEpochMilliseconds() >= accepted.offer.expiresAt) return LinkStep.Failed(LinkExchangeFailure.EXPIRED_OFFER)
        val iv = LinkWire.decodeKey(sealed.iv) ?: return LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE)
        val ciphertext = LinkWire.decodeKey(sealed.ciphertext) ?: return LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE)
        val plaintext =
            try {
                handshake.open(accepted.keys.bundleKey, SealedBytes(iv, ciphertext), accepted.transcriptHash)
            } catch (_: LinkHandshakeException) {
                return LinkStep.Failed(LinkExchangeFailure.BUNDLE_UNREADABLE)
            }
        val bundle =
            try {
                when (val decoded = WearJson.decode(plaintext, WearProtocol.LINK_SCHEMA, LinkBundle.serializer())) {
                    is Decoded.Supported -> decoded.value
                    is Decoded.Unsupported -> return LinkStep.Failed(LinkExchangeFailure.UNSUPPORTED_SCHEMA)
                    is Decoded.Malformed -> return LinkStep.Failed(LinkExchangeFailure.MALFORMED)
                }
            } finally {
                plaintext.fill(0)
            }
        val mac = handshake.confirmation(accepted.keys.confirmKey, accepted.transcriptHash)
        return LinkStep.Ok(Opened(bundle, LinkConfirm(mac = LinkWire.encodeKey(mac)), accepted.keys.pairKey))
    }
}
```

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PairKeyVault.kt`:

```kotlin
package app.logdate.client.sync.peer.link

import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes

/**
 * Where a device stores the pair key from its last link with each peer. Later requests between the two
 * devices can be authenticated with it; it never leaves the device.
 */
interface PairKeyVault {
    suspend fun put(
        peer: String,
        key: ByteArray,
    )

    suspend fun get(peer: String): ByteArray?

    suspend fun remove(peer: String)
}

class SecureStoragePairKeyVault(
    private val secureStorage: SecureStorage,
    private val prefix: String,
) : PairKeyVault {
    override suspend fun put(
        peer: String,
        key: ByteArray,
    ) = secureStorage.putBytes(prefix + peer, key)

    override suspend fun get(peer: String): ByteArray? = secureStorage.getBytes(prefix + peer)

    override suspend fun remove(peer: String) = secureStorage.remove(prefix + peer)
}
```

- [ ] **Step 4: Run the common tests again**

Run the Step 2 command again.
Expected: PASS (6 `LinkWireTest` and 8 `LinkExchangeTest` tests).

- [ ] **Step 5: Run the watch tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.v2.link.WatchLinkCoordinatorTest' --tests 'app.logdate.wear.sync.v2.WatchOwnStateTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'WatchLinkCoordinator'", "Unresolved reference 'WatchLinkStore'", "Unresolved reference 'WatchOwnState'".

- [ ] **Step 6: Write the watch's side**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/LocalNode.kt`:

```kotlin
package app.logdate.wear.sync.v2

import com.google.android.gms.wearable.NodeClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** This watch's Data Layer node, or null, with the failure logged, when Play services cannot say. */
internal suspend fun NodeClient.localNodeIdOrNull(): String? =
    try {
        localNode.await().id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Napier.e("Could not read this watch's Data Layer node; link requests are refused until it can", e)
        null
    }
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkStore.kt`:

```kotlin
package app.logdate.wear.sync.v2.link

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.client.sync.peer.state.PeerLinkState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * The phone this watch is linked with and how.
 *
 * @property stale The watch moved to another phone since; it waits for that phone to link it again.
 */
@Serializable
data class WatchLinkRecord(
    val mode: LinkMode,
    val phoneNode: String,
    val linkedAt: Long,
    val stale: Boolean = false,
    val accountId: String? = null,
)

/** What the watch publishes about its link in its state document. */
fun WatchLinkRecord?.peerLinkState(): PeerLinkState {
    if (this == null) return PeerLinkState.UNLINKED
    if (stale) return PeerLinkState.NEEDS_RELINK
    return when (mode) {
        LinkMode.PAIR -> PeerLinkState.PAIRED
        LinkMode.CLOUD -> PeerLinkState.LINKED
    }
}

/** The watch's link record, as one JSON value, and the pair key in secure storage. */
class WatchLinkStore(
    private val storage: KeyValueStorage,
    private val pairKeys: PairKeyVault,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observe(): Flow<WatchLinkRecord?> = storage.observeString(KEY).map(::decode).distinctUntilChanged()

    suspend fun current(): WatchLinkRecord? = decode(storage.getString(KEY))

    suspend fun savePairing(
        phoneNode: String,
        pairKey: ByteArray,
    ) {
        pairKeys.put(phoneNode, pairKey)
        write(WatchLinkRecord(mode = LinkMode.PAIR, phoneNode = phoneNode, linkedAt = clock.now().toEpochMilliseconds()))
    }

    /** Marks the link with [phoneNode] stale; false when the watch is not linked with that phone. */
    suspend fun markStale(phoneNode: String): Boolean {
        val record = current() ?: return false
        if (record.phoneNode != phoneNode || record.stale) return false
        write(record.copy(stale = true))
        return true
    }

    private suspend fun write(record: WatchLinkRecord) {
        storage.putString(KEY, json.encodeToString(WatchLinkRecord.serializer(), record))
    }

    private fun decode(text: String?): WatchLinkRecord? {
        if (text == null) return null
        return try {
            json.decodeFromString(WatchLinkRecord.serializer(), text)
        } catch (e: SerializationException) {
            Napier.e("This watch's link record could not be read; it reports no link so the phone links it again", e)
            null
        } catch (e: IllegalArgumentException) {
            Napier.e("This watch's link record could not be read; it reports no link so the phone links it again", e)
            null
        }
    }

    private companion object {
        const val KEY = "watch.link.record.v1"
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinator.kt`:

```kotlin
package app.logdate.wear.sync.v2.link

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.link.LinkExchangeFailure
import app.logdate.client.sync.peer.link.LinkStep
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.WatchLinkExchange
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The watch's side of linking. The phone always opens; this answers its offer and its bundle, records
 * the link, and marks it stale when the watch moves to another phone.
 *
 * One exchange runs at a time and a new offer replaces one that never reached its bundle. Every
 * refusal is logged with the reason the phone receives.
 */
class WatchLinkCoordinator(
    private val exchange: WatchLinkExchange,
    private val store: WatchLinkStore,
    private val v2Enabled: suspend () -> Boolean,
    private val localNodeId: suspend () -> String?,
) {
    private val mutex = Mutex()
    private var pending: Pending? = null

    private class Pending(
        val phoneNode: String,
        val accepted: WatchLinkExchange.Accepted,
    )

    /** The answer to a request at [path], or null when [path] is not a link request. */
    suspend fun answer(
        sourceNodeId: String,
        path: String,
        payload: ByteArray,
    ): ByteArray? =
        when (path) {
            WearPaths.LINK_OFFER -> mutex.withLock { answerOffer(sourceNodeId, payload) }
            WearPaths.LINK_BUNDLE -> mutex.withLock { answerBundle(sourceNodeId, payload) }
            else -> null
        }

    suspend fun onNodeMigrated(oldNodeId: String) {
        if (!store.markStale(oldNodeId)) return
        Napier.w("This watch moved to another phone; it waits for that phone to link it again")
    }

    private suspend fun answerOffer(
        sourceNodeId: String,
        payload: ByteArray,
    ): ByteArray {
        if (!v2Enabled()) return refuse(LinkRefusal.UNSUPPORTED, "the redesigned watch app is off")
        val offer =
            when (val decoded = WearJson.decode(payload, WearProtocol.LINK_SCHEMA, LinkOffer.serializer())) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> return refuse(LinkRefusal.UNSUPPORTED, "the offer uses schema ${decoded.schema}")
                is Decoded.Malformed -> return refuse(LinkRefusal.UNSUPPORTED, decoded.reason)
            }
        if (!acceptsMode(offer.mode)) return refuse(LinkRefusal.UNSUPPORTED, "this watch build cannot link to an account")
        val watchNode = localNodeId() ?: return refuse(LinkRefusal.UNSUPPORTED, "this watch's node is unknown")
        return when (val step = exchange.accept(offer, watchNode = watchNode, phoneNode = sourceNodeId)) {
            is LinkStep.Ok -> {
                pending = Pending(sourceNodeId, step.value)
                LinkWire.write(LinkAccept.serializer(), step.value.reply)
            }
            is LinkStep.Failed -> refuse(step.failure.toRefusal(), "the offer failed its ${step.failure} check")
        }
    }

    private suspend fun answerBundle(
        sourceNodeId: String,
        payload: ByteArray,
    ): ByteArray {
        val current =
            pending?.takeIf { it.phoneNode == sourceNodeId }
                ?: return refuse(LinkRefusal.EXPIRED_OFFER, "no offer from this phone is open")
        pending = null
        val sealed =
            when (val decoded = WearJson.decode(payload, WearProtocol.LINK_SCHEMA, SealedLinkBundle.serializer())) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> return refuse(LinkRefusal.UNSUPPORTED, "the bundle uses schema ${decoded.schema}")
                is Decoded.Malformed -> return refuse(LinkRefusal.UNSUPPORTED, decoded.reason)
            }
        val opened =
            when (val step = exchange.open(current.accepted, sealed)) {
                is LinkStep.Ok -> step.value
                is LinkStep.Failed -> return refuse(step.failure.toRefusal(), "the bundle failed its ${step.failure} check")
            }
        return when (opened.bundle.mode) {
            LinkMode.PAIR -> {
                store.savePairing(sourceNodeId, opened.pairKey)
                Napier.i("This watch is paired with its phone")
                LinkWire.write(LinkConfirm.serializer(), opened.confirm)
            }
            LinkMode.CLOUD -> refuse(LinkRefusal.UNSUPPORTED, "this watch build cannot link to an account")
        }
    }

    private fun acceptsMode(mode: LinkMode): Boolean = mode == LinkMode.PAIR

    private fun refuse(
        reason: LinkRefusal,
        why: String,
    ): ByteArray {
        Napier.w("Refused the phone's link request with $reason: $why")
        return LinkWire.refusal(reason)
    }

    private fun LinkExchangeFailure.toRefusal(): LinkRefusal =
        when (this) {
            LinkExchangeFailure.EXPIRED_OFFER -> LinkRefusal.EXPIRED_OFFER
            LinkExchangeFailure.REPLAYED_NONCE -> LinkRefusal.REPLAYED_NONCE
            else -> LinkRefusal.UNSUPPORTED
        }

    companion object {
        fun handles(path: String): Boolean = path == WearPaths.LINK_OFFER || path == WearPaths.LINK_BUNDLE
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchOwnState.kt`:

```kotlin
package app.logdate.wear.sync.v2

import app.logdate.client.sync.peer.state.WatchState
import app.logdate.wear.sync.v2.link.WatchLinkRecord
import app.logdate.wear.sync.v2.link.peerLinkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** How much the watch has waiting and set aside, and how much space it has left. */
data class WatchCounts(
    val outbox: Int = 0,
    val deadLetters: Int = 0,
    val freeBytes: Long? = null,
)

/**
 * The watch-owned section of the watch's state document, assembled from the parts that own each field.
 * [features] lists only what this build implements.
 */
class WatchOwnState(
    private val links: Flow<WatchLinkRecord?>,
    private val features: Set<String> = emptySet(),
    private val counts: Flow<WatchCounts> = flowOf(WatchCounts()),
) {
    fun observe(): Flow<WatchState> =
        combine(links, counts) { link, count ->
            WatchState(
                outboxCount = count.outbox,
                deadLetterCount = count.deadLetters,
                freeBytes = count.freeBytes,
                link = link.peerLinkState(),
                features = features,
            )
        }.distinctUntilChanged()
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchStateMirror.kt`, add the constructor parameter after `merger`:

```kotlin
    private val ownState: Flow<WatchState> = flowOf(WatchState()),
```

replace `start` with:

```kotlin
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            catchUpWithPhone()
            combine(featureFlags.observe(FeatureFlag.WATCH_EXPERIENCE_V2), ownState) { enabled, state -> enabled to state }
                .distinctUntilChanged()
                .collect { (enabled, state) -> publish(enabled, state) }
        }
```

and replace `private suspend fun publish(enabled: Boolean)` and its first line with:

```kotlin
    private suspend fun publish(
        enabled: Boolean,
        state: WatchState,
    ) {
        val document = StateDocument(role = PeerRole.WATCH, writtenAt = clock.now().toEpochMilliseconds(), watch = state)
```

leaving the rest of `publish` unchanged. Add the imports `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.combine` and `kotlinx.coroutines.flow.flowOf`.

Create `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt`:

```kotlin
package app.logdate.wear.di

import android.content.Context
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.client.sync.peer.link.SecureStoragePairKeyVault
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.wear.sync.v2.WatchOwnState
import app.logdate.wear.sync.v2.link.WatchLinkCoordinator
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.sync.v2.localNodeIdOrNull
import com.google.android.gms.wearable.Wearable
import org.koin.dsl.module

/** Koin module for the watch's version 2 sync: linking, delivery and the library window. */
val wearSyncV2Module =
    module {
        single<PairKeyVault> { SecureStoragePairKeyVault(get(), prefix = "watch.link.pair_key.") }
        single { WatchLinkStore(storage = get(), pairKeys = get()) }
        single { WatchLinkExchange(handshake = BouncyCastleLinkHandshake(), nonces = LinkNonceLedger()) }
        single {
            val nodeClient = Wearable.getNodeClient(get<Context>())
            val flags = get<FeatureFlagStore>()
            WatchLinkCoordinator(
                exchange = get(),
                store = get(),
                v2Enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
                localNodeId = { nodeClient.localNodeIdOrNull() },
            )
        }
        single { WatchOwnState(links = get<WatchLinkStore>().observe()) }
    }
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt`, in the `WatchStateMirror(` call after `merger = get(),` add:

```kotlin
                ownState = get<WatchOwnState>().observe(),
```

and add `import app.logdate.wear.sync.v2.WatchOwnState`.

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, add `wearSyncV2Module,` after `wearV2Module,` in `modules(...)` and `import app.logdate.wear.di.wearSyncV2Module`.

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt`, after `onCapabilityChanged` add:

```kotlin
    /**
     * Answers the phone's link requests. The answer is a [Task] the phone waits on; when nothing can
     * answer it fails, which the phone records as a failed exchange and tries again later.
     */
    override fun onRequest(
        nodeId: String,
        path: String,
        request: ByteArray,
    ): Task<ByteArray> {
        val answer = TaskCompletionSource<ByteArray>()
        serviceScope.launch {
            val bytes = withContext(NonCancellable) { answerRequest(nodeId, path, request) }
            if (bytes != null) {
                answer.setResult(bytes)
            } else {
                answer.setException(IllegalArgumentException("This watch has no answer for $path"))
            }
        }
        return answer.task
    }

    private suspend fun answerRequest(
        nodeId: String,
        path: String,
        request: ByteArray,
    ): ByteArray? {
        if (!WatchLinkCoordinator.handles(path)) {
            Napier.w("The phone sent a request this watch does not answer: $path")
            return null
        }
        val koin = resolveKoin()
        if (koin == null) {
            Napier.e("Koin unavailable after $MAX_KOIN_RETRIES retries; the phone's link request fails and is tried again")
            return null
        }
        return koin.get<WatchLinkCoordinator>().answer(nodeId, path, request)
    }

    /** The watch now belongs to another phone, so the link with [nodeId] is stale. */
    override fun onNodeMigrated(
        nodeId: String,
        dataItems: DataItemBuffer,
    ) {
        dataItems.release()
        serviceScope.launch {
            withContext(NonCancellable) {
                val koin = resolveKoin()
                if (koin == null) {
                    Napier.e("Koin unavailable after $MAX_KOIN_RETRIES retries; the move to another phone is noticed when it links")
                    return@withContext
                }
                koin.get<WatchLinkCoordinator>().onNodeMigrated(nodeId)
            }
        }
    }
```

with the imports `app.logdate.wear.sync.v2.link.WatchLinkCoordinator`, `com.google.android.gms.tasks.Task`, `com.google.android.gms.tasks.TaskCompletionSource` and `com.google.android.gms.wearable.DataItemBuffer`.

In `app/wear/src/main/AndroidManifest.xml`, after the `CAPABILITY_CHANGED` `</intent-filter>` of `.sync.WearDataLayerListenerService` add:

```xml
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.REQUEST_RECEIVED" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/link" />
            </intent-filter>
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.NODE_MIGRATED" />
                <data
                    android:scheme="wear"
                    android:host="*" />
            </intent-filter>
```

and add "and the phone's link requests" to the comment above the service.

- [ ] **Step 7: Run the watch tests again**

Run the Step 5 command again, then `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.v2.WatchStateMirrorTest' --console=plain`.
Expected: PASS (8 `WatchLinkCoordinatorTest`, 4 `WatchOwnStateTest`, and every existing `WatchStateMirrorTest` test unchanged).

- [ ] **Step 8: Run every affected module's checks, the iOS compile and the manifest test**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:testAndroidHostTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearManifestFiltersTest --console=plain
```
Expected: BUILD SUCCESSFUL with 5 tests passing, including `every request the phone sends reaches the listener` and `moving the watch to another phone reaches the listener`.

- [ ] **Step 9: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task01-watch-pairing.txt <<'EOF'
chore(wear): answer the phone's pairing offer on the watch

With the redesigned watch app on, the watch now answers the phone's
link requests. It accepts a fresh offer once, opens the bundle sealed
for it, confirms with a MAC over the exchange, and records the pairing
and its key. Moving the watch to another phone marks the pairing
stale, and the watch reports that it needs to be linked again.

Every refusal names the reason the phone receives: an expired or
repeated offer, a bundle that does not open, or a build that cannot
link to an account yet. The exchange itself is shared code both
devices run, tested against the real key exchange. Nothing on the
phone sends a request yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 10: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/LinkWire.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PhoneLinkExchange.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/WatchLinkExchange.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PairKeyVault.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/link/LinkWireTest.kt client/sync/src/androidHostTest/kotlin/app/logdate/client/sync/peer/link/LinkExchangeTest.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/LocalNode.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkStore.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinator.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchOwnState.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchStateMirror.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt app/wear/src/main/AndroidManifest.xml app/wear/src/test/kotlin/app/logdate/wear/sync/v2/MemoryKeyValueStorage.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinatorTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchOwnStateTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task01-watch-pairing.txt
```

---

### Task 2: Pair the phone with its watch without an account (D1, phone)

The phone runs the exchange for every reachable watch that advertises `logdate_watch_sync_v2`, while its own switch is on. It decides per watch from its stored record, the watch's latest state document and the plan (`PAIR` here; Task 5 adds `CLOUD`), runs the offer and bundle requests, and records the outcome. It shows one notification when a watch is set up, and one when a watch is set up for another account (the only failure the person must fix in pair-only mode). Every other failure is recorded with its reason and retried with a growing wait.

**Files:**
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerStateStore.kt` (after `latest`, `:44-48`)
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/LocalNode.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkRequester.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkTargets.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneWatchLinkStore.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkBundleSource.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecision.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiator.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/WatchLinkNotifier.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:125` (include the module)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt:136-145`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt` (new `onCapabilityChanged` after `onChannelOpened`, `:151-157`)
- Modify: `app/android-main/src/main/AndroidManifest.xml:266-273` (a `CAPABILITY_CHANGED` filter)
- Modify: `client/notifications/src/androidMain/res/values/strings.xml:34` (five strings)
- Modify: `app/android-main/build.gradle.kts:532` (test dependencies on `:client:device` and `:client:logdate-datastore`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/PeerStateStoreTest.kt` (1 new test)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/MemoryKeyValueStorage.kt` (test helper, also `MemoryPairKeyVault`)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecisionTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiatorTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/MessageClientLinkRequesterTest.kt`

**Interfaces:**
- Consumes: Task 1's `PhoneLinkExchange`, `WatchLinkExchange` (in tests), `LinkWire`, `LinkAnswer`, `LinkStep`, `valueOrNull`, `PairKeyVault`, `SecureStoragePairKeyVault`; `PeerCapabilityPresence.observe(WearProtocol.WATCH_CAPABILITY_V2)`; `FeatureFlagStore.observe(FeatureFlag.WATCH_EXPERIENCE_V2)`; `LogDateNotificationChannelKey.WATCH_SYNC`; `NotificationResources.drawable.ic_notification_watch`.
- Produces:
  - `fun PeerStateStore.documents(role: PeerRole): Flow<Map<String, StateDocument>>`
  - `fun interface LinkRequester { suspend fun request(nodeId: String, path: String, payload: ByteArray): Result<ByteArray> }`, `class MessageClientLinkRequester(messageClient: MessageClient, timeoutMs: Long = 10_000) : LinkRequester`
  - `data class LinkTarget(val nodeId: String, val displayName: String)`, `fun interface LinkTargets { suspend fun reachable(): List<LinkTarget> }`, `class CapabilityLinkTargets(capabilityClient: CapabilityClient) : LinkTargets`
  - `@Serializable enum class PhoneLinkFailure(val needsAttention: Boolean) { PHONE_NODE_UNKNOWN, WATCH_UNREACHABLE, WATCH_CANNOT_LINK, OFFER_EXPIRED, ANSWER_UNREADABLE, CONFIRMATION_MISMATCH, WATCH_BOUND_TO_ANOTHER_ACCOUNT, BUNDLE_UNAVAILABLE }`
  - `@Serializable data class PhoneWatchLink(nodeId, displayName, mode: LinkMode, accountId: String? = null, linkedAt: Long? = null, confirmedAt: Long? = null, failure: PhoneLinkFailure? = null, attempts: Int = 0, lastAttemptAt: Long = 0, nextAttemptAt: Long = 0, seenWatchDocAt: Long = 0)`
  - `class PhoneWatchLinkStore(storage: KeyValueStorage, pairKeys: PairKeyVault) { fun observe(): Flow<Map<String, PhoneWatchLink>>; suspend fun get(nodeId: String): PhoneWatchLink?; suspend fun recordSuccess(target: LinkTarget, plan: LinkPlan, pairKey: ByteArray, at: Long, seenWatchDocAt: Long): PhoneWatchLink; suspend fun recordFailure(target: LinkTarget, plan: LinkPlan, failure: PhoneLinkFailure, at: Long, seenWatchDocAt: Long): PhoneWatchLink; suspend fun markConfirmed(nodeId: String, at: Long, seenWatchDocAt: Long) }`
  - `data class LinkPlan(val mode: LinkMode, val accountId: String? = null)`, `sealed interface LinkBundleResult { data class Ready(val bundle: LinkBundle); data class Unavailable(val failure: PhoneLinkFailure) }`, `interface LinkBundleSource { fun changes(): Flow<Unit>; suspend fun plan(watchFeatures: Set<String>): LinkPlan; suspend fun bundle(plan: LinkPlan): LinkBundleResult }`, `object PairOnlyBundleSource : LinkBundleSource`
  - `sealed interface PhoneLinkDecision { Wait; Exchange; Confirm; companion object { const val RELINK_GRACE_MS: Long; fun decide(record: PhoneWatchLink?, plan: LinkPlan, document: StateDocument?, now: Long): PhoneLinkDecision } }`
  - `class PhoneLinkInitiator(v2Enabled: Flow<Boolean>, watchCapable: Flow<Boolean>, watchDocuments: Flow<Map<String, StateDocument>>, targets: LinkTargets, requester: LinkRequester, exchange: PhoneLinkExchange, store: PhoneWatchLinkStore, notifier: WatchLinkNotifier, localNodeId: suspend () -> String?, bundles: LinkBundleSource = PairOnlyBundleSource, clock: Clock = Clock.System) { fun start(scope: CoroutineScope): Job; suspend fun reconcileNow(); suspend fun reconcile(documents: Map<String, StateDocument>) }`
  - `interface WatchLinkNotifier { fun linked(target: LinkTarget, mode: LinkMode); fun needsAttention(target: LinkTarget, failure: PhoneLinkFailure) }`, `class AndroidWatchLinkNotifier(context: Context) : WatchLinkNotifier`
  - `internal suspend fun NodeClient.localNodeIdOrNull(): String?` (phone), `val phoneWatchSyncV2Module: Module`

**Failure modes and how they surface:**
- The watch is out of reach, does not answer within 10 s, or has no listener for the request (an older watch build): `WATCH_UNREACHABLE`, retried after 1, 2, 4 … minutes, capped at 6 hours; logged with the watch's name.
- The watch refuses: `OFFER_EXPIRED` (expired or replayed), `WATCH_CANNOT_LINK` (its app is off or too old), `WATCH_BOUND_TO_ANOTHER_ACCOUNT` (notification once: "<watch> is set up for another LogDate account"). An unreadable answer is `ANSWER_UNREADABLE`; a confirmation that does not verify is `CONFIRMATION_MISMATCH` and the pair key is not stored.
- The phone cannot read its own node: `PHONE_NODE_UNKNOWN`. Listing the watches fails: logged; the next state change tries again.
- Every record is readable through `PhoneWatchLinkStore.observe()` for the Watch page (C3b, C4).

- [ ] **Step 1: Write the failing tests**

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/PeerStateStoreTest.kt`, add:

```kotlin
    @Test
    fun `documents lists the latest document of each node of a role`() =
        runTest {
            val watch = StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = WatchState())
            val phone = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState())
            store.accept("watch-node", Decoded.Supported(watch), PeerRole.WATCH)
            store.accept("phone-node", Decoded.Supported(phone), PeerRole.PHONE)

            assertEquals(mapOf("watch-node" to watch), store.documents(PeerRole.WATCH).first())
        }
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/MemoryKeyValueStorage.kt` with exactly the content of Task 1's `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/MemoryKeyValueStorage.kt`, except that the first line is `package app.logdate.client.sync.v2`:

```kotlin
package app.logdate.client.sync.v2

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.peer.link.PairKeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Strings in memory, observable, for the phone's version 2 tests; other value types read back their defaults. */
class MemoryKeyValueStorage : KeyValueStorage {
    private val strings = MutableStateFlow<Map<String, String>>(emptyMap())

    val values: Map<String, String> get() = strings.value

    override suspend fun getString(key: String): String? = strings.value[key]

    override fun getStringSync(key: String): String? = strings.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        strings.update { it + (key to value) }
    }

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) = Unit

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = Unit

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = Unit

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = Unit

    override suspend fun remove(key: String) {
        strings.update { it - key }
    }

    override suspend fun contains(key: String): Boolean = key in strings.value

    override suspend fun clear() {
        strings.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = strings.map { it[key] }.distinctUntilChanged()

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = flowOf(defaultValue)

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = flowOf(defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = flowOf(defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = flowOf(defaultValue)
}

/** Pair keys in memory. */
class MemoryPairKeyVault : PairKeyVault {
    val keys = mutableMapOf<String, ByteArray>()

    override suspend fun put(
        peer: String,
        key: ByteArray,
    ) {
        keys[peer] = key
    }

    override suspend fun get(peer: String): ByteArray? = keys[peer]

    override suspend fun remove(peer: String) {
        keys.remove(peer)
    }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecisionTest.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [PhoneLinkDecision], which says per watch whether the phone links it now, waits, or confirms. */
class PhoneLinkDecisionTest {
    private val pair = LinkPlan(LinkMode.PAIR)
    private val linked =
        PhoneWatchLink(
            nodeId = "w",
            displayName = "Watch",
            mode = LinkMode.PAIR,
            linkedAt = 100,
            confirmedAt = 100,
            lastAttemptAt = 100,
            seenWatchDocAt = 100,
        )

    @Test
    fun `a watch with no record is linked now`() {
        assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(null, pair, null, now = 1_000))
    }

    @Test
    fun `a linked watch that agrees is left alone`() {
        assertEquals(PhoneLinkDecision.Wait, PhoneLinkDecision.decide(linked, pair, document(PeerLinkState.PAIRED, at = 200), now = 1_000))
    }

    @Test
    fun `a watch that reports losing its link after the grace is linked again`() {
        val now = 100 + PhoneLinkDecision.RELINK_GRACE_MS
        assertEquals(
            PhoneLinkDecision.Exchange,
            PhoneLinkDecision.decide(linked, pair, document(PeerLinkState.NEEDS_RELINK, at = now), now),
        )
    }

    @Test
    fun `a report that arrives within the grace waits`() {
        assertEquals(PhoneLinkDecision.Wait, PhoneLinkDecision.decide(linked, pair, document(PeerLinkState.UNLINKED, at = 150), now = 200))
    }

    @Test
    fun `a failed watch waits until its next attempt`() {
        val failed = linked.copy(failure = PhoneLinkFailure.WATCH_UNREACHABLE, nextAttemptAt = 5_000)

        assertEquals(PhoneLinkDecision.Wait, PhoneLinkDecision.decide(failed, pair, null, now = 4_999))
        assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(failed, pair, null, now = 5_000))
    }

    @Test
    fun `a new plan links the watch again`() {
        assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(linked, LinkPlan(LinkMode.CLOUD, "account"), null, now = 1_000))
    }

    @Test
    fun `an unconfirmed link is confirmed once the watch reports it`() {
        val unconfirmed = linked.copy(mode = LinkMode.CLOUD, accountId = "account", confirmedAt = null)

        assertEquals(
            PhoneLinkDecision.Confirm,
            PhoneLinkDecision.decide(
                unconfirmed,
                LinkPlan(LinkMode.CLOUD, "account"),
                document(PeerLinkState.LINKED, at = 300),
                now = 1_000,
            ),
        )
    }

    private fun document(
        link: PeerLinkState,
        at: Long,
    ) = StateDocument(role = PeerRole.WATCH, writtenAt = at, watch = WatchState(link = link))
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiatorTest.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.device.crypto.link.LinkHandshake
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.client.sync.peer.link.valueOrNull
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.WatchState
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.MemoryPairKeyVault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [PhoneLinkInitiator] against the watch's half of the real exchange: a watch is linked once,
 * every failure is recorded with its reason and waits, and only a working link is confirmed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLinkInitiatorTest {
    private val clock = MutableClock(1_000_000)
    private val handshake = BouncyCastleLinkHandshake()
    private val watch = FakeWatch(handshake, clock)
    private val pairKeys = MemoryPairKeyVault()
    private val store = PhoneWatchLinkStore(MemoryKeyValueStorage(), pairKeys)
    private val notifier = RecordingNotifier()

    private fun initiator(enabled: Boolean = true) =
        PhoneLinkInitiator(
            v2Enabled = MutableStateFlow(enabled),
            watchCapable = MutableStateFlow(true),
            watchDocuments = MutableStateFlow(emptyMap()),
            targets = { listOf(LinkTarget(WATCH, "Pixel Watch 3")) },
            requester = watch,
            exchange = PhoneLinkExchange(handshake, clock),
            store = store,
            notifier = notifier,
            localNodeId = { PHONE },
            clock = clock,
        )

    @Test
    fun `a watch that was never linked is paired once and the phone shows one confirmation`() =
        runTest {
            val initiator = initiator()

            initiator.reconcile(emptyMap())
            initiator.reconcile(emptyMap())

            assertEquals(listOf(WearPaths.LINK_OFFER, WearPaths.LINK_BUNDLE), watch.requests)
            assertEquals(listOf("linked Pixel Watch 3 PAIR"), notifier.events)
            val record = assertNotNull(store.get(WATCH))
            assertNull(record.failure)
            assertNotNull(record.confirmedAt)
            assertContentEquals(assertNotNull(watch.pairKey), pairKeys.keys[WATCH])
        }

    @Test
    fun `a refused offer is recorded with its reason and tried again only after the wait`() =
        runTest {
            watch.refuseOffersWith = LinkRefusal.UNSUPPORTED
            val initiator = initiator()

            initiator.reconcile(emptyMap())
            initiator.reconcile(emptyMap())

            assertEquals(PhoneLinkFailure.WATCH_CANNOT_LINK, store.get(WATCH)?.failure)
            assertEquals(1, watch.requests.size)
            clock.millis += 60_000
            initiator.reconcile(emptyMap())
            assertEquals(2, watch.requests.size)
        }

    @Test
    fun `a watch set up for another account is recorded and shown once`() =
        runTest {
            watch.refuseOffersWith = LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT
            val initiator = initiator()

            initiator.reconcile(emptyMap())
            clock.millis += 60_000
            initiator.reconcile(emptyMap())

            assertEquals(PhoneLinkFailure.WATCH_BOUND_TO_ANOTHER_ACCOUNT, store.get(WATCH)?.failure)
            assertEquals(listOf("attention Pixel Watch 3 WATCH_BOUND_TO_ANOTHER_ACCOUNT"), notifier.events)
        }

    @Test
    fun `an unreachable watch is recorded as unreachable`() =
        runTest {
            watch.reachable = false

            initiator().reconcile(emptyMap())

            assertEquals(PhoneLinkFailure.WATCH_UNREACHABLE, store.get(WATCH)?.failure)
            assertTrue(notifier.events.isEmpty())
        }

    @Test
    fun `a confirmation that does not verify is not counted as linked`() =
        runTest {
            watch.corruptConfirmation = true

            initiator().reconcile(emptyMap())

            assertEquals(PhoneLinkFailure.CONFIRMATION_MISMATCH, store.get(WATCH)?.failure)
            assertNull(pairKeys.keys[WATCH])
            assertTrue(notifier.events.isEmpty())
        }

    @Test
    fun `a watch that reports losing its link is paired again`() =
        runTest {
            val initiator = initiator()
            initiator.reconcile(emptyMap())
            clock.millis += PhoneLinkDecision.RELINK_GRACE_MS

            initiator.reconcile(mapOf(WATCH to unlinkedDocument(at = clock.millis)))

            assertEquals(4, watch.requests.size)
        }

    @Test
    fun `a report written right after the link does not start another exchange`() =
        runTest {
            val initiator = initiator()
            initiator.reconcile(emptyMap())

            initiator.reconcile(mapOf(WATCH to unlinkedDocument(at = clock.millis + 1)))

            assertEquals(2, watch.requests.size)
        }

    @Test
    fun `with the switch off the phone sends nothing`() =
        runTest {
            initiator(enabled = false).start(backgroundScope)
            runCurrent()

            assertTrue(watch.requests.isEmpty())
        }

    private fun unlinkedDocument(at: Long) =
        StateDocument(role = PeerRole.WATCH, writtenAt = at, watch = WatchState(link = PeerLinkState.UNLINKED))

    /** The watch's side of the exchange, answering the way the watch app does. */
    private class FakeWatch(
        handshake: LinkHandshake,
        clock: Clock,
    ) : LinkRequester {
        private val exchange = WatchLinkExchange(handshake, LinkNonceLedger(clock), clock)
        private var accepted: WatchLinkExchange.Accepted? = null
        val requests = mutableListOf<String>()
        var reachable = true
        var refuseOffersWith: LinkRefusal? = null
        var corruptConfirmation = false
        var pairKey: ByteArray? = null

        override suspend fun request(
            nodeId: String,
            path: String,
            payload: ByteArray,
        ): Result<ByteArray> {
            requests += path
            if (!reachable) return Result.failure(IOException("The watch is out of reach"))
            return Result.success(
                when (path) {
                    WearPaths.LINK_OFFER -> answerOffer(nodeId, payload)
                    WearPaths.LINK_BUNDLE -> answerBundle(payload)
                    else -> error("Unexpected request $path")
                },
            )
        }

        private suspend fun answerOffer(
            nodeId: String,
            payload: ByteArray,
        ): ByteArray {
            refuseOffersWith?.let { return LinkWire.refusal(it) }
            val offer = (WearJson.decode(payload, WearProtocol.LINK_SCHEMA, LinkOffer.serializer()) as Decoded.Supported).value
            val step = checkNotNull(exchange.accept(offer, watchNode = nodeId, phoneNode = offer.phoneNode).valueOrNull())
            accepted = step
            return LinkWire.write(LinkAccept.serializer(), step.reply)
        }

        private fun answerBundle(payload: ByteArray): ByteArray {
            val sealed = (WearJson.decode(payload, WearProtocol.LINK_SCHEMA, SealedLinkBundle.serializer()) as Decoded.Supported).value
            val opened = checkNotNull(exchange.open(checkNotNull(accepted), sealed).valueOrNull())
            pairKey = opened.pairKey
            val confirm = if (corruptConfirmation) LinkConfirm(mac = LinkWire.encodeKey(ByteArray(32))) else opened.confirm
            return LinkWire.write(LinkConfirm.serializer(), confirm)
        }
    }

    private class RecordingNotifier : WatchLinkNotifier {
        val events = mutableListOf<String>()

        override fun linked(
            target: LinkTarget,
            mode: LinkMode,
        ) {
            events += "linked ${target.displayName} $mode"
        }

        override fun needsAttention(
            target: LinkTarget,
            failure: PhoneLinkFailure,
        ) {
            events += "attention ${target.displayName} $failure"
        }
    }

    private class MutableClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val PHONE = "phone-node"
        const val WATCH = "watch-node"
    }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/MessageClientLinkRequesterTest.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

/** Tests [MessageClientLinkRequester]: an answer, a failure and a watch that never answers. */
class MessageClientLinkRequesterTest {
    private val client = mockk<MessageClient>()

    @Test
    fun `the watch's answer is returned`() =
        runTest {
            every { client.sendRequest("watch", "/logdate/link/offer", any()) } returns Tasks.forResult("ok".encodeToByteArray())

            val answer = MessageClientLinkRequester(client).request("watch", "/logdate/link/offer", ByteArray(0))

            assertContentEquals("ok".encodeToByteArray(), answer.getOrThrow())
        }

    @Test
    fun `a failed request is a failure with its cause`() =
        runTest {
            every { client.sendRequest(any(), any(), any()) } returns Tasks.forException(IllegalStateException("no listener"))

            val answer = MessageClientLinkRequester(client).request("watch", "/logdate/link/offer", ByteArray(0))

            assertIs<IllegalStateException>(answer.exceptionOrNull())
        }

    @Test
    fun `a watch that never answers times out`() =
        runTest {
            every { client.sendRequest(any(), any(), any()) } returns TaskCompletionSource<ByteArray>().task

            val answer = MessageClientLinkRequester(client, timeoutMs = 10).request("watch", "/logdate/link/offer", ByteArray(0))

            assertIs<TimeoutException>(answer.exceptionOrNull())
        }
}
```

In `app/android-main/build.gradle.kts`, after `testImplementation(projects.client.sync)` add:

```kotlin
    testImplementation(projects.client.device)
    testImplementation(projects.client.logdateDatastore)
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.state.PeerStateStoreTest' :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.link.*' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'documents'", "Unresolved reference 'PhoneLinkInitiator'", "Unresolved reference 'PhoneLinkDecision'", "Unresolved reference 'MessageClientLinkRequester'".

- [ ] **Step 3: Implement**

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerStateStore.kt`, after `latest(role)` add:

```kotlin
    /** The latest readable document of each node that writes as [role]. */
    fun documents(role: PeerRole): Flow<Map<String, StateDocument>> =
        documents
            .map { all -> all.filterValues { it.role == role } }
            .distinctUntilChanged()
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/LocalNode.kt`:

```kotlin
package app.logdate.client.sync.v2

import com.google.android.gms.wearable.NodeClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** This phone's Data Layer node, or null, with the failure logged, when Play services cannot say. */
internal suspend fun NodeClient.localNodeIdOrNull(): String? =
    try {
        localNode.await().id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Napier.e("Could not read this phone's Data Layer node; watches are linked once it can", e)
        null
    }
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkRequester.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

/** Sends one request to a watch and returns its answer, or why there is none. */
fun interface LinkRequester {
    suspend fun request(
        nodeId: String,
        path: String,
        payload: ByteArray,
    ): Result<ByteArray>
}

/** [LinkRequester] over `MessageClient.sendRequest`, giving up after [timeoutMs]. */
class MessageClientLinkRequester(
    private val messageClient: MessageClient,
    private val timeoutMs: Long = REQUEST_TIMEOUT_MS,
) : LinkRequester {
    override suspend fun request(
        nodeId: String,
        path: String,
        payload: ByteArray,
    ): Result<ByteArray> =
        try {
            val answer = withTimeoutOrNull(timeoutMs) { messageClient.sendRequest(nodeId, path, payload).await() }
            if (answer == null) {
                Result.failure(TimeoutException("The watch did not answer $path within $timeoutMs ms"))
            } else {
                Result.success(answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkTargets.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.WearProtocol
import com.google.android.gms.wearable.CapabilityClient
import kotlinx.coroutines.tasks.await

/** A watch the phone can link. */
data class LinkTarget(
    val nodeId: String,
    val displayName: String,
)

fun interface LinkTargets {
    suspend fun reachable(): List<LinkTarget>
}

/** The reachable watches whose redesigned app is on: they advertise [WearProtocol.WATCH_CAPABILITY_V2]. */
class CapabilityLinkTargets(
    private val capabilityClient: CapabilityClient,
) : LinkTargets {
    override suspend fun reachable(): List<LinkTarget> =
        capabilityClient
            .getCapability(WearProtocol.WATCH_CAPABILITY_V2, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .map { node -> LinkTarget(node.id, node.displayName) }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkBundleSource.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** How the phone links a watch: pair only, or to [accountId]. */
data class LinkPlan(
    val mode: LinkMode,
    val accountId: String? = null,
)

sealed interface LinkBundleResult {
    data class Ready(
        val bundle: LinkBundle,
    ) : LinkBundleResult

    data class Unavailable(
        val failure: PhoneLinkFailure,
    ) : LinkBundleResult
}

/** Decides the plan for a watch and builds the bundle for it. */
interface LinkBundleSource {
    /** Emits whenever something that changes [plan] changed, such as signing in. */
    fun changes(): Flow<Unit>

    suspend fun plan(watchFeatures: Set<String>): LinkPlan

    suspend fun bundle(plan: LinkPlan): LinkBundleResult
}

/** Pair-only linking: what a phone with no account offers. */
object PairOnlyBundleSource : LinkBundleSource {
    override fun changes(): Flow<Unit> = flowOf(Unit)

    override suspend fun plan(watchFeatures: Set<String>): LinkPlan = LinkPlan(LinkMode.PAIR)

    override suspend fun bundle(plan: LinkPlan): LinkBundleResult = LinkBundleResult.Ready(LinkBundle(mode = LinkMode.PAIR))
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneWatchLinkStore.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.link.PairKeyVault
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Why the phone's last attempt to link a watch failed. [needsAttention] marks the ones only the person can fix. */
@Serializable
enum class PhoneLinkFailure(
    val needsAttention: Boolean,
) {
    PHONE_NODE_UNKNOWN(false),
    WATCH_UNREACHABLE(false),
    WATCH_CANNOT_LINK(false),
    OFFER_EXPIRED(false),
    ANSWER_UNREADABLE(false),
    CONFIRMATION_MISMATCH(false),
    WATCH_BOUND_TO_ANOTHER_ACCOUNT(true),
    BUNDLE_UNAVAILABLE(false),
}

/**
 * What the phone knows about linking one watch.
 *
 * @property seenWatchDocAt The `writtenAt` of the watch's state document the phone last acted on.
 */
@Serializable
data class PhoneWatchLink(
    val nodeId: String,
    val displayName: String,
    val mode: LinkMode,
    val accountId: String? = null,
    val linkedAt: Long? = null,
    val confirmedAt: Long? = null,
    val failure: PhoneLinkFailure? = null,
    val attempts: Int = 0,
    val lastAttemptAt: Long = 0,
    val nextAttemptAt: Long = 0,
    val seenWatchDocAt: Long = 0,
)

/** One record per watch, as one JSON value, and each watch's pair key in secure storage. */
class PhoneWatchLinkStore(
    private val storage: KeyValueStorage,
    private val pairKeys: PairKeyVault,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), PhoneWatchLink.serializer())
    private val mutex = Mutex()

    fun observe(): Flow<Map<String, PhoneWatchLink>> = storage.observeString(KEY).map(::decode).distinctUntilChanged()

    suspend fun get(nodeId: String): PhoneWatchLink? = decode(storage.getString(KEY))[nodeId]

    suspend fun recordSuccess(
        target: LinkTarget,
        plan: LinkPlan,
        pairKey: ByteArray,
        at: Long,
        seenWatchDocAt: Long,
    ): PhoneWatchLink {
        pairKeys.put(target.nodeId, pairKey)
        return update(target.nodeId) { previous ->
            PhoneWatchLink(
                nodeId = target.nodeId,
                displayName = target.displayName,
                mode = plan.mode,
                accountId = plan.accountId,
                linkedAt = at,
                confirmedAt = previous?.confirmedAt?.takeIf { previous.mode == plan.mode && previous.accountId == plan.accountId },
                lastAttemptAt = at,
                seenWatchDocAt = maxOf(seenWatchDocAt, at),
            )
        }
    }

    suspend fun recordFailure(
        target: LinkTarget,
        plan: LinkPlan,
        failure: PhoneLinkFailure,
        at: Long,
        seenWatchDocAt: Long,
    ): PhoneWatchLink =
        update(target.nodeId) { previous ->
            val attempts = if (previous?.failure == failure) previous.attempts + 1 else 1
            PhoneWatchLink(
                nodeId = target.nodeId,
                displayName = target.displayName,
                mode = plan.mode,
                accountId = plan.accountId,
                linkedAt = previous?.linkedAt,
                confirmedAt = previous?.confirmedAt,
                failure = failure,
                attempts = attempts,
                lastAttemptAt = at,
                nextAttemptAt = at + backoffMs(attempts),
                seenWatchDocAt = maxOf(seenWatchDocAt, previous?.seenWatchDocAt ?: 0),
            )
        }

    suspend fun markConfirmed(
        nodeId: String,
        at: Long,
        seenWatchDocAt: Long,
    ) {
        update(nodeId) { previous ->
            checkNotNull(previous) { "Only a recorded link can be confirmed" }.copy(confirmedAt = at, seenWatchDocAt = seenWatchDocAt)
        }
    }

    private suspend fun update(
        nodeId: String,
        change: (PhoneWatchLink?) -> PhoneWatchLink,
    ): PhoneWatchLink =
        mutex.withLock {
            val all = decode(storage.getString(KEY))
            val changed = change(all[nodeId])
            storage.putString(KEY, json.encodeToString(serializer, all + (nodeId to changed)))
            changed
        }

    private fun decode(text: String?): Map<String, PhoneWatchLink> {
        if (text == null) return emptyMap()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            Napier.e("The phone's watch link records could not be read; each watch is linked again", e)
            emptyMap()
        }
    }

    private fun backoffMs(attempts: Int): Long = minOf(FIRST_WAIT_MS shl (attempts - 1).coerceIn(0, MAX_DOUBLINGS), MAX_WAIT_MS)

    private companion object {
        const val KEY = "phone.watch_links.v1"
        const val FIRST_WAIT_MS = 60_000L
        const val MAX_DOUBLINGS = 9
        const val MAX_WAIT_MS = 6L * 60 * 60 * 1000
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecision.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.StateDocument

/** What the phone does next about one watch. */
sealed interface PhoneLinkDecision {
    data object Wait : PhoneLinkDecision

    data object Exchange : PhoneLinkDecision

    /** The watch reports the link the phone made; the phone shows its one confirmation. */
    data object Confirm : PhoneLinkDecision

    companion object {
        /** A report of no link written this soon after an exchange is the watch's state from before it. */
        const val RELINK_GRACE_MS = 2 * 60_000L

        private val RELINK_STATES = setOf(PeerLinkState.UNLINKED, PeerLinkState.NEEDS_RELINK)

        fun decide(
            record: PhoneWatchLink?,
            plan: LinkPlan,
            document: StateDocument?,
            now: Long,
        ): PhoneLinkDecision {
            if (record == null) return Exchange
            val watch = document?.watch
            val fresh = document != null && document.writtenAt > record.seenWatchDocAt
            val watchAsks = fresh && watch != null && watch.link in RELINK_STATES && now - record.lastAttemptAt >= RELINK_GRACE_MS
            if (watchAsks) return Exchange
            if (record.failure != null) return if (now >= record.nextAttemptAt) Exchange else Wait
            if (record.mode != plan.mode || record.accountId != plan.accountId) return Exchange
            if (fresh && record.confirmedAt == null && watch?.link == linkedState(record.mode)) return Confirm
            return Wait
        }

        private fun linkedState(mode: LinkMode): PeerLinkState =
            when (mode) {
                LinkMode.PAIR -> PeerLinkState.PAIRED
                LinkMode.CLOUD -> PeerLinkState.LINKED
            }
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/WatchLinkNotifier.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.sync.datalayer.v2.LinkMode
import io.github.aakira.napier.Napier
import app.logdate.client.notifications.R as NotificationResources

/** What the person sees about linking: one confirmation once it works, and the failures only they can fix. */
interface WatchLinkNotifier {
    fun linked(
        target: LinkTarget,
        mode: LinkMode,
    )

    fun needsAttention(
        target: LinkTarget,
        failure: PhoneLinkFailure,
    )
}

/** Posts on the Watch sync channel, local only, one notification per watch; tapping it opens LogDate. */
class AndroidWatchLinkNotifier(
    private val context: Context,
) : WatchLinkNotifier {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun linked(
        target: LinkTarget,
        mode: LinkMode,
    ) {
        val body =
            when (mode) {
                LinkMode.PAIR -> NotificationResources.string.notification_watch_paired_body
                LinkMode.CLOUD -> NotificationResources.string.notification_watch_linked_body
            }
        post(
            target,
            context.getString(NotificationResources.string.notification_watch_paired_title, target.displayName),
            context.getString(body),
        )
    }

    override fun needsAttention(
        target: LinkTarget,
        failure: PhoneLinkFailure,
    ) {
        val copy = attentionCopy(failure)
        if (copy == null) {
            Napier.e("No notification describes $failure; it shows on the Watch page only")
            return
        }
        post(target, context.getString(copy.first, target.displayName), context.getString(copy.second))
    }

    private fun attentionCopy(failure: PhoneLinkFailure): Pair<Int, Int>? =
        when (failure) {
            PhoneLinkFailure.WATCH_BOUND_TO_ANOTHER_ACCOUNT ->
                NotificationResources.string.notification_watch_other_account_title to
                    NotificationResources.string.notification_watch_other_account_body
            else -> null
        }

    private fun post(
        target: LinkTarget,
        title: String,
        body: String,
    ) {
        val open =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                PendingIntent.getActivity(
                    context,
                    notificationId(target),
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        val notification =
            NotificationCompat
                .Builder(context, LogDateNotificationChannelKey.WATCH_SYNC.id)
                .setSmallIcon(NotificationResources.drawable.ic_notification_watch)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setLocalOnly(true)
                .apply { if (open != null) setContentIntent(open) }
                .build()
        notificationManager.notify(notificationId(target), notification)
    }

    companion object {
        private const val LINK_NOTIFICATION_BASE = 4_100_000

        /** One notification per watch, separate from the watch note notifications. */
        fun notificationId(target: LinkTarget): Int = LINK_NOTIFICATION_BASE + (target.nodeId.hashCode() and 0xFFFF)
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiator.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.link.LinkAnswer
import app.logdate.client.sync.peer.link.LinkExchangeFailure
import app.logdate.client.sync.peer.link.LinkStep
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.state.StateDocument
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Links this phone's watches. While the switch is on and a watch advertises version 2, it decides per
 * watch with [PhoneLinkDecision], runs the offer and bundle requests, and records the outcome.
 *
 * Exchanges run one at a time. A pairing is confirmed to the person once; an account link once the
 * watch reports it installed (Task 5).
 */
class PhoneLinkInitiator(
    private val v2Enabled: Flow<Boolean>,
    private val watchCapable: Flow<Boolean>,
    private val watchDocuments: Flow<Map<String, StateDocument>>,
    private val targets: LinkTargets,
    private val requester: LinkRequester,
    private val exchange: PhoneLinkExchange,
    private val store: PhoneWatchLinkStore,
    private val notifier: WatchLinkNotifier,
    private val localNodeId: suspend () -> String?,
    private val bundles: LinkBundleSource = PairOnlyBundleSource,
    private val clock: Clock = Clock.System,
) {
    private data class Trigger(
        val active: Boolean,
        val documents: Map<String, StateDocument>,
    )

    private sealed interface ExchangeOutcome {
        class Linked(
            val pairKey: ByteArray,
        ) : ExchangeOutcome

        data class Failed(
            val failure: PhoneLinkFailure,
        ) : ExchangeOutcome
    }

    private val mutex = Mutex()
    private val latest = MutableStateFlow(Trigger(active = false, documents = emptyMap()))

    fun start(scope: CoroutineScope): Job =
        scope.launch {
            combine(v2Enabled, watchCapable, watchDocuments, bundles.changes()) { enabled, capable, documents, _ ->
                Trigger(active = enabled && capable, documents = documents)
            }.conflate()
                .collect { trigger ->
                    latest.value = trigger
                    if (trigger.active) reconcile(trigger.documents)
                }
        }

    /** Decides again for the latest state, such as when a watch's capability arrives. */
    suspend fun reconcileNow() {
        val trigger = latest.value
        if (trigger.active) reconcile(trigger.documents)
    }

    suspend fun reconcile(documents: Map<String, StateDocument>) {
        mutex.withLock {
            val reachable = reachableTargets() ?: return@withLock
            reachable.forEach { target -> reconcileTarget(target, documents[target.nodeId]) }
        }
    }

    private suspend fun reachableTargets(): List<LinkTarget>? =
        try {
            targets.reachable()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not list the watches to link; linking is tried again on the next change", e)
            null
        }

    private suspend fun reconcileTarget(
        target: LinkTarget,
        document: StateDocument?,
    ) {
        val now = clock.now().toEpochMilliseconds()
        val record = store.get(target.nodeId)
        val plan = bundles.plan(document?.watch?.features.orEmpty())
        when (PhoneLinkDecision.decide(record, plan, document, now)) {
            PhoneLinkDecision.Wait -> Unit
            PhoneLinkDecision.Exchange -> runExchange(target, plan, document, now)
            PhoneLinkDecision.Confirm -> confirm(target, checkNotNull(record).mode, checkNotNull(document), now)
        }
    }

    private suspend fun runExchange(
        target: LinkTarget,
        plan: LinkPlan,
        document: StateDocument?,
        now: Long,
    ) {
        val seen = document?.writtenAt ?: 0L
        when (val outcome = exchangeWith(target, plan)) {
            is ExchangeOutcome.Linked -> {
                store.recordSuccess(target, plan, outcome.pairKey, at = now, seenWatchDocAt = seen)
                Napier.i("Linked ${target.displayName} in ${plan.mode} mode")
                if (plan.mode == LinkMode.PAIR) confirm(target, plan.mode, document, now)
            }
            is ExchangeOutcome.Failed -> {
                val record = store.recordFailure(target, plan, outcome.failure, at = now, seenWatchDocAt = seen)
                Napier.w("Linking ${target.displayName} failed with ${outcome.failure}; it is tried again after ${record.nextAttemptAt}")
                if (outcome.failure.needsAttention && record.attempts == 1) notifier.needsAttention(target, outcome.failure)
            }
        }
    }

    private suspend fun confirm(
        target: LinkTarget,
        mode: LinkMode,
        document: StateDocument?,
        now: Long,
    ) {
        notifier.linked(target, mode)
        store.markConfirmed(target.nodeId, at = now, seenWatchDocAt = maxOf(document?.writtenAt ?: 0L, now))
    }

    private suspend fun exchangeWith(
        target: LinkTarget,
        plan: LinkPlan,
    ): ExchangeOutcome {
        val phoneNode = localNodeId() ?: return ExchangeOutcome.Failed(PhoneLinkFailure.PHONE_NODE_UNKNOWN)
        val started = exchange.start(plan.mode, phoneNode)
        val offerAnswer =
            requester
                .request(target.nodeId, WearPaths.LINK_OFFER, LinkWire.write(LinkOffer.serializer(), started.offer))
                .getOrElse { return unreachable(target, it) }
        val accept =
            when (val answer = LinkWire.read(offerAnswer, LinkAccept.serializer())) {
                is LinkAnswer.Answered -> answer.value
                is LinkAnswer.Refused -> return ExchangeOutcome.Failed(answer.reason.toFailure())
                is LinkAnswer.Unreadable -> return ExchangeOutcome.Failed(PhoneLinkFailure.ANSWER_UNREADABLE)
            }
        val bundle =
            when (val result = bundles.bundle(plan)) {
                is LinkBundleResult.Ready -> result.bundle
                is LinkBundleResult.Unavailable -> return ExchangeOutcome.Failed(result.failure)
            }
        val sealed =
            when (val step = exchange.seal(started, accept, target.nodeId, bundle)) {
                is LinkStep.Ok -> step.value
                is LinkStep.Failed -> return ExchangeOutcome.Failed(step.failure.toFailure())
            }
        return finish(target, sealed)
    }

    private suspend fun finish(
        target: LinkTarget,
        sealed: PhoneLinkExchange.Sealed,
    ): ExchangeOutcome {
        val bundleAnswer =
            requester
                .request(target.nodeId, WearPaths.LINK_BUNDLE, LinkWire.write(SealedLinkBundle.serializer(), sealed.bundle))
                .getOrElse { return unreachable(target, it) }
        val confirm =
            when (val answer = LinkWire.read(bundleAnswer, LinkConfirm.serializer())) {
                is LinkAnswer.Answered -> answer.value
                is LinkAnswer.Refused -> return ExchangeOutcome.Failed(answer.reason.toFailure())
                is LinkAnswer.Unreadable -> return ExchangeOutcome.Failed(PhoneLinkFailure.ANSWER_UNREADABLE)
            }
        if (!exchange.verify(sealed, confirm)) return ExchangeOutcome.Failed(PhoneLinkFailure.CONFIRMATION_MISMATCH)
        return ExchangeOutcome.Linked(sealed.pairKey)
    }

    private fun unreachable(
        target: LinkTarget,
        error: Throwable,
    ): ExchangeOutcome {
        Napier.w("${target.displayName} did not answer a link request", error)
        return ExchangeOutcome.Failed(PhoneLinkFailure.WATCH_UNREACHABLE)
    }

    private fun LinkRefusal.toFailure(): PhoneLinkFailure =
        when (this) {
            LinkRefusal.EXPIRED_OFFER, LinkRefusal.REPLAYED_NONCE -> PhoneLinkFailure.OFFER_EXPIRED
            LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT -> PhoneLinkFailure.WATCH_BOUND_TO_ANOTHER_ACCOUNT
            LinkRefusal.UNSUPPORTED -> PhoneLinkFailure.WATCH_CANNOT_LINK
        }

    private fun LinkExchangeFailure.toFailure(): PhoneLinkFailure =
        when (this) {
            LinkExchangeFailure.EXPIRED_OFFER, LinkExchangeFailure.REPLAYED_NONCE -> PhoneLinkFailure.OFFER_EXPIRED
            else -> PhoneLinkFailure.ANSWER_UNREADABLE
        }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`:

```kotlin
package app.logdate.di

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.SecureStoragePairKeyVault
import app.logdate.client.sync.peer.state.PeerCapabilityPresence
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.v2.link.AndroidWatchLinkNotifier
import app.logdate.client.sync.v2.link.CapabilityLinkTargets
import app.logdate.client.sync.v2.link.LinkRequester
import app.logdate.client.sync.v2.link.LinkTargets
import app.logdate.client.sync.v2.link.MessageClientLinkRequester
import app.logdate.client.sync.v2.link.PhoneLinkInitiator
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import app.logdate.client.sync.v2.link.WatchLinkNotifier
import app.logdate.client.sync.v2.localNodeIdOrNull
import com.google.android.gms.wearable.Wearable
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin module for the phone's side of the version 2 watch protocol. */
val phoneWatchSyncV2Module =
    module {
        single<PairKeyVault> { SecureStoragePairKeyVault(get(), prefix = "phone.watch_link.pair_key.") }
        single { PhoneWatchLinkStore(storage = get(), pairKeys = get()) }
        single { PhoneLinkExchange(BouncyCastleLinkHandshake()) }
        single<LinkRequester> { MessageClientLinkRequester(Wearable.getMessageClient(androidContext())) }
        single<LinkTargets> { CapabilityLinkTargets(Wearable.getCapabilityClient(androidContext())) }
        single<WatchLinkNotifier> { AndroidWatchLinkNotifier(androidContext()) }
        single {
            val nodeClient = Wearable.getNodeClient(androidContext())
            PhoneLinkInitiator(
                v2Enabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                watchCapable = get<PeerCapabilityPresence>().observe(WearProtocol.WATCH_CAPABILITY_V2),
                watchDocuments = get<PeerStateStore>().documents(PeerRole.WATCH),
                targets = get(),
                requester = get(),
                exchange = get(),
                store = get(),
                notifier = get(),
                localNodeId = { nodeClient.localNodeIdOrNull() },
            )
        }
    }
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt`, after `includes(widgetModule)` add `includes(phoneWatchSyncV2Module)`.

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt`, call `startWatchLinking()` after `startPhoneStatePublishing()` in `onCreate`, add `import app.logdate.client.sync.v2.link.PhoneLinkInitiator`, and add:

```kotlin
    /** Links this phone's watches while the redesigned watch app is on; a failure here must not stop startup. */
    private fun startWatchLinking() {
        runCatching {
            get<PhoneLinkInitiator>().start(get())
        }.onFailure { error ->
            Napier.w("Failed to start linking this phone's watches", error)
        }
    }
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt`, after `onChannelOpened` add:

```kotlin
    /** A watch turning on its redesigned app is the moment to link it, even when LogDate was not running. */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != WearProtocol.WATCH_CAPABILITY_V2 || capabilityInfo.nodes.isEmpty()) return
        serviceScope.launch {
            val initiator = resolveKoin()?.get<PhoneLinkInitiator>()
            if (initiator == null) {
                Napier.e("Koin unavailable after $MAX_KOIN_RETRIES retries; the watch is linked when LogDate next starts")
                return@launch
            }
            initiator.reconcileNow()
        }
    }
```

with imports `app.logdate.client.sync.datalayer.v2.WearProtocol`, `app.logdate.client.sync.v2.link.PhoneLinkInitiator`, `com.google.android.gms.wearable.CapabilityInfo`.

In `app/android-main/src/main/AndroidManifest.xml`, after the listener's `CHANNEL_EVENT` `</intent-filter>` add:

```xml
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.CAPABILITY_CHANGED" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:path="/logdate_watch_sync_v2" />
            </intent-filter>
```

In `client/notifications/src/androidMain/res/values/strings.xml`, after `notification_watch_note_open_body` add:

```xml
    <string name="notification_watch_paired_title">LogDate is set up on %1$s</string>
    <string name="notification_watch_paired_body">Your watch and this phone now share your moments.</string>
    <string name="notification_watch_linked_body">Your watch now backs up to your LogDate account on its own.</string>
    <string name="notification_watch_other_account_title">%1$s is set up for another LogDate account</string>
    <string name="notification_watch_other_account_body">Erase LogDate on the watch to set it up with this phone.</string>
```

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again.
Expected: PASS (1 new `PeerStateStoreTest`, 7 `PhoneLinkDecisionTest`, 8 `PhoneLinkInitiatorTest`, 3 `MessageClientLinkRequesterTest`).

- [ ] **Step 5: Run every affected module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :app:android-main:testDebugUnitTest :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck :app:android-main:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL. Then:
```bash
grep -c "logdate_watch_sync_v2" app/android-main/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
```
Expected: `1`.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task02-phone-pairing.txt <<'EOF'
chore(wear): pair the phone with its watch without an account

With the redesigned watch app on, the phone now pairs each watch whose
app is on: it sends the offer, seals the bundle, checks the watch's
confirmation, and stores the pair key. A watch that loses its pairing,
or moves to another phone, is paired again when it reports it.

Each watch has a record of the last attempt and why it failed, with a
wait that grows from one minute to six hours. The phone shows one
notification when a watch is set up, and one when a watch is set up
for another account; every other failure is logged and shown on the
Watch page later. A watch's capability arriving wakes the phone.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/PeerStateStore.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/PeerStateStoreTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/LocalNode.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkRequester.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkTargets.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkBundleSource.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneWatchLinkStore.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecision.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/WatchLinkNotifier.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiator.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt app/android-main/src/main/AndroidManifest.xml client/notifications/src/androidMain/res/values/strings.xml app/android-main/build.gradle.kts app/android-main/src/test/kotlin/app/logdate/client/sync/v2/MemoryKeyValueStorage.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecisionTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiatorTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/MessageClientLinkRequesterTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task02-phone-pairing.txt
```

---

### Task 3: Install a linked watch's keys and session in steps (D2, core)

The part of the account link that both apps share and nothing wires yet: `LinkedDeviceInstaller` checks a `CLOUD` bundle, installs the identity and media keys, redeems the link code for the device's own session, moves what the device captured before the link to the account (`OwnerAdoptionMover`), adopts the account as the device's owner, and switches to the account's server. Each step is written to a journal first, so a process killed after the code is spent finishes on the next start; one killed before that asks the phone for a new link, because the code is never stored.

`OwnerAdoptionMover` composes the history move `HistoryOwnerAdoptionModule.kt:23` already does (`dao.adopt(old, new, origin, device) { it.rewriteHistoryOwner(new) }`) with `SyncMetadataDao.reassignPending` and `reassignCursors`, which can move between origins. `HistoryOwnerAdoptionModule` itself is unchanged, so signing in on the phone behaves as before.

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/LinkedDeviceInstaller.kt`
- Create: `client/data/src/commonMain/kotlin/app/logdate/client/data/account/OwnerAdoptionMover.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/link/LinkedDeviceInstallerTest.kt`
- Test: `client/data/src/desktopTest/kotlin/app/logdate/client/data/account/OwnerAdoptionMoverTest.kt`

**Interfaces:**
- Consumes: `IdentityKeyManager.installLinkedIdentityKey(key, expectedFingerprint)`, `bindToAccount`, `canUseForAccount`; `MediaPayloadKeyProvider.canInstallAccountKey/installAccountKey`; `CanonicalOwnerProvider.getCanonicalOwnerId/hasBoundOwner/adoptRemoteOwnerIfUninitialized`; `LogDateConfigRepository.getCurrentBackendUrl/updateBackendUrl/updateServerDescriptor`; `OriginSessionVault.write/clear`; `DeviceLinkApiException(status, code)`, `DeviceLinkErrorCodes`; `DeviceLinkRedeemResponse(accountId, sessionId, tokens: AccountTokens)`; `LinkBundle`, `LinkWire.decodeKey` (Task 1); `SyncMetadataDao.reassignPending/reassignCursors`; `HistoryOwnerAdoptionDao.adopt`; `HistoryRecordEntity.rewriteHistoryOwner` (internal to `:client:data`).
- Produces:
  - `enum class LinkProblem { INCOMPLETE_BUNDLE, LINK_CODE_EXPIRED, LINK_CODE_INVALID, DEVICE_LIMIT_REACHED, BOUND_TO_ANOTHER_ACCOUNT, KEY_CONFLICT, SERVER_REFUSED, NETWORK, INTERRUPTED, SESSION_REVOKED }`
  - `data class OwnerScope(val ownerId: String, val origin: String)`, `fun interface OwnerRowsMover { suspend fun move(from: OwnerScope, to: OwnerScope, deviceId: String): Int }`
  - `fun interface LinkCodeRedeemer { suspend fun redeem(origin: String, descriptor: ServerDescriptor?, code: String, deviceName: String): Result<DeviceLinkRedeemResponse> }`
  - `sealed interface InstallOutcome { data class Installed(val accountId: String, val origin: String, val leftBehind: Int); data class Failed(val problem: LinkProblem) }`
  - `interface LinkInstaller { suspend fun bindingProblem(bundle: LinkBundle): LinkProblem?; suspend fun install(bundle: LinkBundle): InstallOutcome; suspend fun resume(): InstallOutcome? }`
  - `class LinkedDeviceInstaller(journal: KeyValueStorage, identityKeys: IdentityKeyManager, mediaKeys: MediaPayloadKeyProvider, owner: CanonicalOwnerProvider, config: LogDateConfigRepository, sessions: OriginSessionVault, redeemer: LinkCodeRedeemer, mover: OwnerRowsMover, deviceId: suspend () -> String, deviceName: () -> String, clock: Clock = Clock.System) : LinkInstaller`
  - `class OwnerAdoptionMover(syncMetadataDao: SyncMetadataDao, historyOwnerAdoptionDao: HistoryOwnerAdoptionDao) : OwnerRowsMover`

**Failure modes and how they surface:**
- A bundle without an account, origin, code, keys or fingerprint: `INCOMPLETE_BUNDLE`. A code already past its expiry: `LINK_CODE_EXPIRED`. An owner or identity key bound to another account: `BOUND_TO_ANOTHER_ACCOUNT`. Keys that do not match their fingerprint or a key already installed: `KEY_CONFLICT`. These are checked before anything changes.
- Redeem answers `LINK_CODE_INVALID` (spent or unknown), `DEVICE_LIMIT_REACHED`, another server code (`SERVER_REFUSED`), or no answer (`NETWORK`): the journal is removed, the installed keys stay (they are the account's own), and the problem is returned for the watch to publish.
- The process stops before the session is saved: `resume()` returns `INTERRUPTED` and the phone links again. After: `resume()` finishes moving rows, adopting the owner and switching servers.
- Moving rows fails (a database error): logged, the journal stays, `INTERRUPTED` is returned, and the next start finishes it. Queued changes the account already queues for the same record stay with the old owner and are counted in `leftBehind` and logged.
- The journal cannot be written: an `IllegalStateException` the caller logs; nothing past it runs.

- [ ] **Step 1: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/link/LinkedDeviceInstallerTest.kt`:

```kotlin
package app.logdate.client.sync.peer.link

import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityMintPolicy
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [LinkedDeviceInstaller]: a phone's account bundle becomes this device's keys, session, owner
 * and server, every refusal is named, and a stop after the code is spent finishes on resume.
 */
class LinkedDeviceInstallerTest {
    private val crypto = TestCryptoManager()
    private val identity = IdentityKeyManager(InMemorySecureStorage(), crypto, mintPolicy = IdentityMintPolicy.NEVER)
    private val mediaKeys = MediaPayloadKeyProvider(InMemorySecureStorage(), crypto, identity, KeyDerivation(crypto))
    private val owner = FakeOwner(LOCAL_OWNER)
    private val config = DefaultLogDateConfigRepository(initialBackendUrl = DEFAULT_ORIGIN)
    private val vault = FakeVault()
    private val journal = InMemoryKeyValueStorage()
    private val moves = mutableListOf<Triple<OwnerScope, OwnerScope, String>>()
    private var moveFails = false
    private var redeemResult: Result<DeviceLinkRedeemResponse> =
        Result.success(DeviceLinkRedeemResponse(ACCOUNT, "session", AccountTokens("access", "refresh")))
    private var nowMs = 1_000L
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
        }
    private val identityKey = ByteArray(32) { 0x31 }
    private val mediaKey = ByteArray(32) { 0x42 }

    private val installer =
        LinkedDeviceInstaller(
            journal = journal,
            identityKeys = identity,
            mediaKeys = mediaKeys,
            owner = owner,
            config = config,
            sessions = vault,
            redeemer = { _, _, _, _ -> redeemResult },
            mover =
                OwnerRowsMover { from, to, device ->
                    check(!moveFails) { "The database is busy" }
                    moves += Triple(from, to, device)
                    0
                },
            deviceId = { "device" },
            deviceName = { "Pixel Watch 3" },
            clock = clock,
        )

    @Test
    fun `an account bundle installs the keys the session the owner and the server`() =
        runTest {
            val outcome = installer.install(bundle())

            assertEquals(InstallOutcome.Installed(ACCOUNT, LINKED_ORIGIN, leftBehind = 0), outcome)
            assertContentEquals(identityKey, identity.getIdentityKey())
            assertContentEquals(mediaKey, mediaKeys.getOrCreateKey())
            assertEquals(UserSession("access", "refresh", ACCOUNT), vault.sessions[LINKED_ORIGIN])
            assertEquals(ACCOUNT, owner.id)
            assertEquals(LINKED_ORIGIN, config.getCurrentBackendUrl().trimEnd('/'))
            assertEquals(listOf(Triple(OwnerScope(LOCAL_OWNER, DEFAULT_ORIGIN), OwnerScope(ACCOUNT, LINKED_ORIGIN), "device")), moves)
            assertTrue(journal.values.isEmpty())
        }

    @Test
    fun `an expired code is refused before anything changes`() =
        runTest {
            nowMs = 200_000

            assertEquals(InstallOutcome.Failed(LinkProblem.LINK_CODE_EXPIRED), installer.install(bundle()))
            assertFalse(identity.hasIdentityKey())
            assertTrue(vault.sessions.isEmpty())
        }

    @Test
    fun `a device that belongs to another account is refused`() =
        runTest {
            owner.bound = true

            assertEquals(LinkProblem.BOUND_TO_ANOTHER_ACCOUNT, installer.bindingProblem(bundle()))
            assertEquals(InstallOutcome.Failed(LinkProblem.BOUND_TO_ANOTHER_ACCOUNT), installer.install(bundle()))
            assertFalse(identity.hasIdentityKey())
        }

    @Test
    fun `a bundle without its keys is incomplete`() =
        runTest {
            assertEquals(LinkProblem.INCOMPLETE_BUNDLE, installer.bindingProblem(bundle().copy(identityKey = null)))
        }

    @Test
    fun `keys that do not match their fingerprint are refused`() =
        runTest {
            assertEquals(
                InstallOutcome.Failed(LinkProblem.KEY_CONFLICT),
                installer.install(bundle().copy(identityKeyFingerprint = "wrong")),
            )
            assertTrue(vault.sessions.isEmpty())
        }

    @Test
    fun `a spent code leaves the keys installed and asks for a new link`() =
        runTest {
            redeemResult = Result.failure(DeviceLinkApiException(400, DeviceLinkErrorCodes.LINK_CODE_INVALID))

            assertEquals(InstallOutcome.Failed(LinkProblem.LINK_CODE_INVALID), installer.install(bundle()))
            assertTrue(identity.hasIdentityKey())
            assertTrue(vault.sessions.isEmpty())
            assertEquals(LOCAL_OWNER, owner.id)
            assertTrue(journal.values.isEmpty())
        }

    @Test
    fun `an account with no room for another device is reported as such`() =
        runTest {
            redeemResult = Result.failure(DeviceLinkApiException(409, DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED))

            assertEquals(InstallOutcome.Failed(LinkProblem.DEVICE_LIMIT_REACHED), installer.install(bundle()))
        }

    @Test
    fun `no answer from the server is a network problem`() =
        runTest {
            redeemResult = Result.failure(IllegalStateException("offline"))

            assertEquals(InstallOutcome.Failed(LinkProblem.NETWORK), installer.install(bundle()))
        }

    @Test
    fun `an install that stopped after the session finishes on resume`() =
        runTest {
            moveFails = true
            assertEquals(InstallOutcome.Failed(LinkProblem.INTERRUPTED), installer.install(bundle()))
            assertTrue(journal.values.isNotEmpty())
            moveFails = false

            assertEquals(InstallOutcome.Installed(ACCOUNT, LINKED_ORIGIN, leftBehind = 0), installer.resume())
            assertEquals(ACCOUNT, owner.id)
            assertTrue(journal.values.isEmpty())
        }

    @Test
    fun `an install that stopped before the session asks for a new link`() =
        runTest {
            journal.putString(
                "linked_device.install_journal.v1",
                """{"step":"KEYS_INSTALLED","accountId":"$ACCOUNT","origin":"$LINKED_ORIGIN",""" +
                    """"oldOwner":"$LOCAL_OWNER","oldOrigin":"$DEFAULT_ORIGIN"}""",
            )

            assertEquals(InstallOutcome.Failed(LinkProblem.INTERRUPTED), installer.resume())
            assertTrue(journal.values.isEmpty())
            assertEquals(LOCAL_OWNER, owner.id)
        }

    @Test
    fun `resume with nothing started does nothing`() =
        runTest {
            assertNull(installer.resume())
        }

    private fun bundle() =
        LinkBundle(
            mode = LinkMode.CLOUD,
            accountId = ACCOUNT,
            serverOrigin = "$LINKED_ORIGIN/",
            linkCode = "code",
            linkCodeExpiresAt = 100_000,
            identityKey = LinkWire.encodeKey(identityKey),
            identityKeyFingerprint = IdentityKeyFingerprint.of(identityKey, crypto),
            mediaKey = LinkWire.encodeKey(mediaKey),
        )

    private class FakeOwner(
        var id: String,
    ) : CanonicalOwnerProvider {
        var bound = false

        override suspend fun getCanonicalOwnerId(): String = id

        override suspend fun hasBoundOwner(): Boolean = bound

        override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean {
            if (bound) return id == remoteOwnerId
            id = remoteOwnerId
            bound = true
            return true
        }
    }

    private class FakeVault : OriginSessionVault {
        val sessions = mutableMapOf<String, UserSession>()

        override suspend fun read(origin: String): UserSession? = sessions[origin]

        override suspend fun write(
            origin: String,
            session: UserSession,
        ) {
            sessions[origin] = session
        }

        override suspend fun clear(origin: String) {
            sessions.remove(origin)
        }
    }

    private companion object {
        const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"
        const val LOCAL_OWNER = "5f0c3a52-0000-4000-8000-000000000002"
        const val DEFAULT_ORIGIN = "https://default.test"
        const val LINKED_ORIGIN = "https://linked.test"
    }
}
```

Create `client/data/src/desktopTest/kotlin/app/logdate/client/data/account/OwnerAdoptionMoverTest.kt`:

```kotlin
package app.logdate.client.data.account

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.sync.peer.link.OwnerScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests [OwnerAdoptionMover] against a real database: a linked device's queue and cursors follow it to its account. */
class OwnerAdoptionMoverTest {
    private val database =
        getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(Files.createTempFile("owner-adoption-mover", ".db").toString()))
    private val dao = database.syncMetadataDao()
    private val mover = OwnerAdoptionMover(dao, database.historyOwnerAdoptionDao())

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `queued uploads and cursors move to the linked account and server`() =
        runBlocking {
            dao.insertPending(pending(LOCAL, DEFAULT, "note-1", "op-1"))
            dao.upsertCursor(SyncCursorEntity(LOCAL, DEFAULT, "NOTE", 50))

            val leftBehind = mover.move(OwnerScope(LOCAL, DEFAULT), OwnerScope(ACCOUNT, LINKED), "watch-device")

            assertEquals(0, leftBehind)
            assertEquals(listOf("op-1"), dao.getPendingForOrigin(ACCOUNT, LINKED).map { it.operationId })
            assertTrue(dao.getPendingForOrigin(LOCAL, DEFAULT).isEmpty())
            assertEquals(50, dao.getCursor(ACCOUNT, LINKED, "NOTE")?.lastSyncTimestamp)
        }

    @Test
    fun `a record the account already queues stays behind and is counted`() =
        runBlocking {
            dao.insertPending(pending(LOCAL, DEFAULT, "note-1", "op-local"))
            dao.insertPending(pending(ACCOUNT, LINKED, "note-1", "op-account"))

            val leftBehind = mover.move(OwnerScope(LOCAL, DEFAULT), OwnerScope(ACCOUNT, LINKED), "watch-device")

            assertEquals(1, leftBehind)
            assertEquals(listOf("op-local"), dao.getPendingForOrigin(LOCAL, DEFAULT).map { it.operationId })
            assertEquals(listOf("op-account"), dao.getPendingForOrigin(ACCOUNT, LINKED).map { it.operationId })
        }

    @Test
    fun `moving a second time changes nothing`() =
        runBlocking {
            dao.insertPending(pending(LOCAL, DEFAULT, "note-1", "op-1"))
            mover.move(OwnerScope(LOCAL, DEFAULT), OwnerScope(ACCOUNT, LINKED), "watch-device")

            assertEquals(0, mover.move(OwnerScope(LOCAL, DEFAULT), OwnerScope(ACCOUNT, LINKED), "watch-device"))
            assertEquals(listOf("op-1"), dao.getPendingForOrigin(ACCOUNT, LINKED).map { it.operationId })
        }

    private fun pending(
        owner: String,
        origin: String,
        id: String,
        operationId: String,
    ) = PendingUploadEntity(
        ownerId = owner,
        serverOrigin = origin,
        entityType = "NOTE",
        entityId = id,
        operation = "CREATE",
        createdAt = 1,
        operationId = operationId,
    )

    private companion object {
        const val LOCAL = "local-owner"
        const val ACCOUNT = "account-owner"
        const val DEFAULT = "https://default.test"
        const val LINKED = "https://linked.test"
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.link.LinkedDeviceInstallerTest' :client:data:desktopTest --tests 'app.logdate.client.data.account.OwnerAdoptionMoverTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'LinkedDeviceInstaller'", "Unresolved reference 'OwnerRowsMover'", "Unresolved reference 'OwnerAdoptionMover'".

- [ ] **Step 3: Implement**

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/LinkedDeviceInstaller.kt`:

```kotlin
package app.logdate.client.sync.peer.link

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** Why a phone's account bundle was not installed on this device. The watch reports it in its state document. */
enum class LinkProblem {
    INCOMPLETE_BUNDLE,
    LINK_CODE_EXPIRED,
    LINK_CODE_INVALID,
    DEVICE_LIMIT_REACHED,
    BOUND_TO_ANOTHER_ACCOUNT,
    KEY_CONFLICT,
    SERVER_REFUSED,
    NETWORK,
    INTERRUPTED,
    SESSION_REVOKED,
}

/** An owner and the server its rows belong to. */
data class OwnerScope(
    val ownerId: String,
    val origin: String,
)

/** Moves what a device recorded under one owner and server to another; returns how many queued changes stayed behind. */
fun interface OwnerRowsMover {
    suspend fun move(
        from: OwnerScope,
        to: OwnerScope,
        deviceId: String,
    ): Int
}

/** Redeems a link code at the account's server, whichever server this device is connected to. */
fun interface LinkCodeRedeemer {
    suspend fun redeem(
        origin: String,
        descriptor: ServerDescriptor?,
        code: String,
        deviceName: String,
    ): Result<DeviceLinkRedeemResponse>
}

sealed interface InstallOutcome {
    data class Installed(
        val accountId: String,
        val origin: String,
        val leftBehind: Int,
    ) : InstallOutcome

    data class Failed(
        val problem: LinkProblem,
    ) : InstallOutcome
}

/** Installs a phone's account bundle on a linked device. */
interface LinkInstaller {
    /** Why this device cannot take [bundle] at all; checked before the exchange is confirmed. */
    suspend fun bindingProblem(bundle: LinkBundle): LinkProblem?

    suspend fun install(bundle: LinkBundle): InstallOutcome

    /** Finishes an install a stopped process left part way, or null when none was. */
    suspend fun resume(): InstallOutcome?
}

/**
 * Turns a phone's account bundle into this device's keys, session, owner and server, one journaled
 * step at a time. The link code is never written down: a process stopped before redeeming it ends the
 * install, and the phone links again with a new code.
 */
class LinkedDeviceInstaller(
    private val journal: KeyValueStorage,
    private val identityKeys: IdentityKeyManager,
    private val mediaKeys: MediaPayloadKeyProvider,
    private val owner: CanonicalOwnerProvider,
    private val config: LogDateConfigRepository,
    private val sessions: OriginSessionVault,
    private val redeemer: LinkCodeRedeemer,
    private val mover: OwnerRowsMover,
    private val deviceId: suspend () -> String,
    private val deviceName: () -> String,
    private val clock: Clock = Clock.System,
) : LinkInstaller {
    private val mutex = Mutex()

    override suspend fun bindingProblem(bundle: LinkBundle): LinkProblem? {
        val fields = CloudLinkFields.of(bundle) ?: return LinkProblem.INCOMPLETE_BUNDLE
        if (clock.now().toEpochMilliseconds() >= fields.codeExpiresAt) return LinkProblem.LINK_CODE_EXPIRED
        if (owner.hasBoundOwner() && owner.getCanonicalOwnerId() != fields.accountId) return LinkProblem.BOUND_TO_ANOTHER_ACCOUNT
        if (!identityKeys.canUseForAccount(fields.accountId)) return LinkProblem.BOUND_TO_ANOTHER_ACCOUNT
        if (!mediaKeys.canInstallAccountKey(fields.mediaKey)) return LinkProblem.KEY_CONFLICT
        return null
    }

    override suspend fun install(bundle: LinkBundle): InstallOutcome =
        mutex.withLock {
            bindingProblem(bundle)?.let { problem -> return@withLock refused(problem) }
            val fields = checkNotNull(CloudLinkFields.of(bundle))
            val entry =
                InstallJournal(
                    step = InstallStep.STARTED,
                    accountId = fields.accountId,
                    origin = fields.origin,
                    descriptor = fields.descriptorJson,
                    oldOwner = owner.getCanonicalOwnerId(),
                    oldOrigin = config.getCurrentBackendUrl().trimEnd('/'),
                )
            save(entry)
            installKeys(fields)?.let { problem -> return@withLock abandon(problem) }
            save(entry.copy(step = InstallStep.KEYS_INSTALLED))
            val session =
                when (val redeemed = redeem(fields)) {
                    is Redeemed.Session -> redeemed.session
                    is Redeemed.Refused -> return@withLock abandon(redeemed.problem)
                }
            sessions.write(fields.origin, session)
            val saved = entry.copy(step = InstallStep.SESSION_SAVED)
            save(saved)
            finish(saved)
        }

    override suspend fun resume(): InstallOutcome? =
        mutex.withLock {
            val pending = read() ?: return@withLock null
            if (pending.step < InstallStep.SESSION_SAVED) return@withLock abandon(LinkProblem.INTERRUPTED)
            finish(pending)
        }

    private suspend fun finish(entry: InstallJournal): InstallOutcome {
        val moved =
            if (entry.step == InstallStep.SESSION_SAVED) {
                moveRows(entry) ?: return InstallOutcome.Failed(LinkProblem.INTERRUPTED)
            } else {
                entry
            }
        if (!owner.adoptRemoteOwnerIfUninitialized(moved.accountId)) {
            sessions.clear(moved.origin)
            return abandon(LinkProblem.BOUND_TO_ANOTHER_ACCOUNT)
        }
        config.updateBackendUrl(moved.origin)
        config.updateServerDescriptor(moved.descriptor?.let(::descriptorFromText))
        journal.remove(KEY)
        Napier.i("This device is linked to its phone's account; ${moved.leftBehind} queued change(s) stayed with its earlier owner")
        return InstallOutcome.Installed(moved.accountId, moved.origin, moved.leftBehind)
    }

    private suspend fun moveRows(entry: InstallJournal): InstallJournal? {
        val leftBehind =
            try {
                mover.move(OwnerScope(entry.oldOwner, entry.oldOrigin), OwnerScope(entry.accountId, entry.origin), deviceId())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Moving this device's earlier captures to its account failed; it is finished when LogDate next starts", e)
                return null
            }
        val moved = entry.copy(step = InstallStep.ROWS_MOVED, leftBehind = leftBehind)
        save(moved)
        return moved
    }

    private suspend fun installKeys(fields: CloudLinkFields): LinkProblem? =
        try {
            identityKeys.installLinkedIdentityKey(fields.identityKey, fields.fingerprint)
            if (identityKeys.bindToAccount(fields.accountId)) {
                mediaKeys.installAccountKey(fields.mediaKey)
                null
            } else {
                LinkProblem.BOUND_TO_ANOTHER_ACCOUNT
            }
        } catch (e: IllegalArgumentException) {
            Napier.e("The keys from the phone do not match their fingerprint or this device's keys", e)
            LinkProblem.KEY_CONFLICT
        } catch (e: IllegalStateException) {
            Napier.e("This device already has a different identity key", e)
            LinkProblem.KEY_CONFLICT
        }

    private suspend fun redeem(fields: CloudLinkFields): Redeemed {
        val response =
            redeemer.redeem(fields.origin, fields.descriptor, fields.code, deviceName()).getOrElse { error ->
                Napier.w("The phone's link code was not redeemed", error)
                return Redeemed.Refused(problemFor(error))
            }
        if (response.accountId != fields.accountId) return Redeemed.Refused(LinkProblem.SERVER_REFUSED)
        return Redeemed.Session(UserSession(response.tokens.accessToken, response.tokens.refreshToken, response.accountId))
    }

    private fun problemFor(error: Throwable): LinkProblem {
        if (error !is DeviceLinkApiException) return LinkProblem.NETWORK
        return when (error.code) {
            DeviceLinkErrorCodes.LINK_CODE_INVALID -> LinkProblem.LINK_CODE_INVALID
            DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED -> LinkProblem.DEVICE_LIMIT_REACHED
            else -> LinkProblem.SERVER_REFUSED
        }
    }

    private fun refused(problem: LinkProblem): InstallOutcome {
        Napier.w("The phone's account bundle was not installed: $problem")
        return InstallOutcome.Failed(problem)
    }

    private suspend fun abandon(problem: LinkProblem): InstallOutcome {
        journal.remove(KEY)
        Napier.w("Linking this device to its phone's account stopped with $problem; the phone links it again")
        return InstallOutcome.Failed(problem)
    }

    private suspend fun save(entry: InstallJournal) {
        val text = WearJson.json.encodeToString(InstallJournal.serializer(), entry)
        journal.putString(KEY, text)
        check(journal.getString(KEY) == text) { "Could not write the link install journal" }
    }

    private suspend fun read(): InstallJournal? {
        val text = journal.getString(KEY) ?: return null
        return try {
            WearJson.json.decodeFromString(InstallJournal.serializer(), text)
        } catch (e: IllegalArgumentException) {
            Napier.e("The link install journal could not be read; the phone links this device again", e)
            journal.remove(KEY)
            null
        }
    }

    private sealed interface Redeemed {
        data class Session(
            val session: UserSession,
        ) : Redeemed

        data class Refused(
            val problem: LinkProblem,
        ) : Redeemed
    }

    @Serializable
    private enum class InstallStep {
        STARTED,
        KEYS_INSTALLED,
        SESSION_SAVED,
        ROWS_MOVED,
    }

    @Serializable
    private data class InstallJournal(
        val step: InstallStep,
        val accountId: String,
        val origin: String,
        val descriptor: String? = null,
        val oldOwner: String,
        val oldOrigin: String,
        val leftBehind: Int = 0,
    )

    private class CloudLinkFields(
        val accountId: String,
        val origin: String,
        val descriptor: ServerDescriptor?,
        val descriptorJson: String?,
        val code: String,
        val codeExpiresAt: Long,
        val identityKey: ByteArray,
        val fingerprint: String,
        val mediaKey: ByteArray,
    ) {
        companion object {
            fun of(bundle: LinkBundle): CloudLinkFields? {
                if (bundle.mode != LinkMode.CLOUD) return null
                return CloudLinkFields(
                    accountId = bundle.accountId ?: return null,
                    origin = bundle.serverOrigin?.trimEnd('/') ?: return null,
                    descriptor = bundle.serverDescriptor?.let(::descriptorFromJson),
                    descriptorJson = bundle.serverDescriptor?.toString(),
                    code = bundle.linkCode ?: return null,
                    codeExpiresAt = bundle.linkCodeExpiresAt ?: return null,
                    identityKey = bundle.identityKey?.let(LinkWire::decodeKey) ?: return null,
                    fingerprint = bundle.identityKeyFingerprint ?: return null,
                    mediaKey = bundle.mediaKey?.let(LinkWire::decodeKey) ?: return null,
                )
            }
        }
    }

    private companion object {
        const val KEY = "linked_device.install_journal.v1"
    }
}

private fun descriptorFromJson(element: JsonElement): ServerDescriptor? =
    try {
        WearJson.json.decodeFromJsonElement(ServerDescriptor.serializer(), element)
    } catch (e: IllegalArgumentException) {
        Napier.w("The phone's server descriptor could not be read; the server's features are read again when it is next contacted", e)
        null
    }

private fun descriptorFromText(text: String): ServerDescriptor? =
    try {
        WearJson.json.decodeFromString(ServerDescriptor.serializer(), text)
    } catch (e: IllegalArgumentException) {
        Napier.w("The saved server descriptor could not be read", e)
        null
    }
```

Create `client/data/src/commonMain/kotlin/app/logdate/client/data/account/OwnerAdoptionMover.kt`:

```kotlin
package app.logdate.client.data.account

import app.logdate.client.database.dao.HistoryOwnerAdoptionDao
import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.sync.peer.link.OwnerRowsMover
import app.logdate.client.sync.peer.link.OwnerScope
import io.github.aakira.napier.Napier

/**
 * Moves what a device recorded before it was linked to the account it is linked to: its queued uploads
 * and download cursors, which may change servers too, and the history rows [HistoryOwnerAdoption] moves
 * when a phone signs in. Every step can run again safely, so a stopped move is finished by running it again.
 *
 * History rows stay with their server; location history does not sync on a linked watch.
 */
class OwnerAdoptionMover(
    private val syncMetadataDao: SyncMetadataDao,
    private val historyOwnerAdoptionDao: HistoryOwnerAdoptionDao,
) : OwnerRowsMover {
    override suspend fun move(
        from: OwnerScope,
        to: OwnerScope,
        deviceId: String,
    ): Int {
        val leftBehind = syncMetadataDao.reassignPending(from.ownerId, from.origin, to.ownerId, to.origin)
        syncMetadataDao.reassignCursors(from.ownerId, from.origin, to.ownerId, to.origin)
        historyOwnerAdoptionDao.adopt(from.ownerId, to.ownerId, from.origin, deviceId) { it.rewriteHistoryOwner(to.ownerId) }
        if (leftBehind > 0) {
            Napier.w("$leftBehind queued change(s) stayed with the earlier owner because the account already queues the same records")
        }
        return leftBehind
    }
}
```

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again.
Expected: PASS (11 `LinkedDeviceInstallerTest`, 3 `OwnerAdoptionMoverTest`).

- [ ] **Step 5: Run every affected module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt :client:data:desktopTest :client:data:compileKotlinIosSimulatorArm64 :client:data:compileTestKotlinIosSimulatorArm64 :client:data:ktlintCheck :client:data:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task03-link-installer.txt <<'EOF'
chore(sync): install a linked watch's keys and session in steps

A device linked from its phone can now take the phone's account
bundle: it checks it, installs the identity and media keys, redeems
the one-time code for its own session, moves what it captured before
the link to the account, adopts the account as its owner, and
switches to the account's server.

Each step is journaled. A device stopped after redeeming the code
finishes on its next start; one stopped before asks the phone for a
new link, because the code is never written down. Every refusal has
a name the phone can show: an expired or spent code, an account with
no room for another device, another account, or keys that do not
match. Nothing installs a bundle yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/LinkedDeviceInstaller.kt client/data/src/commonMain/kotlin/app/logdate/client/data/account/OwnerAdoptionMover.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/link/LinkedDeviceInstallerTest.kt client/data/src/desktopTest/kotlin/app/logdate/client/data/account/OwnerAdoptionMoverTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task03-link-installer.txt
```

---

### Task 4: Finish linking the watch to the phone's account (D2, watch)

The watch now accepts `CLOUD` offers. When the bundle opens, it checks it with `LinkedDeviceInstaller.bindingProblem` before confirming: a watch that has another account or another key refuses with `BOUND_TO_ANOTHER_ACCOUNT`, so the phone can offer to erase it. Otherwise it confirms at once and installs in the background, because redeeming the code may take longer than the phone waits for an answer. The outcome goes into the link record; a failure becomes `NEEDS_RELINK` with a `linkProblem` in the watch's state document, so the phone can show it and decide when to try again (Task 5). An install a stopped process left after the code was spent finishes when the app next starts. The watch starts advertising `link.cloud.v1`.

This task gives the watch the crypto bindings its phone has (`platformCryptoModule()`, `KeyDerivation`, a privacy epoch for `SecureSessionStorage`) and an `IdentityKeyManager` with `IdentityMintPolicy.NEVER`, and makes `:app:wear` depend on `:client:networking` for `DeviceLinkApiClient`. It also starts `LogDateConfigDataSource` on the watch, so the account's server survives a restart.

**Files:**
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt:69-77` (`WatchState.linkProblem`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkStore.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinator.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchOwnState.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/di/WearIdentityModule.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`
- Modify: `app/wear/build.gradle.kts:141` (`implementation(projects.client.networking)`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateDocumentCodecTest.kt` (1 new test)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinatorTest.kt` (5 new tests)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchOwnStateTest.kt` (1 new test)

**Interfaces:**
- Consumes: Task 3's `LinkInstaller`, `LinkedDeviceInstaller`, `InstallOutcome`, `LinkProblem`, `OwnerRowsMover`, `LinkCodeRedeemer`, `OwnerAdoptionMover`; `DeviceLinkApiClient(httpClient, configRepository).redeem(code, deviceName)`; `PinnedLogDateConfigRepository(origin, descriptor)`; `httpClient` (`app.logdate.client.networking`); `platformCryptoModule()`; `PrivacyScopeEpoch(PrivacyEpochStorage)`; `Context.userVisibleDeviceName()`; `DeviceIdProvider.getDeviceId()`; `LogDateConfigDataSource.awaitConfigurationLoaded()`.
- Produces:
  - `WatchState(..., linkProblem: String? = null)` (last parameter)
  - `WatchLinkRecord(..., problem: String? = null)`; `peerLinkState()` is `NEEDS_RELINK` while `problem != null`
  - `WatchLinkStore.savePairKey(phoneNode: String, pairKey: ByteArray)`, `saveCloudLink(phoneNode: String, accountId: String)`, `recordProblem(phoneNode: String?, problem: LinkProblem)`
  - `WatchLinkCoordinator(exchange, store, v2Enabled, localNodeId, installer: LinkInstaller? = null, installScope: CoroutineScope? = null, beforeInstall: suspend () -> Unit = {})`, `suspend fun resumeInstall()`
  - `val wearIdentityModule: Module` binding `IdentityKeyManager` (mint policy `NEVER`), `MediaPayloadKeyProvider`, `KeyDerivation`, `PrivacyScopeEpoch`, `OwnerRowsMover`, `LinkCodeRedeemer`, `LinkInstaller`

**Failure modes and how they surface:**
- A watch with another account, or an identity key that is not the account's: refused before confirming with `BOUND_TO_ANOTHER_ACCOUNT`; the phone shows "<watch> is set up for another LogDate account".
- A code that expired on the way: refused with `EXPIRED_OFFER`; the phone tries again with a new code. An incomplete bundle: refused with `UNSUPPORTED`.
- Any install failure (spent code, account limit, network, server, interrupted): the record gets the `LinkProblem`, the watch publishes `NEEDS_RELINK` with `linkProblem`, and the failure is logged. An install that throws is recorded as `INTERRUPTED`.
- A watch whose redesigned app turns off mid-install: the install finishes; the link only takes effect when version 2 is active.

- [ ] **Step 1: Write the failing tests**

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateDocumentCodecTest.kt`, add:

```kotlin
    @Test
    fun `a watch's link problem reads back and is left out when there is none`() {
        val failed =
            watchDocument.copy(watch = watchDocument.watch?.copy(link = PeerLinkState.NEEDS_RELINK, linkProblem = "DEVICE_LIMIT_REACHED"))

        assertEquals(Decoded.Supported(failed), StateDocumentCodec.decode(StateDocumentCodec.encode(failed)))
        assertEquals(false, StateDocumentCodec.encode(watchDocument).contains("linkProblem"))
    }
```

In `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchOwnStateTest.kt`, add:

```kotlin
    @Test
    fun `a failed account link is reported with its problem`() =
        runTest {
            val record = WatchLinkRecord(LinkMode.CLOUD, "phone", 1, problem = "DEVICE_LIMIT_REACHED")

            val state = WatchOwnState(links = flowOf(record)).observe().first()

            assertEquals(PeerLinkState.NEEDS_RELINK, state.link)
            assertEquals("DEVICE_LIMIT_REACHED", state.linkProblem)
        }
```

In `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinatorTest.kt`, annotate the class with `@OptIn(ExperimentalCoroutinesApi::class)`, add the imports `app.logdate.client.sync.peer.link.InstallOutcome`, `app.logdate.client.sync.peer.link.LinkInstaller`, `app.logdate.client.sync.peer.link.LinkProblem`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.ExperimentalCoroutinesApi` and `kotlinx.coroutines.test.runCurrent`, add `const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"` to the companion, and add:

```kotlin
    private val installer = FakeInstaller()

    private fun accountCoordinator(scope: CoroutineScope) =
        WatchLinkCoordinator(
            exchange = WatchLinkExchange(handshake, LinkNonceLedger(clock), clock),
            store = store,
            v2Enabled = { true },
            localNodeId = { WATCH },
            installer = installer,
            installScope = scope,
        )

    @Test
    fun `an account bundle is confirmed and then installed`() =
        runTest {
            val answer = linkAccount(accountCoordinator(backgroundScope))
            runCurrent()

            assertTrue(answer is LinkAnswer.Answered)
            assertEquals(PeerLinkState.LINKED, store.current().peerLinkState())
            assertEquals(ACCOUNT, store.current()?.accountId)
            assertEquals(1, installer.installs)
        }

    @Test
    fun `a watch that has another account refuses the bundle`() =
        runTest {
            installer.problem = LinkProblem.BOUND_TO_ANOTHER_ACCOUNT

            val answer = linkAccount(accountCoordinator(backgroundScope))
            runCurrent()

            assertEquals(LinkAnswer.Refused(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT), answer)
            assertEquals(0, installer.installs)
            assertNull(store.current())
        }

    @Test
    fun `a failed install is recorded with its problem`() =
        runTest {
            installer.outcome = InstallOutcome.Failed(LinkProblem.DEVICE_LIMIT_REACHED)

            linkAccount(accountCoordinator(backgroundScope))
            runCurrent()

            assertEquals(PeerLinkState.NEEDS_RELINK, store.current().peerLinkState())
            assertEquals("DEVICE_LIMIT_REACHED", store.current()?.problem)
        }

    @Test
    fun `an install that throws is recorded as interrupted`() =
        runTest {
            installer.throws = true

            linkAccount(accountCoordinator(backgroundScope))
            runCurrent()

            assertEquals("INTERRUPTED", store.current()?.problem)
        }

    @Test
    fun `an install finished on start is recorded`() =
        runTest {
            installer.resumeOutcome = InstallOutcome.Installed(ACCOUNT, "https://linked.test", leftBehind = 0)

            accountCoordinator(backgroundScope).resumeInstall()

            assertEquals(PeerLinkState.LINKED, store.current().peerLinkState())
        }

    private suspend fun linkAccount(coordinator: WatchLinkCoordinator): LinkAnswer<LinkConfirm> {
        val started = phone.start(LinkMode.CLOUD, PHONE)
        val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
        val bundle = LinkBundle(mode = LinkMode.CLOUD, accountId = ACCOUNT)
        val sealed = assertNotNull(phone.seal(started, accept, WATCH, bundle).valueOrNull())
        return LinkWire.read(assertNotNull(coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed))), LinkConfirm.serializer())
    }

    private class FakeInstaller : LinkInstaller {
        var problem: LinkProblem? = null
        var outcome: InstallOutcome = InstallOutcome.Installed(ACCOUNT, "https://linked.test", leftBehind = 0)
        var resumeOutcome: InstallOutcome? = null
        var throws = false
        var installs = 0

        override suspend fun bindingProblem(bundle: LinkBundle): LinkProblem? = problem

        override suspend fun install(bundle: LinkBundle): InstallOutcome {
            installs++
            check(!throws) { "The journal could not be written" }
            return outcome
        }

        override suspend fun resume(): InstallOutcome? = resumeOutcome
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.state.StateDocumentCodecTest' :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.v2.*' --console=plain
```
Expected: FAIL, compilation errors "No parameter with name 'linkProblem' found", "No parameter with name 'problem' found", "No parameter with name 'installer' found", "Unresolved reference 'resumeInstall'".

- [ ] **Step 3: Implement**

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt`, change the `WatchState` KDoc and add the last parameter:

```kotlin
/**
 * Fields only the watch writes.
 *
 * @property linkProblem Why the last account link failed, as a `LinkProblem` name. It is a string so a
 *   value a newer watch adds never makes the document unreadable.
 */
@Serializable
data class WatchState(
    val outboxCount: Int = 0,
    val deadLetterCount: Int = 0,
    val freeBytes: Long? = null,
    val link: PeerLinkState = PeerLinkState.UNLINKED,
    val presence: Presence = Presence(),
    val features: Set<String> = emptySet(),
    val linkProblem: String? = null,
)
```

In `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkStore.kt`:

1. Add `val problem: String? = null,` as the last `WatchLinkRecord` property and `@property problem Why the last account link failed, as a [LinkProblem] name.` to its KDoc.
2. In `peerLinkState()`, replace `if (stale) return PeerLinkState.NEEDS_RELINK` with `if (stale || problem != null) return PeerLinkState.NEEDS_RELINK`.
3. Add `import app.logdate.client.sync.peer.link.LinkProblem` and, after `savePairing`, add:

```kotlin
    /** Keeps the pair key from an account link before its install finishes. */
    suspend fun savePairKey(
        phoneNode: String,
        pairKey: ByteArray,
    ) = pairKeys.put(phoneNode, pairKey)

    suspend fun saveCloudLink(
        phoneNode: String,
        accountId: String,
    ) {
        write(
            WatchLinkRecord(
                mode = LinkMode.CLOUD,
                phoneNode = phoneNode,
                linkedAt = clock.now().toEpochMilliseconds(),
                accountId = accountId,
            ),
        )
    }

    /** Records why an account link failed; the watch then asks its phone to link it again. */
    suspend fun recordProblem(
        phoneNode: String?,
        problem: LinkProblem,
    ) {
        val previous = current()
        val node = phoneNode ?: previous?.phoneNode.orEmpty()
        val record =
            previous?.copy(phoneNode = node, problem = problem.name)
                ?: WatchLinkRecord(mode = LinkMode.CLOUD, phoneNode = node, linkedAt = 0, problem = problem.name)
        write(record)
    }
```

In `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchOwnState.kt`, in the `WatchState(` call add `linkProblem = link?.problem,`.

In `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinator.kt`:

1. Add the constructor parameters after `localNodeId`:

```kotlin
    private val installer: LinkInstaller? = null,
    private val installScope: CoroutineScope? = null,
    private val beforeInstall: suspend () -> Unit = {},
```

2. Replace `acceptsMode` with:

```kotlin
    private fun acceptsMode(mode: LinkMode): Boolean = mode == LinkMode.PAIR || (installer != null && installScope != null)
```

3. Replace the `LinkMode.CLOUD ->` branch of `answerBundle` with `LinkMode.CLOUD -> linkAccount(sourceNodeId, opened)`.
4. Add, after `onNodeMigrated`:

```kotlin
    /** Finishes an account link a stopped process left after the code was spent. */
    suspend fun resumeInstall() {
        val outcome = installer?.resume() ?: return
        record(store.current()?.phoneNode, outcome)
    }
```

5. Add, after `answerBundle`:

```kotlin
    private suspend fun linkAccount(
        phoneNode: String,
        opened: WatchLinkExchange.Opened,
    ): ByteArray {
        val installer = installer
        val scope = installScope
        if (installer == null || scope == null) return refuse(LinkRefusal.UNSUPPORTED, "this watch build cannot link to an account")
        installer.bindingProblem(opened.bundle)?.let { problem ->
            return refuse(problem.toRefusal(), "the account bundle cannot be installed: $problem")
        }
        store.savePairKey(phoneNode, opened.pairKey)
        scope.launch { install(installer, phoneNode, opened) }
        Napier.i("Confirmed the phone's account link; installing it now")
        return LinkWire.write(LinkConfirm.serializer(), opened.confirm)
    }

    private suspend fun install(
        installer: LinkInstaller,
        phoneNode: String,
        opened: WatchLinkExchange.Opened,
    ) {
        val outcome =
            try {
                beforeInstall()
                installer.install(opened.bundle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Installing the phone's account link failed part way; the phone links this watch again", e)
                InstallOutcome.Failed(LinkProblem.INTERRUPTED)
            }
        record(phoneNode, outcome)
    }

    private suspend fun record(
        phoneNode: String?,
        outcome: InstallOutcome,
    ) {
        when (outcome) {
            is InstallOutcome.Installed -> {
                store.saveCloudLink(phoneNode.orEmpty(), outcome.accountId)
                Napier.i("This watch now syncs with its phone's account")
            }
            is InstallOutcome.Failed -> {
                store.recordProblem(phoneNode, outcome.problem)
                Napier.w(
                    "Linking this watch to its phone's account failed with ${outcome.problem}; the phone sees it in this watch's state",
                )
            }
        }
    }

    private fun LinkProblem.toRefusal(): LinkRefusal =
        when (this) {
            LinkProblem.BOUND_TO_ANOTHER_ACCOUNT, LinkProblem.KEY_CONFLICT -> LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT
            LinkProblem.LINK_CODE_EXPIRED -> LinkRefusal.EXPIRED_OFFER
            else -> LinkRefusal.UNSUPPORTED
        }
```

with imports `app.logdate.client.sync.peer.link.InstallOutcome`, `app.logdate.client.sync.peer.link.LinkInstaller`, `app.logdate.client.sync.peer.link.LinkProblem`, `kotlinx.coroutines.CancellationException`, `kotlinx.coroutines.CoroutineScope` and `kotlinx.coroutines.launch`.

Create `app/wear/src/main/kotlin/app/logdate/wear/di/WearIdentityModule.kt`:

```kotlin
package app.logdate.wear.di

import android.content.Context
import app.logdate.client.data.account.OwnerAdoptionMover
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityMintPolicy
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.crypto.NoOpIdentityKeyBackupStore
import app.logdate.client.device.crypto.platformCryptoModule
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.userVisibleDeviceName
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.networking.httpClient
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.peer.link.LinkCodeRedeemer
import app.logdate.client.sync.peer.link.LinkInstaller
import app.logdate.client.sync.peer.link.LinkedDeviceInstaller
import app.logdate.client.sync.peer.link.OwnerRowsMover
import app.logdate.shared.config.PinnedLogDateConfigRepository
import app.logdate.shared.config.PrivacyEpochStorage
import app.logdate.shared.config.PrivacyScopeEpoch
import org.koin.dsl.module

/**
 * Koin module for the watch's identity: the crypto its phone has, an identity key manager that never
 * creates a key of its own, and the installer that takes the phone's account link.
 */
val wearIdentityModule =
    module {
        includes(platformCryptoModule())
        single {
            val secure = get<SecureStorage>()
            PrivacyScopeEpoch(
                object : PrivacyEpochStorage {
                    override suspend fun read(): String? = secure.getString(PRIVACY_EPOCH_KEY)

                    override suspend fun write(epoch: String) = secure.putString(PRIVACY_EPOCH_KEY, epoch)
                },
            )
        }
        single { KeyDerivation(get()) }
        single {
            IdentityKeyManager(
                secureStorage = get(),
                cryptoManager = get(),
                backupStore = NoOpIdentityKeyBackupStore,
                mintPolicy = IdentityMintPolicy.NEVER,
            )
        }
        single { MediaPayloadKeyProvider(get(), get(), get(), get()) }
        single<OwnerRowsMover> { OwnerAdoptionMover(get(), get()) }
        single<LinkCodeRedeemer> {
            LinkCodeRedeemer { origin, descriptor, code, deviceName ->
                DeviceLinkApiClient(httpClient, PinnedLogDateConfigRepository(origin, descriptor)).redeem(code, deviceName)
            }
        }
        single<LinkInstaller> {
            val context = get<Context>()
            val devices = get<DeviceIdProvider>()
            LinkedDeviceInstaller(
                journal = get(),
                identityKeys = get(),
                mediaKeys = get(),
                owner = get(),
                config = get(),
                sessions = get(),
                redeemer = get(),
                mover = get(),
                deviceId = { devices.getDeviceId().value.toString() },
                deviceName = { context.userVisibleDeviceName() },
            )
        }
    }

/** The key the phone's device module uses for the same value. */
private const val PRIVACY_EPOCH_KEY = "diagnostic_privacy_epoch"
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt`, replace the `WatchLinkCoordinator` and `WatchOwnState` bindings with:

```kotlin
        single {
            val nodeClient = Wearable.getNodeClient(get<Context>())
            val flags = get<FeatureFlagStore>()
            val config = get<LogDateConfigDataSource>()
            WatchLinkCoordinator(
                exchange = get(),
                store = get(),
                v2Enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
                localNodeId = { nodeClient.localNodeIdOrNull() },
                installer = get<LinkInstaller>(),
                installScope = get<CoroutineScope>(),
                beforeInstall = { config.awaitConfigurationLoaded() },
            )
        }
        single { WatchOwnState(links = get<WatchLinkStore>().observe(), features = setOf(WearFeatures.LINK_CLOUD)) }
```

with imports `app.logdate.client.datastore.LogDateConfigDataSource`, `app.logdate.client.sync.datalayer.v2.WearFeatures`, `app.logdate.client.sync.peer.link.LinkInstaller` and `kotlinx.coroutines.CoroutineScope`.

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, add `wearIdentityModule,` to `modules(...)` after `wearDataModule,` with its import, and in `startSync()` after `koin.get<WatchStateMirror>().start(scope)` add:

```kotlin
        scope.launch { resumeAccountLink(koin) }
```

and the function:

```kotlin
    /** Loads the saved server first, so a link finished now is not undone by the saved one arriving later. */
    private suspend fun resumeAccountLink(koin: org.koin.core.Koin) {
        try {
            koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
            koin.get<WatchLinkCoordinator>().resumeInstall()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Finishing the phone's account link failed; the phone links this watch again", e)
        }
    }
```

with imports `app.logdate.client.datastore.LogDateConfigDataSource`, `app.logdate.wear.sync.v2.link.WatchLinkCoordinator` and `kotlinx.coroutines.CancellationException`.

In `app/wear/build.gradle.kts`, after `implementation(projects.client.logdateDatastore)` add `implementation(projects.client.networking)`.

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again.
Expected: PASS (the new tests and every existing `WatchLinkCoordinatorTest`, `WatchOwnStateTest`, `WatchStateMirrorTest` and `StateDocumentCodecTest` test).

- [ ] **Step 5: Run every affected module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task04-watch-account-link.txt <<'EOF'
chore(wear): finish linking the watch to the phone's account

With the redesigned watch app on, the watch now accepts its phone's
account link. It refuses one when it already has another account,
so the phone can offer to erase it; otherwise it confirms and installs
the keys, its own session and the account's server, and moves what it
captured before the link to the account.

The outcome is in the watch's state: linked, or a named problem such
as a spent code or an account with no room for another device. An
install stopped after the code was spent finishes when the app next
starts. The watch now advertises account linking, and it can never
create an identity key of its own.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/state/StateDocumentCodecTest.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkStore.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinator.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchOwnState.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearIdentityModule.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/build.gradle.kts app/wear/src/test/kotlin/app/logdate/wear/sync/v2/link/WatchLinkCoordinatorTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/WatchOwnStateTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task04-watch-account-link.txt
```

---

### Task 5: Send the account link from the phone to the watch (D2, phone)

The phone now links a watch to its account when all of these are true: it is signed in, its server lists `deviceLinkingV1`, it has an identity key, and the watch lists `link.cloud.v1`. Otherwise it pairs. The plan is re-evaluated on every trigger the master plan names: a watch's capability arriving (Task 2), signing in or out (`SessionStorage.getSessionFlow()`), the switch turning on, a server descriptor change, and the watch reporting `NEEDS_RELINK`. The code is minted with `DeviceLinkApiClient` right after the watch accepts (codes live 120 s), with one refresh of the access token on a 401. The bundle contains the account, origin, descriptor, code, identity key, fingerprint and media key, never the recovery phrase.

The phone confirms an account link only when the watch's state says `LINKED`. When the watch reports a `linkProblem`, the phone records it with its wait and notifies once for the problems the person must fix: an account with no room for another device, another account, or another key. The phone publishes `link.cloud.v1` and whether it is signed in (`PhoneState.cloud`).

**Files:**
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkCodeMinter.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/CloudLinkBundleSource.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneWatchLinkStore.kt` (`PhoneLinkFailure` values)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecision.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiator.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/WatchLinkNotifier.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt:228-235` (move the `PhoneStatePublisher` binding out)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`
- Modify: `client/notifications/src/androidMain/res/values/strings.xml` (two strings)
- Modify: `app/android-main/build.gradle.kts` (test dependencies on `:client:networking` and `:shared:config`)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/FakeSessions.kt` (test helper)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/LinkCodeMinterTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/CloudLinkBundleSourceTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecisionTest.kt` (3 new tests)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiatorTest.kt` (2 new tests)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt` (2 new tests)

**Interfaces:**
- Consumes: Tasks 2 and 4; `DeviceLinkApiClientContract.mintLinkCode(accessToken)`, `DeviceLinkApiException(status, code)`, `DeviceLinkCodeResponse(code, expiresAt)`; `SessionStorage.getOriginBoundSession()/replaceSessionIfCurrent/getSessionFlow()`; `SessionRefreshingAccountRepository.refreshSession(refreshToken): Result<RefreshedSession>`; `IdentityKeyManager.hasIdentityKey/getIdentityKey`; `IdentityKeyFingerprint.of(key, cryptoManager)`; `MediaPayloadKeyProvider.getOrCreateKey()`; `ServerProtocolFeature.DEVICE_LINKING_V1`; `WearFeatures.LINK_CLOUD`; `LinkProblem` names.
- Produces:
  - `class LinkCodeMinter(links: DeviceLinkApiClientContract, sessions: SessionStorage, refresher: SessionRefreshingAccountRepository?) { suspend fun mint(): Result<DeviceLinkCodeResponse> }`
  - `interface AccountKeySource { suspend fun identityKey(): ByteArray?; fun fingerprint(identityKey: ByteArray): String; suspend fun mediaKey(): ByteArray }`, `class DeviceAccountKeySource(identityKeys: IdentityKeyManager, mediaKeys: MediaPayloadKeyProvider, cryptoManager: CryptoManager) : AccountKeySource`
  - `class CloudLinkBundleSource(sessions: SessionStorage, config: LogDateConfigRepository, minter: LinkCodeMinter, keys: AccountKeySource, v2Enabled: suspend () -> Boolean) : LinkBundleSource`
  - `PhoneLinkFailure` gains `SIGNED_OUT, LINK_CODE_UNAVAILABLE, KEYS_UNAVAILABLE, DEVICE_LIMIT_REACHED (needs attention), WATCH_KEY_CONFLICT (needs attention), WATCH_INSTALL_FAILED`
  - `PhoneLinkDecision.WatchProblem(val failure: PhoneLinkFailure)`
  - `data class PhoneStateParts(val cloud: CloudPresence = CloudPresence.UNKNOWN, val dayStartHour: Int = 0, val campfire: CampfireSnapshot? = null)`; `PhoneStatePublisher(v2Enabled, channel, capabilities, peerStates, clock, timeZone, features: Set<String> = emptySet(), parts: Flow<PhoneStateParts> = flowOf(PhoneStateParts()))`

**Failure modes and how they surface:**
- Signed out between the plan and the bundle: `SIGNED_OUT`; the next trigger plans a pairing. No identity key yet: the plan is a pairing, never a bundle without keys; a media key that cannot be derived: `KEYS_UNAVAILABLE`.
- Minting fails: `DEVICE_LIMIT_REACHED` (notification once: "Backup is not set up on <watch>" with how to fix it) or `LINK_CODE_UNAVAILABLE` (network, server, refresh refused; retried with the growing wait). A refused refresh leaves the session unchanged; `SyncTokenRefresher` owns ending it.
- The watch's install fails: `DEVICE_LIMIT_REACHED`, `WATCH_BOUND_TO_ANOTHER_ACCOUNT` or `WATCH_KEY_CONFLICT` (each notified once), anything else `WATCH_INSTALL_FAILED`; all logged and retried after the wait with a new code.
- Every record stays readable through `PhoneWatchLinkStore.observe()`.

- [ ] **Step 1: Write the failing tests**

In `app/android-main/build.gradle.kts`, after `testImplementation(projects.client.logdateDatastore)` add:

```kotlin
    testImplementation(projects.client.networking)
    testImplementation(projects.shared.config)
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/FakeSessions.kt`:

```kotlin
package app.logdate.client.sync.v2

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A session store for the phone's version 2 tests, bound to one server. */
class FakeSessions(
    bound: OriginBoundSession?,
) : SessionStorage {
    private val state = MutableStateFlow(bound)
    var replaced: UserSession? = null

    var bound: OriginBoundSession?
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun getSession(): UserSession? = state.value?.session

    override fun getOriginBoundSession(): OriginBoundSession? = state.value

    override suspend fun replaceSessionIfCurrent(
        expected: OriginBoundSession,
        updated: UserSession,
    ): Boolean {
        if (state.value != expected) return false
        state.value = expected.copy(session = updated)
        replaced = updated
        return true
    }

    override fun getSessionFlow(): Flow<UserSession?> = MutableStateFlow(state.value?.session)

    override suspend fun hasValidSession(): Boolean = state.value != null

    override suspend fun saveSession(session: UserSession) {
        state.value = OriginBoundSession(state.value?.origin ?: "https://logdate.test", session)
    }

    override suspend fun clearSession() {
        state.value = null
    }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/LinkCodeMinterTest.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.DeviceLinkApiClientContract
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.cloud.RefreshedSession
import app.logdate.client.sync.cloud.SessionRefreshingAccountRepository
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.shared.model.devices.DeviceLinkCodeResponse
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import app.logdate.shared.model.devices.LinkedDevice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests [LinkCodeMinter]: a code is minted with the phone's own session, refreshed once when it expired. */
class LinkCodeMinterTest {
    private val sessions = FakeSessions(OriginBoundSession(ORIGIN, UserSession("access", "refresh", ACCOUNT)))
    private val links = FakeLinks()
    private val refresher = FakeRefresher()

    @Test
    fun `a signed-in phone mints a code with its access token`() =
        runTest {
            links.results += Result.success(CODE)

            assertEquals(CODE, LinkCodeMinter(links, sessions, refresher).mint().getOrThrow())
            assertEquals(listOf("access"), links.tokens)
        }

    @Test
    fun `an expired access token is refreshed once and the code minted`() =
        runTest {
            links.results += Result.failure(DeviceLinkApiException(401))
            links.results += Result.success(CODE)
            refresher.result = Result.success(RefreshedSession("new-access", "new-refresh"))

            assertEquals(CODE, LinkCodeMinter(links, sessions, refresher).mint().getOrThrow())
            assertEquals(listOf("access", "new-access"), links.tokens)
            assertEquals(UserSession("new-access", "new-refresh", ACCOUNT), sessions.replaced)
        }

    @Test
    fun `a phone that is not signed in mints nothing`() =
        runTest {
            sessions.bound = null

            assertTrue(LinkCodeMinter(links, sessions, refresher).mint().isFailure)
            assertTrue(links.tokens.isEmpty())
        }

    @Test
    fun `a refused refresh is the failure`() =
        runTest {
            links.results += Result.failure(DeviceLinkApiException(401))
            refresher.result = Result.failure(IllegalStateException("revoked"))

            assertTrue(LinkCodeMinter(links, sessions, refresher).mint().isFailure)
            assertEquals(listOf("access"), links.tokens)
        }

    private class FakeLinks : DeviceLinkApiClientContract {
        val tokens = mutableListOf<String>()
        val results = ArrayDeque<Result<DeviceLinkCodeResponse>>()

        override suspend fun mintLinkCode(accessToken: String): Result<DeviceLinkCodeResponse> {
            tokens += accessToken
            return results.removeFirst()
        }

        override suspend fun redeem(
            code: String,
            deviceName: String,
            platform: String,
        ): Result<DeviceLinkRedeemResponse> = error("The phone never redeems")

        override suspend fun listDevices(accessToken: String): Result<List<LinkedDevice>> = error("Not used")

        override suspend fun revoke(
            deviceId: String,
            accessToken: String,
        ): Result<Unit> = error("Not used")

        override suspend fun unlinkSelf(accessToken: String): Result<Unit> = error("Not used")
    }

    private class FakeRefresher : SessionRefreshingAccountRepository {
        var result: Result<RefreshedSession> = Result.failure(IllegalStateException("No refresh expected"))

        override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> = result
    }

    private companion object {
        const val ORIGIN = "https://logdate.test"
        const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"
        val CODE = DeviceLinkCodeResponse(code = "code-1", expiresAt = 9_000)
    }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/CloudLinkBundleSourceTest.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.DeviceLinkApiClientContract
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import app.logdate.shared.model.devices.DeviceLinkCodeResponse
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import app.logdate.shared.model.devices.LinkedDevice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Tests [CloudLinkBundleSource]: the phone links a capable watch to its account only when it is signed in
 * to a server that links devices, and the bundle contains what the watch needs and nothing more.
 */
class CloudLinkBundleSourceTest {
    private val sessions = FakeSessions(OriginBoundSession(ORIGIN, UserSession("access", "refresh", ACCOUNT)))
    private val config = DefaultLogDateConfigRepository(initialBackendUrl = ORIGIN)
    private val links = MintOnly()
    private val keys = FakeKeys()
    private val source = CloudLinkBundleSource(sessions, config, LinkCodeMinter(links, sessions, null), keys, v2Enabled = { true })

    @Test
    fun `a signed-in phone on a server that links devices links a capable watch to its account`() =
        runTest {
            config.updateServerDescriptor(descriptor(linking = true))

            assertEquals(LinkPlan(LinkMode.CLOUD, ACCOUNT), source.plan(setOf(WearFeatures.LINK_CLOUD)))
        }

    @Test
    fun `a watch that cannot link to an account is paired`() =
        runTest {
            config.updateServerDescriptor(descriptor(linking = true))

            assertEquals(LinkPlan(LinkMode.PAIR), source.plan(emptySet()))
        }

    @Test
    fun `a server that does not link devices means a pairing`() =
        runTest {
            config.updateServerDescriptor(descriptor(linking = false))

            assertEquals(LinkPlan(LinkMode.PAIR), source.plan(setOf(WearFeatures.LINK_CLOUD)))
        }

    @Test
    fun `a phone with no account pairs`() =
        runTest {
            config.updateServerDescriptor(descriptor(linking = true))
            sessions.bound = null

            assertEquals(LinkPlan(LinkMode.PAIR), source.plan(setOf(WearFeatures.LINK_CLOUD)))
        }

    @Test
    fun `an account bundle contains the code the server and the keys`() =
        runTest {
            config.updateServerDescriptor(descriptor(linking = true))
            links.result = Result.success(DeviceLinkCodeResponse(code = "code-1", expiresAt = 9_000))

            val bundle = assertIs<LinkBundleResult.Ready>(source.bundle(LinkPlan(LinkMode.CLOUD, ACCOUNT))).bundle

            assertEquals(LinkMode.CLOUD, bundle.mode)
            assertEquals(ACCOUNT, bundle.accountId)
            assertEquals(ORIGIN, bundle.serverOrigin)
            assertNotNull(bundle.serverDescriptor)
            assertEquals("code-1", bundle.linkCode)
            assertEquals(9_000, bundle.linkCodeExpiresAt)
            assertContentEquals(keys.identity, LinkWire.decodeKey(assertNotNull(bundle.identityKey)))
            assertEquals("fingerprint", bundle.identityKeyFingerprint)
            assertContentEquals(keys.media, LinkWire.decodeKey(assertNotNull(bundle.mediaKey)))
            assertEquals(mapOf("watch_experience_v2_enabled" to true), bundle.flags)
        }

    @Test
    fun `an account with no room for another device is reported`() =
        runTest {
            links.result = Result.failure(DeviceLinkApiException(409, DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED))

            assertEquals(
                LinkBundleResult.Unavailable(PhoneLinkFailure.DEVICE_LIMIT_REACHED),
                source.bundle(LinkPlan(LinkMode.CLOUD, ACCOUNT)),
            )
        }

    @Test
    fun `a phone that signed out since the plan sends nothing`() =
        runTest {
            sessions.bound = null

            assertEquals(LinkBundleResult.Unavailable(PhoneLinkFailure.SIGNED_OUT), source.bundle(LinkPlan(LinkMode.CLOUD, ACCOUNT)))
        }

    private fun descriptor(linking: Boolean) =
        ServerDescriptor(
            serverOrigin = ORIGIN,
            apiBaseUrl = "$ORIGIN/api/v1",
            deploymentKind = DeploymentKind.FIRST_PARTY,
            displayName = "LogDate",
            protocolFeatures = if (linking) listOf(ServerProtocolFeature.DEVICE_LINKING_V1) else emptyList(),
        )

    private class FakeKeys : AccountKeySource {
        val identity = ByteArray(32) { 1 }
        val media = ByteArray(32) { 2 }

        override suspend fun identityKey(): ByteArray = identity

        override fun fingerprint(identityKey: ByteArray): String = "fingerprint"

        override suspend fun mediaKey(): ByteArray = media
    }

    private class MintOnly : DeviceLinkApiClientContract {
        var result: Result<DeviceLinkCodeResponse> = Result.failure(IllegalStateException("No code expected"))

        override suspend fun mintLinkCode(accessToken: String): Result<DeviceLinkCodeResponse> = result

        override suspend fun redeem(
            code: String,
            deviceName: String,
            platform: String,
        ): Result<DeviceLinkRedeemResponse> = error("The phone never redeems")

        override suspend fun listDevices(accessToken: String): Result<List<LinkedDevice>> = error("Not used")

        override suspend fun revoke(
            deviceId: String,
            accessToken: String,
        ): Result<Unit> = error("Not used")

        override suspend fun unlinkSelf(accessToken: String): Result<Unit> = error("Not used")
    }

    private companion object {
        const val ORIGIN = "https://logdate.test"
        const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"
    }
}
```

In `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecisionTest.kt`, add:

```kotlin
    @Test
    fun `a watch that could not install the link records why`() {
        val cloud = linked.copy(mode = LinkMode.CLOUD, accountId = "account", confirmedAt = null)
        val failed =
            StateDocument(
                role = PeerRole.WATCH,
                writtenAt = 200,
                watch = WatchState(link = PeerLinkState.NEEDS_RELINK, linkProblem = "DEVICE_LIMIT_REACHED"),
            )

        assertEquals(
            PhoneLinkDecision.WatchProblem(PhoneLinkFailure.DEVICE_LIMIT_REACHED),
            PhoneLinkDecision.decide(cloud, LinkPlan(LinkMode.CLOUD, "account"), failed, now = 1_000),
        )
    }

    @Test
    fun `a problem this phone does not know is an install failure`() {
        val failed =
            StateDocument(
                role = PeerRole.WATCH,
                writtenAt = 200,
                watch = WatchState(link = PeerLinkState.NEEDS_RELINK, linkProblem = "SOMETHING_NEW"),
            )

        assertEquals(
            PhoneLinkDecision.WatchProblem(PhoneLinkFailure.WATCH_INSTALL_FAILED),
            PhoneLinkDecision.decide(linked, pair, failed, now = 1_000),
        )
    }

    @Test
    fun `a problem already recorded waits for its next attempt`() {
        val recorded = linked.copy(failure = PhoneLinkFailure.DEVICE_LIMIT_REACHED, nextAttemptAt = 5_000, seenWatchDocAt = 200)
        val failed =
            StateDocument(
                role = PeerRole.WATCH,
                writtenAt = 200,
                watch = WatchState(link = PeerLinkState.NEEDS_RELINK, linkProblem = "DEVICE_LIMIT_REACHED"),
            )

        assertEquals(PhoneLinkDecision.Wait, PhoneLinkDecision.decide(recorded, pair, failed, now = 1_000))
    }
```

In `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiatorTest.kt`, change `private fun initiator(enabled: Boolean = true)` to `private fun initiator(enabled: Boolean = true, bundles: LinkBundleSource = PairOnlyBundleSource)` passing `bundles = bundles,` to the constructor, add `import app.logdate.client.sync.datalayer.v2.LinkBundle`, and add:

```kotlin
    private val cloud =
        object : LinkBundleSource {
            override fun changes() = kotlinx.coroutines.flow.flowOf(Unit)

            override suspend fun plan(watchFeatures: Set<String>) = LinkPlan(LinkMode.CLOUD, ACCOUNT)

            override suspend fun bundle(plan: LinkPlan) = LinkBundleResult.Ready(LinkBundle(mode = LinkMode.CLOUD, accountId = ACCOUNT))
        }

    @Test
    fun `an account link is confirmed only once the watch reports it`() =
        runTest {
            val initiator = initiator(bundles = cloud)
            initiator.reconcile(emptyMap())
            assertTrue(notifier.events.isEmpty())

            initiator.reconcile(mapOf(WATCH to watchDocument(PeerLinkState.LINKED, problem = null, at = clock.millis + 1)))

            assertEquals(listOf("linked Pixel Watch 3 CLOUD"), notifier.events)
        }

    @Test
    fun `a watch that could not install the link is recorded and shown once`() =
        runTest {
            val initiator = initiator(bundles = cloud)
            initiator.reconcile(emptyMap())
            val failed = mapOf(WATCH to watchDocument(PeerLinkState.NEEDS_RELINK, problem = "DEVICE_LIMIT_REACHED", at = clock.millis + 1))

            initiator.reconcile(failed)
            initiator.reconcile(failed)

            assertEquals(PhoneLinkFailure.DEVICE_LIMIT_REACHED, store.get(WATCH)?.failure)
            assertEquals(listOf("attention Pixel Watch 3 DEVICE_LIMIT_REACHED"), notifier.events)
            assertEquals(2, watch.requests.size)
        }

    private fun watchDocument(
        link: PeerLinkState,
        problem: String?,
        at: Long,
    ) = StateDocument(role = PeerRole.WATCH, writtenAt = at, watch = WatchState(link = link, linkProblem = problem))
```

and add `const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"` to its companion.

In `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt`, add `import app.logdate.client.sync.datalayer.v2.CloudPresence` and:

```kotlin
    @Test
    fun `the phone lists the features it supports`() =
        runTest {
            val withFeatures =
                PhoneStatePublisher(
                    v2Enabled = enabled,
                    channel = channel,
                    capabilities = capabilities,
                    peerStates = peerStates,
                    clock = FixedClock(1_000),
                    timeZone = { TimeZone.of("Europe/Paris") },
                    features = setOf("link.cloud.v1"),
                )

            withFeatures.start(backgroundScope)
            runCurrent()

            assertEquals(setOf("link.cloud.v1"), channel.published.last().phone?.features)
        }

    @Test
    fun `signing in publishes the account presence without advertising again`() =
        runTest {
            val parts = MutableStateFlow(PhoneStateParts(cloud = CloudPresence.NO_ACCOUNT))
            val withParts =
                PhoneStatePublisher(
                    v2Enabled = enabled,
                    channel = channel,
                    capabilities = capabilities,
                    peerStates = peerStates,
                    clock = FixedClock(1_000),
                    timeZone = { TimeZone.of("Europe/Paris") },
                    parts = parts,
                )
            withParts.start(backgroundScope)
            runCurrent()

            parts.value = PhoneStateParts(cloud = CloudPresence.SIGNED_IN)
            runCurrent()

            assertEquals(CloudPresence.SIGNED_IN, channel.published.last().phone?.cloud)
            assertEquals(listOf("remove $PHONE_V2"), capabilities.calls)
        }
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.*' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'LinkCodeMinter'", "Unresolved reference 'CloudLinkBundleSource'", "Unresolved reference 'WatchProblem'", "Unresolved reference 'PhoneStateParts'".

- [ ] **Step 3: Implement**

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkCodeMinter.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.networking.DeviceLinkApiClientContract
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.cloud.SessionRefreshingAccountRepository
import app.logdate.shared.model.devices.DeviceLinkCodeResponse
import io.github.aakira.napier.Napier

/**
 * Mints a one-time link code with this phone's own session. An access token that expired is refreshed
 * once; the refreshed session is saved only if it is still the one the phone has.
 */
class LinkCodeMinter(
    private val links: DeviceLinkApiClientContract,
    private val sessions: SessionStorage,
    private val refresher: SessionRefreshingAccountRepository?,
) {
    suspend fun mint(): Result<DeviceLinkCodeResponse> {
        val bound = sessions.getOriginBoundSession() ?: return Result.failure(IllegalStateException("This phone is not signed in"))
        val first = links.mintLinkCode(bound.session.accessToken)
        val refused = first.exceptionOrNull() as? DeviceLinkApiException ?: return first
        if (refused.status != HTTP_UNAUTHORIZED || refresher == null) return first
        val refreshed =
            refresher.refreshSession(bound.session.refreshToken).getOrElse { error ->
                Napier.w("Refreshing this phone's session to mint a link code failed", error)
                return Result.failure(error)
            }
        val updated =
            bound.session.copy(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken ?: bound.session.refreshToken,
            )
        if (!sessions.replaceSessionIfCurrent(bound, updated)) {
            return Result.failure(IllegalStateException("The session changed while minting a link code"))
        }
        return links.mintLinkCode(updated.accessToken)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/CloudLinkBundleSource.kt`:

```kotlin
package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The account keys a phone hands to a watch it links. Never the recovery phrase. */
interface AccountKeySource {
    suspend fun identityKey(): ByteArray?

    fun fingerprint(identityKey: ByteArray): String

    suspend fun mediaKey(): ByteArray
}

class DeviceAccountKeySource(
    private val identityKeys: IdentityKeyManager,
    private val mediaKeys: MediaPayloadKeyProvider,
    private val cryptoManager: CryptoManager,
) : AccountKeySource {
    override suspend fun identityKey(): ByteArray? = if (identityKeys.hasIdentityKey()) identityKeys.getIdentityKey() else null

    override fun fingerprint(identityKey: ByteArray): String = IdentityKeyFingerprint.of(identityKey, cryptoManager)

    override suspend fun mediaKey(): ByteArray = mediaKeys.getOrCreateKey()
}

/**
 * Links a watch to this phone's account when the phone is signed in to a server that links devices,
 * has an identity key, and the watch can take an account link; otherwise pairs it.
 */
class CloudLinkBundleSource(
    private val sessions: SessionStorage,
    private val config: LogDateConfigRepository,
    private val minter: LinkCodeMinter,
    private val keys: AccountKeySource,
    private val v2Enabled: suspend () -> Boolean,
) : LinkBundleSource {
    override fun changes(): Flow<Unit> = combine(sessions.getSessionFlow(), config.serverDescriptor) { _, _ -> }

    override suspend fun plan(watchFeatures: Set<String>): LinkPlan {
        val bound = sessions.getOriginBoundSession() ?: return LinkPlan(LinkMode.PAIR)
        if (!serverLinksDevices() || WearFeatures.LINK_CLOUD !in watchFeatures) return LinkPlan(LinkMode.PAIR)
        if (keys.identityKey() == null) return LinkPlan(LinkMode.PAIR)
        return LinkPlan(LinkMode.CLOUD, bound.session.accountId)
    }

    override suspend fun bundle(plan: LinkPlan): LinkBundleResult {
        val flags = mapOf(FeatureFlag.WATCH_EXPERIENCE_V2.key to v2Enabled())
        if (plan.mode == LinkMode.PAIR) return LinkBundleResult.Ready(LinkBundle(mode = LinkMode.PAIR, flags = flags))
        val bound = sessions.getOriginBoundSession()
        if (bound == null || bound.session.accountId != plan.accountId) return LinkBundleResult.Unavailable(PhoneLinkFailure.SIGNED_OUT)
        val identity = keys.identityKey() ?: return LinkBundleResult.Unavailable(PhoneLinkFailure.KEYS_UNAVAILABLE)
        val media = mediaKeyOrNull() ?: return LinkBundleResult.Unavailable(PhoneLinkFailure.KEYS_UNAVAILABLE)
        val code = minter.mint().getOrElse { error -> return LinkBundleResult.Unavailable(mintFailure(error)) }
        val descriptor = config.getCurrentServerDescriptor()?.let { WearJson.json.encodeToJsonElement(ServerDescriptor.serializer(), it) }
        return LinkBundleResult.Ready(
            LinkBundle(
                mode = LinkMode.CLOUD,
                accountId = bound.session.accountId,
                serverOrigin = bound.origin,
                serverDescriptor = descriptor,
                linkCode = code.code,
                linkCodeExpiresAt = code.expiresAt,
                identityKey = LinkWire.encodeKey(identity),
                identityKeyFingerprint = keys.fingerprint(identity),
                mediaKey = LinkWire.encodeKey(media),
                flags = flags,
            ),
        )
    }

    private suspend fun mediaKeyOrNull(): ByteArray? =
        try {
            keys.mediaKey()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("This phone's media key could not be read; the watch is linked once it can", e)
            null
        }

    private fun serverLinksDevices(): Boolean =
        config.getCurrentServerDescriptor()?.hasProtocolFeature(ServerProtocolFeature.DEVICE_LINKING_V1) == true

    private fun mintFailure(error: Throwable): PhoneLinkFailure {
        Napier.w("A link code could not be minted for the watch", error)
        return when ((error as? DeviceLinkApiException)?.code) {
            DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED -> PhoneLinkFailure.DEVICE_LIMIT_REACHED
            else -> PhoneLinkFailure.LINK_CODE_UNAVAILABLE
        }
    }
}
```

In `PhoneWatchLinkStore.kt`, replace the `PhoneLinkFailure` entries with:

```kotlin
    PHONE_NODE_UNKNOWN(false),
    WATCH_UNREACHABLE(false),
    WATCH_CANNOT_LINK(false),
    OFFER_EXPIRED(false),
    ANSWER_UNREADABLE(false),
    CONFIRMATION_MISMATCH(false),
    WATCH_BOUND_TO_ANOTHER_ACCOUNT(true),
    BUNDLE_UNAVAILABLE(false),
    SIGNED_OUT(false),
    LINK_CODE_UNAVAILABLE(false),
    KEYS_UNAVAILABLE(false),
    DEVICE_LIMIT_REACHED(true),
    WATCH_KEY_CONFLICT(true),
    WATCH_INSTALL_FAILED(false),
```

In `PhoneLinkDecision.kt`, add after `Confirm`:

```kotlin
    /** The watch could not install the phone's last account link; the phone records why and waits. */
    data class WatchProblem(
        val failure: PhoneLinkFailure,
    ) : PhoneLinkDecision
```

in `decide`, after `val fresh = ...` add:

```kotlin
            val problem = watch?.linkProblem
            if (fresh && watch != null && watch.link == PeerLinkState.NEEDS_RELINK && problem != null) {
                return WatchProblem(failureFor(problem))
            }
```

and in the companion add (with `import app.logdate.client.sync.peer.link.LinkProblem`):

```kotlin
        private fun failureFor(problem: String): PhoneLinkFailure =
            when (problem) {
                LinkProblem.DEVICE_LIMIT_REACHED.name -> PhoneLinkFailure.DEVICE_LIMIT_REACHED
                LinkProblem.BOUND_TO_ANOTHER_ACCOUNT.name -> PhoneLinkFailure.WATCH_BOUND_TO_ANOTHER_ACCOUNT
                LinkProblem.KEY_CONFLICT.name -> PhoneLinkFailure.WATCH_KEY_CONFLICT
                else -> PhoneLinkFailure.WATCH_INSTALL_FAILED
            }
```

In `PhoneLinkInitiator.kt`, replace the `when` in `reconcileTarget` with:

```kotlin
        when (val decision = PhoneLinkDecision.decide(record, plan, document, now)) {
            PhoneLinkDecision.Wait -> Unit
            PhoneLinkDecision.Exchange -> runExchange(target, plan, document, now)
            PhoneLinkDecision.Confirm -> confirm(target, checkNotNull(record).mode, checkNotNull(document), now)
            is PhoneLinkDecision.WatchProblem -> recordWatchProblem(target, plan, decision.failure, checkNotNull(document), now)
        }
```

and add:

```kotlin
    private suspend fun recordWatchProblem(
        target: LinkTarget,
        plan: LinkPlan,
        failure: PhoneLinkFailure,
        document: StateDocument,
        now: Long,
    ) {
        val record = store.recordFailure(target, plan, failure, at = now, seenWatchDocAt = document.writtenAt)
        Napier.w("${target.displayName} could not install the account link: $failure; it is tried again after ${record.nextAttemptAt}")
        if (failure.needsAttention && record.attempts == 1) notifier.needsAttention(target, failure)
    }
```

In `WatchLinkNotifier.kt`, replace `attentionCopy` with:

```kotlin
    private fun attentionCopy(failure: PhoneLinkFailure): Pair<Int, Int>? =
        when (failure) {
            PhoneLinkFailure.WATCH_BOUND_TO_ANOTHER_ACCOUNT, PhoneLinkFailure.WATCH_KEY_CONFLICT ->
                NotificationResources.string.notification_watch_other_account_title to
                    NotificationResources.string.notification_watch_other_account_body
            PhoneLinkFailure.DEVICE_LIMIT_REACHED ->
                NotificationResources.string.notification_watch_device_limit_title to
                    NotificationResources.string.notification_watch_device_limit_body
            else -> null
        }
```

In `client/notifications/src/androidMain/res/values/strings.xml`, after `notification_watch_other_account_body` add:

```xml
    <string name="notification_watch_device_limit_title">Backup is not set up on %1$s</string>
    <string name="notification_watch_device_limit_body">This account has the most linked devices it can have. Remove one in Settings, then Devices.</string>
```

Replace `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt` with:

```kotlin
package app.logdate.client.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.CampfireSnapshot
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** The parts of this phone's state document that change while the app runs, apart from the switch. */
data class PhoneStateParts(
    val cloud: CloudPresence = CloudPresence.UNKNOWN,
    val dayStartHour: Int = 0,
    val campfire: CampfireSnapshot? = null,
)

/**
 * Publishes this phone's state document whenever the Labs watch switch or one of its [parts] changes,
 * and advertises [WearProtocol.PHONE_CAPABILITY_V2] only while the switch is on.
 *
 * The document goes out whatever the switch says, because the watch takes the switch from it. It lists
 * the version 2 [features] this build implements.
 */
class PhoneStatePublisher(
    private val v2Enabled: Flow<Boolean>,
    private val channel: StateDocumentChannel,
    private val capabilities: LocalCapabilities,
    private val peerStates: PeerStateStore,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val features: Set<String> = emptySet(),
    private val parts: Flow<PhoneStateParts> = flowOf(PhoneStateParts()),
) {
    private var advertised: Boolean? = null

    fun start(scope: CoroutineScope): Job =
        scope.launch {
            catchUpWithWatches()
            combine(v2Enabled.distinctUntilChanged(), parts.distinctUntilChanged()) { enabled, current -> enabled to current }
                .collect { (enabled, current) -> publish(enabled, current) }
        }

    /** Reads the documents the watches published while this phone's app was not running. */
    suspend fun catchUpWithWatches() {
        channel.peerDocuments().forEach { peer -> peerStates.accept(peer.nodeId, peer.decoded, PeerRole.WATCH) }
    }

    suspend fun publish(
        v2Enabled: Boolean,
        parts: PhoneStateParts = PhoneStateParts(),
    ) {
        val document =
            StateDocument(
                role = PeerRole.PHONE,
                writtenAt = clock.now().toEpochMilliseconds(),
                phone =
                    PhoneState(
                        flags = mapOf(FeatureFlag.WATCH_EXPERIENCE_V2.key to v2Enabled),
                        cloud = parts.cloud,
                        campfire = parts.campfire,
                        dayStartHour = parts.dayStartHour,
                        zone = timeZone().id,
                        features = features,
                    ),
            )
        if (!channel.publish(document)) {
            Napier.e("Could not publish this phone's state; watches use the old one until the next change or restart")
        }
        if (advertised == v2Enabled) return
        val capabilityUpdated =
            if (v2Enabled) {
                capabilities.add(WearProtocol.PHONE_CAPABILITY_V2)
            } else {
                capabilities.remove(WearProtocol.PHONE_CAPABILITY_V2)
            }
        if (capabilityUpdated) {
            advertised = v2Enabled
            return
        }
        Napier.e("Could not update ${WearProtocol.PHONE_CAPABILITY_V2}; watches use the old protocol until the next change or restart")
    }
}
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt`, delete the `single { PhoneStatePublisher(...) }` block (`:228-235`) and its import.

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`, add the bindings:

```kotlin
        single<DeviceLinkApiClientContract> { DeviceLinkApiClient(get(), get()) }
        single {
            LinkCodeMinter(
                links = get(),
                sessions = get(),
                refresher = get<CloudAccountRepository>() as? SessionRefreshingAccountRepository,
            )
        }
        single<AccountKeySource> { DeviceAccountKeySource(get(), get(), get()) }
        single<LinkBundleSource> {
            val flags = get<FeatureFlagStore>()
            CloudLinkBundleSource(
                sessions = get(),
                config = get(),
                minter = get(),
                keys = get(),
                v2Enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
            )
        }
        single {
            val sessions = get<SessionStorage>()
            PhoneStatePublisher(
                v2Enabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                channel = get(),
                capabilities = get(),
                peerStates = get(),
                features = setOf(WearFeatures.LINK_CLOUD),
                parts = sessions.getSessionFlow().map { session -> PhoneStateParts(cloud = cloudPresence(session)) },
            )
        }
```

add `bundles = get(),` to the `PhoneLinkInitiator(` call, and add at the end of the file:

```kotlin
internal fun cloudPresence(session: UserSession?): CloudPresence =
    if (session == null) CloudPresence.NO_ACCOUNT else CloudPresence.SIGNED_IN
```

with imports `app.logdate.client.datastore.SessionStorage`, `app.logdate.client.datastore.UserSession`, `app.logdate.client.networking.DeviceLinkApiClient`, `app.logdate.client.networking.DeviceLinkApiClientContract`, `app.logdate.client.sync.cloud.SessionRefreshingAccountRepository`, `app.logdate.client.sync.datalayer.v2.CloudPresence`, `app.logdate.client.sync.datalayer.v2.WearFeatures`, `app.logdate.client.sync.v2.PhoneStateParts`, `app.logdate.client.sync.v2.PhoneStatePublisher`, `app.logdate.client.sync.v2.link.AccountKeySource`, `app.logdate.client.sync.v2.link.CloudLinkBundleSource`, `app.logdate.client.sync.v2.link.DeviceAccountKeySource`, `app.logdate.client.sync.v2.link.LinkBundleSource`, `app.logdate.client.sync.v2.link.LinkCodeMinter`, `app.logdate.shared.model.CloudAccountRepository` and `kotlinx.coroutines.flow.map`.

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again.
Expected: PASS (4 `LinkCodeMinterTest`, 7 `CloudLinkBundleSourceTest`, the new decision, initiator and publisher tests, and every existing test in those classes).

- [ ] **Step 5: Run every affected module's checks**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck :app:android-main:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task05-phone-account-link.txt <<'EOF'
chore(wear): send the account link from the phone to the watch

With the redesigned watch app on, a signed-in phone whose server links
devices now links a capable watch to its account instead of only
pairing it. It mints a one-time code as soon as the watch accepts,
refreshing its own session once if it expired, and sends the account,
server, code and keys, never the recovery phrase.

The plan is checked again when the phone signs in or out, the server
changes, a watch arrives or a watch asks to be linked again. The phone
confirms a link only once the watch reports it works, and shows one
notification for an account with no room for another device or a
watch that has another account. It now publishes whether it is
signed in and that it can link watches to an account.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/LinkCodeMinter.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/CloudLinkBundleSource.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneWatchLinkStore.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecision.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiator.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/link/WatchLinkNotifier.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/AppModule.android.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt client/notifications/src/androidMain/res/values/strings.xml app/android-main/build.gradle.kts app/android-main/src/test/kotlin/app/logdate/client/sync/v2/FakeSessions.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/LinkCodeMinterTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/CloudLinkBundleSourceTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkDecisionTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/link/PhoneLinkInitiatorTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task05-phone-account-link.txt
```

---

### Task 6: Treat the watch's Bluetooth link as metered (D3, network)

A watch near its phone reaches the internet through the phone over Bluetooth. That path is slow and the phone's own data, so it must count as metered: `DefaultDataUsagePolicy` makes it `Conservative` (text syncs, media waits), and Task 11's router never uploads directly over it. The Android provider now reports `TRANSPORT_BLUETOOTH` as `NetworkConnectionType.BLUETOOTH_PROXY` instead of `OTHER`, and whether the system validated the network (`NET_CAPABILITY_VALIDATED`), so a captive portal is not taken for internet. Phones are unaffected: they never route through Bluetooth for LogDate.

**Files:**
- Modify: `client/networking/src/commonMain/kotlin/app/logdate/client/networking/saver/NetworkSaverState.kt:6-21`
- Modify: `client/networking/src/commonMain/kotlin/app/logdate/client/networking/DefaultDataUsagePolicy.kt:37-43`
- Modify: `client/sensor/src/androidMain/kotlin/app/logdate/client/sensor/network/AndroidNetworkSaverModeProvider.kt:95-133`
- Test: `client/networking/src/commonTest/kotlin/app/logdate/client/networking/DefaultDataUsagePolicyTest.kt` (3 new tests)
- Test: `client/sensor/src/androidHostTest/kotlin/app/logdate/client/sensor/network/AndroidNetworkSaverModeProviderTest.kt` (2 new tests and a `validated` stub)

**Interfaces:**
- Consumes: `NetworkCapabilities.TRANSPORT_BLUETOOTH`, `NetworkCapabilities.NET_CAPABILITY_VALIDATED`.
- Produces: `NetworkConnectionType.BLUETOOTH_PROXY`; `NetworkSaverState(isDataSaverEnabled, connectionType, isMetered = connectionType == CELLULAR || connectionType == BLUETOOTH_PROXY, isValidated: Boolean = connectionType != NONE)`.

**Failure modes and how they surface:** a capability the system cannot read counts as not validated (the safe answer: no direct upload) and the transport as `OTHER`, as before. There is no exhaustive `when` over `NetworkConnectionType` in the repository (checked with `grep -rn "NetworkConnectionType\." --include='*.kt'`), so the new value breaks no build; iOS and desktop never produce it.

- [ ] **Step 1: Write the failing tests**

In `client/networking/src/commonTest/kotlin/app/logdate/client/networking/DefaultDataUsagePolicyTest.kt`, add:

```kotlin
    @Test
    fun `a watch's Bluetooth link through its phone is conservative`() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.BLUETOOTH_PROXY),
            )

            assertIs<DataUsageMode.Conservative>(policy.currentMode())
        }

    @Test
    fun `the Bluetooth link with data saver on is restricted`() =
        runTest {
            networkSaverProvider.setNetworkSaverState(
                NetworkSaverState(isDataSaverEnabled = true, connectionType = NetworkConnectionType.BLUETOOTH_PROXY),
            )

            assertIs<DataUsageMode.Restricted>(policy.currentMode())
        }

    @Test
    fun `a network is validated unless there is none`() {
        assertTrue(NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.WIFI).isValidated)
        assertFalse(NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.NONE).isValidated)
    }
```

In `client/sensor/src/androidHostTest/kotlin/app/logdate/client/sensor/network/AndroidNetworkSaverModeProviderTest.kt`, change `stubActiveNetwork` to:

```kotlin
    private fun stubActiveNetwork(
        notRestricted: Boolean,
        transport: Int = NetworkCapabilities.TRANSPORT_CELLULAR,
        validated: Boolean = true,
    ) {
        every { connectivityManager.activeNetwork } returns activeNetwork
        every { connectivityManager.getNetworkCapabilities(activeNetwork) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) } returns notRestricted
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
        every { capabilities.hasTransport(any()) } returns false
        every { capabilities.hasTransport(transport) } returns true
    }
```

and add:

```kotlin
    @Test
    fun `a watch's Bluetooth link through its phone is reported as the proxy`() =
        runTest {
            stubBackgroundRestriction(enabled = false)
            stubActiveNetwork(notRestricted = true, transport = NetworkCapabilities.TRANSPORT_BLUETOOTH)

            assertEquals(NetworkConnectionType.BLUETOOTH_PROXY, buildProvider().getCurrentDataSaverState().connectionType)
        }

    @Test
    fun `a network the system has not validated is reported as such`() =
        runTest {
            stubBackgroundRestriction(enabled = false)
            stubActiveNetwork(notRestricted = true, transport = NetworkCapabilities.TRANSPORT_WIFI, validated = false)

            assertFalse(buildProvider().getCurrentDataSaverState().isValidated)
        }
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:networking:jvmTest --tests 'app.logdate.client.networking.DefaultDataUsagePolicyTest' :client:sensor:testAndroidHostTest --tests 'app.logdate.client.sensor.network.AndroidNetworkSaverModeProviderTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'BLUETOOTH_PROXY'", "Unresolved reference 'isValidated'".

- [ ] **Step 3: Implement**

Replace `client/networking/src/commonMain/kotlin/app/logdate/client/networking/saver/NetworkSaverState.kt` with:

```kotlin
package app.logdate.client.networking.saver

/**
 * Represents Android's background-data status and the active network's transport and cost.
 *
 * @property isValidated Whether the system confirmed the network reaches the internet; a captive portal
 *   or a dead access point is connected but not validated.
 */
data class NetworkSaverState(
    val isDataSaverEnabled: Boolean,
    val connectionType: NetworkConnectionType,
    val isMetered: Boolean = connectionType == NetworkConnectionType.CELLULAR || connectionType == NetworkConnectionType.BLUETOOTH_PROXY,
    val isValidated: Boolean = connectionType != NetworkConnectionType.NONE,
)

/**
 * Represents the type of network connection the device is currently using.
 */
enum class NetworkConnectionType {
    NONE,
    CELLULAR,
    WIFI,
    ETHERNET,
    OTHER,

    /** A watch's connection through its phone over Bluetooth: slow, and the phone's own data. */
    BLUETOOTH_PROXY,
}
```

In `client/networking/src/commonMain/kotlin/app/logdate/client/networking/DefaultDataUsagePolicy.kt`, replace `toDataUsageMode` with:

```kotlin
internal fun NetworkSaverState.toDataUsageMode(): DataUsageMode =
    when {
        connectionType == NetworkConnectionType.NONE -> DataUsageMode.Restricted
        isDataSaverEnabled && isMetered -> DataUsageMode.Restricted
        connectionType == NetworkConnectionType.BLUETOOTH_PROXY -> DataUsageMode.Conservative
        isMetered -> DataUsageMode.Conservative
        else -> DataUsageMode.Unrestricted
    }
```

and add to the class KDoc's mapping list: `- A watch's Bluetooth link through its phone → [DataUsageMode.Conservative], metered or not`.

In `client/sensor/src/androidMain/kotlin/app/logdate/client/sensor/network/AndroidNetworkSaverModeProvider.kt`, in `getCurrentNetworkSaverStateInternal()` pass `isValidated = activeCapabilities.isValidated(),` after `isMetered = ...`; in `toConnectionType()` add `hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkConnectionType.BLUETOOTH_PROXY` after the `TRANSPORT_ETHERNET` branch; and add:

```kotlin
    /** Unknown counts as not validated, so a network that cannot be checked is never trusted for uploads. */
    private fun NetworkCapabilities?.isValidated(): Boolean {
        if (this == null) return false
        return try {
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }
```

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again.
Expected: PASS, including every existing test in both classes.

- [ ] **Step 5: Run every affected module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:networking:jvmTest :client:networking:testAndroidHostTest :client:networking:compileKotlinIosSimulatorArm64 :client:networking:compileTestKotlinIosSimulatorArm64 :client:networking:ktlintCheck :client:networking:detekt :client:sensor:jvmTest :client:sensor:testAndroidHostTest :client:sensor:compileKotlinIosSimulatorArm64 :client:sensor:ktlintCheck :client:sensor:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task06-bluetooth-proxy.txt <<'EOF'
chore(networking): treat the watch's Bluetooth link as metered

A watch near its phone reaches the internet through the phone over
Bluetooth. That connection is now reported as its own type and counts
as metered: text syncs over it and media waits for Wi-Fi.

The network state also says whether the system validated the network,
so a captive portal is not mistaken for internet; a capability that
cannot be read counts as not validated. Phones never use the Bluetooth
link for LogDate, so nothing changes for them.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/networking/src/commonMain/kotlin/app/logdate/client/networking/saver/NetworkSaverState.kt client/networking/src/commonMain/kotlin/app/logdate/client/networking/DefaultDataUsagePolicy.kt client/sensor/src/androidMain/kotlin/app/logdate/client/sensor/network/AndroidNetworkSaverModeProvider.kt client/networking/src/commonTest/kotlin/app/logdate/client/networking/DefaultDataUsagePolicyTest.kt client/sensor/src/androidHostTest/kotlin/app/logdate/client/sensor/network/AndroidNetworkSaverModeProviderTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task06-bluetooth-proxy.txt
```

---

### Task 7: Add the watch's own cloud engine (D3, engine)

`wearCloudSyncModule` builds the second `DefaultSyncManager`, qualified `named("watchCloud")`, with `SyncProfile.LinkedWatch(RecentCaptureFilter(14.days))` and its own stores: dead letters, retry times, conflicts, last error, first-sync marks, recovery and unreadable-record stores all live under the `watch_cloud.` key prefix through a new `PrefixedKeyValueStorage`, so they never mix with the version 1 Data Layer manager's stores of the same types. It reads the shared outbox through `RoutedSyncMetadataService` with a `CloudEligibility` that admits nothing yet (Task 11 replaces it), so building the engine changes nothing; nothing runs it until Task 11. A revoked session is recorded as `LinkProblem.SESSION_REVOKED` in the link record; erasing the watch is D10's.

`:app:wear` gains `:client:sensor` for `AndroidNetworkSaverModeProvider` and `AndroidBatteryInfoProvider`. A Gradle Managed Device test resolves the whole graph on a real watch image, which is where a missing binding would otherwise first show up.

**Files:**
- Create: `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/PrefixedKeyValueStorage.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/di/WearCloudSyncModule.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` (module list)
- Modify: `app/wear/build.gradle.kts` (`implementation(projects.client.sensor)`)
- Test: `client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/PrefixedKeyValueStorageTest.kt`
- Test: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearKoinGraphTest.kt`

**Interfaces:**
- Consumes: Task 4's `wearIdentityModule` bindings and `WatchLinkStore.recordProblem`; Task 6's provider; `DefaultSyncManager(...)` (`DefaultSyncManager.kt:62-116`); `SyncProfile.LinkedWatch`, `RecentCaptureFilter`, `RoutedSyncMetadataService`, `CloudEligibility`, `DeviceSessionRevocationListener`; `LogDateCloudApiClient(configRepository, httpClient, sessionStorage)`; `DurableCloudApiClient`, `DownloadInbox`, `DownloadScope`, `SyncPayloadCipher(contentEncryptionService, identityKeyManager, cryptoManager)`, `ContentEncryptionService(identityKeyManager, keyDerivation, cryptoManager)`, `StoredMediaPayloadCrypto`, the `KeyValue*Store`s, `RoomSyncTransactionManager(database)`, `OfflineFirstJournalContentRepository`, `DefaultCloudAccountRepository`, `AndroidMediaManager(contentResolver, context)`, `SyncQualifiers`.
- Produces:
  - `class PrefixedKeyValueStorage(delegate: KeyValueStorage, prefix: String) : KeyValueStorage`
  - `val WatchCloud: Qualifier` (`named("watchCloud")`), `val wearCloudSyncModule: Module`
  - Koin: `DefaultSyncManager` qualified `WatchCloud`; `CloudEligibility` qualified `WatchCloud`; `NetworkSaverModeProvider`, `DataUsagePolicy`, `BatteryInfoProvider`, `MediaManager` unqualified

**Failure modes and how they surface:**
- No session or no identity key from the phone: the engine reports `SyncPausedReason.NEEDS_DEVICE_LINK` (asserted on the managed device).
- The server revokes the watch's session: `SyncTokenRefresher` clears it and the listener records `SESSION_REVOKED`; the watch publishes `NEEDS_RELINK` with that problem, and the phone records `WATCH_INSTALL_FAILED` and waits.
- A missing Koin binding: `WearKoinGraphTest` fails before the commit.
- `clear()` on a prefixed store throws `UnsupportedOperationException` instead of erasing every other store's values.

- [ ] **Step 1: Write the failing tests**

Create `client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/PrefixedKeyValueStorageTest.kt`:

```kotlin
package app.logdate.client.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Tests [PrefixedKeyValueStorage]: a second store under a prefix never reads or overwrites the first. */
class PrefixedKeyValueStorageTest {
    private val shared = MapStorage()
    private val prefixed = PrefixedKeyValueStorage(shared, "watch_cloud.")

    @Test
    fun `values live under the prefix`() =
        runTest {
            prefixed.putString("retry", "a")
            prefixed.putLong("since", 9)

            assertEquals("a", shared.strings.value["watch_cloud.retry"])
            assertEquals("a", prefixed.getString("retry"))
            assertEquals(9, prefixed.getLong("since"))
        }

    @Test
    fun `the shared store's own values are not seen or removed`() =
        runTest {
            shared.putString("retry", "phone")

            assertNull(prefixed.getString("retry"))
            prefixed.remove("retry")
            assertEquals("phone", shared.getString("retry"))
        }

    @Test
    fun `observing reads the prefixed key`() =
        runTest {
            prefixed.putString("error", "x")

            assertEquals("x", prefixed.observeString("error").first())
        }

    @Test
    fun `clearing is refused because it would erase the shared store`() =
        runTest {
            assertFailsWith<UnsupportedOperationException> { prefixed.clear() }
        }

    /** Strings and longs in memory. */
    private class MapStorage : KeyValueStorage {
        val strings = MutableStateFlow<Map<String, String>>(emptyMap())
        private val longs = mutableMapOf<String, Long>()

        override suspend fun getString(key: String): String? = strings.value[key]

        override fun getStringSync(key: String): String? = strings.value[key]

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            strings.value = strings.value + (key to value)
        }

        override suspend fun getBoolean(
            key: String,
            defaultValue: Boolean,
        ): Boolean = defaultValue

        override suspend fun putBoolean(
            key: String,
            value: Boolean,
        ) = Unit

        override suspend fun getInt(
            key: String,
            defaultValue: Int,
        ): Int = defaultValue

        override suspend fun putInt(
            key: String,
            value: Int,
        ) = Unit

        override suspend fun getLong(
            key: String,
            defaultValue: Long,
        ): Long = longs[key] ?: defaultValue

        override suspend fun putLong(
            key: String,
            value: Long,
        ) {
            longs[key] = value
        }

        override suspend fun getFloat(
            key: String,
            defaultValue: Float,
        ): Float = defaultValue

        override suspend fun putFloat(
            key: String,
            value: Float,
        ) = Unit

        override suspend fun remove(key: String) {
            strings.value = strings.value - key
            longs.remove(key)
        }

        override suspend fun contains(key: String): Boolean = key in strings.value || key in longs

        override suspend fun clear() {
            strings.value = emptyMap()
            longs.clear()
        }

        override fun observeString(key: String): Flow<String?> = strings.map { it[key] }

        override fun observeBoolean(
            key: String,
            defaultValue: Boolean,
        ): Flow<Boolean> = MutableStateFlow(defaultValue)

        override fun observeInt(
            key: String,
            defaultValue: Int,
        ): Flow<Int> = MutableStateFlow(defaultValue)

        override fun observeLong(
            key: String,
            defaultValue: Long,
        ): Flow<Long> = MutableStateFlow(defaultValue)

        override fun observeFloat(
            key: String,
            defaultValue: Float,
        ): Flow<Float> = MutableStateFlow(defaultValue)
    }
}
```

Create `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearKoinGraphTest.kt`:

```kotlin
package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.metadata.CloudEligibility
import app.logdate.client.sync.peer.link.LinkInstaller
import app.logdate.wear.sync.v2.link.WatchLinkCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Resolves the watch's version 2 sync graph the way the installed app builds it, so a missing binding
 * fails here rather than on a watch.
 */
@RunWith(AndroidJUnit4::class)
class WearKoinGraphTest {
    private val koin = KoinJavaComponent.getKoin()

    @Test
    fun `the watch's own cloud engine resolves and waits to be linked`() {
        val engine = koin.get<DefaultSyncManager>(named("watchCloud"))

        assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, runBlocking { engine.getSyncStatus() }.pausedReason)
    }

    @Test
    fun `the link bindings resolve and the watch has no key of its own`() {
        koin.get<WatchLinkCoordinator>()
        koin.get<LinkInstaller>()
        koin.get<CloudEligibility>(named("watchCloud"))

        assertFalse(runBlocking { koin.get<IdentityKeyManager>().hasIdentityKey() })
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:logdate-datastore:jvmTest --tests 'app.logdate.client.datastore.PrefixedKeyValueStorageTest' --console=plain
```
Expected: FAIL, compilation error "Unresolved reference 'PrefixedKeyValueStorage'".

- [ ] **Step 3: Implement**

Create `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/PrefixedKeyValueStorage.kt`:

```kotlin
package app.logdate.client.datastore

import kotlinx.coroutines.flow.Flow

/**
 * A [KeyValueStorage] whose keys are [prefix] followed by the key, so a second copy of a store can share
 * [delegate] without reading or overwriting the first copy's values. Clearing would erase every other
 * store in [delegate] too, so it is refused.
 */
class PrefixedKeyValueStorage(
    private val delegate: KeyValueStorage,
    private val prefix: String,
) : KeyValueStorage {
    override suspend fun getString(key: String): String? = delegate.getString(prefix + key)

    override fun getStringSync(key: String): String? = delegate.getStringSync(prefix + key)

    override suspend fun putString(
        key: String,
        value: String,
    ) = delegate.putString(prefix + key, value)

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = delegate.getBoolean(prefix + key, defaultValue)

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) = delegate.putBoolean(prefix + key, value)

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = delegate.getInt(prefix + key, defaultValue)

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = delegate.putInt(prefix + key, value)

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = delegate.getLong(prefix + key, defaultValue)

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = delegate.putLong(prefix + key, value)

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = delegate.getFloat(prefix + key, defaultValue)

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = delegate.putFloat(prefix + key, value)

    override suspend fun remove(key: String) = delegate.remove(prefix + key)

    override suspend fun contains(key: String): Boolean = delegate.contains(prefix + key)

    override suspend fun clear(): Unit = throw UnsupportedOperationException("A prefixed store cannot clear the store it shares")

    override fun observeString(key: String): Flow<String?> = delegate.observeString(prefix + key)

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = delegate.observeBoolean(prefix + key, defaultValue)

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = delegate.observeInt(prefix + key, defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = delegate.observeLong(prefix + key, defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = delegate.observeFloat(prefix + key, defaultValue)
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/di/WearCloudSyncModule.kt`:

```kotlin
package app.logdate.wear.di

import android.content.Context
import app.logdate.client.data.journals.OfflineFirstJournalContentRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.PrefixedKeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.media.AndroidMediaManager
import app.logdate.client.media.MediaManager
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.DefaultDataUsagePolicy
import app.logdate.client.networking.httpClient
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.sensor.battery.AndroidBatteryInfoProvider
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.network.AndroidNetworkSaverModeProvider
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RecentCaptureFilter
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.SyncProfile
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.DeviceSessionRevocationListener
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.cloud.account.DefaultCloudAccountRepository
import app.logdate.client.sync.conflict.KeyValueSyncConflictStore
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.StoredMediaPayloadCrypto
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.di.SyncQualifiers
import app.logdate.client.sync.metadata.CloudEligibility
import app.logdate.client.sync.metadata.KeyValueFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.KeyValueIdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.KeyValueLastSyncErrorStore
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.KeyValueSyncDeadLetterStore
import app.logdate.client.sync.metadata.KeyValueSyncRetryScheduleStore
import app.logdate.client.sync.metadata.KeyValueUnreadableCloudRecordStore
import app.logdate.client.sync.metadata.RoutedSyncMetadataService
import app.logdate.client.sync.peer.link.LinkProblem
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.wear.sync.v2.link.WatchLinkStore
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** The qualifier of every binding that belongs to the watch's own cloud engine. */
val WatchCloud = named("watchCloud")

/** Notes the server sends a linked watch are stored only from this far back; the phone's library window adds the rest. */
private val RECENT_CAPTURES = 14.days

/**
 * Koin module for the watch's own cloud engine: the shared engine with the linked-watch profile, its own
 * stores under the `watch_cloud.` prefix, and only the outbox entries its router releases.
 */
val wearCloudSyncModule =
    module {
        single { ContentEncryptionService(get(), get(), get()) }
        single<NetworkSaverModeProvider> { AndroidNetworkSaverModeProvider(get<Context>()) }
        single<DataUsagePolicy> { DefaultDataUsagePolicy(get()) }
        single<BatteryInfoProvider> { AndroidBatteryInfoProvider(get<Context>()) }
        single<MediaManager> { AndroidMediaManager(get<Context>().contentResolver, get<Context>()) }
        single<MediaPayloadCrypto> { StoredMediaPayloadCrypto(get()) }
        single<KeyValueStorage>(WatchCloud) { PrefixedKeyValueStorage(get(), prefix = "watch_cloud.") }
        single<SyncTransactionManager>(WatchCloud) { RoomSyncTransactionManager(get<LogDateDatabase>()) }
        single<CloudApiClient>(WatchCloud) {
            LogDateCloudApiClient(configRepository = get(), httpClient = httpClient, sessionStorage = get())
        }
        single(WatchCloud) {
            val sessions = get<SessionStorage>()
            DownloadInbox(
                get<LogDateDatabase>().downloadInboxDao(),
                get(WatchCloud),
                {
                    val bound = sessions.getOriginBoundSession()
                    DownloadScope(bound?.session?.accountId.orEmpty(), bound?.origin.orEmpty())
                },
                { Clock.System.now().toEpochMilliseconds() },
            )
        }
        single(WatchCloud) { DurableCloudApiClient(get<CloudApiClient>(WatchCloud), get<DownloadInbox>(WatchCloud)) }
        single(WatchCloud) { SyncPayloadCipher(get(), get(), get()) }
        single<CloudAccountRepository>(WatchCloud) {
            DefaultCloudAccountRepository(apiClient = get(WatchCloud), secureStorage = get(WatchCloud), configRepository = get())
        }
        single<CloudEligibility>(WatchCloud) { CloudEligibility { _, _ -> false } }
        single<JournalContentRepository>(WatchCloud) {
            OfflineFirstJournalContentRepository(get(), get(), get(), syncMetadataService = get())
        }
        single(WatchCloud) { watchCloudEngine() }
    }

private fun Scope.watchCloudEngine(): DefaultSyncManager {
    val storage = get<KeyValueStorage>(WatchCloud)
    val durable = get<DurableCloudApiClient>(WatchCloud)
    val cipher = get<SyncPayloadCipher>(WatchCloud)
    val config = get<LogDateConfigRepository>()
    val sessions = get<SessionStorage>()
    val links = get<WatchLinkStore>()
    return DefaultSyncManager(
        cloudContentDataSource = DefaultCloudContentDataSource(durable, cipher),
        cloudJournalDataSource = DefaultCloudJournalDataSource(durable, cipher),
        cloudAssociationDataSource = DefaultCloudAssociationDataSource(durable),
        cloudMediaDataSource = DefaultCloudMediaDataSource(get(WatchCloud), get()),
        cloudDraftDataSource = DefaultCloudDraftDataSource(durable, cipher),
        cloudAccountRepository = get(WatchCloud),
        sessionStorage = sessions,
        mediaManager = get(),
        mediaSyncRefStore =
            KeyValueMediaSyncRefStore(
                storage,
                currentOrigin = { config.getCurrentBackendUrl() },
                currentOwnerId = { sessions.getSession()?.accountId.orEmpty() },
            ),
        journalRepository = get(),
        journalNotesRepository = get(),
        journalContentRepository = get(WatchCloud),
        journalConflictResolver = get(named(SyncQualifiers.JOURNAL_CONFLICT_RESOLVER)),
        noteConflictResolver = get(named(SyncQualifiers.NOTE_CONFLICT_RESOLVER)),
        conflictStore = KeyValueSyncConflictStore(storage),
        deadLetterStore = KeyValueSyncDeadLetterStore(storage),
        retryScheduleStore = KeyValueSyncRetryScheduleStore(storage),
        syncMetadataService = RoutedSyncMetadataService(get(), get(WatchCloud)),
        transactionManager = get(WatchCloud),
        dataUsagePolicy = get(),
        deviceIdProvider = get(),
        identityKeyManager = get(),
        mediaPayloadKeyProvider = get(),
        lastErrorStore = KeyValueLastSyncErrorStore(storage),
        firstSyncEnqueueStore = KeyValueFirstSyncEnqueueStore(storage),
        cloudApiClient = get(WatchCloud),
        identityRecoveryNeededStore = KeyValueIdentityRecoveryNeededStore(storage),
        unreadableCloudRecordStore = KeyValueUnreadableCloudRecordStore(storage),
        downloadInbox = get(WatchCloud),
        deviceSessionRevocationListener = DeviceSessionRevocationListener { links.recordProblem(null, LinkProblem.SESSION_REVOKED) },
        profile = SyncProfile.LinkedWatch(RecentCaptureFilter(RECENT_CAPTURES)),
    )
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, add `wearCloudSyncModule,` to `modules(...)` after `wearSyncV2Module,` with its import. In `app/wear/build.gradle.kts`, after `implementation(projects.client.networking)` add `implementation(projects.client.sensor)`.

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again.
Expected: PASS (4 tests).

- [ ] **Step 5: Run the module checks, the watch build and the graph test on the managed device**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:logdate-datastore:jvmTest :client:logdate-datastore:compileKotlinIosSimulatorArm64 :client:logdate-datastore:compileTestKotlinIosSimulatorArm64 :client:logdate-datastore:ktlintCheck :client:logdate-datastore:detekt :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearKoinGraphTest --console=plain
```
Expected: BUILD SUCCESSFUL with 2 tests passing.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task07-watch-cloud-engine.txt <<'EOF'
chore(wear): add the watch's own cloud sync engine

The watch now builds the same sync engine as the phone, set up for a
linked watch: it never creates an identity key, uploads only creates
and deletions, stores only recent notes from the server, and stores
its retry times, set-aside items and other sync state separately from
the phone sync's.

It reads the outbox through a filter that releases nothing yet, so
nothing changes until the watch decides what goes straight to the
cloud. A session the server revoked is recorded as the reason the
watch needs to be linked again. A test on a watch image resolves the
whole graph and checks an unlinked watch waits for its phone.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/PrefixedKeyValueStorage.kt client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/PrefixedKeyValueStorageTest.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearCloudSyncModule.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/build.gradle.kts app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearKoinGraphTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task07-watch-cloud-engine.txt
```

---

### Task 8: Route each watch change and record its delivery (D4, common)

The shared, pure parts of delivery. `PeerDeliveryLedger` stores one durable row per record and direction in `peer_delivery` (Room 53, no migration: only new queries). `WatchSyncRouter` is the master plan's table (contract 1): settle what the phone backed up or deleted, settle what is on a phone with no account, wait 15 minutes for a signed-in phone's backup and then upload directly if allowed, send to a nearby phone, send records over the cloud relay but media straight to the cloud, never send an edit to the cloud, and upload directly only when linked, on a server with `deviceLinkingV1` and `conditionalCreateV1`, over validated internet that is not the Bluetooth proxy, with audio on mobile data waiting for Wi-Fi or the charger. Every other case is a `Wait` with a named reason. `EnvelopeDataMap` and `NoteEnvelopeBody` put a note in a `RecordEnvelope` using the version 1 note JSON (`NoteDataMapper`), so both protocols agree on the note's fields, transcripts included. `WearPaths.recordMedia` names the channel a watch note's audio travels on in version 2.

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/PeerDeliveryLedger.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/WatchSyncRouter.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/EnvelopeDataMap.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt:36-75`
- Modify: `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/PeerDeliveryDao.kt:45`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/MemoryPeerDeliveryDao.kt` (test helper)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/PeerDeliveryLedgerTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/WatchSyncRouterTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/EnvelopeDataMapTest.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearPathsTest.kt` (1 new test)
- Test: `client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncDaoJvmTest.kt` (1 new test)

**Interfaces:**
- Consumes: `PeerDeliveryDao`, `PeerDeliveryEntity`; `EntityType`, `PendingOperation`; `ReceiptState`, `CloudPresence`, `RecordEnvelope`, `RecordEnvelopeCodec`, `RecordKind`, `RecordOrigin`, `DeliveryPriority`, `WindowReason`, `MediaRef`, `Decoded`, `WearJson`; `NoteDataMapper`; `NetworkSaverState`, `NetworkConnectionType.BLUETOOTH_PROXY` (Task 6).
- Produces:
  - `PeerDeliveryDao.all(peerScope: String, direction: String): List<PeerDeliveryEntity>`, `observeAll(peerScope: String, direction: String): Flow<List<PeerDeliveryEntity>>`, `earliestNextAttempt(peerScope: String, direction: String): Long?`
  - `enum class DeliveryDirection { TO_PHONE, FROM_WATCH }`, `object PeerScopes { const val PHONE = "phone"; const val WATCHES = "watches" }`
  - `enum class DeliveryState { NEW, SENT, ON_PHONE, BACKED_UP, CLOUD_PENDING, DELETED_ON_PHONE, REJECTED; companion object { fun of(receipt: ReceiptState): DeliveryState } }`
  - `enum class HoldReason { PHONE_UNREACHABLE, AWAITING_RECEIPT, PHONE_BACKING_UP, NOT_LINKED, SERVER_TOO_OLD, NO_INTERNET, BLUETOOTH_ONLY, AUDIO_WAITS_FOR_WIFI, PHONE_CANNOT_READ, EDIT_NEEDS_PHONE, SEND_FAILED }`
  - `data class PeerDelivery(val scope: String, val direction: DeliveryDirection, val entityType: EntityType, val entityId: String, val contentVersion: Long, val state: DeliveryState, val attempts: Int, val lastAttemptAt: Long?, val nextAttemptAt: Long?, val stateChangedAt: Long, val holdReason: HoldReason?)`
  - `class PeerDeliveryLedger(dao: PeerDeliveryDao, clock: Clock = Clock.System) { suspend fun track(scope, direction, entityType, entityId, contentVersion: Long): PeerDelivery; suspend fun get(scope, direction, entityType, entityId): PeerDelivery?; suspend fun moveTo(entry: PeerDelivery, state: DeliveryState, holdReason: HoldReason? = null, nextAttemptAt: Long? = null): PeerDelivery; suspend fun recordAttempt(entry: PeerDelivery, nextAttemptAt: Long?): PeerDelivery; suspend fun applyReceipt(scope, direction, entityType, entityId, receiptVersion: Long, state: DeliveryState): PeerDelivery?; suspend fun forget(scope, direction, entityType, entityId); suspend fun forgetAllExcept(scope, direction, entityType, keep: Set<String>): Int; suspend fun inState(scope, direction, state): List<PeerDelivery>; fun observe(scope, direction): Flow<List<PeerDelivery>>; suspend fun earliestDue(scope, direction): Long? }`
  - `enum class PhoneReach { NEARBY, RELAY_ONLY, UNREACHABLE }`, `enum class WatchNetwork { UNMETERED, METERED, BLUETOOTH_PROXY, NONE }`, `fun watchNetworkOf(state: NetworkSaverState): WatchNetwork`
  - `data class RouteConditions(val phone: PhoneReach, val phoneCloud: CloudPresence, val linked: Boolean, val serverReady: Boolean, val network: WatchNetwork, val charging: Boolean)`, `data class RouteInput(val operation: PendingOperation, val hasMedia: Boolean, val state: DeliveryState, val stateAgeMs: Long, val conditions: RouteConditions)`
  - `sealed interface Route { data object SendToPhone; data object Settle; data object UploadDirect; data class Wait(val reason: HoldReason) }`, `class WatchSyncRouter(backupGraceMs: Long = 15 * 60_000L, receiptWaitMs: Long = 30_000L) { fun route(input: RouteInput): Route }`
  - `object EnvelopeDataMap { const val KEY_ENVELOPE = "envelope"; const val KEY_SENT_AT = "sentAt"; fun toDataMap(envelope: RecordEnvelope, sentAt: Long): Map<String, String>; fun fromDataMap(data: Map<String, String>): Decoded<RecordEnvelope> }`
  - `object NoteEnvelopeBody { fun encode(note: JournalNote, mapper: NoteDataMapper = NoteDataMapper()): JsonElement; fun decode(body: JsonElement, mapper: NoteDataMapper = NoteDataMapper()): JournalNote; fun envelope(note: JournalNote, origin: RecordOrigin, priority: DeliveryPriority, reasons: Set<WindowReason> = emptySet(), media: List<MediaRef> = emptyList()): RecordEnvelope }`
  - `WearPaths.recordMedia(kind: RecordKind, id: Uuid): String`, `WearPaths.parseRecordMedia(path: String): RecordPath?`

**Failure modes and how they surface:** a ledger row written by a newer build with a state, direction, type or reason this build does not know is skipped with a warning instead of read wrongly; a receipt for an older version or one that would move a row backwards is ignored and `applyReceipt` returns null; a body that is not a note makes `NoteEnvelopeBody.decode` throw `IllegalArgumentException`, which each caller turns into a rejection receipt (Task 9). Nothing here sends or stores records yet.

- [ ] **Step 1: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/MemoryPeerDeliveryDao.kt`:

```kotlin
package app.logdate.client.sync.peer.delivery

import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The delivery ledger's table in memory, keyed like the real one. */
class MemoryPeerDeliveryDao : PeerDeliveryDao {
    private val rows = MutableStateFlow<Map<List<String>, PeerDeliveryEntity>>(emptyMap())

    override suspend fun upsert(entry: PeerDeliveryEntity) {
        rows.value = rows.value + (key(entry.peerScope, entry.direction, entry.entityType, entry.entityId) to entry)
    }

    override suspend fun get(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ): PeerDeliveryEntity? = rows.value[key(peerScope, direction, entityType, entityId)]

    override suspend fun due(
        peerScope: String,
        direction: String,
        now: Long,
        limit: Int,
    ): List<PeerDeliveryEntity> =
        rows.value.values
            .filter { it.peerScope == peerScope && it.direction == direction && (it.nextAttemptAt ?: Long.MAX_VALUE) <= now }
            .sortedBy { it.nextAttemptAt }
            .take(limit)

    override suspend fun delete(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ) {
        rows.value = rows.value - listOf(key(peerScope, direction, entityType, entityId))
    }

    override suspend fun all(
        peerScope: String,
        direction: String,
    ): List<PeerDeliveryEntity> = rows.value.values.filter { it.peerScope == peerScope && it.direction == direction }

    override fun observeAll(
        peerScope: String,
        direction: String,
    ): Flow<List<PeerDeliveryEntity>> = rows.map { all -> all.values.filter { it.peerScope == peerScope && it.direction == direction } }

    override suspend fun earliestNextAttempt(
        peerScope: String,
        direction: String,
    ): Long? = all(peerScope, direction).mapNotNull { it.nextAttemptAt }.minOrNull()

    private fun key(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ) = listOf(peerScope, direction, entityType, entityId)
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/PeerDeliveryLedgerTest.kt`:

```kotlin
package app.logdate.client.sync.peer.delivery

import app.logdate.client.sync.metadata.EntityType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Tests [PeerDeliveryLedger]: one row per record version that only ever moves forward. */
class PeerDeliveryLedgerTest {
    private var now = 1_000L
    private val ledger =
        PeerDeliveryLedger(
            MemoryPeerDeliveryDao(),
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(now)
            },
        )

    @Test
    fun `a record is tracked as new at its version`() =
        runTest {
            val entry = track("a", version = 5)

            assertEquals(DeliveryState.NEW, entry.state)
            assertEquals(5, entry.contentVersion)
            assertEquals(1_000, entry.stateChangedAt)
        }

    @Test
    fun `tracking the same version again leaves its state unchanged`() =
        runTest {
            ledger.moveTo(track("a", version = 5), DeliveryState.SENT)

            assertEquals(DeliveryState.SENT, track("a", version = 5).state)
        }

    @Test
    fun `a newer version starts over`() =
        runTest {
            ledger.moveTo(track("a", version = 5), DeliveryState.ON_PHONE)

            assertEquals(DeliveryState.NEW, track("a", version = 6).state)
        }

    @Test
    fun `a receipt for the current version moves the record forward`() =
        runTest {
            track("a", version = 5)

            assertEquals(DeliveryState.BACKED_UP, receipt("a", version = 5, DeliveryState.BACKED_UP)?.state)
        }

    @Test
    fun `a receipt for an older version is ignored`() =
        runTest {
            track("a", version = 6)

            assertNull(receipt("a", version = 5, DeliveryState.ON_PHONE))
            assertEquals(DeliveryState.NEW, get("a")?.state)
        }

    @Test
    fun `a late receipt never moves a record back`() =
        runTest {
            track("a", version = 5)
            receipt("a", version = 5, DeliveryState.BACKED_UP)

            assertNull(receipt("a", version = 5, DeliveryState.ON_PHONE))
            assertEquals(DeliveryState.BACKED_UP, get("a")?.state)
        }

    @Test
    fun `a record waiting for the cloud moves only to backed up or deleted`() =
        runTest {
            ledger.moveTo(track("a", version = 5), DeliveryState.CLOUD_PENDING)

            assertNull(receipt("a", version = 5, DeliveryState.ON_PHONE))
            assertEquals(DeliveryState.BACKED_UP, receipt("a", version = 5, DeliveryState.BACKED_UP)?.state)
        }

    @Test
    fun `records no longer queued are forgotten`() =
        runTest {
            track("a", version = 1)
            track("b", version = 1)

            assertEquals(1, ledger.forgetAllExcept(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, keep = setOf("a")))
            assertNull(get("b"))
        }

    @Test
    fun `a wait records its reason and when it is due`() =
        runTest {
            val waiting = ledger.moveTo(track("a", version = 1), DeliveryState.NEW, HoldReason.PHONE_UNREACHABLE, nextAttemptAt = 2_000)

            assertEquals(HoldReason.PHONE_UNREACHABLE, get("a")?.holdReason)
            assertEquals(2_000, ledger.earliestDue(PeerScopes.PHONE, DeliveryDirection.TO_PHONE))
            assertEquals(1, ledger.recordAttempt(waiting, nextAttemptAt = 3_000).attempts)
        }

    private suspend fun track(
        id: String,
        version: Long,
    ) = ledger.track(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id, version)

    private suspend fun get(id: String) = ledger.get(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id)

    private suspend fun receipt(
        id: String,
        version: Long,
        state: DeliveryState,
    ) = ledger.applyReceipt(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id, version, state)
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/WatchSyncRouterTest.kt`:

```kotlin
package app.logdate.client.sync.peer.delivery

import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverState
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.metadata.PendingOperation
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [WatchSyncRouter], the table that decides where each watch change goes next. */
class WatchSyncRouterTest {
    private val router = WatchSyncRouter()
    private val ready =
        RouteConditions(
            phone = PhoneReach.NEARBY,
            phoneCloud = CloudPresence.SIGNED_IN,
            linked = true,
            serverReady = true,
            network = WatchNetwork.UNMETERED,
            charging = false,
        )

    @Test
    fun `a record the phone backed up settles`() {
        assertEquals(Route.Settle, router.route(input(state = DeliveryState.BACKED_UP)))
    }

    @Test
    fun `a record the phone deleted settles`() {
        assertEquals(Route.Settle, router.route(input(state = DeliveryState.DELETED_ON_PHONE)))
    }

    @Test
    fun `a new record goes to a phone nearby`() {
        assertEquals(Route.SendToPhone, router.route(input()))
    }

    @Test
    fun `a record on a phone with no account settles`() {
        val signedOut = ready.copy(phoneCloud = CloudPresence.NO_ACCOUNT)

        assertEquals(Route.Settle, router.route(input(state = DeliveryState.ON_PHONE, conditions = signedOut)))
    }

    @Test
    fun `a record on a signed-in phone waits for its backup`() {
        assertEquals(Route.Wait(HoldReason.PHONE_BACKING_UP), router.route(input(state = DeliveryState.ON_PHONE, ageMs = 10 * 60_000L)))
    }

    @Test
    fun `a record the phone has not backed up after fifteen minutes goes straight to the cloud`() {
        assertEquals(Route.UploadDirect, router.route(input(state = DeliveryState.ON_PHONE, ageMs = 15 * 60_000L)))
    }

    @Test
    fun `after fifteen minutes a watch that cannot upload still waits for the phone`() {
        val unlinked = ready.copy(linked = false)

        assertEquals(
            Route.Wait(HoldReason.PHONE_BACKING_UP),
            router.route(input(state = DeliveryState.ON_PHONE, ageMs = 15 * 60_000L, conditions = unlinked)),
        )
    }

    @Test
    fun `a phone whose account is not known yet means waiting for a receipt`() {
        val unknown = ready.copy(phoneCloud = CloudPresence.UNKNOWN)

        assertEquals(Route.Wait(HoldReason.AWAITING_RECEIPT), router.route(input(state = DeliveryState.ON_PHONE, conditions = unknown)))
    }

    @Test
    fun `a record sent moments ago waits for its receipt`() {
        assertEquals(Route.Wait(HoldReason.AWAITING_RECEIPT), router.route(input(state = DeliveryState.SENT, ageMs = 10_000)))
    }

    @Test
    fun `a record sent long ago is sent again`() {
        assertEquals(Route.SendToPhone, router.route(input(state = DeliveryState.SENT, ageMs = 31_000)))
    }

    @Test
    fun `with the phone out of reach a linked watch uploads directly`() {
        assertEquals(Route.UploadDirect, router.route(input(conditions = ready.copy(phone = PhoneReach.UNREACHABLE))))
    }

    @Test
    fun `with the phone out of reach an unlinked watch waits for it`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE, linked = false)

        assertEquals(Route.Wait(HoldReason.PHONE_UNREACHABLE), router.route(input(conditions = away)))
    }

    @Test
    fun `a server without create if absent is never uploaded to`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE, serverReady = false)

        assertEquals(Route.Wait(HoldReason.SERVER_TOO_OLD), router.route(input(conditions = away)))
    }

    @Test
    fun `the Bluetooth link is never used for a direct upload`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE, network = WatchNetwork.BLUETOOTH_PROXY)

        assertEquals(Route.Wait(HoldReason.BLUETOOTH_ONLY), router.route(input(conditions = away)))
    }

    @Test
    fun `no internet means waiting`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE, network = WatchNetwork.NONE)

        assertEquals(Route.Wait(HoldReason.NO_INTERNET), router.route(input(conditions = away)))
    }

    @Test
    fun `audio on mobile data waits for wifi or the charger`() {
        val lte = ready.copy(phone = PhoneReach.UNREACHABLE, network = WatchNetwork.METERED)

        assertEquals(Route.Wait(HoldReason.AUDIO_WAITS_FOR_WIFI), router.route(input(hasMedia = true, conditions = lte)))
        assertEquals(Route.UploadDirect, router.route(input(hasMedia = true, conditions = lte.copy(charging = true))))
        assertEquals(Route.UploadDirect, router.route(input(hasMedia = false, conditions = lte)))
    }

    @Test
    fun `audio with the phone reachable only through the relay goes to the cloud`() {
        assertEquals(Route.UploadDirect, router.route(input(hasMedia = true, conditions = ready.copy(phone = PhoneReach.RELAY_ONLY))))
    }

    @Test
    fun `text with the phone reachable only through the relay goes over the relay`() {
        assertEquals(Route.SendToPhone, router.route(input(hasMedia = false, conditions = ready.copy(phone = PhoneReach.RELAY_ONLY))))
    }

    @Test
    fun `an edit never goes straight to the cloud`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE)

        assertEquals(Route.Wait(HoldReason.EDIT_NEEDS_PHONE), router.route(input(operation = PendingOperation.UPDATE, conditions = away)))
        assertEquals(Route.SendToPhone, router.route(input(operation = PendingOperation.UPDATE)))
    }

    @Test
    fun `a record the phone cannot read goes to the cloud when it can`() {
        assertEquals(Route.UploadDirect, router.route(input(state = DeliveryState.REJECTED)))
        assertEquals(
            Route.Wait(HoldReason.PHONE_CANNOT_READ),
            router.route(input(state = DeliveryState.REJECTED, conditions = ready.copy(linked = false))),
        )
    }

    @Test
    fun `a record waiting for the cloud waits until it can go`() {
        assertEquals(Route.UploadDirect, router.route(input(state = DeliveryState.CLOUD_PENDING)))
        assertEquals(
            Route.Wait(HoldReason.NO_INTERNET),
            router.route(input(state = DeliveryState.CLOUD_PENDING, conditions = ready.copy(network = WatchNetwork.NONE))),
        )
    }

    @Test
    fun `the watch's network is read from its connection`() {
        assertEquals(WatchNetwork.BLUETOOTH_PROXY, watchNetworkOf(NetworkSaverState(false, NetworkConnectionType.BLUETOOTH_PROXY)))
        assertEquals(WatchNetwork.METERED, watchNetworkOf(NetworkSaverState(false, NetworkConnectionType.CELLULAR)))
        assertEquals(WatchNetwork.UNMETERED, watchNetworkOf(NetworkSaverState(false, NetworkConnectionType.WIFI)))
        assertEquals(WatchNetwork.NONE, watchNetworkOf(NetworkSaverState(false, NetworkConnectionType.WIFI, isValidated = false)))
    }

    private fun input(
        state: DeliveryState = DeliveryState.NEW,
        operation: PendingOperation = PendingOperation.CREATE,
        hasMedia: Boolean = false,
        ageMs: Long = 0,
        conditions: RouteConditions = ready,
    ) = RouteInput(operation, hasMedia, state, ageMs, conditions)
}
```

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/EnvelopeDataMapTest.kt`:

```kotlin
package app.logdate.client.sync.peer.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [EnvelopeDataMap] and [NoteEnvelopeBody]: a note crosses as the same JSON version 1 sends. */
class EnvelopeDataMapTest {
    private val note =
        JournalNote.Text(
            uid = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
            creationTimestamp = Instant.fromEpochMilliseconds(1_000),
            lastUpdated = Instant.fromEpochMilliseconds(2_000),
            content = "said on the watch",
            syncVersion = 3,
        )

    @Test
    fun `a note envelope names the note and its version`() {
        val envelope = NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT)

        assertEquals(RecordKind.NOTE, envelope.kind)
        assertEquals(note.uid.toString(), envelope.id)
        assertEquals(2_000, envelope.contentVersion)
        assertEquals(3, envelope.syncVersion)
    }

    @Test
    fun `a note body reads back as the note`() {
        assertEquals(note, NoteEnvelopeBody.decode(NoteEnvelopeBody.encode(note)))
    }

    @Test
    fun `an envelope round trips through a data map`() {
        val envelope = NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT)
        val data = EnvelopeDataMap.toDataMap(envelope, sentAt = 7)

        assertEquals("7", data[EnvelopeDataMap.KEY_SENT_AT])
        assertEquals(Decoded.Supported(envelope), EnvelopeDataMap.fromDataMap(data))
    }

    @Test
    fun `a data map without an envelope is malformed`() {
        assertIs<Decoded.Malformed>(EnvelopeDataMap.fromDataMap(mapOf("other" to "x")))
    }
}
```

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearPathsTest.kt`, add:

```kotlin
    @Test
    fun `a record's media channel reads back and is not a record path`() {
        val path = WearPaths.recordMedia(RecordKind.NOTE, id)

        assertEquals("/logdate/v2/w2p/media/note/$id", path)
        assertEquals(RecordPath(RecordKind.NOTE, id), WearPaths.parseRecordMedia(path))
        assertNull(WearPaths.parseWatchRecord(path))
    }
```

and add `WearPaths.recordMedia(RecordKind.NOTE, id),` to the `v2Paths` list in `no version 2 path reaches a version 1 listener`.

In `client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncDaoJvmTest.kt`, add:

```kotlin
    @Test
    fun `every delivery of a peer and direction is listed and observed with its earliest attempt`() =
        runTest {
            val dao = database.peerDeliveryDao()
            listOf(
                delivery("a", nextAttemptAt = 300),
                delivery("b", nextAttemptAt = 100),
                delivery("c", nextAttemptAt = null),
            ).forEach { dao.upsert(it) }

            assertEquals(setOf("a", "b", "c"), dao.all("phone", "TO_PHONE").map { it.entityId }.toSet())
            assertEquals(3, dao.observeAll("phone", "TO_PHONE").first().size)
            assertEquals(100, dao.earliestNextAttempt("phone", "TO_PHONE"))
            assertNull(dao.earliestNextAttempt("phone", "FROM_WATCH"))
        }
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:jvmTest --tests 'app.logdate.client.database.WatchSyncDaoJvmTest' :client:sync:desktopTest --tests 'app.logdate.client.sync.peer.delivery.*' --tests 'app.logdate.client.sync.datalayer.v2.WearPathsTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'all'", "Unresolved reference 'observeAll'", "Unresolved reference 'PeerDeliveryLedger'", "Unresolved reference 'WatchSyncRouter'", "Unresolved reference 'recordMedia'".

- [ ] **Step 3: Implement**

In `client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/PeerDeliveryDao.kt`, add `import kotlinx.coroutines.flow.Flow` and, after `delete`:

```kotlin
    @Query("SELECT * FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction")
    suspend fun all(
        peerScope: String,
        direction: String,
    ): List<PeerDeliveryEntity>

    @Query("SELECT * FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction")
    fun observeAll(
        peerScope: String,
        direction: String,
    ): Flow<List<PeerDeliveryEntity>>

    /** The soonest time anything for this peer and direction is due; rows with no time are not counted. */
    @Query("SELECT MIN(nextAttemptAt) FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction")
    suspend fun earliestNextAttempt(
        peerScope: String,
        direction: String,
    ): Long?
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt`, add `private const val MEDIA_PREFIX = "/logdate/v2/w2p/media/"` after `RECORD_PREFIX`, and after `watchRecord`:

```kotlin
    /** The channel the media file of a record made on the watch travels on. */
    fun recordMedia(
        kind: RecordKind,
        id: Uuid,
    ): String = "$MEDIA_PREFIX${kind.pathSegment}/$id"
```

and after `parseWatchRecord`:

```kotlin
    fun parseRecordMedia(path: String): RecordPath? = parse(path, MEDIA_PREFIX)
```

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/PeerDeliveryLedger.kt`:

```kotlin
package app.logdate.client.sync.peer.delivery

import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.metadata.EntityType
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** Which way a record travels between the watch and the phone. */
enum class DeliveryDirection {
    TO_PHONE,
    FROM_WATCH,
}

/** The peer a ledger row is about: the watch has one phone; the phone has one ledger for all its watches. */
object PeerScopes {
    const val PHONE = "phone"
    const val WATCHES = "watches"
}

/** Where one version of a record stands on its way to being backed up. */
enum class DeliveryState {
    NEW,
    SENT,
    ON_PHONE,
    BACKED_UP,

    /** Released to the watch's own cloud engine. */
    CLOUD_PENDING,
    DELETED_ON_PHONE,

    /** The phone cannot read it; the watch still has it and may upload it itself. */
    REJECTED,
    ;

    companion object {
        fun of(receipt: ReceiptState): DeliveryState =
            when (receipt) {
                ReceiptState.ON_PHONE -> ON_PHONE
                ReceiptState.BACKED_UP -> BACKED_UP
                ReceiptState.DELETED_ON_PHONE -> DELETED_ON_PHONE
                ReceiptState.REJECTED_UNSUPPORTED -> REJECTED
            }
    }
}

/** Why a record is waiting. Each one names what is missing, so status can say what to fix. */
enum class HoldReason {
    PHONE_UNREACHABLE,
    AWAITING_RECEIPT,
    PHONE_BACKING_UP,
    NOT_LINKED,
    SERVER_TOO_OLD,
    NO_INTERNET,
    BLUETOOTH_ONLY,
    AUDIO_WAITS_FOR_WIFI,
    PHONE_CANNOT_READ,
    EDIT_NEEDS_PHONE,
    SEND_FAILED,
}

data class PeerDelivery(
    val scope: String,
    val direction: DeliveryDirection,
    val entityType: EntityType,
    val entityId: String,
    val contentVersion: Long,
    val state: DeliveryState,
    val attempts: Int,
    val lastAttemptAt: Long?,
    val nextAttemptAt: Long?,
    val stateChangedAt: Long,
    val holdReason: HoldReason?,
)

/**
 * The durable record of where each record stands between a watch and its phone, so a decision survives
 * the process being stopped. A row only ever moves forward, and a newer version of a record starts over.
 */
class PeerDeliveryLedger(
    private val dao: PeerDeliveryDao,
    private val clock: Clock = Clock.System,
) {
    suspend fun track(
        scope: String,
        direction: DeliveryDirection,
        entityType: EntityType,
        entityId: String,
        contentVersion: Long,
    ): PeerDelivery {
        val existing = get(scope, direction, entityType, entityId)
        if (existing != null && existing.contentVersion == contentVersion) return existing
        val fresh =
            PeerDelivery(
                scope = scope,
                direction = direction,
                entityType = entityType,
                entityId = entityId,
                contentVersion = contentVersion,
                state = DeliveryState.NEW,
                attempts = 0,
                lastAttemptAt = null,
                nextAttemptAt = null,
                stateChangedAt = now(),
                holdReason = null,
            )
        dao.upsert(fresh.toEntity())
        return fresh
    }

    suspend fun get(
        scope: String,
        direction: DeliveryDirection,
        entityType: EntityType,
        entityId: String,
    ): PeerDelivery? = dao.get(scope, direction.name, entityType.name, entityId)?.toDelivery()

    suspend fun moveTo(
        entry: PeerDelivery,
        state: DeliveryState,
        holdReason: HoldReason? = null,
        nextAttemptAt: Long? = null,
    ): PeerDelivery {
        val changedAt = if (state == entry.state) entry.stateChangedAt else now()
        val changed = entry.copy(state = state, holdReason = holdReason, nextAttemptAt = nextAttemptAt, stateChangedAt = changedAt)
        dao.upsert(changed.toEntity())
        return changed
    }

    suspend fun recordAttempt(
        entry: PeerDelivery,
        nextAttemptAt: Long?,
    ): PeerDelivery {
        val attempted = entry.copy(attempts = entry.attempts + 1, lastAttemptAt = now(), nextAttemptAt = nextAttemptAt)
        dao.upsert(attempted.toEntity())
        return attempted
    }

    /** Applies a receipt for [receiptVersion] or later; returns null when it is older or would move the row back. */
    suspend fun applyReceipt(
        scope: String,
        direction: DeliveryDirection,
        entityType: EntityType,
        entityId: String,
        receiptVersion: Long,
        state: DeliveryState,
    ): PeerDelivery? {
        val entry = get(scope, direction, entityType, entityId) ?: return null
        if (receiptVersion < entry.contentVersion || !entry.state.canBecome(state)) return null
        return moveTo(entry, state)
    }

    suspend fun forget(
        scope: String,
        direction: DeliveryDirection,
        entityType: EntityType,
        entityId: String,
    ) = dao.delete(scope, direction.name, entityType.name, entityId)

    /** Removes the rows of [entityType] whose record is no longer in [keep]; returns how many went. */
    suspend fun forgetAllExcept(
        scope: String,
        direction: DeliveryDirection,
        entityType: EntityType,
        keep: Set<String>,
    ): Int {
        val stale = dao.all(scope, direction.name).filter { it.entityType == entityType.name && it.entityId !in keep }
        stale.forEach { dao.delete(it.peerScope, it.direction, it.entityType, it.entityId) }
        return stale.size
    }

    suspend fun inState(
        scope: String,
        direction: DeliveryDirection,
        state: DeliveryState,
    ): List<PeerDelivery> = dao.all(scope, direction.name).mapNotNull { it.toDelivery() }.filter { it.state == state }

    fun observe(
        scope: String,
        direction: DeliveryDirection,
    ): Flow<List<PeerDelivery>> = dao.observeAll(scope, direction.name).map { rows -> rows.mapNotNull { it.toDelivery() } }

    suspend fun earliestDue(
        scope: String,
        direction: DeliveryDirection,
    ): Long? = dao.earliestNextAttempt(scope, direction.name)

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private fun DeliveryState.canBecome(next: DeliveryState): Boolean =
        when (this) {
            DeliveryState.BACKED_UP, DeliveryState.DELETED_ON_PHONE -> false
            DeliveryState.CLOUD_PENDING, DeliveryState.ON_PHONE -> next == DeliveryState.BACKED_UP || next == DeliveryState.DELETED_ON_PHONE
            DeliveryState.NEW, DeliveryState.SENT, DeliveryState.REJECTED -> next != DeliveryState.NEW && next != DeliveryState.SENT
        }

    private fun PeerDelivery.toEntity() =
        PeerDeliveryEntity(
            peerScope = scope,
            direction = direction.name,
            entityType = entityType.name,
            entityId = entityId,
            contentVersion = contentVersion,
            state = state.name,
            attempts = attempts,
            lastAttemptAt = lastAttemptAt,
            nextAttemptAt = nextAttemptAt,
            stateChangedAt = stateChangedAt,
            holdReason = holdReason?.name,
        )

    private fun PeerDeliveryEntity.toDelivery(): PeerDelivery? {
        val rowDirection = DeliveryDirection.entries.firstOrNull { it.name == direction }
        val rowType = EntityType.entries.firstOrNull { it.name == entityType }
        val rowState = DeliveryState.entries.firstOrNull { it.name == state }
        val rowHold = holdReason?.let { name -> HoldReason.entries.firstOrNull { it.name == name } }
        if (rowDirection == null || rowType == null || rowState == null || (holdReason != null && rowHold == null)) {
            Napier.w("Skipping a delivery row a newer version wrote ($direction $entityType $state $holdReason)")
            return null
        }
        return PeerDelivery(
            scope = peerScope,
            direction = rowDirection,
            entityType = rowType,
            entityId = entityId,
            contentVersion = contentVersion,
            state = rowState,
            attempts = attempts,
            lastAttemptAt = lastAttemptAt,
            nextAttemptAt = nextAttemptAt,
            stateChangedAt = stateChangedAt,
            holdReason = rowHold,
        )
    }
}
```

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/WatchSyncRouter.kt`:

```kotlin
package app.logdate.client.sync.peer.delivery

import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverState
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.metadata.PendingOperation

/** How the watch can reach its phone right now. */
enum class PhoneReach {
    NEARBY,

    /** Only through Google's cloud relay: fine for records, slow and costly for media. */
    RELAY_ONLY,
    UNREACHABLE,
}

/** The watch's own internet, as far as a direct upload is concerned. */
enum class WatchNetwork {
    UNMETERED,
    METERED,
    BLUETOOTH_PROXY,
    NONE,
}

/** A network the system has not validated counts as none: it may be a captive portal. */
fun watchNetworkOf(state: NetworkSaverState): WatchNetwork =
    when {
        state.connectionType == NetworkConnectionType.NONE || !state.isValidated -> WatchNetwork.NONE
        state.connectionType == NetworkConnectionType.BLUETOOTH_PROXY -> WatchNetwork.BLUETOOTH_PROXY
        state.isMetered -> WatchNetwork.METERED
        else -> WatchNetwork.UNMETERED
    }

/**
 * What the watch knows when it routes a change.
 *
 * @property serverReady The server lists both `deviceLinkingV1` and `conditionalCreateV1`.
 */
data class RouteConditions(
    val phone: PhoneReach,
    val phoneCloud: CloudPresence,
    val linked: Boolean,
    val serverReady: Boolean,
    val network: WatchNetwork,
    val charging: Boolean,
)

data class RouteInput(
    val operation: PendingOperation,
    val hasMedia: Boolean,
    val state: DeliveryState,
    val stateAgeMs: Long,
    val conditions: RouteConditions,
)

sealed interface Route {
    data object SendToPhone : Route

    data object Settle : Route

    data object UploadDirect : Route

    data class Wait(
        val reason: HoldReason,
    ) : Route
}

/**
 * Decides where a change made on the watch goes next. The phone is preferred; the watch uploads only
 * creates and deletions itself, and only when it is linked, its server can create without overwriting,
 * and it has real internet of its own.
 */
class WatchSyncRouter(
    private val backupGraceMs: Long = BACKUP_GRACE_MS,
    private val receiptWaitMs: Long = RECEIPT_WAIT_MS,
) {
    fun route(input: RouteInput): Route =
        when (input.state) {
            DeliveryState.BACKED_UP, DeliveryState.DELETED_ON_PHONE -> Route.Settle
            DeliveryState.REJECTED -> if (directBlocker(input) == null) Route.UploadDirect else Route.Wait(HoldReason.PHONE_CANNOT_READ)
            DeliveryState.ON_PHONE -> onPhone(input)
            DeliveryState.CLOUD_PENDING -> directBlocker(input)?.let { Route.Wait(it) } ?: Route.UploadDirect
            DeliveryState.SENT ->
                if (input.stateAgeMs < receiptWaitMs) Route.Wait(HoldReason.AWAITING_RECEIPT) else beforeDelivery(input)
            DeliveryState.NEW -> beforeDelivery(input)
        }

    private fun onPhone(input: RouteInput): Route =
        when (input.conditions.phoneCloud) {
            CloudPresence.NO_ACCOUNT -> Route.Settle
            CloudPresence.UNKNOWN -> Route.Wait(HoldReason.AWAITING_RECEIPT)
            CloudPresence.SIGNED_IN ->
                if (input.stateAgeMs < backupGraceMs || directBlocker(input) != null) {
                    Route.Wait(HoldReason.PHONE_BACKING_UP)
                } else {
                    Route.UploadDirect
                }
        }

    private fun beforeDelivery(input: RouteInput): Route {
        val blocker = directBlocker(input)
        return when (input.conditions.phone) {
            PhoneReach.NEARBY -> Route.SendToPhone
            PhoneReach.RELAY_ONLY -> if (input.hasMedia && blocker == null) Route.UploadDirect else Route.SendToPhone
            PhoneReach.UNREACHABLE ->
                when (blocker) {
                    null -> Route.UploadDirect
                    HoldReason.EDIT_NEEDS_PHONE -> Route.Wait(HoldReason.EDIT_NEEDS_PHONE)
                    HoldReason.NOT_LINKED -> Route.Wait(HoldReason.PHONE_UNREACHABLE)
                    else -> Route.Wait(blocker)
                }
        }
    }

    private fun directBlocker(input: RouteInput): HoldReason? {
        val conditions = input.conditions
        return when {
            input.operation == PendingOperation.UPDATE -> HoldReason.EDIT_NEEDS_PHONE
            !conditions.linked -> HoldReason.NOT_LINKED
            !conditions.serverReady -> HoldReason.SERVER_TOO_OLD
            conditions.network == WatchNetwork.NONE -> HoldReason.NO_INTERNET
            conditions.network == WatchNetwork.BLUETOOTH_PROXY -> HoldReason.BLUETOOTH_ONLY
            input.hasMedia && conditions.network == WatchNetwork.METERED && !conditions.charging -> HoldReason.AUDIO_WAITS_FOR_WIFI
            else -> null
        }
    }

    companion object {
        const val BACKUP_GRACE_MS = 15 * 60_000L
        const val RECEIPT_WAIT_MS = 30_000L
    }
}
```

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/EnvelopeDataMap.kt`:

```kotlin
package app.logdate.client.sync.peer.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.MediaRef
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordEnvelopeCodec
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowReason
import kotlinx.serialization.json.JsonElement

/**
 * A [RecordEnvelope] as a data item. [KEY_SENT_AT] lets the sender make a resend a changed item; the Data
 * Layer drops a put that matches what it already has.
 */
object EnvelopeDataMap {
    const val KEY_ENVELOPE = "envelope"
    const val KEY_SENT_AT = "sentAt"

    /** Throws [IllegalArgumentException] for an envelope [RecordEnvelopeCodec] refuses to write. */
    fun toDataMap(
        envelope: RecordEnvelope,
        sentAt: Long,
    ): Map<String, String> = mapOf(KEY_ENVELOPE to RecordEnvelopeCodec.encode(envelope), KEY_SENT_AT to sentAt.toString())

    fun fromDataMap(data: Map<String, String>): Decoded<RecordEnvelope> {
        val text = data[KEY_ENVELOPE] ?: return Decoded.Malformed("The data item has no record envelope")
        return RecordEnvelopeCodec.decode(text)
    }
}

/** A note as an envelope body: the same JSON version 1 sends, so both protocols agree on every field. */
object NoteEnvelopeBody {
    fun encode(
        note: JournalNote,
        mapper: NoteDataMapper = NoteDataMapper(),
    ): JsonElement = WearJson.json.parseToJsonElement(checkNotNull(mapper.toDataMap(note)[NoteDataMapper.KEY_JSON_PAYLOAD]))

    /** Throws [IllegalArgumentException] for a body that is not a note. */
    fun decode(
        body: JsonElement,
        mapper: NoteDataMapper = NoteDataMapper(),
    ): JournalNote = mapper.fromDataMap(mapOf(NoteDataMapper.KEY_JSON_PAYLOAD to body.toString()))

    fun envelope(
        note: JournalNote,
        origin: RecordOrigin,
        priority: DeliveryPriority,
        reasons: Set<WindowReason> = emptySet(),
        media: List<MediaRef> = emptyList(),
    ): RecordEnvelope =
        RecordEnvelope(
            kind = RecordKind.NOTE,
            id = note.uid.toString(),
            contentVersion = note.lastUpdated.toEpochMilliseconds(),
            syncVersion = note.syncVersion,
            origin = origin,
            reasons = reasons,
            priority = priority,
            body = encode(note),
            media = media,
        )
}
```

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again.
Expected: PASS (1 new DAO test, 9 ledger, 22 router, 4 envelope tests, the new path test, and every existing `WearPathsTest` and `WatchSyncDaoJvmTest` test).

- [ ] **Step 5: Run every affected module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:database:jvmTest :client:database:compileKotlinIosSimulatorArm64 :client:database:compileTestKotlinIosSimulatorArm64 :client:database:ktlintCheck :client:database:detekt :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task08-delivery-router.txt <<'EOF'
chore(sync): route each watch change and record its delivery

Watch changes now have a durable ledger and a routing table, shared by
both devices and not used yet. The ledger records where each version
of a record stands, only ever moves it forward, and starts over when
the record changes. The table sends a change to a phone nearby, waits
fifteen minutes for a signed-in phone's backup before uploading it
directly, settles it when the phone has no account, leaves edits for
the phone, sends audio straight to the cloud when the phone is only
reachable through the cloud relay, and names the reason whenever it
waits.

A direct upload needs a link, a server that creates without
overwriting, and internet that is not the phone's Bluetooth link;
audio on mobile data waits for Wi-Fi or the charger. Notes travel in
envelopes as the same JSON the current protocol sends.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/PeerDeliveryDao.kt client/database/src/jvmTest/kotlin/app/logdate/client/database/WatchSyncDaoJvmTest.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearPaths.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/PeerDeliveryLedger.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/WatchSyncRouter.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/delivery/EnvelopeDataMap.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/MemoryPeerDeliveryDao.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/PeerDeliveryLedgerTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/WatchSyncRouterTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/EnvelopeDataMapTest.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WearPathsTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task08-delivery-router.txt
```

---

### Task 9: Store watch captures on the phone and send receipts (D4, phone)

The phone's side of version 2 delivery. `WatchRecordIngestorV2` reads a `RecordEnvelope` the watch wrote at `/logdate/v2/w2p/record/note/<id>` and hands the note to a second `WatchNoteIngestor` (the existing class, so the audio digest check, the deletion marker and the arrival notification behave exactly as in version 1); the recording itself arrives on `/logdate/v2/w2p/media/note/<id>`. Instead of the version 1 acknowledgement, this ingestor's `ReceiptAcknowledger` records the note in the phone's ledger and pushes an `ON_PHONE` receipt. `PhoneReceiptBook` computes a receipt from what the phone has: `ON_PHONE`, `BACKED_UP` (signed in, a server version, nothing queued), `DELETED_ON_PHONE` (received once, gone now), or `REJECTED_UNSUPPORTED` (a kind this phone does not take this way, which covers every version). `WatchReceiptResponder` answers the watch's `/logdate/v2/receipts` request; `WatchReceiptNotifier` pushes receipts on `/logdate/v2/receipts/push` as notes arrive and as the phone's outbox drains. The phone starts advertising `receipts.v1`.

**Files:**
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/PhoneReceiptBook.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptResponder.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptNotifier.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchRecordIngestorV2.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt` (`onChannelOpened` `:151-157`, `processSnapshotEvent` `:240-249`, `resolveSyncDependencies` `:219-227`, new `onRequest`, `PhoneDataLayerSyncDependencies` `:484-489`)
- Modify: `app/android-main/src/main/AndroidManifest.xml:224-273`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/MemoryPeerDeliveryDao.kt` (test helper, same content as Task 8's with package `app.logdate.client.sync.v2`)
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/PhoneReceiptBookTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchRecordIngestorV2Test.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptNotifierTest.kt`

**Interfaces:**
- Consumes: Task 8's `PeerDeliveryLedger`, `DeliveryState`, `DeliveryDirection`, `PeerScopes`, `EnvelopeDataMap`, `NoteEnvelopeBody`, `WearPaths.recordMedia/parseRecordMedia`; `WatchNoteIngestor(notesRepository, audioStore, acknowledger, notifier, noteDataMapper)`, `WatchNoteAcknowledger`, `WatchMessageSender`; `NoteDataMapper.withAudioDigest`, `AudioFileDigest(sizeBytes, sha256Hex)`; `ReceiptsRequest`, `ReceiptsResponse`, `Receipt`, `ReceiptQuery`, `ReceiptState`, `CloudPresence`; `SyncMetadataService.hasPending/observePendingCount`; `WearProtocolGate.isFeatureActive(WearFeatures.RECEIPTS)`.
- Produces:
  - `class PhoneReceiptBook(notes: JournalNotesRepository, outbox: SyncMetadataService, sessions: SessionStorage, ledger: PeerDeliveryLedger) { fun cloudPresence(): CloudPresence; suspend fun receiptFor(query: ReceiptQuery): Receipt?; companion object { const val ANY_VERSION: Long } }`
  - `class WatchReceiptResponder(book: PhoneReceiptBook) { suspend fun answer(payload: ByteArray): ByteArray }`
  - `fun interface RecordRejection { suspend fun reject(kind: RecordKind, id: String) }`
  - `class WatchReceiptNotifier(book: PhoneReceiptBook, ledger: PeerDeliveryLedger, sender: WatchMessageSender, active: suspend () -> Boolean, clock: Clock = Clock.System) : RecordRejection { suspend fun push(noteId: String, contentVersion: Long); suspend fun pushBackedUp(): Int; fun start(scope: CoroutineScope, outboxChanges: Flow<Int>): Job }`
  - `class ReceiptAcknowledger(ledger: PeerDeliveryLedger, receipts: WatchReceiptNotifier) : WatchNoteAcknowledger`
  - `class WatchRecordIngestorV2(ingestMetadata: suspend (Map<String, String>) -> Unit, ingestAudio: suspend (Uuid, InputStream) -> Unit, notes: JournalNotesRepository, acknowledger: WatchNoteAcknowledger, rejection: RecordRejection, mapper: NoteDataMapper = NoteDataMapper()) { suspend fun onRecord(path: String, data: Map<String, String>); suspend fun onMedia(path: String, input: InputStream) }`
  - `PhoneDataLayerSyncDependencies(..., recordIngestorV2: WatchRecordIngestorV2? = null)`; `PhoneDataLayerListenerService.answerRequest(nodeId, path, request): ByteArray?` (protected open)

**Failure modes and how they surface:**
- An envelope from a newer version, a record of a kind this phone does not take this way (a marked moment until D7), a body that is not a note, an envelope whose id does not match its path, or a body sent by reference: a `REJECTED_UNSUPPORTED` receipt for every version, so the watch stops resending and can upload it itself; logged with why.
- A recording whose size or SHA-256 does not match: discarded by `WatchNoteIngestor` as in version 1; no receipt, so the watch sends it again.
- No watch takes the push, or it is lost: logged; the watch also asks with the receipts request on its next sync.
- A receipts request the phone cannot read: answered with no receipts and logged; the watch waits and asks again.
- Koin not ready for a request or a recording: the request fails or the channel is closed, logged; the watch retries.

- [ ] **Step 1: Write the failing tests**

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/MemoryPeerDeliveryDao.kt` with the content of Task 8's `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/MemoryPeerDeliveryDao.kt`, changing only the first line to `package app.logdate.client.sync.v2`.

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/PhoneReceiptBookTest.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [PhoneReceiptBook]: what the phone has of each watch record, said as a receipt. */
class PhoneReceiptBookTest {
    private val notes = mockk<JournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private val sessions = FakeSessions(OriginBoundSession("https://logdate.test", UserSession("access", "refresh", "account")))
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val book = PhoneReceiptBook(notes, outbox, sessions, ledger)
    private val id = Uuid.random()

    @Test
    fun `a stored note not yet backed up is on the phone`() =
        runTest {
            stored(updated = 5, syncVersion = 0, pending = true)

            assertEquals(Receipt(RecordKind.NOTE, id.toString(), 5, ReceiptState.ON_PHONE), book.receiptFor(query(5)))
        }

    @Test
    fun `a backed up note is reported with its server version`() =
        runTest {
            stored(updated = 5, syncVersion = 7, pending = false)

            assertEquals(Receipt(RecordKind.NOTE, id.toString(), 5, ReceiptState.BACKED_UP, serverVersion = 7), book.receiptFor(query(5)))
        }

    @Test
    fun `with no account a stored note stays on the phone`() =
        runTest {
            sessions.bound = null
            stored(updated = 5, syncVersion = 7, pending = false)

            assertEquals(ReceiptState.ON_PHONE, book.receiptFor(query(5))?.state)
            assertEquals(CloudPresence.NO_ACCOUNT, book.cloudPresence())
        }

    @Test
    fun `an older copy on the phone gives no receipt`() =
        runTest {
            stored(updated = 4, syncVersion = 0, pending = true)

            assertNull(book.receiptFor(query(5)))
        }

    @Test
    fun `a note the phone deleted after receiving it is reported deleted`() =
        runTest {
            coEvery { notes.getNoteById(id) } returns null
            ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), 5)

            assertEquals(ReceiptState.DELETED_ON_PHONE, book.receiptFor(query(5))?.state)
        }

    @Test
    fun `a note the phone never received gives no receipt`() =
        runTest {
            coEvery { notes.getNoteById(id) } returns null

            assertNull(book.receiptFor(query(5)))
        }

    @Test
    fun `a kind this phone does not take this way is rejected for every version`() =
        runTest {
            assertEquals(
                Receipt(RecordKind.MOMENT, id.toString(), PhoneReceiptBook.ANY_VERSION, ReceiptState.REJECTED_UNSUPPORTED),
                book.receiptFor(ReceiptQuery(RecordKind.MOMENT, id.toString(), 5)),
            )
        }

    private fun stored(
        updated: Long,
        syncVersion: Long,
        pending: Boolean,
    ) {
        coEvery { notes.getNoteById(id) } returns
            JournalNote.Text(
                uid = id,
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(updated),
                content = "from the watch",
                syncVersion = syncVersion,
            )
        coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } returns pending
    }

    private fun query(version: Long) = ReceiptQuery(RecordKind.NOTE, id.toString(), version)
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchRecordIngestorV2Test.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.WatchNoteAcknowledger
import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.MediaRef
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WatchRecordIngestorV2]: a watch record becomes a phone note, and anything unreadable is refused by name. */
class WatchRecordIngestorV2Test {
    private val stored = mutableListOf<Map<String, String>>()
    private val audio = mutableListOf<Uuid>()
    private val acknowledged = mutableListOf<Pair<Uuid, Long>>()
    private val rejected = mutableListOf<Pair<RecordKind, String>>()
    private val notes = mockk<JournalNotesRepository>()
    private val ingestor =
        WatchRecordIngestorV2(
            ingestMetadata = { stored += it },
            ingestAudio = { id, _ -> audio += id },
            notes = notes,
            acknowledger = WatchNoteAcknowledger { id, at -> acknowledged += id to at.toEpochMilliseconds() },
            rejection = RecordRejection { kind, id -> rejected += kind to id },
        )
    private val text =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
            content = "said on the watch",
        )

    @Test
    fun `a text note is stored and acknowledged once the phone has it`() =
        runTest {
            coEvery { notes.getNoteById(text.uid) } returns text

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), dataMap(text))

            assertEquals(text, NoteDataMapper().fromDataMap(stored.single()))
            assertEquals(listOf(text.uid to 5L), acknowledged)
        }

    @Test
    fun `an audio note includes its file's size and digest`() =
        runTest {
            val recording =
                JournalNote.Audio(
                    mediaRef = "/data/user/0/app/files/audio_notes/a.m4a",
                    uid = Uuid.random(),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(5),
                )
            val media = MediaRef(sha256 = "ab".repeat(32), sizeBytes = 42, mimeType = "audio/mp4")
            val envelope = NoteEnvelopeBody.envelope(recording, RecordOrigin.WATCH, DeliveryPriority.URGENT, media = listOf(media))

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, recording.uid), EnvelopeDataMap.toDataMap(envelope, sentAt = 1))

            assertEquals(AudioFileDigest(sizeBytes = 42, sha256Hex = "ab".repeat(32)), NoteDataMapper.audioDigest(stored.single()))
            assertTrue(acknowledged.isEmpty())
        }

    @Test
    fun `a record from a newer version is rejected so the watch stops sending it`() =
        runTest {
            val newer = mapOf(EnvelopeDataMap.KEY_ENVELOPE to """{"schema":2,"kind":"NOTE","id":"${text.uid}"}""")

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), newer)

            assertEquals(listOf(RecordKind.NOTE to text.uid.toString()), rejected)
            assertTrue(stored.isEmpty())
        }

    @Test
    fun `a marked moment is rejected until this phone takes them`() =
        runTest {
            val id = Uuid.random()
            val moment =
                RecordEnvelope(
                    kind = RecordKind.MOMENT,
                    id = id.toString(),
                    contentVersion = 1,
                    origin = RecordOrigin.WATCH,
                    body = JsonObject(emptyMap()),
                )

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.MOMENT, id), EnvelopeDataMap.toDataMap(moment, sentAt = 1))

            assertEquals(listOf(RecordKind.MOMENT to id.toString()), rejected)
        }

    @Test
    fun `a body that is not a note is rejected`() =
        runTest {
            val broken =
                RecordEnvelope(
                    kind = RecordKind.NOTE,
                    id = text.uid.toString(),
                    contentVersion = 5,
                    origin = RecordOrigin.WATCH,
                    body = JsonPrimitive("x"),
                )

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), EnvelopeDataMap.toDataMap(broken, sentAt = 1))

            assertEquals(listOf(RecordKind.NOTE to text.uid.toString()), rejected)
            assertTrue(stored.isEmpty())
        }

    @Test
    fun `a recording goes to its note's audio`() =
        runTest {
            ingestor.onMedia(WearPaths.recordMedia(RecordKind.NOTE, text.uid), ByteArrayInputStream(ByteArray(3)))

            assertEquals(listOf(text.uid), audio)
        }

    private fun dataMap(note: JournalNote) =
        EnvelopeDataMap.toDataMap(NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT), sentAt = 1)
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptNotifierTest.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.WatchMessageSender
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsRequest
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WatchReceiptNotifier], [ReceiptAcknowledger] and [WatchReceiptResponder]: receipts pushed and answered. */
class WatchReceiptNotifierTest {
    private val notes = mockk<JournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val sessions = FakeSessions(OriginBoundSession("https://logdate.test", UserSession("a", "r", "account")))
    private val book = PhoneReceiptBook(notes, outbox, sessions, ledger)
    private val sent = mutableListOf<Pair<String, ByteArray>>()
    private var active = true
    private val notifier =
        WatchReceiptNotifier(
            book = book,
            ledger = ledger,
            sender =
                WatchMessageSender { path, data ->
                    sent += path to data
                    true
                },
            active = { active },
        )
    private val id = Uuid.random()

    @Test
    fun `a stored watch note is recorded and receipted as on the phone`() =
        runTest {
            stored(syncVersion = 0, pending = true)

            ReceiptAcknowledger(ledger, notifier).acknowledge(id, Instant.fromEpochMilliseconds(5))

            val entry = ledger.get(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString())
            assertEquals(DeliveryState.ON_PHONE, entry?.state)
            assertEquals(listOf(Receipt(RecordKind.NOTE, id.toString(), 5, ReceiptState.ON_PHONE)), pushedReceipts())
        }

    @Test
    fun `a note backed up since it arrived is pushed once and recorded`() =
        runTest {
            stored(syncVersion = 0, pending = true)
            ReceiptAcknowledger(ledger, notifier).acknowledge(id, Instant.fromEpochMilliseconds(5))
            sent.clear()
            stored(syncVersion = 7, pending = false)

            assertEquals(1, notifier.pushBackedUp())
            assertEquals(0, notifier.pushBackedUp())
            assertEquals(listOf(Receipt(RecordKind.NOTE, id.toString(), 5, ReceiptState.BACKED_UP, serverVersion = 7)), pushedReceipts())
        }

    @Test
    fun `nothing is pushed while the watch does not take receipts`() =
        runTest {
            active = false

            notifier.push(id.toString(), 5)

            assertTrue(sent.isEmpty())
        }

    @Test
    fun `a rejection covers every version`() =
        runTest {
            notifier.reject(RecordKind.MOMENT, id.toString())

            assertEquals(
                listOf(Receipt(RecordKind.MOMENT, id.toString(), PhoneReceiptBook.ANY_VERSION, ReceiptState.REJECTED_UNSUPPORTED)),
                pushedReceipts(),
            )
        }

    @Test
    fun `the phone answers a receipts request with its account and what it has`() =
        runTest {
            stored(syncVersion = 0, pending = true)
            val query = ReceiptsRequest(items = listOf(ReceiptQuery(RecordKind.NOTE, id.toString(), 5)))
            val request = WearJson.encodeToBytes(ReceiptsRequest.serializer(), query)

            val answer = decode(WatchReceiptResponder(book).answer(request))

            assertEquals(CloudPresence.SIGNED_IN, answer.cloud)
            assertEquals(listOf(ReceiptState.ON_PHONE), answer.receipts.map { it.state })
        }

    @Test
    fun `an unreadable request is answered with no receipts`() =
        runTest {
            assertTrue(decode(WatchReceiptResponder(book).answer("nope".encodeToByteArray())).receipts.isEmpty())
        }

    private fun stored(
        syncVersion: Long,
        pending: Boolean,
    ) {
        coEvery { notes.getNoteById(id) } returns
            JournalNote.Text(
                uid = id,
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(5),
                content = "from the watch",
                syncVersion = syncVersion,
            )
        coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } returns pending
    }

    private fun pushedReceipts(): List<Receipt> =
        sent.flatMap { (path, data) ->
            assertEquals(WearPaths.RECEIPTS_PUSH, path)
            decode(data).receipts
        }

    private fun decode(bytes: ByteArray): ReceiptsResponse =
        (WearJson.decode(bytes, WearProtocol.RECEIPTS_SCHEMA, ReceiptsResponse.serializer()) as Decoded.Supported).value
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.receipts.*' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'PhoneReceiptBook'", "Unresolved reference 'WatchRecordIngestorV2'", "Unresolved reference 'WatchReceiptNotifier'".

- [ ] **Step 3: Implement**

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/PhoneReceiptBook.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import kotlin.uuid.Uuid

/**
 * What the phone has of each record a watch sent, as a receipt. A note is backed up only when the phone
 * is signed in, the note has a server version, and no change to it is waiting in the outbox.
 */
class PhoneReceiptBook(
    private val notes: JournalNotesRepository,
    private val outbox: SyncMetadataService,
    private val sessions: SessionStorage,
    private val ledger: PeerDeliveryLedger,
) {
    fun cloudPresence(): CloudPresence = if (sessions.getSession() == null) CloudPresence.NO_ACCOUNT else CloudPresence.SIGNED_IN

    /** Null when the phone has no copy of that version yet, so the watch sends it again. */
    suspend fun receiptFor(query: ReceiptQuery): Receipt? {
        val id = Uuid.parseOrNull(query.id)
        if (query.kind != RecordKind.NOTE || id == null) return rejected(query.kind, query.id)
        val note = notes.getNoteById(id)
        if (note == null) {
            val received = ledger.get(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, query.id) != null
            return if (received) Receipt(RecordKind.NOTE, query.id, query.contentVersion, ReceiptState.DELETED_ON_PHONE) else null
        }
        val version = note.lastUpdated.toEpochMilliseconds()
        if (version < query.contentVersion) return null
        val backedUp = cloudPresence() == CloudPresence.SIGNED_IN && note.syncVersion > 0 && !outbox.hasPending(EntityType.NOTE, query.id)
        return Receipt(
            kind = RecordKind.NOTE,
            id = query.id,
            contentVersion = version,
            state = if (backedUp) ReceiptState.BACKED_UP else ReceiptState.ON_PHONE,
            serverVersion = note.syncVersion.takeIf { backedUp },
        )
    }

    companion object {
        /** A rejection names the highest version, so it covers every version of the record. */
        const val ANY_VERSION = Long.MAX_VALUE

        fun rejected(
            kind: RecordKind,
            id: String,
        ): Receipt = Receipt(kind, id, ANY_VERSION, ReceiptState.REJECTED_UNSUPPORTED)
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptResponder.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.ReceiptsRequest
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import io.github.aakira.napier.Napier

/** Answers a watch's receipts request with what this phone has of each record it asks about. */
class WatchReceiptResponder(
    private val book: PhoneReceiptBook,
) {
    suspend fun answer(payload: ByteArray): ByteArray {
        val receipts =
            when (val decoded = WearJson.decode(payload, WearProtocol.RECEIPTS_SCHEMA, ReceiptsRequest.serializer())) {
                is Decoded.Supported -> decoded.value.items.mapNotNull { book.receiptFor(it) }
                is Decoded.Unsupported -> {
                    Napier.w("A watch asked for receipts with schema ${decoded.schema}; it gets none until this phone updates")
                    emptyList()
                }
                is Decoded.Malformed -> {
                    Napier.e("A watch's receipts request could not be read: ${decoded.reason}")
                    emptyList()
                }
            }
        return WearJson.encodeToBytes(ReceiptsResponse.serializer(), ReceiptsResponse(cloud = book.cloudPresence(), receipts = receipts))
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptNotifier.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.WatchMessageSender
import app.logdate.client.sync.WatchNoteAcknowledger
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tells the watches that a record they sent cannot be read here, so they stop sending it. */
fun interface RecordRejection {
    suspend fun reject(
        kind: RecordKind,
        id: String,
    )
}

/**
 * Pushes receipts to the watches as soon as something changes: a note arriving, the phone's outbox
 * draining, a record refused. Pushes are best effort; the watch also asks with a request.
 */
class WatchReceiptNotifier(
    private val book: PhoneReceiptBook,
    private val ledger: PeerDeliveryLedger,
    private val sender: WatchMessageSender,
    private val active: suspend () -> Boolean,
    private val clock: Clock = Clock.System,
) : RecordRejection {
    suspend fun push(
        noteId: String,
        contentVersion: Long,
    ) {
        if (!active()) return
        val receipt = book.receiptFor(ReceiptQuery(RecordKind.NOTE, noteId, contentVersion)) ?: return
        send(listOf(receipt))
    }

    override suspend fun reject(
        kind: RecordKind,
        id: String,
    ) {
        if (!active()) return
        send(listOf(PhoneReceiptBook.rejected(kind, id)))
    }

    /** Pushes a backed-up receipt for each watch note that was only on the phone until now; returns how many. */
    suspend fun pushBackedUp(): Int {
        if (!active()) return 0
        val backedUp =
            ledger.inState(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, DeliveryState.ON_PHONE).mapNotNull { entry ->
                val receipt = book.receiptFor(ReceiptQuery(RecordKind.NOTE, entry.entityId, entry.contentVersion))
                if (receipt?.state == ReceiptState.BACKED_UP) entry to receipt else null
            }
        forgetOldSettled()
        if (backedUp.isEmpty()) return 0
        backedUp.forEach { (entry, _) -> ledger.moveTo(entry, DeliveryState.BACKED_UP) }
        send(backedUp.map { it.second })
        return backedUp.size
    }

    @OptIn(FlowPreview::class)
    fun start(
        scope: CoroutineScope,
        outboxChanges: Flow<Int>,
    ): Job =
        scope.launch {
            outboxChanges.debounce(SETTLE_MS).collect {
                try {
                    pushBackedUp()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Could not tell the watches which of their notes are backed up; they ask on their next sync", e)
                }
            }
        }

    /** A backed-up row is needed only while the phone might still upload a duplicate (Task 10); a month is plenty. */
    private suspend fun forgetOldSettled() {
        val cutoff = clock.now().toEpochMilliseconds() - KEEP_SETTLED_MS
        ledger
            .inState(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, DeliveryState.BACKED_UP)
            .filter { it.stateChangedAt < cutoff }
            .forEach { ledger.forget(it.scope, it.direction, it.entityType, it.entityId) }
    }

    private suspend fun send(receipts: List<Receipt>) {
        val response = ReceiptsResponse(cloud = book.cloudPresence(), receipts = receipts)
        val bytes = WearJson.encodeToBytes(ReceiptsResponse.serializer(), response)
        if (!sender.send(WearPaths.RECEIPTS_PUSH, bytes)) {
            Napier.w("No watch took the phone's ${receipts.size} receipt(s); each watch asks for them on its next sync")
        }
    }

    private companion object {
        const val SETTLE_MS = 2_000L
        const val KEEP_SETTLED_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** The version 2 acknowledgement: the note is recorded as received from the watch and an on-phone receipt goes out. */
class ReceiptAcknowledger(
    private val ledger: PeerDeliveryLedger,
    private val receipts: WatchReceiptNotifier,
) : WatchNoteAcknowledger {
    override suspend fun acknowledge(
        noteId: Uuid,
        lastUpdated: Instant,
    ) {
        val version = lastUpdated.toEpochMilliseconds()
        val entry = ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, noteId.toString(), version)
        if (entry.state == DeliveryState.NEW) ledger.moveTo(entry, DeliveryState.ON_PHONE)
        receipts.push(noteId.toString(), version)
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchRecordIngestorV2.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.WatchNoteAcknowledger
import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordPath
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import io.github.aakira.napier.Napier
import java.io.InputStream
import kotlin.uuid.Uuid

/**
 * Turns the version 2 records a watch writes into phone notes. Notes and recordings go through the same
 * ingest as version 1 ([ingestMetadata], [ingestAudio]), so a recording is stored only once its size and
 * SHA-256 match; a text note is acknowledged here once stored, a recording by the ingest once complete.
 */
class WatchRecordIngestorV2(
    private val ingestMetadata: suspend (Map<String, String>) -> Unit,
    private val ingestAudio: suspend (Uuid, InputStream) -> Unit,
    private val notes: JournalNotesRepository,
    private val acknowledger: WatchNoteAcknowledger,
    private val rejection: RecordRejection,
    private val mapper: NoteDataMapper = NoteDataMapper(),
) {
    suspend fun onRecord(
        path: String,
        data: Map<String, String>,
    ) {
        val recordPath = WearPaths.parseWatchRecord(path)
        if (recordPath == null) {
            Napier.w("Ignoring a data item that is not a watch record: $path")
            return
        }
        val envelope =
            when (val decoded = EnvelopeDataMap.fromDataMap(data)) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> return refuse(recordPath, "it was written by a newer version")
                is Decoded.Malformed -> return refuse(recordPath, decoded.reason)
            }
        if (envelope.kind != RecordKind.NOTE) return refuse(recordPath, "this phone takes only notes this way")
        if (envelope.id != recordPath.id.toString()) return refuse(recordPath, "its id does not match its path")
        val note = readNote(envelope) ?: return refuse(recordPath, "its body is not a note")
        ingestMetadata(dataMapFor(note, envelope))
        if (note !is JournalNote.Audio) acknowledgeIfStored(note)
    }

    suspend fun onMedia(
        path: String,
        input: InputStream,
    ) {
        val recordPath = WearPaths.parseRecordMedia(path)
        if (recordPath == null || recordPath.kind != RecordKind.NOTE) {
            Napier.w("Ignoring a recording on a channel that is not a note's: $path")
            return
        }
        ingestAudio(recordPath.id, input)
    }

    private fun readNote(envelope: RecordEnvelope): JournalNote? {
        val body = envelope.body ?: return null
        return try {
            NoteEnvelopeBody.decode(body, mapper)
        } catch (e: IllegalArgumentException) {
            Napier.e("A watch note's body could not be read", e)
            null
        }
    }

    private fun dataMapFor(
        note: JournalNote,
        envelope: RecordEnvelope,
    ): Map<String, String> {
        val map = mapper.toDataMap(note)
        val media = envelope.media.firstOrNull() ?: return map
        return NoteDataMapper.withAudioDigest(map, AudioFileDigest(sizeBytes = media.sizeBytes, sha256Hex = media.sha256))
    }

    private suspend fun acknowledgeIfStored(note: JournalNote) {
        val stored = notes.getNoteById(note.uid) ?: return
        acknowledger.acknowledge(note.uid, stored.lastUpdated)
    }

    private suspend fun refuse(
        path: RecordPath,
        why: String,
    ) {
        Napier.w("Refused a ${path.kind} record from the watch because $why")
        rejection.reject(path.kind, path.id.toString())
    }
}
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt`:

1. Add `val recordIngestorV2: WatchRecordIngestorV2? = null,` as the last property of `PhoneDataLayerSyncDependencies`, and `recordIngestorV2 = koin.get(),` in `resolveSyncDependencies()`.
2. In `processSnapshotEvent`, add as the first branch of the `when`: `WearPaths.parseWatchRecord(path) != null -> dependencies.recordIngestorV2?.onRecord(path, event.data)`.
3. At the start of `onChannelOpened`, add:

```kotlin
        if (WearPaths.parseRecordMedia(channel.path) != null) {
            serviceScope.launch { receiveRecordMedia(channel) }
            return
        }
```

4. After `receiveAudioChannel`, add:

```kotlin
    /** A version 2 recording from the watch; the watch sends it again when this fails. */
    private suspend fun receiveRecordMedia(channel: ChannelClient.Channel) =
        withContext(NonCancellable) {
            val ingestor = resolveSyncDependencies()?.recordIngestorV2
            if (ingestor == null) {
                Napier.e("Koin unavailable; the watch sends this recording again")
                closeChannel(channel)
                return@withContext
            }
            try {
                openChannelInput(channel).use { input -> ingestor.onMedia(channel.path, input) }
            } catch (e: Exception) {
                Napier.w("Failed to receive a recording from the watch", e)
            } finally {
                closeChannel(channel)
            }
        }

    /** Answers a watch's version 2 requests; when nothing can, the request fails and the watch asks again. */
    override fun onRequest(
        nodeId: String,
        path: String,
        request: ByteArray,
    ): Task<ByteArray> {
        val answer = TaskCompletionSource<ByteArray>()
        serviceScope.launch {
            val bytes = withContext(NonCancellable) { answerRequest(nodeId, path, request) }
            if (bytes != null) {
                answer.setResult(bytes)
            } else {
                answer.setException(IllegalArgumentException("This phone has no answer for $path"))
            }
        }
        return answer.task
    }

    protected open suspend fun answerRequest(
        nodeId: String,
        path: String,
        request: ByteArray,
    ): ByteArray? {
        val koin = resolveKoin()
        if (koin == null) {
            Napier.e("Koin unavailable after $MAX_KOIN_RETRIES retries; the watch's $path request fails and is asked again")
            return null
        }
        return when (path) {
            WearPaths.RECEIPTS -> koin.get<WatchReceiptResponder>().answer(request)
            else -> {
                Napier.w("A watch sent a request this phone does not answer: $path")
                null
            }
        }
    }
```

with imports `app.logdate.client.sync.v2.receipts.WatchReceiptResponder`, `app.logdate.client.sync.v2.receipts.WatchRecordIngestorV2`, `com.google.android.gms.tasks.Task` and `com.google.android.gms.tasks.TaskCompletionSource`.

In `app/android-main/src/main/AndroidManifest.xml`, in `PhoneDataLayerListenerService`: add to the `DATA_CHANGED` filter

```xml
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/v2/w2p/record" />
```

add to the `CHANNEL_EVENT` filter

```xml
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/v2/w2p/media" />
```

and add the filter

```xml
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.REQUEST_RECEIVED" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/v2/receipts" />
            </intent-filter>
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`, change the publisher's features to `setOf(WearFeatures.LINK_CLOUD, WearFeatures.RECEIPTS)` and add:

```kotlin
        single { PeerDeliveryLedger(get()) }
        single { PhoneReceiptBook(notes = get(), outbox = get(), sessions = get(), ledger = get()) }
        single { WatchReceiptResponder(get()) }
        single {
            val gate = get<WearProtocolGate>()
            WatchReceiptNotifier(
                book = get(),
                ledger = get(),
                sender = get(),
                active = { gate.isFeatureActive(WearFeatures.RECEIPTS).first() },
            )
        }
        single { ReceiptAcknowledger(get(), get()) }
        single {
            val acknowledger = get<ReceiptAcknowledger>()
            val ingestor =
                WatchNoteIngestor(
                    notesRepository = get(),
                    audioStore = get(),
                    acknowledger = acknowledger,
                    notifier = get(),
                    noteDataMapper = get(),
                )
            WatchRecordIngestorV2(
                ingestMetadata = ingestor::onNoteMetadata,
                ingestAudio = { id, input -> ingestor.onAudioBytes(id, input) },
                notes = get(),
                acknowledger = acknowledger,
                rejection = get<WatchReceiptNotifier>(),
                mapper = get(),
            )
        }
```

with imports `app.logdate.client.sync.WatchNoteIngestor`, `app.logdate.client.sync.datalayer.v2.WearProtocolGate`, `app.logdate.client.sync.peer.delivery.PeerDeliveryLedger`, `app.logdate.client.sync.v2.receipts.PhoneReceiptBook`, `app.logdate.client.sync.v2.receipts.ReceiptAcknowledger`, `app.logdate.client.sync.v2.receipts.WatchReceiptNotifier`, `app.logdate.client.sync.v2.receipts.WatchReceiptResponder`, `app.logdate.client.sync.v2.receipts.WatchRecordIngestorV2` and `kotlinx.coroutines.flow.first`.

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt`, call `startWatchReceipts()` after `startWatchLinking()` and add:

```kotlin
    /** Tells watches when their notes are backed up; a failure here must not stop startup. */
    private fun startWatchReceipts() {
        runCatching {
            get<WatchReceiptNotifier>().start(get(), get<SyncMetadataService>().observePendingCount())
        }.onFailure { error ->
            Napier.w("Failed to start telling watches which notes are backed up", error)
        }
    }
```

with imports `app.logdate.client.sync.metadata.SyncMetadataService` and `app.logdate.client.sync.v2.receipts.WatchReceiptNotifier`.

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again, then `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.PhoneDataLayerListenerServiceTest' --console=plain`.
Expected: PASS (7 book, 6 ingestor, 6 notifier and responder tests; every existing listener test unchanged).

- [ ] **Step 5: Run every affected module's checks and the merged manifest check**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck :app:android-main:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL. Then:
```bash
grep -c -e "/logdate/v2/w2p/record" -e "/logdate/v2/w2p/media" -e "/logdate/v2/receipts" app/android-main/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
```
Expected: `3`.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task09-phone-receipts.txt <<'EOF'
chore(wear): store watch captures on the phone and send receipts

With the redesigned watch app on, the phone now takes watch captures
sent the new way and answers with receipts: on the phone, backed up,
deleted on the phone, or not readable here. A capture goes through
the same checks as before, so a recording is stored only once its
size and digest match.

The phone pushes a receipt when a capture arrives and again once its
backup includes it, and answers the watch's receipt requests. A
record it cannot read, such as a marked moment, is refused for every
version, so the watch stops resending it. The phone now advertises
receipts.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/PhoneReceiptBook.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptResponder.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptNotifier.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchRecordIngestorV2.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt app/android-main/src/main/AndroidManifest.xml app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/MemoryPeerDeliveryDao.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/PhoneReceiptBookTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchRecordIngestorV2Test.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchReceiptNotifierTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task09-phone-receipts.txt
```

---

### Task 10: Settle a capture the watch already backed up (D4, fallback)

When a signed-in phone has not backed up a watch capture within 15 minutes, a linked watch uploads it itself with a strict create (Task 11). The phone still has the same note as a queued CREATE. Today its plain create sends `If-None-Match: *`, the server answers `412 CONTENT_EXISTS` (`SyncHelpers.kt:349-376`), and `SyncUploader` counts that as a failure until the note is set aside in Sync Issues (`SyncUploader.kt:498-545`). This task resolves that collision: a `CreateCollisionPolicy` says whether another device of this account created the record, and at which version; the uploader reads the server's version with `HEAD /contents/{id}` and either settles the create (same version: no second server version, and the next download brings the server's copy) or turns it into an edit of the server's version (the phone changed the note after receiving it). On the phone, `WatchCaptureCollisionPolicy` answers from the `FROM_WATCH` rows Task 9 records. The reverse order needs nothing new: a watch's strict create of a note the phone uploaded first is answered `AlreadyExists` and settled by the existing `ConditionalCreateSettler`. `WatchFallbackBackupE2ETest` proves both orders against the real server.

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/CreateCollision.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchCaptureCollisionPolicy.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt:45-52` (interface) and `:100-111` (`DefaultCloudContentDataSource`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncRetryCoordinator.kt:161-167` (new `releaseAttempt` after `endAttempt`)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncUploader.kt:41-90` (constructor, settler) and `:507-509` (failure branch)
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt:114-116` (constructor) and `:458-480` (`SyncUploader(` call)
- Modify: `client/sync/src/androidMain/kotlin/app/logdate/client/sync/di/SyncModule.android.kt:56-96`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt` (`testDefaultSyncManager` parameter, `FakeCloudApiClient.contentState`)
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/CreateCollisionTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchCaptureCollisionPolicyTest.kt`
- Test: `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/WatchFallbackBackupE2ETest.kt`

**Interfaces:**
- Consumes: `CloudApiClient.contentState(accessToken, contentId): Result<ContentState>`, `ContentState.Present/Absent/Deleted`, `CloudApiException.errorCode`; `SyncMetadataService.bindCreateToServerVersion(entityType, pending, serverVersion): Boolean`; `SyncRetryCoordinator.markUploadSettled(entityType, pending, syncedAt, version)`; Task 8's `PeerDeliveryLedger.get`, `DeliveryDirection.FROM_WATCH`, `PeerScopes.WATCHES`; Task 9's `PeerDeliveryLedger` binding on the phone.
- Produces:
  - `sealed interface CreateCollision { data object Unexpected; data class CreatedElsewhere(val contentVersion: Long) }`
  - `fun interface CreateCollisionPolicy { suspend fun collisionFor(entityType: EntityType, entityId: String): CreateCollision; companion object { val None: CreateCollisionPolicy } }`
  - `sealed interface CollisionOutcome { data class Settle(val serverVersion: Long); data class RebaseAsUpdate(val serverVersion: Long); data object Retry }`, `object CreateCollisionResolver { fun resolve(collision: CreateCollision, localContentVersion: Long, server: ContentState): CollisionOutcome }`
  - `internal class CreateCollisionSettler(policy, retryCoordinator, syncMetadataService, recordProgress) { suspend fun settle(entityType, pending, localContentVersion: Long, error: Throwable, serverCopy: suspend () -> Result<ContentState>): Boolean }`
  - `CloudContentDataSource.contentState(accessToken: String, noteId: Uuid): Result<ContentState>` (default: failure)
  - `SyncRetryCoordinator.releaseAttempt(entityType: EntityType, pending: PendingUpload)`
  - `DefaultSyncManager(..., createCollisionPolicy: CreateCollisionPolicy = CreateCollisionPolicy.None)` (last parameter); `testDefaultSyncManager(..., createCollisionPolicy = CreateCollisionPolicy.None)`
  - `class WatchCaptureCollisionPolicy(ledger: PeerDeliveryLedger) : CreateCollisionPolicy`

**Failure modes and how they surface:**
- A `412` for a record no device of this account is known to have created (any record without a `FROM_WATCH` row, or any device without a policy): unchanged, an upload failure that is retried and set aside in Sync Issues.
- The server's version cannot be read (network, an old server without `conditionalCreateV1`, which also never received a watch's strict create): the ordinary failure path, logged as "Could not read the server's copy...", retried.
- The server no longer has the record (deleted or gone between the `412` and the read): the ordinary failure path; the next plain create recreates it, as it would today.
- The create cannot be turned into an edit (its scope or operation changed meanwhile): the ordinary failure path, logged.
- A phone edit made after the watch's copy and a concurrent watch edit: the phone's edit goes up as an edit of the server's version, so the newer phone edit is the server's latest; this is the existing last-writer behaviour for edits.

- [ ] **Step 1: Write the failing tests**

In `client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt`:

1. In `testDefaultSyncManager`, after `profile: SyncProfile = SyncProfile.Standard,` add the parameter `createCollisionPolicy: CreateCollisionPolicy = CreateCollisionPolicy.None,`, and in both `DefaultSyncManager(` calls of that function, after `profile = profile,`, add `createCollisionPolicy = createCollisionPolicy,`.
2. In `FakeCloudApiClient`, after `val createContentIfAbsentCalls = mutableListOf<ContentUploadRequest>()` add:

```kotlin
    var contentStateResponse: Result<ContentState> = Result.success(ContentState.Absent)

    val contentStateCalls = mutableListOf<String>()
```

and after the `createContentIfAbsent` override add:

```kotlin
    override suspend fun contentState(
        accessToken: String,
        contentId: String,
    ): Result<ContentState> {
        methodCalls.add("contentState")
        contentStateCalls.add(contentId)
        return contentStateResponse
    }
```

3. Add the imports `app.logdate.client.sync.CreateCollisionPolicy` and `app.logdate.client.sync.cloud.ContentState` in sorted position.

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/CreateCollisionTest.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.ContentState
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests [CreateCollisionResolver] and the upload pass's handling of a create the server refused because
 * another device of the account, such as a linked watch, uploaded the record first.
 */
class CreateCollisionTest {
    @Test
    fun `a record another device created at this version settles against the server's`() {
        assertEquals(
            CollisionOutcome.Settle(9),
            CreateCollisionResolver.resolve(CreateCollision.CreatedElsewhere(5), localContentVersion = 5, server = ContentState.Present(9)),
        )
    }

    @Test
    fun `an edit made here after the other device's copy goes up as an edit`() {
        assertEquals(
            CollisionOutcome.RebaseAsUpdate(9),
            CreateCollisionResolver.resolve(CreateCollision.CreatedElsewhere(5), localContentVersion = 6, server = ContentState.Present(9)),
        )
    }

    @Test
    fun `a record no other device made stays an ordinary failure`() {
        assertEquals(
            CollisionOutcome.Retry,
            CreateCollisionResolver.resolve(CreateCollision.Unexpected, localContentVersion = 5, server = ContentState.Present(9)),
        )
    }

    @Test
    fun `a record gone from the server is created again on the next attempt`() {
        val elsewhere = CreateCollision.CreatedElsewhere(5)

        assertEquals(CollisionOutcome.Retry, CreateCollisionResolver.resolve(elsewhere, 5, ContentState.Absent))
        val deleted = ContentState.Deleted(Instant.fromEpochMilliseconds(1))
        assertEquals(CollisionOutcome.Retry, CreateCollisionResolver.resolve(elsewhere, 5, deleted))
    }

    @Test
    fun `a phone upload the watch already made settles without a second version`() =
        runTest {
            val fixture = collidingNote()
            val manager = fixture.manager(CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(fixture.version) })

            val result = manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
            assertEquals(1, fixture.api.uploadContentCalls.size)
            assertTrue(fixture.api.updateContentCalls.isEmpty())
            assertEquals(listOf(fixture.note.uid.toString()), fixture.api.contentStateCalls)
            assertEquals(0L, fixture.notes.getNoteById(fixture.note.uid)?.syncVersion)
        }

    @Test
    fun `a phone edit newer than the watch's upload goes up as one edit`() =
        runTest {
            val fixture = collidingNote()
            val manager = fixture.manager(CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(fixture.version - 1) })

            val first = manager.uploadPendingChanges()

            assertTrue(first.success, first.errors.toString())
            val rebased = fixture.metadata.getPendingUploads(EntityType.NOTE).single()
            assertEquals(PendingOperation.UPDATE, rebased.operation)
            assertEquals(9L, rebased.expectedServerVersion)

            val second = manager.uploadPendingChanges()

            assertTrue(second.success, second.errors.toString())
            assertEquals(9L, fixture.api.updateContentCalls.single().third.syncVersion)
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `without a policy the conflict stays an upload failure`() =
        runTest {
            val fixture = collidingNote()

            val result = fixture.manager(CreateCollisionPolicy.None).uploadPendingChanges()

            assertFalse(result.success)
            assertEquals(PendingOperation.CREATE, fixture.metadata.getPendingUploads(EntityType.NOTE).single().operation)
            assertTrue(fixture.api.contentStateCalls.isEmpty())
        }

    @Test
    fun `when the server's copy cannot be read the create stays queued`() =
        runTest {
            val fixture = collidingNote()
            fixture.api.contentStateResponse = Result.failure(CloudApiException("NETWORK_ERROR", "Failed to read content state"))

            val manager = fixture.manager(CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(fixture.version) })

            val result = manager.uploadPendingChanges()

            assertFalse(result.success)
            assertEquals(PendingOperation.CREATE, fixture.metadata.getPendingUploads(EntityType.NOTE).single().operation)
        }

    private suspend fun collidingNote(): Fixture {
        val api = fakeCloudApiClient()
        api.uploadContentResponse = Result.failure(CloudApiException("CONTENT_EXISTS", "Request failed (HTTP 412)", statusCode = 412))
        api.contentStateResponse = Result.success(ContentState.Present(9))
        val notes = fakeJournalNotesRepository("said on the watch")
        val metadata = fakeSyncMetadataService()
        val note = notes.allNotesObserved.first().single()
        metadata.enqueuePending(note.uid.toString(), EntityType.NOTE, PendingOperation.CREATE)
        return Fixture(api, notes, metadata, note)
    }

    private class Fixture(
        val api: FakeCloudApiClient,
        val notes: FakeJournalNotesRepository,
        val metadata: FakeSyncMetadataService,
        val note: JournalNote,
    ) {
        val version: Long = note.lastUpdated.toEpochMilliseconds()

        fun manager(policy: CreateCollisionPolicy) =
            testDefaultSyncManager(
                cloudContentDataSource = DefaultCloudContentDataSource(api),
                journalNotesRepository = notes,
                syncMetadataService = metadata,
                createCollisionPolicy = policy,
            )
    }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchCaptureCollisionPolicyTest.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.CreateCollision
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [WatchCaptureCollisionPolicy]: only notes received from a watch may already be on the server. */
class WatchCaptureCollisionPolicyTest {
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val policy = WatchCaptureCollisionPolicy(ledger)

    @Test
    fun `a note received from a watch may have been backed up by it`() =
        runTest {
            ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, "a", 5)

            assertEquals(CreateCollision.CreatedElsewhere(5), policy.collisionFor(EntityType.NOTE, "a"))
        }

    @Test
    fun `a note made on the phone was created nowhere else`() =
        runTest {
            assertEquals(CreateCollision.Unexpected, policy.collisionFor(EntityType.NOTE, "b"))
        }

    @Test
    fun `other kinds of record are never a watch's`() =
        runTest {
            ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, "a", 5)

            assertEquals(CreateCollision.Unexpected, policy.collisionFor(EntityType.JOURNAL, "a"))
        }
}
```

Create `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/WatchFallbackBackupE2ETest.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.DefaultServerDiscoveryClient
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.CreateCollision
import app.logdate.client.sync.CreateCollisionPolicy
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.ContentState
import app.logdate.client.sync.cloud.ContentUploadRequest
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * The watch's fallback backup against the real server: when a linked watch and its phone both upload
 * the same capture, in either order, the server ends up with exactly one version of it; an edit the phone made
 * after receiving the capture goes up as one edit of that version.
 */
class WatchFallbackBackupE2ETest {
    @Test
    fun `a capture the watch backed up first leaves exactly one server version`() =
        runTest {
            withServerClientHarness {
                val pair = linkWatch("fallback_once")
                val note = capture("said on the watch")
                val watchCreate = pair.client.createContentIfAbsent(pair.watchToken, request(note)).getOrThrow()
                val created = assertIs<ConditionalCreateResult.Created>(watchCreate)

                val received = note.lastUpdated.toEpochMilliseconds()
                withPhoneEngine(pair, note, receivedVersion = received, scope = backgroundScope) { manager, metadata ->
                    val result = manager.uploadPendingChanges()

                    assertTrue(result.success, result.errors.toString())
                    assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
                }

                assertEquals(listOf(created.serverVersion), serverVersions(pair, note.uid))
                val state = pair.client.contentState(pair.phoneToken, note.uid.toString()).getOrThrow()
                assertEquals(ContentState.Present(created.serverVersion), state)
            }
        }

    @Test
    fun `a capture the phone backed up first is settled by the watch's strict create`() =
        runTest {
            withServerClientHarness {
                val pair = linkWatch("fallback_phone_first")
                val note = capture("said on the watch")
                val uploaded = pair.client.uploadContent(pair.phoneToken, request(note)).getOrThrow()

                val again = pair.client.createContentIfAbsent(pair.watchToken, request(note)).getOrThrow()

                assertEquals(ConditionalCreateResult.AlreadyExists(uploaded.serverVersion), again)
                assertEquals(listOf(uploaded.serverVersion), serverVersions(pair, note.uid))
            }
        }

    @Test
    fun `an edit the phone made after the watch's backup goes up as one edit`() =
        runTest {
            withServerClientHarness {
                val pair = linkWatch("fallback_edit")
                val original = capture("said on the watch")
                val edited =
                    original.copy(content = "said on the watch and fixed on the phone", lastUpdated = original.lastUpdated + 1.seconds)
                val watchCreate = pair.client.createContentIfAbsent(pair.watchToken, request(original)).getOrThrow()
                val created = assertIs<ConditionalCreateResult.Created>(watchCreate)

                val received = original.lastUpdated.toEpochMilliseconds()
                withPhoneEngine(pair, edited, receivedVersion = received, scope = backgroundScope) { manager, metadata ->
                    val first = manager.uploadPendingChanges()
                    assertTrue(first.success, first.errors.toString())
                    assertEquals(PendingOperation.UPDATE, metadata.getPendingUploads(EntityType.NOTE).single().operation)

                    val second = manager.uploadPendingChanges()
                    assertTrue(second.success, second.errors.toString())
                    assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
                }

                val change =
                    pair.client
                        .getContentChanges(pair.phoneToken, 0L)
                        .getOrThrow()
                        .changes
                        .single { it.id == original.uid.toString() }
                assertEquals(edited.content, change.content)
                assertTrue(change.serverVersion > created.serverVersion)
            }
        }

    private class LinkedPair(
        val origin: String,
        val config: DefaultLogDateConfigRepository,
        val client: LogDateCloudApiClient,
        val phoneAccountId: String,
        val phoneToken: String,
        val phoneRefreshToken: String,
        val watchToken: String,
    )

    private suspend fun ServerClientE2EHarness.linkWatch(prefix: String): LinkedPair {
        val origin = baseUrl.removeSuffix("/api/v1")
        val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
        val descriptor = DefaultServerDiscoveryClient(httpClient).discoverServer(origin).getOrThrow()
        config.updateServerDescriptor(descriptor.copy(serverOrigin = origin))
        val phone = apiClient.createAccountWithSyntheticPasskey("${prefix}_${Random.nextInt(100000, 999999)}").data
        val links = DeviceLinkApiClient(httpClient, config)
        val watch = links.redeem(links.mintLinkCode(phone.tokens.accessToken).getOrThrow().code, "Pixel Watch 3").getOrThrow()
        return LinkedPair(
            origin = origin,
            config = config,
            client = LogDateCloudApiClient(config, httpClient),
            phoneAccountId = phone.account.id.toString(),
            phoneToken = phone.tokens.accessToken,
            phoneRefreshToken = phone.tokens.refreshToken,
            watchToken = watch.tokens.accessToken,
        )
    }

    /** The phone's real upload pass over a Room outbox with [note] queued as a create it received from the watch. */
    private suspend fun ServerClientE2EHarness.withPhoneEngine(
        pair: LinkedPair,
        note: JournalNote.Text,
        receivedVersion: Long,
        scope: CoroutineScope,
        block: suspend (DefaultSyncManager, DatabaseSyncMetadataService) -> Unit,
    ) {
        val file = Files.createTempFile("watch-fallback-backup", ".db")
        val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
        try {
            val owner = pair.phoneAccountId
            val phoneOrigin = pair.origin
            val ownerProvider = mockk<CanonicalOwnerProvider> { coEvery { getCanonicalOwnerId() } returns owner }
            val metadata = DatabaseSyncMetadataService(database.syncMetadataDao(), pair.config, ownerProvider)
            val transactions = RoomSyncTransactionManager(database)
            val inbox =
                DownloadInbox(
                    database.downloadInboxDao(),
                    transactions,
                    { DownloadScope(owner, phoneOrigin) },
                    { System.currentTimeMillis() },
                )
            val notes =
                mockk<SyncableJournalNotesRepository>(relaxed = true) {
                    every { allNotesObserved } returns flowOf(listOf(note))
                    coEvery { getNoteById(note.uid) } returns note
                }
            metadata.enqueuePending(note.uid.toString(), EntityType.NOTE, PendingOperation.CREATE)
            val session = UserSession(pair.phoneToken, pair.phoneRefreshToken, owner)
            val sessions =
                mockk<SessionStorage> {
                    every { getSession() } returns session
                    every { getSessionFlow() } returns flowOf(session)
                    every { getOriginBoundSession() } returns OriginBoundSession(phoneOrigin, session)
                }
            val accounts =
                object : CloudAccountRepository by mockk(relaxed = true), CloudRequestLocationProvider {
                    override fun captureLocation() = CloudRequestLocation(phoneOrigin, baseUrl)

                    override fun isCurrentOrigin(origin: String) = origin == phoneOrigin
                }
            val retrySchedule =
                mockk<SyncRetryScheduleStore>(relaxed = true) {
                    coEvery { nextAttemptAt(any(), any()) } returns null
                }
            val manager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(DurableCloudApiClient(pair.client, inbox)),
                    cloudJournalDataSource = mockk(relaxed = true),
                    cloudAssociationDataSource = mockk(relaxed = true),
                    cloudMediaDataSource = mockk(relaxed = true),
                    cloudDraftDataSource = mockk(relaxed = true),
                    cloudAccountRepository = accounts,
                    sessionStorage = sessions,
                    mediaManager = mockk(relaxed = true),
                    mediaSyncRefStore = mockk(relaxed = true),
                    journalRepository = mockk(relaxed = true),
                    journalNotesRepository = notes,
                    journalContentRepository = mockk(relaxed = true),
                    journalConflictResolver = mockk(relaxed = true),
                    noteConflictResolver = mockk(relaxed = true),
                    conflictStore = mockk(relaxed = true),
                    deadLetterStore = mockk(relaxed = true),
                    retryScheduleStore = retrySchedule,
                    syncMetadataService = metadata,
                    transactionManager = transactions,
                    dataUsagePolicy = mockk(relaxed = true),
                    cloudApiClient = pair.client,
                    downloadInbox = inbox,
                    syncScope = scope,
                    createCollisionPolicy = CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(receivedVersion) },
                )
            block(manager, metadata)
        } finally {
            database.close()
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
        }
    }

    private suspend fun serverVersions(
        pair: LinkedPair,
        id: Uuid,
    ): List<Long> =
        pair.client
            .getContentChanges(pair.phoneToken, 0L)
            .getOrThrow()
            .changes
            .filter { it.id == id.toString() }
            .map { it.serverVersion }

    private fun capture(text: String): JournalNote.Text {
        val now = Clock.System.now()
        return JournalNote.Text(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, content = text)
    }

    private fun request(note: JournalNote.Text) =
        ContentUploadRequest(
            id = note.uid.toString(),
            type = "TEXT",
            content = note.content,
            mediaUri = null,
            createdAt = note.creationTimestamp.toEpochMilliseconds(),
            lastUpdated = note.lastUpdated.toEpochMilliseconds(),
        )
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.CreateCollisionTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'CreateCollisionPolicy'", "Unresolved reference 'CreateCollision'", "Unresolved reference 'CreateCollisionResolver'". (`FakeCloudApiClient.contentState` compiles already: `CloudApiClient.contentState` exists.)

- [ ] **Step 3: Implement**

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/CreateCollision.kt`:

```kotlin
package app.logdate.client.sync

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.ContentState
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlin.time.Clock

/** What this device knows about a record the server already had when this device created it. */
sealed interface CreateCollision {
    /** Nothing says another device made it, so the refusal stays an upload failure. */
    data object Unexpected : CreateCollision

    /** Another device of this account made it; [contentVersion] is the version this device received from it. */
    data class CreatedElsewhere(
        val contentVersion: Long,
    ) : CreateCollision
}

/** Tells the upload pass which records another device of this account, such as a linked watch, may have created first. */
fun interface CreateCollisionPolicy {
    suspend fun collisionFor(
        entityType: EntityType,
        entityId: String,
    ): CreateCollision

    companion object {
        val None = CreateCollisionPolicy { _, _ -> CreateCollision.Unexpected }
    }
}

/** What to do with a create the server refused because the record already exists. */
sealed interface CollisionOutcome {
    /** The server's version is the one this device has: the create is done, and the next download brings the server's copy. */
    data class Settle(
        val serverVersion: Long,
    ) : CollisionOutcome

    /** This device changed the record after the other device's copy: the change goes up as an edit of [serverVersion]. */
    data class RebaseAsUpdate(
        val serverVersion: Long,
    ) : CollisionOutcome

    /** An ordinary failure: retried, and set aside after repeated failures. */
    data object Retry : CollisionOutcome
}

object CreateCollisionResolver {
    fun resolve(
        collision: CreateCollision,
        localContentVersion: Long,
        server: ContentState,
    ): CollisionOutcome {
        if (collision !is CreateCollision.CreatedElsewhere || server !is ContentState.Present) return CollisionOutcome.Retry
        if (localContentVersion > collision.contentVersion) return CollisionOutcome.RebaseAsUpdate(server.serverVersion)
        return CollisionOutcome.Settle(server.serverVersion)
    }
}

/**
 * Resolves a plain create the server refused with `CONTENT_EXISTS` for a record another device of this
 * account created first, so the same capture never becomes a second server version or a set-aside upload.
 */
internal class CreateCollisionSettler(
    private val policy: CreateCollisionPolicy,
    private val retryCoordinator: SyncRetryCoordinator,
    private val syncMetadataService: SyncMetadataService,
    private val recordProgress: (Int) -> Unit,
) {
    /** Returns true when [error] was such a create and this device resolved it; false leaves it to the failure path. */
    suspend fun settle(
        entityType: EntityType,
        pending: PendingUpload,
        localContentVersion: Long,
        error: Throwable,
        serverCopy: suspend () -> Result<ContentState>,
    ): Boolean {
        if (pending.operation != PendingOperation.CREATE || (error as? CloudApiException)?.errorCode != CONTENT_EXISTS) return false
        val collision = policy.collisionFor(entityType, pending.entityId)
        if (collision is CreateCollision.Unexpected) return false
        val server =
            serverCopy().getOrElse {
                Napier.w("Could not read the server's copy of a record another device created; the upload is retried")
                return false
            }
        return when (val outcome = CreateCollisionResolver.resolve(collision, localContentVersion, server)) {
            is CollisionOutcome.Settle -> {
                retryCoordinator.markUploadSettled(entityType, pending, Clock.System.now(), outcome.serverVersion)
                recordProgress(1)
                Napier.i("Another device of this account backed this record up first; the next download brings the server's copy")
                true
            }
            is CollisionOutcome.RebaseAsUpdate -> rebase(entityType, pending, outcome.serverVersion)
            CollisionOutcome.Retry -> {
                Napier.w("The server no longer has a record another device created; the upload is retried")
                false
            }
        }
    }

    private suspend fun rebase(
        entityType: EntityType,
        pending: PendingUpload,
        serverVersion: Long,
    ): Boolean {
        if (!syncMetadataService.bindCreateToServerVersion(entityType, pending, serverVersion)) {
            Napier.w("Could not turn a create into an edit of the server's copy; the upload is retried")
            return false
        }
        retryCoordinator.releaseAttempt(entityType, pending)
        Napier.i("This device changed a record after another device backed it up; the change goes up as an edit")
        return true
    }

    private companion object {
        const val CONTENT_EXISTS = "CONTENT_EXISTS"
    }
}
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt`, after the interface's `createNoteIfAbsent` add:

```kotlin
    /** Reads whether the server has this note, and at which version, without downloading it. */
    suspend fun contentState(
        accessToken: String,
        noteId: Uuid,
    ): Result<ContentState> = Result.failure(UnsupportedOperationException("Content state is not supported by this data source"))
```

and in `DefaultCloudContentDataSource`, after its `createNoteIfAbsent` override:

```kotlin
    override suspend fun contentState(
        accessToken: String,
        noteId: Uuid,
    ): Result<ContentState> = cloudApiClient.contentState(accessToken, noteId.toString())
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncRetryCoordinator.kt`, after the private `endAttempt` function add:

```kotlin
    /** Ends the attempt for [pending] without settling or counting it: its operation was changed in place and goes up next. */
    suspend fun releaseAttempt(
        entityType: EntityType,
        pending: PendingUpload,
    ) {
        endAttempt(entityType, pending.retryKey())
        retryScheduleStore.clear(entityType, pending.retryKey())
        clearFailureKind(entityType, pending.retryKey())
    }
```

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncUploader.kt`:

1. After `private val profile: SyncProfile = SyncProfile.Standard,` add the constructor parameter `createCollisionPolicy: CreateCollisionPolicy = CreateCollisionPolicy.None,`.
2. After `private val conditionalCreates = ConditionalCreateSettler(retryCoordinator, recordProgress)` add:

```kotlin
    private val collisions = CreateCollisionSettler(createCollisionPolicy, retryCoordinator, syncMetadataService, recordProgress)
```

3. In `uploadContent`, directly after `val error = result.exceptionOrNull() ?: Exception("Unknown upload error")` add:

```kotlin
                            val serverCopy: suspend () -> Result<ContentState> = {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.contentState(token, noteId) },
                                    "contentState($noteId)",
                                    expectedScope = pending.scope,
                                )
                            }
                            val resolved =
                                collisions.settle(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    localContentVersion = note.lastUpdated.toEpochMilliseconds(),
                                    error = error,
                                    serverCopy = serverCopy,
                                )
                            if (resolved) continue
```

4. Add `import app.logdate.client.sync.cloud.ContentState`.

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt`, after `internal val profile: SyncProfile = SyncProfile.Standard,` add:

```kotlin
    /** Which refused creates another device of this account made first; the phone answers for its watches. */
    private val createCollisionPolicy: CreateCollisionPolicy = CreateCollisionPolicy.None,
```

and in the `SyncUploader(` call, after `profile = profile,`, add `createCollisionPolicy = createCollisionPolicy,`.

In `client/sync/src/androidMain/kotlin/app/logdate/client/sync/di/SyncModule.android.kt`, after `locationHistorySyncEngine = get(),` add `createCollisionPolicy = getOrNull<CreateCollisionPolicy>() ?: CreateCollisionPolicy.None,` and `import app.logdate.client.sync.CreateCollisionPolicy`.

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchCaptureCollisionPolicy.kt`:

```kotlin
package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.CreateCollision
import app.logdate.client.sync.CreateCollisionPolicy
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes

/**
 * A note this phone received from a watch may already be on the server, because a linked watch backs
 * up a capture itself when the phone has not within 15 minutes. Nothing else this phone uploads was
 * created elsewhere.
 */
class WatchCaptureCollisionPolicy(
    private val ledger: PeerDeliveryLedger,
) : CreateCollisionPolicy {
    override suspend fun collisionFor(
        entityType: EntityType,
        entityId: String,
    ): CreateCollision {
        if (entityType != EntityType.NOTE) return CreateCollision.Unexpected
        val received =
            ledger.get(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, entityId) ?: return CreateCollision.Unexpected
        return CreateCollision.CreatedElsewhere(received.contentVersion)
    }
}
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`, after `single { PeerDeliveryLedger(get()) }` add:

```kotlin
        single<CreateCollisionPolicy> { WatchCaptureCollisionPolicy(get()) }
```

with imports `app.logdate.client.sync.CreateCollisionPolicy` and `app.logdate.client.sync.v2.receipts.WatchCaptureCollisionPolicy`.

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again, then:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.LinkedWatchProfileTest' --tests 'app.logdate.client.sync.recovery.LegacyRepairCycleTest' :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.receipts.WatchCaptureCollisionPolicyTest' --console=plain
```
Expected: PASS (8 `CreateCollisionTest` tests, 3 policy tests, and every existing linked-watch and legacy repair test unchanged; the legacy repair cycle's own `CONTENT_EXISTS` case has no policy and behaves as before).

- [ ] **Step 5: Run the end-to-end test against the real server**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :integration:server-client-e2e:test --tests 'app.logdate.integration.e2e.journeys.WatchFallbackBackupE2ETest' --console=plain
```
Expected: PASS (3 tests). If the edit test fails with a version conflict, print `second.errors` and check that `DatabaseSyncMetadataService.bindCreateToServerVersion` returned true (the pending scope must match the config's backend URL and the owner); do not loosen the assertion.

- [ ] **Step 6: Run every affected module's checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt :app:android-main:testDebugUnitTest :app:compose-main:ktlintCheck :app:compose-main:detekt :integration:server-client-e2e:test :integration:server-client-e2e:ktlintCheck :integration:server-client-e2e:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-collision-settle.txt <<'EOF'
chore(sync): settle a capture the watch already backed up

When a linked watch backs up a capture itself, the phone still has
the same note waiting to upload. The server refuses the phone's create
because the note exists, and the phone used to count that as a failure
until the note was set aside in Sync Issues.

The phone now reads the server's version of a note it received from a
watch when that happens. If its copy is the one the watch sent, the
upload is settled and the next download brings the server's copy, so
the server has one version. If the phone changed the note after
receiving it, the change goes up as an edit of the server's version.
Any other refused create fails as before. A test against the real
server covers both upload orders and the edit.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/CreateCollision.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncRetryCoordinator.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/SyncUploader.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt client/sync/src/androidMain/kotlin/app/logdate/client/sync/di/SyncModule.android.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/test/SyncTestFakes.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/CreateCollisionTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/receipts/WatchCaptureCollisionPolicy.kt app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/receipts/WatchCaptureCollisionPolicyTest.kt integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/WatchFallbackBackupE2ETest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task10-collision-settle.txt
```

---

### Task 11: Send watch captures with receipts and back them up (D4, watch)

The watch's side of version 2 delivery. While the phone lists `receipts.v1`, the version 1 manager hands its note pass to `WatchNoteDelivery` through a one-line seam (`WatchNotesV2`), so journals, links and health snapshots still travel the version 1 way under the same lock. Each queued note gets a ledger row and is routed by Task 8's `WatchSyncRouter`: sent to the phone as a `RecordEnvelope` data item (and its recording over `WearPaths.recordMedia`), settled when a receipt says it is backed up, deleted on the phone, or on a phone with no account, released to the watch's own cloud engine (Task 7) when the router says so, or made to wait with a named reason and a time to look again. Receipts arrive by push (`/logdate/v2/receipts/push`, through `WearPhoneMessageHandler`) and are asked for at the start of every run with the phone in reach. A removal still uses the version 1 removal signal, which the phone already honours and which the Data Layer delivers whenever the phone is next in reach; out of reach, a linked watch also removes the note from the cloud. The cloud engine sees only the notes the router released (`ReleasedToCloud`). `WatchSyncScheduler` wakes the watch at the ledger's earliest time while version 2 sends notes. The watch starts advertising `receipts.v1`.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/DataLayerTransportV2.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchReceiptApplier.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchRouteConditions.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/ReleasedToCloud.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchNoteDelivery.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt:57-73` (constructor) and `:96-99` (`uploadPendingChangesLocked`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearPhoneMessageHandler.kt:13-28`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt:37-82`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt:106-134`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearCloudSyncModule.kt` (the `CloudEligibility` binding)
- Modify: `app/wear/src/main/AndroidManifest.xml:105-115`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/MemoryPeerDeliveryDao.kt` (test helper, Task 8's content with package `app.logdate.wear.sync.v2.delivery`)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchNoteDeliveryTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchReceiptApplierTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchRouteConditionsTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/GoogleDataLayerTransportV2Test.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerV2Test.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WatchSyncSchedulerTest.kt` (2 new tests)
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt` (2 new tests)
- Test: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt` (`RECEIPTS_PUSH`)

**Interfaces:**
- Consumes: Task 8's `PeerDeliveryLedger`, `PeerDelivery`, `DeliveryState`, `HoldReason`, `DeliveryDirection`, `PeerScopes`, `WatchSyncRouter` (`BACKUP_GRACE_MS`, `RECEIPT_WAIT_MS`), `Route`, `RouteInput`, `RouteConditions`, `PhoneReach`, `WatchNetwork`, `watchNetworkOf`, `EnvelopeDataMap`, `NoteEnvelopeBody`, `WearPaths.recordMedia`; Task 7's `WatchCloud` engine and `CloudEligibility` binding; Task 4's `WatchLinkStore.current()`, `peerLinkState()`; Task 6's `NetworkSaverModeProvider` and `BatteryInfoProvider` bindings; `WearDataLayerClient.putDataItem/deleteOwnDataItem/sendFile`; `PhoneNodeSelector.select()`; `MessageClient.sendRequest(nodeId, path, data)`; `AudioFileDigester`; `ReceiptsRequest`, `ReceiptsResponse`, `Receipt`, `ReceiptQuery`; `ServerDescriptor.hasProtocolFeature`, `ServerProtocolFeature.DEVICE_LINKING_V1/CONDITIONAL_CREATE_V1`.
- Produces:
  - `interface DataLayerTransportV2 { suspend fun reach(): PhoneReach; suspend fun put(path: String, data: Map<String, String>): Boolean; suspend fun deleteOwn(path: String): Boolean; suspend fun sendFile(channelPath: String, localFilePath: String): Boolean; suspend fun request(path: String, payload: ByteArray): Result<ByteArray> }`, `class GoogleDataLayerTransportV2(dataLayer: WearDataLayerClient, phones: PhoneNodeSelector, messageClient: MessageClient, timeoutMs: Long = 10_000L)`
  - `class WatchReceiptApplier(ledger: PeerDeliveryLedger) { fun phoneCloud(): CloudPresence?; suspend fun apply(payload: ByteArray): Int }`
  - `class WatchRouteConditions(transport, phoneCloud: suspend () -> CloudPresence, links: WatchLinkStore, config: LogDateConfigRepository, network: NetworkSaverModeProvider, battery: BatteryInfoProvider) { suspend fun current(): RouteConditions }`, `internal fun serverReady(descriptor: ServerDescriptor?): Boolean`
  - `class ReleasedToCloud(ledger: PeerDeliveryLedger) : CloudEligibility`
  - `fun interface WatchNotesV2 { suspend fun deliverIfActive(): SyncResult? }`, `fun interface CloudRelease { suspend fun upload(): SyncResult }`, `internal object HoldTimes { const val RECHECK_MS; fun recheckAt(reason: HoldReason, entry: PeerDelivery, now: Long): Long?; fun sendRetryDelay(attempts: Int): Long }`
  - `class WatchNoteDelivery(active: suspend () -> Boolean, outbox: SyncMetadataService, notes: JournalNotesRepository, ledger: PeerDeliveryLedger, transport: DataLayerTransportV2, conditions: suspend () -> RouteConditions, receipts: WatchReceiptApplier, cloud: CloudRelease, digester: AudioFileDigester = FileAudioDigester(), router: WatchSyncRouter = WatchSyncRouter(), clock: Clock = Clock.System) : WatchNotesV2 { suspend fun schedule(): NoteSchedule }`
  - `sealed interface NoteSchedule { data object Outbox; data class Ledger(val dueAt: Long?) }` and `WatchSyncScheduler(..., noteSchedule: suspend () -> NoteSchedule = { NoteSchedule.Outbox })`
  - `WearDataLayerSyncManager(..., notesV2: WatchNotesV2? = null)`; `WearPhoneMessageHandler(ackHandler, syncManager, receiptsPushed: suspend (ByteArray) -> Int = { 0 })`

**Failure modes and how they surface:**
- A data item or recording that cannot be sent: the note waits as `SEND_FAILED` with a retry time (30 seconds after the first failure, doubling, at most six hours), the run reports a retryable `NETWORK_ERROR`, and the log names what failed.
- A recording that cannot be read for its digest: the same `SEND_FAILED` wait, logged as "its recording could not be read".
- A note too large for one data item: marked `REJECTED` with an error log; a linked watch uploads it itself when it can, otherwise it waits as `PHONE_CANNOT_READ`. (Bodies over 90 KB on a channel are D6.)
- No answer to a receipts request, or receipts this build cannot read: logged; the notes wait and the watch asks again on its next run.
- The cloud engine failing: its errors are returned in the run's result and logged; the note stays queued and released, and the engine's own retry times and set-aside list apply; the ledger looks at it again after 15 minutes.
- No way to deliver (phone out of reach and not linked, an old server, no internet, only the Bluetooth link, audio on mobile data, an edit with the phone away): a wait with that `HoldReason`, visible through `PeerDeliveryLedger.observe`.
- A queued note no longer on the watch: logged and left queued for its removal.

- [ ] **Step 1: Write the failing tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/MemoryPeerDeliveryDao.kt` with the content of Task 8's `client/sync/src/commonTest/kotlin/app/logdate/client/sync/peer/delivery/MemoryPeerDeliveryDao.kt`, changing only the first line to `package app.logdate.wear.sync.v2.delivery`.

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchNoteDeliveryTest.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.HoldReason
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.wear.sync.AudioFileDigester
import app.logdate.wear.sync.NoteSchedule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WatchNoteDelivery]: each queued note is sent, settled, released to the cloud, or waits with a reason. */
class WatchNoteDeliveryTest {
    private var now = 1_700_000_000_000L
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(now)
        }
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao(), clock)
    private val outbox = mockk<SyncMetadataService>(relaxed = true)
    private val notes = mockk<JournalNotesRepository>()
    private val transport = FakeTransport()
    private var active = true
    private var cloudRuns = 0
    private var conditions =
        RouteConditions(
            phone = PhoneReach.NEARBY,
            phoneCloud = CloudPresence.SIGNED_IN,
            linked = true,
            serverReady = true,
            network = WatchNetwork.UNMETERED,
            charging = false,
        )
    private val delivery =
        WatchNoteDelivery(
            active = { active },
            outbox = outbox,
            notes = notes,
            ledger = ledger,
            transport = transport,
            conditions = { conditions },
            receipts = WatchReceiptApplier(ledger),
            cloud =
                CloudRelease {
                    cloudRuns++
                    SyncResult(success = true)
                },
            digester = AudioFileDigester { AudioFileDigest(sizeBytes = 42, sha256Hex = "ab".repeat(32)) },
            clock = clock,
        )
    private val text =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
            content = "said on the watch",
        )

    init {
        coEvery { outbox.settleIfCurrent(any(), any(), any(), any()) } returns true
    }

    @Test
    fun `while version 2 is not active the notes are left to version 1`() =
        runTest {
            active = false
            queued(text)

            assertNull(delivery.deliverIfActive())
            assertTrue(transport.puts.isEmpty())
        }

    @Test
    fun `a new note goes to a phone nearby and waits for its receipt`() =
        runTest {
            queued(text)

            val result = assertNotNull(delivery.deliverIfActive())

            assertTrue(result.success)
            val (path, data) = transport.puts.single()
            assertEquals(WearPaths.watchRecord(RecordKind.NOTE, text.uid), path)
            assertEquals(text.uid.toString(), envelopeOf(data).id)
            val entry = assertNotNull(row(text))
            assertEquals(DeliveryState.SENT, entry.state)
            assertEquals(now + 30_000, entry.nextAttemptAt)
        }

    @Test
    fun `an audio note includes its recording's digest and sends the recording on its own channel`() =
        runTest {
            val audio =
                JournalNote.Audio(
                    mediaRef = "/data/user/0/app/files/audio_notes/a.m4a",
                    uid = Uuid.random(),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(5),
                )
            queued(audio)

            delivery.deliverIfActive()

            assertEquals(42, envelopeOf(transport.puts.single().second).media.single().sizeBytes)
            assertEquals(listOf(WearPaths.recordMedia(RecordKind.NOTE, audio.uid) to audio.mediaRef), transport.files)
        }

    @Test
    fun `receipts asked for at the start of a run settle what the phone backed up`() =
        runTest {
            queued(text)
            delivery.deliverIfActive()
            transport.answer = answer(Receipt(RecordKind.NOTE, text.uid.toString(), 5, ReceiptState.BACKED_UP, serverVersion = 7))
            now += 1_000

            val result = assertNotNull(delivery.deliverIfActive())

            assertEquals(1, result.uploadedItems)
            coVerify { outbox.settleIfCurrent(EntityType.NOTE, PendingUpload(text.uid.toString(), PendingOperation.CREATE), any(), 1L) }
            assertEquals(listOf(WearPaths.watchRecord(RecordKind.NOTE, text.uid)), transport.deleted)
            assertNull(row(text))
        }

    @Test
    fun `a note on a phone with no account is settled`() =
        runTest {
            queued(text)
            delivery.deliverIfActive()
            conditions = conditions.copy(phoneCloud = CloudPresence.NO_ACCOUNT)
            val onPhone = Receipt(RecordKind.NOTE, text.uid.toString(), 5, ReceiptState.ON_PHONE)
            transport.answer = answer(onPhone, cloud = CloudPresence.NO_ACCOUNT)

            delivery.deliverIfActive()

            assertNull(row(text))
        }

    @Test
    fun `with the phone out of reach a linked watch backs the note up itself`() =
        runTest {
            conditions = conditions.copy(phone = PhoneReach.UNREACHABLE)
            queued(text)

            delivery.deliverIfActive()

            assertEquals(DeliveryState.CLOUD_PENDING, row(text)?.state)
            assertEquals(1, cloudRuns)
            assertTrue(transport.puts.isEmpty())
            assertTrue(ReleasedToCloud(ledger).isEligible(EntityType.NOTE, PendingUpload(text.uid.toString(), PendingOperation.CREATE)))
        }

    @Test
    fun `a note the phone has had for fifteen minutes without a backup goes to the cloud`() =
        runTest {
            queued(text)
            delivery.deliverIfActive()
            transport.answer = answer(Receipt(RecordKind.NOTE, text.uid.toString(), 5, ReceiptState.ON_PHONE))
            now += 1_000

            delivery.deliverIfActive()

            val waiting = assertNotNull(row(text))
            assertEquals(HoldReason.PHONE_BACKING_UP, waiting.holdReason)
            assertEquals(waiting.stateChangedAt + 15 * 60_000L, waiting.nextAttemptAt)
            assertEquals(0, cloudRuns)

            now += 15 * 60_000L
            delivery.deliverIfActive()

            assertEquals(DeliveryState.CLOUD_PENDING, row(text)?.state)
            assertEquals(1, cloudRuns)
        }

    @Test
    fun `a failed write waits for its retry time`() =
        runTest {
            transport.putOk = false
            queued(text)

            val failed = assertNotNull(delivery.deliverIfActive())

            assertFalse(failed.success)
            val entry = assertNotNull(row(text))
            assertEquals(HoldReason.SEND_FAILED, entry.holdReason)
            assertEquals(now + 30_000, entry.nextAttemptAt)

            transport.putOk = true
            delivery.deliverIfActive()
            assertEquals(1, transport.puts.size)

            now += 30_000
            delivery.deliverIfActive()
            assertEquals(2, transport.puts.size)
            assertEquals(DeliveryState.SENT, row(text)?.state)
        }

    @Test
    fun `a removal is signalled the version 1 way and settled while the phone is in reach`() =
        runTest {
            val removal = PendingUpload(text.uid.toString(), PendingOperation.DELETE)
            coEvery { outbox.getPendingUploads(EntityType.NOTE) } returns listOf(removal)

            delivery.deliverIfActive()

            assertEquals(
                NoteDataMapper.noteDeletePath(text.uid) to
                    mapOf(NoteDataMapper.KEY_UID to text.uid.toString(), NoteDataMapper.KEY_NOTE_TYPE to "DELETE"),
                transport.puts.single(),
            )
            coVerify { outbox.settleIfCurrent(EntityType.NOTE, removal, any(), 1L) }
        }

    @Test
    fun `a removal with the phone away also goes to the cloud from a linked watch`() =
        runTest {
            conditions = conditions.copy(phone = PhoneReach.UNREACHABLE)
            val removal = PendingUpload(text.uid.toString(), PendingOperation.DELETE)
            coEvery { outbox.getPendingUploads(EntityType.NOTE) } returns listOf(removal)

            delivery.deliverIfActive()

            assertEquals(NoteDataMapper.noteDeletePath(text.uid), transport.puts.single().first)
            assertEquals(DeliveryState.CLOUD_PENDING, row(text)?.state)
            assertEquals(1, cloudRuns)
        }

    @Test
    fun `a note that cannot go anywhere records why it waits`() =
        runTest {
            conditions = conditions.copy(phone = PhoneReach.UNREACHABLE, linked = false)
            queued(text)

            delivery.deliverIfActive()

            val entry = assertNotNull(row(text))
            assertEquals(HoldReason.PHONE_UNREACHABLE, entry.holdReason)
            assertNull(entry.nextAttemptAt)
        }

    @Test
    fun `a note too large for a data item is marked as one the phone cannot take`() =
        runTest {
            val long = text.copy(content = "x".repeat(100 * 1024))
            queued(long)

            delivery.deliverIfActive()

            assertEquals(DeliveryState.REJECTED, row(long)?.state)
            assertTrue(transport.puts.isEmpty())
        }

    @Test
    fun `a note no longer queued is forgotten and its data item deleted`() =
        runTest {
            queued(text)
            delivery.deliverIfActive()
            coEvery { outbox.getPendingUploads(EntityType.NOTE) } returns emptyList()

            delivery.deliverIfActive()

            assertNull(row(text))
            assertEquals(listOf(WearPaths.watchRecord(RecordKind.NOTE, text.uid)), transport.deleted)
        }

    @Test
    fun `the ledger decides the next note run while version 2 is active`() =
        runTest {
            queued(text)
            delivery.deliverIfActive()

            assertEquals(NoteSchedule.Ledger(now + 30_000), delivery.schedule())

            active = false
            assertEquals(NoteSchedule.Outbox, delivery.schedule())
        }

    private fun queued(note: JournalNote) {
        coEvery { outbox.getPendingUploads(EntityType.NOTE) } returns listOf(PendingUpload(note.uid.toString(), PendingOperation.CREATE))
        coEvery { notes.getNoteById(note.uid) } returns note
    }

    private suspend fun row(note: JournalNote) =
        ledger.get(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, note.uid.toString())

    private fun envelopeOf(data: Map<String, String>): RecordEnvelope = (EnvelopeDataMap.fromDataMap(data) as Decoded.Supported).value

    private fun answer(
        receipt: Receipt,
        cloud: CloudPresence = CloudPresence.SIGNED_IN,
    ): Result<ByteArray> {
        val response = ReceiptsResponse(cloud = cloud, receipts = listOf(receipt))
        return Result.success(WearJson.encodeToBytes(ReceiptsResponse.serializer(), response))
    }

    private class FakeTransport : DataLayerTransportV2 {
        var putOk = true
        var answer: Result<ByteArray> = Result.failure(IllegalStateException("The phone has not answered"))
        val puts = mutableListOf<Pair<String, Map<String, String>>>()
        val files = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()

        override suspend fun reach(): PhoneReach = PhoneReach.NEARBY

        override suspend fun put(
            path: String,
            data: Map<String, String>,
        ): Boolean {
            puts += path to data
            return putOk
        }

        override suspend fun deleteOwn(path: String): Boolean {
            deleted += path
            return true
        }

        override suspend fun sendFile(
            channelPath: String,
            localFilePath: String,
        ): Boolean {
            files += channelPath to localFilePath
            return true
        }

        override suspend fun request(
            path: String,
            payload: ByteArray,
        ): Result<ByteArray> = answer
    }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchReceiptApplierTest.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Tests [WatchReceiptApplier]: the phone's receipts move the watch's ledger forward and nothing else. */
class WatchReceiptApplierTest {
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val applier = WatchReceiptApplier(ledger)
    private val id = Uuid.random().toString()

    @Test
    fun `a receipt moves its note forward and is counted`() =
        runTest {
            track(version = 5)

            assertEquals(1, applier.apply(bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP, serverVersion = 7))))
            assertEquals(DeliveryState.BACKED_UP, state())
            assertEquals(CloudPresence.SIGNED_IN, applier.phoneCloud())
        }

    @Test
    fun `a receipt for an older version moves nothing`() =
        runTest {
            track(version = 6)

            assertEquals(0, applier.apply(bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP))))
            assertEquals(DeliveryState.NEW, state())
        }

    @Test
    fun `a rejection covers every version`() =
        runTest {
            track(version = 5)

            applier.apply(bytes(Receipt(RecordKind.NOTE, id, Long.MAX_VALUE, ReceiptState.REJECTED_UNSUPPORTED)))

            assertEquals(DeliveryState.REJECTED, state())
        }

    @Test
    fun `receipts for other kinds are ignored`() =
        runTest {
            track(version = 5)

            assertEquals(0, applier.apply(bytes(Receipt(RecordKind.MOMENT, id, 5, ReceiptState.BACKED_UP))))
        }

    @Test
    fun `receipts this build cannot read change nothing`() =
        runTest {
            track(version = 5)

            assertEquals(0, applier.apply("nope".encodeToByteArray()))
            assertNull(applier.phoneCloud())
        }

    private suspend fun track(version: Long) = ledger.track(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id, version)

    private suspend fun state() = ledger.get(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id)?.state

    private fun bytes(receipt: Receipt) =
        WearJson.encodeToBytes(ReceiptsResponse.serializer(), ReceiptsResponse(cloud = CloudPresence.SIGNED_IN, receipts = listOf(receipt)))
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchRouteConditionsTest.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkSaverState
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.battery.BatteryState
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.link.LinkProblem
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.MemoryPairKeyVault
import app.logdate.wear.sync.v2.link.WatchLinkStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Tests [WatchRouteConditions]: what the watch knows when it routes a change. */
class WatchRouteConditionsTest {
    private val links = WatchLinkStore(MemoryKeyValueStorage(), MemoryPairKeyVault())
    private val config = mockk<LogDateConfigRepository>()
    private val network = mockk<NetworkSaverModeProvider>()
    private val battery = mockk<BatteryInfoProvider>()
    private val transport = mockk<DataLayerTransportV2>()
    private val conditions = WatchRouteConditions(transport, { CloudPresence.SIGNED_IN }, links, config, network, battery)

    init {
        coEvery { transport.reach() } returns PhoneReach.RELAY_ONLY
        every { config.getCurrentServerDescriptor() } returns
            descriptor(ServerProtocolFeature.DEVICE_LINKING_V1, ServerProtocolFeature.CONDITIONAL_CREATE_V1)
        coEvery { network.getCurrentDataSaverState() } returns NetworkSaverState(false, NetworkConnectionType.WIFI)
        coEvery { battery.getCurrentBatteryState() } returns BatteryState(level = 50, isCharging = true, isPowerSaveMode = false)
    }

    @Test
    fun `a watch linked to the account on a ready server over wifi may upload`() =
        runTest {
            links.saveCloudLink("phone-node", "account")

            assertEquals(
                RouteConditions(
                    phone = PhoneReach.RELAY_ONLY,
                    phoneCloud = CloudPresence.SIGNED_IN,
                    linked = true,
                    serverReady = true,
                    network = WatchNetwork.UNMETERED,
                    charging = true,
                ),
                conditions.current(),
            )
        }

    @Test
    fun `a watch only paired with its phone is not linked`() =
        runTest {
            links.savePairing("phone-node", ByteArray(32))

            assertFalse(conditions.current().linked)
        }

    @Test
    fun `a link with a problem is not linked`() =
        runTest {
            links.saveCloudLink("phone-node", "account")
            links.recordProblem("phone-node", LinkProblem.SESSION_REVOKED)

            assertFalse(conditions.current().linked)
        }

    @Test
    fun `a server without create if absent is not ready`() {
        assertFalse(serverReady(descriptor(ServerProtocolFeature.DEVICE_LINKING_V1)))
        assertFalse(serverReady(null))
    }

    @Test
    fun `a network that cannot be read counts as none`() =
        runTest {
            coEvery { network.getCurrentDataSaverState() } throws IllegalStateException("No connectivity service")

            assertEquals(WatchNetwork.NONE, conditions.current().network)
        }

    private fun descriptor(vararg features: String) =
        ServerDescriptor(
            serverOrigin = "https://logdate.test",
            apiBaseUrl = "https://logdate.test/api/v1",
            deploymentKind = DeploymentKind.FIRST_PARTY,
            displayName = "LogDate",
            protocolFeatures = features.toList(),
        )
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/GoogleDataLayerTransportV2Test.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.wear.sync.PhoneNodeSelector
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests [GoogleDataLayerTransportV2], the watch's version 2 Data Layer adapter. */
class GoogleDataLayerTransportV2Test {
    private val dataLayer = mockk<WearDataLayerClient>(relaxed = true)
    private val phones = mockk<PhoneNodeSelector>()
    private val messageClient = mockk<MessageClient>()
    private val transport = GoogleDataLayerTransportV2(dataLayer, phones, messageClient)

    @Test
    fun `a phone connected directly is nearby and one reached through the cloud is relay only`() =
        runTest {
            coEvery { phones.select() } returns node("direct", nearby = true)
            assertEquals(PhoneReach.NEARBY, transport.reach())

            coEvery { phones.select() } returns node("relay", nearby = false)
            assertEquals(PhoneReach.RELAY_ONLY, transport.reach())
        }

    @Test
    fun `no phone or a failed lookup is out of reach`() =
        runTest {
            coEvery { phones.select() } returns null
            assertEquals(PhoneReach.UNREACHABLE, transport.reach())

            coEvery { phones.select() } throws IllegalStateException("Play services unavailable")
            assertEquals(PhoneReach.UNREACHABLE, transport.reach())
        }

    @Test
    fun `a request goes to the selected phone and returns its answer`() =
        runTest {
            coEvery { phones.select() } returns node("phone", nearby = true)
            every { messageClient.sendRequest("phone", WearPaths.RECEIPTS, any()) } returns Tasks.forResult("answer".encodeToByteArray())

            assertEquals("answer", transport.request(WearPaths.RECEIPTS, byteArrayOf()).getOrThrow().decodeToString())
        }

    @Test
    fun `a request with no phone in reach fails`() =
        runTest {
            coEvery { phones.select() } returns null

            assertTrue(transport.request(WearPaths.RECEIPTS, byteArrayOf()).isFailure)
        }

    @Test
    fun `every write is urgent`() =
        runTest {
            transport.put("/logdate/v2/w2p/record/note/x", mapOf("k" to "v"))

            coVerify { dataLayer.putDataItem("/logdate/v2/w2p/record/note/x", mapOf("k" to "v"), urgent = true) }
        }

    private fun node(
        nodeId: String,
        nearby: Boolean,
    ): Node =
        mockk {
            every { id } returns nodeId
            every { isNearby } returns nearby
        }
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerV2Test.kt`:

```kotlin
package app.logdate.wear.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.sync.v2.delivery.WatchNotesV2
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests the seam where version 2 takes over the version 1 manager's note pass. */
@OptIn(ExperimentalCoroutinesApi::class)
class WearDataLayerSyncManagerV2Test {
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val deadLetterStore = mockk<SyncDeadLetterStore>(relaxed = true)
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val note =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
            content = "said on the watch",
        )

    init {
        every { syncMetadataService.observePendingCount() } returns MutableStateFlow(0)
        every { deadLetterStore.observe() } returns MutableStateFlow<List<SyncDeadLetterRecord>>(emptyList())
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
        coEvery { dataLayerClient.putDataItem(any(), any(), any()) } returns true
        coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
            listOf(PendingUpload(note.uid.toString(), PendingOperation.CREATE))
        coEvery { notesRepository.getNoteById(note.uid) } returns note
    }

    @Test
    fun `version 2 sends the notes while it is active`() =
        runTest {
            val result = manager { SyncResult(success = true, uploadedItems = 1) }.uploadPendingChanges()

            assertEquals(1, result.uploadedItems)
            coVerify(exactly = 0) { dataLayerClient.putDataItem(any(), any(), any()) }
        }

    @Test
    fun `version 1 sends the notes while version 2 is not active`() =
        runTest {
            manager { null }.uploadPendingChanges()

            coVerify { dataLayerClient.putDataItem(NoteDataMapper.notePath(note.uid), any(), true) }
        }

    @Test
    fun `version 2 runs even with no phone in reach`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false

            val result = manager { SyncResult(success = true) }.uploadPendingChanges()

            assertTrue(result.success)
        }

    private fun manager(notesV2: WatchNotesV2) =
        WearDataLayerSyncManager(
            dataLayerClient = dataLayerClient,
            syncMetadataService = syncMetadataService,
            retryScheduleStore = mockk(relaxed = true),
            deadLetterStore = deadLetterStore,
            notesRepository = notesRepository,
            journalRepository = mockk(relaxed = true),
            healthSnapshotDao = mockk(relaxed = true),
            noteDataMapper = NoteDataMapper(),
            journalDataMapper = mockk(relaxed = true),
            associationDataMapper = mockk(relaxed = true),
            healthSnapshotDataMapper = mockk(relaxed = true),
            phoneReachability = PhoneReachability { flowOf(ReachablePhone("Pixel 9")) },
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            notesV2 = notesV2,
        )
}
```

In `app/wear/src/test/kotlin/app/logdate/wear/sync/WatchSyncSchedulerTest.kt`, give the `scheduler(` helper a second parameter `noteSchedule: suspend () -> NoteSchedule = { NoteSchedule.Outbox },` passed as `noteSchedule = noteSchedule,`, and add:

```kotlin
    @Test
    fun `a version 2 note wait is scheduled even with no phone in reach`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false

            scheduler(noteSchedule = { NoteSchedule.Ledger(now.toEpochMilliseconds() + 60_000) }).scheduleNext()

            assertEquals(listOf(60_000L), scheduled)
        }

    @Test
    fun `while version 2 sends notes their outbox entries do not decide the run`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")

            scheduler(noteSchedule = { NoteSchedule.Ledger(now.toEpochMilliseconds() + 60_000) }).scheduleNext()

            assertEquals(listOf(60_000L), scheduled)
        }
```

In `app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt`, add the imports `app.logdate.client.sync.datalayer.v2.WearPaths` and `kotlin.test.assertEquals` and:

```kotlin
    @Test
    fun `pushed receipts that move a note start a sync`() =
        runTest {
            val pushed = mutableListOf<ByteArray>()
            val receiving =
                WearPhoneMessageHandler(ackHandler, syncManager) { payload ->
                    pushed += payload
                    1
                }

            receiving.onMessage(WearPaths.RECEIPTS_PUSH, "receipts".encodeToByteArray())

            coVerify(exactly = 1) { syncManager.fullSync() }
            assertEquals("receipts", pushed.single().decodeToString())
        }

    @Test
    fun `pushed receipts that move nothing start no sync`() =
        runTest {
            WearPhoneMessageHandler(ackHandler, syncManager) { 0 }.onMessage(WearPaths.RECEIPTS_PUSH, byteArrayOf())

            coVerify(exactly = 0) { syncManager.fullSync() }
        }
```

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt`, add `WearPaths.RECEIPTS_PUSH,` to the list in `every message the phone sends reaches the listener`.

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.v2.delivery.*' --tests 'app.logdate.wear.sync.WearDataLayerSyncManagerV2Test' --tests 'app.logdate.wear.sync.WatchSyncSchedulerTest' --tests 'app.logdate.wear.sync.WearPhoneMessageHandlerTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'WatchNoteDelivery'", "Unresolved reference 'DataLayerTransportV2'", "Unresolved reference 'NoteSchedule'", "No parameter with name 'notesV2' found".

- [ ] **Step 3: Implement the transport, receipts, conditions and cloud release**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/DataLayerTransportV2.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.wear.sync.PhoneNodeSelector
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** What the watch's version 2 delivery needs from the Data Layer. Every write is urgent: someone just made it. */
interface DataLayerTransportV2 {
    suspend fun reach(): PhoneReach

    suspend fun put(
        path: String,
        data: Map<String, String>,
    ): Boolean

    /** Deletes the item this watch wrote at [path]; true when it is gone, including when there was none. */
    suspend fun deleteOwn(path: String): Boolean

    suspend fun sendFile(
        channelPath: String,
        localFilePath: String,
    ): Boolean

    /** Asks the phone with `MessageClient.sendRequest`; fails when no phone is in reach or it does not answer in time. */
    suspend fun request(
        path: String,
        payload: ByteArray,
    ): Result<ByteArray>
}

/** [DataLayerTransportV2] over the version 1 client for writes and files, and [MessageClient] for requests. */
class GoogleDataLayerTransportV2(
    private val dataLayer: WearDataLayerClient,
    private val phones: PhoneNodeSelector,
    private val messageClient: MessageClient,
    private val timeoutMs: Long = REQUEST_TIMEOUT_MS,
) : DataLayerTransportV2 {
    override suspend fun reach(): PhoneReach {
        val phone = selectPhone() ?: return PhoneReach.UNREACHABLE
        return if (phone.isNearby) PhoneReach.NEARBY else PhoneReach.RELAY_ONLY
    }

    override suspend fun put(
        path: String,
        data: Map<String, String>,
    ): Boolean = dataLayer.putDataItem(path, data, urgent = true)

    override suspend fun deleteOwn(path: String): Boolean = dataLayer.deleteOwnDataItem(path)

    override suspend fun sendFile(
        channelPath: String,
        localFilePath: String,
    ): Boolean = dataLayer.sendFile(channelPath, localFilePath)

    override suspend fun request(
        path: String,
        payload: ByteArray,
    ): Result<ByteArray> {
        val phone = selectPhone() ?: return Result.failure(IllegalStateException("No phone is in reach for $path"))
        return try {
            val answer = withTimeoutOrNull(timeoutMs) { messageClient.sendRequest(phone.id, path, payload).await() }
            if (answer == null) Result.failure(IllegalStateException("The phone did not answer $path in time")) else Result.success(answer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun selectPhone(): Node? =
        try {
            phones.select()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not look for the phone; it counts as out of reach", e)
            null
        }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchReceiptApplier.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import io.github.aakira.napier.Napier

/** Applies the phone's receipts, pushed or answered, to the watch's ledger. A receipt only ever moves a note forward. */
class WatchReceiptApplier(
    private val ledger: PeerDeliveryLedger,
) {
    @Volatile
    private var lastCloud: CloudPresence? = null

    /** What the phone last said about its account in a receipt; null before the first. */
    fun phoneCloud(): CloudPresence? = lastCloud

    /** Returns how many notes moved forward; receipts this build cannot read move none and are asked for again. */
    suspend fun apply(payload: ByteArray): Int {
        val response =
            when (val decoded = WearJson.decode(payload, WearProtocol.RECEIPTS_SCHEMA, ReceiptsResponse.serializer())) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> {
                    Napier.w("The phone's receipts use schema ${decoded.schema}; the watch waits and asks again")
                    return 0
                }
                is Decoded.Malformed -> {
                    Napier.e("The phone's receipts could not be read: ${decoded.reason}; the watch asks again")
                    return 0
                }
            }
        lastCloud = response.cloud
        return response.receipts.count { applyOne(it) }
    }

    private suspend fun applyOne(receipt: Receipt): Boolean {
        if (receipt.kind != RecordKind.NOTE) {
            Napier.w("Ignoring a ${receipt.kind} receipt; this watch sends only notes this way")
            return false
        }
        val moved =
            ledger.applyReceipt(
                scope = PeerScopes.PHONE,
                direction = DeliveryDirection.TO_PHONE,
                entityType = EntityType.NOTE,
                entityId = receipt.id,
                receiptVersion = receipt.contentVersion,
                state = DeliveryState.of(receipt.state),
            )
        return moved != null
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchRouteConditions.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.delivery.watchNetworkOf
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.sync.v2.link.peerLinkState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Reads what the watch knows when it routes a change: the phone, the link, the server, the network and the charger. */
class WatchRouteConditions(
    private val transport: DataLayerTransportV2,
    private val phoneCloud: suspend () -> CloudPresence,
    private val links: WatchLinkStore,
    private val config: LogDateConfigRepository,
    private val network: NetworkSaverModeProvider,
    private val battery: BatteryInfoProvider,
) {
    suspend fun current(): RouteConditions =
        RouteConditions(
            phone = transport.reach(),
            phoneCloud = phoneCloud(),
            linked = links.current().peerLinkState() == PeerLinkState.LINKED,
            serverReady = serverReady(config.getCurrentServerDescriptor()),
            network = readNetwork(),
            charging = readCharging(),
        )

    private suspend fun readNetwork(): WatchNetwork =
        try {
            watchNetworkOf(network.getCurrentDataSaverState())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not read the watch's network; it counts as none", e)
            WatchNetwork.NONE
        }

    private suspend fun readCharging(): Boolean =
        try {
            battery.getCurrentBatteryState().isCharging
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not read whether the watch is charging; it counts as not charging", e)
            false
        }
}

/** A watch uploads only to a server that links devices and creates without overwriting. */
internal fun serverReady(descriptor: ServerDescriptor?): Boolean =
    descriptor != null &&
        descriptor.hasProtocolFeature(ServerProtocolFeature.DEVICE_LINKING_V1) &&
        descriptor.hasProtocolFeature(ServerProtocolFeature.CONDITIONAL_CREATE_V1)
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/ReleasedToCloud.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.metadata.CloudEligibility
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes

/** The outbox entries the watch's own cloud engine may upload: the notes its router released, and nothing else. */
class ReleasedToCloud(
    private val ledger: PeerDeliveryLedger,
) : CloudEligibility {
    override suspend fun isEligible(
        entityType: EntityType,
        pending: PendingUpload,
    ): Boolean {
        if (entityType != EntityType.NOTE) return false
        val entry = ledger.get(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, pending.entityId) ?: return false
        return entry.state == DeliveryState.CLOUD_PENDING && entry.holdReason == null
    }
}
```

- [ ] **Step 4: Implement the delivery and plug it in**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchNoteDelivery.kt`:

```kotlin
package app.logdate.wear.sync.v2.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.MediaRef
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptsRequest
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.HoldReason
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.client.sync.peer.delivery.PeerDelivery
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.Route
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.RouteInput
import app.logdate.client.sync.peer.delivery.WatchSyncRouter
import app.logdate.wear.sync.AudioFileDigester
import app.logdate.wear.sync.FileAudioDigester
import app.logdate.wear.sync.NoteSchedule
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** The version 2 note pass, plugged into the version 1 manager; null means version 1 sends the notes. */
fun interface WatchNotesV2 {
    suspend fun deliverIfActive(): SyncResult?
}

/** Runs the watch's own cloud engine over the notes the router released. */
fun interface CloudRelease {
    suspend fun upload(): SyncResult
}

/** When a waiting note is looked at again; null waits for an event such as the phone returning or the watch being linked. */
internal object HoldTimes {
    const val RECHECK_MS = 15 * 60_000L
    private const val SEND_RETRY_BASE_MS = 30_000L
    private const val SEND_RETRY_MAX_MS = 6 * 60 * 60_000L

    fun recheckAt(
        reason: HoldReason,
        entry: PeerDelivery,
        now: Long,
    ): Long? =
        when (reason) {
            HoldReason.AWAITING_RECEIPT -> later((entry.lastAttemptAt ?: entry.stateChangedAt) + WatchSyncRouter.RECEIPT_WAIT_MS, now)
            HoldReason.PHONE_BACKING_UP -> later(entry.stateChangedAt + WatchSyncRouter.BACKUP_GRACE_MS, now)
            HoldReason.NO_INTERNET, HoldReason.BLUETOOTH_ONLY, HoldReason.AUDIO_WAITS_FOR_WIFI -> now + RECHECK_MS
            HoldReason.SEND_FAILED -> now + sendRetryDelay(entry.attempts)
            HoldReason.PHONE_UNREACHABLE,
            HoldReason.NOT_LINKED,
            HoldReason.SERVER_TOO_OLD,
            HoldReason.PHONE_CANNOT_READ,
            HoldReason.EDIT_NEEDS_PHONE,
            -> null
        }

    /** 30 seconds after the first failure, doubling with each one, at most six hours. */
    fun sendRetryDelay(attempts: Int): Long = (SEND_RETRY_BASE_MS shl (attempts - 1).coerceIn(0, 20)).coerceAtMost(SEND_RETRY_MAX_MS)

    /** A time already past means the reason outlived its window: look again later, never in a tight loop. */
    private fun later(
        at: Long,
        now: Long,
    ): Long = if (at > now) at else now + RECHECK_MS
}

/**
 * Sends the watch's queued notes the version 2 way. Each note gets a ledger row; [WatchSyncRouter] decides
 * whether it goes to the phone, is settled by a receipt, goes to the watch's own cloud engine, or waits
 * with a named reason. Runs under the version 1 manager's lock, so it never overlaps another run.
 */
class WatchNoteDelivery(
    private val active: suspend () -> Boolean,
    private val outbox: SyncMetadataService,
    private val notes: JournalNotesRepository,
    private val ledger: PeerDeliveryLedger,
    private val transport: DataLayerTransportV2,
    private val conditions: suspend () -> RouteConditions,
    private val receipts: WatchReceiptApplier,
    private val cloud: CloudRelease,
    private val digester: AudioFileDigester = FileAudioDigester(),
    private val router: WatchSyncRouter = WatchSyncRouter(),
    private val clock: Clock = Clock.System,
) : WatchNotesV2 {
    override suspend fun deliverIfActive(): SyncResult? {
        if (!active()) return null
        return deliver()
    }

    /** While version 2 sends notes, the ledger's earliest time decides the next note run. */
    suspend fun schedule(): NoteSchedule {
        if (!active()) return NoteSchedule.Outbox
        return NoteSchedule.Ledger(ledger.earliestDue(PeerScopes.PHONE, DeliveryDirection.TO_PHONE))
    }

    private suspend fun deliver(): SyncResult {
        val pending = outbox.getPendingUploads(EntityType.NOTE)
        forgetSettled(pending.mapTo(HashSet()) { it.entityId })
        val routeConditions = conditions()
        if (routeConditions.phone != PhoneReach.UNREACHABLE) askForReceipts()
        val errors = mutableListOf<SyncError>()
        var settled = 0
        var released = 0
        for (entry in pending) {
            when (val step = deliverOne(entry, routeConditions)) {
                Step.Settled -> settled++
                Step.Released -> released++
                Step.Waiting -> Unit
                is Step.Failed -> errors += step.error
            }
        }
        if (released > 0) errors += uploadReleased(released)
        val finishedAt = clock.now()
        outbox.updateLastSyncTime(EntityType.NOTE, finishedAt)
        return SyncResult(success = errors.isEmpty(), uploadedItems = settled, errors = errors, lastSyncTime = finishedAt)
    }

    private suspend fun deliverOne(
        pending: PendingUpload,
        conditions: RouteConditions,
    ): Step {
        val id = Uuid.parseOrNull(pending.entityId)
        if (id == null) {
            Napier.w("Skipping a queued note whose id is not a UUID")
            return Step.Waiting
        }
        if (pending.operation == PendingOperation.DELETE) return deliverRemoval(pending, id, conditions)
        val note = notes.getNoteById(id)
        if (note == null) {
            Napier.w("Note $id is queued but no longer on the watch; it waits for its removal to be queued")
            return Step.Waiting
        }
        val version = note.lastUpdated.toEpochMilliseconds()
        val entry = ledger.track(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, pending.entityId, version)
        if (entry.holdReason == HoldReason.SEND_FAILED && (entry.nextAttemptAt ?: 0L) > now()) return Step.Waiting
        val input = RouteInput(pending.operation, note is JournalNote.Audio, entry.state, ageOf(entry), conditions)
        return when (val route = router.route(input)) {
            Route.Settle -> settle(pending, entry, id)
            Route.SendToPhone -> send(note, entry)
            Route.UploadDirect -> release(entry)
            is Route.Wait -> postpone(entry, route.reason)
        }
    }

    /**
     * A removal goes to the phone as the version 1 removal signal, which the phone already honours and the
     * Data Layer delivers whenever the phone is next in reach. With the phone away, a linked watch also
     * removes the note from the cloud itself.
     */
    private suspend fun deliverRemoval(
        pending: PendingUpload,
        id: Uuid,
        conditions: RouteConditions,
    ): Step {
        if (!transport.put(NoteDataMapper.noteDeletePath(id), removalSignal(id))) {
            Napier.w("Could not record the removal of note $id for the phone; it is tried again on the next run")
            return Step.Failed(SyncError(SyncErrorType.NETWORK_ERROR, "Could not tell the phone a note was removed", retryable = true))
        }
        val entry = ledger.track(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, pending.entityId, REMOVAL_VERSION)
        if (conditions.phone != PhoneReach.UNREACHABLE) return settle(pending, entry, id)
        val input = RouteInput(PendingOperation.DELETE, false, entry.state, ageOf(entry), conditions)
        return when (val route = router.route(input)) {
            Route.UploadDirect -> release(entry)
            is Route.Wait -> postpone(entry, route.reason)
            Route.Settle, Route.SendToPhone -> Step.Waiting
        }
    }

    private suspend fun settle(
        pending: PendingUpload,
        entry: PeerDelivery,
        id: Uuid,
    ): Step {
        if (!outbox.settleIfCurrent(EntityType.NOTE, pending, clock.now(), SETTLED_VERSION)) {
            Napier.i("Note $id changed while the phone was confirming it; the newer change stays queued")
            return Step.Waiting
        }
        removeRecord(id)
        ledger.forget(entry.scope, entry.direction, entry.entityType, entry.entityId)
        return Step.Settled
    }

    private suspend fun send(
        note: JournalNote,
        entry: PeerDelivery,
    ): Step {
        val media =
            if (note is JournalNote.Audio) {
                recordingOf(note) ?: return failed(entry, "its recording could not be read")
            } else {
                null
            }
        val envelope = NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT, media = listOfNotNull(media))
        val data =
            try {
                EnvelopeDataMap.toDataMap(envelope, sentAt = now())
            } catch (e: IllegalArgumentException) {
                Napier.e("Note ${note.uid} is too large for one data item; the phone cannot take it this way", e)
                ledger.moveTo(entry, DeliveryState.REJECTED)
                return Step.Waiting
            }
        val path = WearPaths.watchRecord(RecordKind.NOTE, note.uid)
        if (!transport.put(path, data)) return failed(entry, "its data item could not be written")
        if (note is JournalNote.Audio && !transport.sendFile(WearPaths.recordMedia(RecordKind.NOTE, note.uid), note.mediaRef)) {
            return failed(entry, "its recording could not be sent")
        }
        val attempted = ledger.recordAttempt(entry, nextAttemptAt = null)
        ledger.moveTo(attempted, DeliveryState.SENT, nextAttemptAt = now() + WatchSyncRouter.RECEIPT_WAIT_MS)
        return Step.Waiting
    }

    private suspend fun release(entry: PeerDelivery): Step {
        ledger.moveTo(entry, DeliveryState.CLOUD_PENDING, nextAttemptAt = now() + HoldTimes.RECHECK_MS)
        return Step.Released
    }

    private suspend fun postpone(
        entry: PeerDelivery,
        reason: HoldReason,
    ): Step {
        ledger.moveTo(entry, entry.state, reason, HoldTimes.recheckAt(reason, entry, now()))
        return Step.Waiting
    }

    private suspend fun failed(
        entry: PeerDelivery,
        why: String,
    ): Step {
        val attempted = ledger.recordAttempt(entry, nextAttemptAt = null)
        ledger.moveTo(attempted, attempted.state, HoldReason.SEND_FAILED, now() + HoldTimes.sendRetryDelay(attempted.attempts))
        Napier.w("Could not send note ${entry.entityId} to the phone because $why; it is sent again later")
        return Step.Failed(SyncError(SyncErrorType.NETWORK_ERROR, "Could not send a note to the phone: $why", retryable = true))
    }

    private suspend fun recordingOf(note: JournalNote.Audio): MediaRef? {
        val digest = digester.digest(note.mediaRef) ?: return null
        return MediaRef(sha256 = digest.sha256Hex, sizeBytes = digest.sizeBytes, mimeType = AUDIO_MIME_TYPE)
    }

    private suspend fun askForReceipts() {
        val waiting =
            ledger
                .observe(PeerScopes.PHONE, DeliveryDirection.TO_PHONE)
                .first()
                .filter { it.state == DeliveryState.SENT || it.state == DeliveryState.ON_PHONE }
        if (waiting.isEmpty()) return
        val request = ReceiptsRequest(items = waiting.map { ReceiptQuery(RecordKind.NOTE, it.entityId, it.contentVersion) })
        val answer =
            transport.request(WearPaths.RECEIPTS, WearJson.encodeToBytes(ReceiptsRequest.serializer(), request)).getOrElse { error ->
                Napier.w("The phone did not answer the watch's receipts request; it is asked again on the next run", error)
                return
            }
        receipts.apply(answer)
    }

    /** A row whose note is no longer queued is done; its data item is deleted so Play services stops storing it. */
    private suspend fun forgetSettled(queued: Set<String>) {
        ledger
            .observe(PeerScopes.PHONE, DeliveryDirection.TO_PHONE)
            .first()
            .filter { it.entityId !in queued }
            .forEach { entry ->
                Uuid.parseOrNull(entry.entityId)?.let { removeRecord(it) }
                ledger.forget(entry.scope, entry.direction, entry.entityType, entry.entityId)
            }
    }

    private suspend fun removeRecord(id: Uuid) {
        if (transport.deleteOwn(WearPaths.watchRecord(RecordKind.NOTE, id))) return
        Napier.w("Could not delete the data item of note $id; the phone ignores a version it already has")
    }

    private suspend fun uploadReleased(count: Int): List<SyncError> {
        val result =
            try {
                cloud.upload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("The watch could not back up $count note(s) itself; they stay queued", e)
                return listOf(SyncError(SyncErrorType.UNKNOWN_ERROR, "The watch could not back up notes itself", e))
            }
        if (!result.success) Napier.w("The watch backed up only part of $count note(s) itself; the rest stay queued and are tried again")
        return result.errors
    }

    private fun ageOf(entry: PeerDelivery): Long {
        val since = if (entry.state == DeliveryState.SENT) entry.lastAttemptAt ?: entry.stateChangedAt else entry.stateChangedAt
        return now() - since
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private fun removalSignal(id: Uuid): Map<String, String> =
        mapOf(NoteDataMapper.KEY_UID to id.toString(), NoteDataMapper.KEY_NOTE_TYPE to "DELETE")

    private sealed interface Step {
        data object Settled : Step

        data object Released : Step

        data object Waiting : Step

        data class Failed(
            val error: SyncError,
        ) : Step
    }

    private companion object {
        /** Version 1 settles a note the phone confirmed with version 1; the watch stores no server version of its captures. */
        const val SETTLED_VERSION = 1L

        /** A removal outranks every version of its note. */
        const val REMOVAL_VERSION = Long.MAX_VALUE

        const val AUDIO_MIME_TYPE = "audio/mp4"
    }
}
```

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt`:

1. Before `class WatchSyncScheduler`, add:

```kotlin
/** Who decides when the watch's notes are next due. */
sealed interface NoteSchedule {
    /** Version 1: the outbox's retry times. */
    data object Outbox : NoteSchedule

    /** Version 2: the delivery ledger's earliest time, or null when nothing is due until something changes. */
    data class Ledger(
        val dueAt: Long?,
    ) : NoteSchedule
}
```

2. Add the constructor parameter `private val noteSchedule: suspend () -> NoteSchedule = { NoteSchedule.Outbox },` after `private val clock: Clock = Clock.System,`.
3. Replace `scheduleNext()` and `earliestAttemptAt()` with:

```kotlin
    /**
     * Sets the next run for when the earliest waiting item is due. Outbox items count only with a phone in
     * reach; a version 2 note wait counts regardless, because the watch may upload it itself.
     */
    suspend fun scheduleNext() {
        val notes = noteSchedule()
        val outboxTypes = if (notes is NoteSchedule.Ledger) PHONE_ONLY_TYPES else UPLOADED_TYPES
        val outboxDue = if (dataLayerClient.isPhoneConnected()) earliestAttemptAt(outboxTypes) else null
        val dueAt = listOfNotNull(outboxDue, (notes as? NoteSchedule.Ledger)?.dueAt).minOrNull() ?: return
        work.runAfter((dueAt - clock.now().toEpochMilliseconds()).coerceAtLeast(0L))
    }

    private suspend fun earliestAttemptAt(types: List<EntityType>): Long? =
        types
            .flatMap { type -> syncMetadataService.getPendingUploads(type).map { pending -> type to pending.entityId } }
            .minOfOrNull { (type, entityId) -> retryScheduleStore.nextAttemptAt(type, entityId) ?: 0L }
```

4. In the companion, after `UPLOADED_TYPES` add:

```kotlin
        /** What still goes the version 1 way while version 2 sends notes. */
        val PHONE_ONLY_TYPES = listOf(EntityType.JOURNAL, EntityType.ASSOCIATION)
```

5. In the class KDoc, replace "With no phone in reach nothing is set: the phone's return starts a sync by itself." with "With no phone in reach only a version 2 note wait is set: the phone's return starts a sync by itself."

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt`, after `private val clock: Clock = Clock.System,` add the constructor parameter:

```kotlin
    /** Sends notes the version 2 way while the phone takes receipts; journals, links and health stay version 1. */
    private val notesV2: WatchNotesV2? = null,
```

make the first line of `uploadPendingChangesLocked()`:

```kotlin
        notesV2?.deliverIfActive()?.let { return it }
```

and add `import app.logdate.wear.sync.v2.delivery.WatchNotesV2`.

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WearPhoneMessageHandler.kt`, add the constructor parameter `private val receiptsPushed: suspend (ByteArray) -> Int = { 0 },` after `syncManager`, add the branch `path == WearPaths.RECEIPTS_PUSH -> applyReceipts(data)` before `else ->` in `onMessage`, and add:

```kotlin
    /** Receipts that moved a note forward are acted on now, so a backed-up note leaves the outbox at once. */
    private suspend fun applyReceipts(data: ByteArray) {
        val moved =
            try {
                receiptsPushed(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Failed to apply the phone's receipts; the watch asks for them on its next sync", e)
                return
            }
        if (moved > 0) sendPendingChanges()
    }
```

with imports `app.logdate.client.sync.datalayer.v2.WearPaths` and `kotlinx.coroutines.CancellationException`.

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt`, change the `WatchOwnState` binding's features to `setOf(WearFeatures.LINK_CLOUD, WearFeatures.RECEIPTS)` and add:

```kotlin
        single { PeerDeliveryLedger(get()) }
        single<DataLayerTransportV2> {
            val context = get<Context>()
            GoogleDataLayerTransportV2(
                dataLayer = get(),
                phones = PhoneNodeSelector(Wearable.getCapabilityClient(context), Wearable.getNodeClient(context)),
                messageClient = Wearable.getMessageClient(context),
            )
        }
        single { WatchReceiptApplier(get()) }
        single {
            val peerStates = get<PeerStateStore>()
            val receipts = get<WatchReceiptApplier>()
            WatchRouteConditions(
                transport = get(),
                phoneCloud = {
                    peerStates
                        .latest(PeerRole.PHONE)
                        .first()
                        ?.phone
                        ?.cloud
                        ?.takeIf { it != CloudPresence.UNKNOWN }
                        ?: receipts.phoneCloud()
                        ?: CloudPresence.UNKNOWN
                },
                links = get(),
                config = get(),
                network = get(),
                battery = get(),
            )
        }
        single {
            val gate = get<WearProtocolGate>()
            val conditions = get<WatchRouteConditions>()
            val koin = getKoin()
            WatchNoteDelivery(
                active = { gate.isFeatureActive(WearFeatures.RECEIPTS).first() },
                outbox = get(),
                notes = get(),
                ledger = get(),
                transport = get(),
                conditions = conditions::current,
                receipts = get(),
                cloud = CloudRelease { koin.get<DefaultSyncManager>(WatchCloud).uploadPendingChanges() },
            )
        } bind WatchNotesV2::class
```

with imports `android.content.Context` (if not present), `app.logdate.client.sync.DefaultSyncManager`, `app.logdate.client.sync.datalayer.v2.CloudPresence`, `app.logdate.client.sync.datalayer.v2.WearProtocolGate`, `app.logdate.client.sync.peer.delivery.PeerDeliveryLedger`, `app.logdate.client.sync.peer.state.PeerRole`, `app.logdate.client.sync.peer.state.PeerStateStore`, `app.logdate.wear.sync.PhoneNodeSelector`, `app.logdate.wear.sync.v2.delivery.CloudRelease`, `app.logdate.wear.sync.v2.delivery.DataLayerTransportV2`, `app.logdate.wear.sync.v2.delivery.GoogleDataLayerTransportV2`, `app.logdate.wear.sync.v2.delivery.WatchNoteDelivery`, `app.logdate.wear.sync.v2.delivery.WatchNotesV2`, `app.logdate.wear.sync.v2.delivery.WatchReceiptApplier`, `app.logdate.wear.sync.v2.delivery.WatchRouteConditions`, `kotlinx.coroutines.flow.first` and `org.koin.dsl.bind`. The engine is looked up only when a note is released, so building the version 1 manager never builds the cloud engine.

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearCloudSyncModule.kt`, replace `single<CloudEligibility>(WatchCloud) { CloudEligibility { _, _ -> false } }` with:

```kotlin
        single<CloudEligibility>(WatchCloud) { ReleasedToCloud(get()) }
```

and add `import app.logdate.wear.sync.v2.delivery.ReleasedToCloud`.

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt`:

1. In the `WearDataLayerSyncManager(` call, after `scope = get(),` add `notesV2 = getOrNull<WatchNotesV2>(),`.
2. Replace `single { WearPhoneMessageHandler(ackHandler = get(), syncManager = get()) }` with:

```kotlin
        single {
            val receipts = getOrNull<WatchReceiptApplier>()
            WearPhoneMessageHandler(
                ackHandler = get(),
                syncManager = get(),
                receiptsPushed = { payload -> receipts?.apply(payload) ?: 0 },
            )
        }
```

3. In the `WatchSyncScheduler(` binding, resolve `val delivery = getOrNull<WatchNoteDelivery>()` first and add `noteSchedule = { delivery?.schedule() ?: NoteSchedule.Outbox },` after `dataLayerClient = get(),`.
4. Add the imports `app.logdate.wear.sync.NoteSchedule`, `app.logdate.wear.sync.v2.delivery.WatchNoteDelivery`, `app.logdate.wear.sync.v2.delivery.WatchNotesV2` and `app.logdate.wear.sync.v2.delivery.WatchReceiptApplier`.

In `app/wear/src/main/AndroidManifest.xml`, in the `MESSAGE_RECEIVED` filter of `WearDataLayerListenerService`, after the `/logdate/sync` entry add:

```xml
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/v2/receipts" />
```

- [ ] **Step 5: Run the tests again**

Run the Step 2 command again, then `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.WearDataLayerSyncManagerTest' --console=plain`.
Expected: PASS (14 delivery, 5 applier, 5 conditions, 5 transport, 3 seam, the 2 new scheduler and 2 new message handler tests, and every existing test in those classes and in `WearDataLayerSyncManagerTest` unchanged).

- [ ] **Step 6: Run the watch checks, the build and the managed-device tests**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearManifestFiltersTest --console=plain
```
Expected: BUILD SUCCESSFUL, every manifest filter test passing, including the receipts push.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearKoinGraphTest --console=plain
```
Expected: BUILD SUCCESSFUL; the whole graph, now with the delivery bindings, resolves.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task11-watch-delivery.txt <<'EOF'
chore(wear): send watch captures with receipts and back them up

With the redesigned watch app on and a phone that takes receipts, the
watch now sends its notes the new way. Each note is sent to the phone
and stays queued until a receipt says the phone backed it up, deleted
it, or has no account. Journals, links and health readings still go the
current way.

If a signed-in phone has not backed a note up within fifteen minutes,
or the phone is out of reach, a watch linked to the account uploads
the note itself, but only to a server that never overwrites, over its
own internet, and with audio on mobile data waiting for Wi-Fi or the
charger. Edits always wait for the phone. Whenever a note waits, the
reason and the next time it is looked at are recorded. The watch now
advertises receipts.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/DataLayerTransportV2.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchReceiptApplier.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchRouteConditions.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/ReleasedToCloud.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/delivery/WatchNoteDelivery.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerSyncManager.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearPhoneMessageHandler.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WatchSyncScheduler.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearCloudSyncModule.kt app/wear/src/main/AndroidManifest.xml app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/MemoryPeerDeliveryDao.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchNoteDeliveryTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchReceiptApplierTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/WatchRouteConditionsTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/delivery/GoogleDataLayerTransportV2Test.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearDataLayerSyncManagerV2Test.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WatchSyncSchedulerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task11-watch-delivery.txt
```

---

### Task 12: Choose what the watch's library window contains (D5, policy)

The pure rule for the watch's library window, in `:client:domain` so it compiles for every platform and is tested on the JVM. A note is in the window when it is `RECENT` (its logical day, by the phone's day-start hour and time zone, is one of the last 14 including today), `ON_THIS_DAY` (the same month and day in an earlier year; on February 28 of a year without a leap day, February 29 counts too), `STARRED` or `REWIND` (ids that other features supply through `WindowExtraIds`; empty until D11). When the window is over its budget (400 notes or 8 MB of note bodies by default), notes are chosen in this order: recent, starred, on this day, Rewind, and within each the newest first; a note too large for what is left is skipped and smaller ones still fit. The plan also says when the window next changes with no new notes: the next day start, which `nextStreakDayStart` computes across clock changes.

**Files:**
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchWindowPolicy.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/WatchWindowPolicyTest.kt`

**Interfaces:**
- Consumes: `Instant.toStreakDay(timeZone, dayStartHour)`, `nextStreakDayStart(after, timeZone, dayStartHour)` (`client/domain/src/commonMain/kotlin/app/logdate/client/domain/streak/StreakDay.kt:20-52`); `WindowReason` (`:client:sync`, a dependency of `:client:domain`).
- Produces:
  - `data class WindowCandidate(val id: Uuid, val createdAt: Instant, val sizeBytes: Long)`
  - `data class WindowExtraIds(val rewind: Set<Uuid> = emptySet(), val starred: Set<Uuid> = emptySet())`
  - `data class WindowBudget(val maxItems: Int = 400, val maxBytes: Long = 8L * 1024 * 1024)`
  - `data class WindowMember(val id: Uuid, val reasons: Set<WindowReason>)`
  - `data class WindowPlan(val today: LocalDate, val members: List<WindowMember>, val dropped: Int, val nextChangeAt: Instant)`
  - `class WatchWindowPolicy(recentDays: Int = 14, budget: WindowBudget = WindowBudget()) { fun choose(candidates: List<WindowCandidate>, extras: WindowExtraIds, now: Instant, zone: TimeZone, dayStartHour: Int): WindowPlan; companion object { const val DEFAULT_RECENT_DAYS = 14; fun isOnThisDay(day: LocalDate, today: LocalDate): Boolean } }`

**Failure modes and how they surface:** none at run time: the policy is pure. A note that qualifies but does not fit is counted in `WindowPlan.dropped`, which Task 13 logs.

- [ ] **Step 1: Write the failing tests**

Create `client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/WatchWindowPolicyTest.kt`:

```kotlin
package app.logdate.client.domain.watch

import app.logdate.client.sync.datalayer.v2.WindowReason
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WatchWindowPolicy]: which notes the watch's library window contains, and which come first. */
class WatchWindowPolicyTest {
    private val zone = TimeZone.of("America/New_York")
    private val policy = WatchWindowPolicy()

    @Test
    fun `the fourteen logical days up to today are recent`() {
        val firstDay = note(at(2026, 9, 27, 5, 0))
        val dayBefore = note(at(2026, 9, 27, 3, 0))

        val plan = policy.choose(listOf(firstDay, dayBefore), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(listOf(WindowMember(firstDay.id, setOf(WindowReason.RECENT))), plan.members)
    }

    @Test
    fun `before the day start it is still the day before`() {
        val plan = policy.choose(emptyList(), WindowExtraIds(), at(2026, 10, 10, 2, 0), zone, dayStartHour = 4)

        assertEquals(LocalDate(2026, 10, 9), plan.today)
    }

    @Test
    fun `the window next changes at the day start even across a clock change`() {
        val plan = policy.choose(emptyList(), WindowExtraIds(), at(2026, 3, 7, 12, 0), zone, dayStartHour = 4)

        assertEquals(Instant.parse("2026-03-08T08:00:00Z"), plan.nextChangeAt)
    }

    @Test
    fun `notes from this date in earlier years are on this day`() {
        val lastYear = note(at(2025, 10, 10, 12, 0))
        val otherDay = note(at(2025, 10, 11, 12, 0))

        val plan = policy.choose(listOf(lastYear, otherDay), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(listOf(WindowMember(lastYear.id, setOf(WindowReason.ON_THIS_DAY))), plan.members)
    }

    @Test
    fun `on February 28 of a year without a leap day notes from February 29 are on this day`() {
        assertTrue(WatchWindowPolicy.isOnThisDay(LocalDate(2024, 2, 29), LocalDate(2027, 2, 28)))
        assertFalse(WatchWindowPolicy.isOnThisDay(LocalDate(2024, 2, 29), LocalDate(2028, 2, 28)))
        assertTrue(WatchWindowPolicy.isOnThisDay(LocalDate(2024, 2, 29), LocalDate(2028, 2, 29)))
    }

    @Test
    fun `this year's notes are never on this day`() {
        assertFalse(WatchWindowPolicy.isOnThisDay(LocalDate(2026, 10, 10), LocalDate(2026, 10, 10)))
    }

    @Test
    fun `starred and Rewind notes join the window with their reasons`() {
        val old = note(at(2020, 1, 5, 12, 0))
        val extras = WindowExtraIds(rewind = setOf(old.id), starred = setOf(old.id))

        val plan = policy.choose(listOf(old), extras, at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(setOf(WindowReason.STARRED, WindowReason.REWIND), plan.members.single().reasons)
    }

    @Test
    fun `over the budget recent notes come first and newer before older`() {
        val onThisDay = note(at(2025, 10, 10, 12, 0))
        val older = note(at(2026, 10, 1, 12, 0))
        val newer = note(at(2026, 10, 9, 12, 0))
        val small = WatchWindowPolicy(budget = WindowBudget(maxItems = 2))

        val plan = small.choose(listOf(onThisDay, older, newer), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(listOf(newer.id, older.id), plan.members.map { it.id })
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `a note too large for what is left is skipped and smaller ones still fit`() {
        val large = note(at(2026, 10, 9, 12, 0), sizeBytes = 900)
        val little = note(at(2026, 10, 8, 12, 0), sizeBytes = 100)
        val tight = WatchWindowPolicy(budget = WindowBudget(maxBytes = 500))

        val plan = tight.choose(listOf(large, little), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(listOf(little.id), plan.members.map { it.id })
        assertEquals(1, plan.dropped)
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Instant = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    private fun note(
        createdAt: Instant,
        sizeBytes: Long = 100,
    ) = WindowCandidate(Uuid.random(), createdAt, sizeBytes)
}
```

(2026-03-08 is the second Sunday of March: New York moves from 2:00 EST to 3:00 EDT, so 4:00 local is 08:00 UTC, not 09:00.)

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.watch.WatchWindowPolicyTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'WatchWindowPolicy'", "Unresolved reference 'WindowCandidate'", "Unresolved reference 'WindowExtraIds'".

- [ ] **Step 3: Implement**

Create `client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchWindowPolicy.kt`:

```kotlin
package app.logdate.client.domain.watch

import app.logdate.client.domain.streak.nextStreakDayStart
import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.sync.datalayer.v2.WindowReason
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One note the phone could put in the watch's library window.
 *
 * @property sizeBytes The size of the note's body as it crosses; media does not count until D6 sends it.
 */
data class WindowCandidate(
    val id: Uuid,
    val createdAt: Instant,
    val sizeBytes: Long,
)

/** Notes other features pick for the watch; empty until Rewind and starred notes supply them. */
data class WindowExtraIds(
    val rewind: Set<Uuid> = emptySet(),
    val starred: Set<Uuid> = emptySet(),
)

/** How much of the library the watch stores. */
data class WindowBudget(
    val maxItems: Int = DEFAULT_MAX_ITEMS,
    val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    companion object {
        const val DEFAULT_MAX_ITEMS = 400
        const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024
    }
}

data class WindowMember(
    val id: Uuid,
    val reasons: Set<WindowReason>,
)

/**
 * @property members In budget order; the first ones are chosen when the budget runs out.
 * @property dropped How many notes qualified but did not fit the budget.
 * @property nextChangeAt When the window changes with no new notes: the next day start.
 */
data class WindowPlan(
    val today: LocalDate,
    val members: List<WindowMember>,
    val dropped: Int,
    val nextChangeAt: Instant,
)

/**
 * Chooses the notes in the watch's library window. Days are logical days: a day runs from the phone's
 * day-start hour to the same hour the next morning, in the phone's time zone, as streaks count them.
 */
class WatchWindowPolicy(
    private val recentDays: Int = DEFAULT_RECENT_DAYS,
    private val budget: WindowBudget = WindowBudget(),
) {
    fun choose(
        candidates: List<WindowCandidate>,
        extras: WindowExtraIds,
        now: Instant,
        zone: TimeZone,
        dayStartHour: Int,
    ): WindowPlan {
        val today = now.toStreakDay(zone, dayStartHour)
        val firstRecentDay = today.minus(recentDays - 1, DateTimeUnit.DAY)
        val qualified =
            candidates.mapNotNull { candidate ->
                val day = candidate.createdAt.toStreakDay(zone, dayStartHour)
                val reasons =
                    buildSet {
                        if (day in firstRecentDay..today) add(WindowReason.RECENT)
                        if (isOnThisDay(day, today)) add(WindowReason.ON_THIS_DAY)
                        if (candidate.id in extras.starred) add(WindowReason.STARRED)
                        if (candidate.id in extras.rewind) add(WindowReason.REWIND)
                    }
                if (reasons.isEmpty()) null else candidate to reasons
            }
        val ordered =
            qualified.sortedWith(
                compareBy<Pair<WindowCandidate, Set<WindowReason>>> { (_, reasons) -> tier(reasons) }
                    .thenByDescending { (candidate, _) -> candidate.createdAt },
            )
        val members = mutableListOf<WindowMember>()
        var bytes = 0L
        for ((candidate, reasons) in ordered) {
            if (members.size == budget.maxItems) break
            if (bytes + candidate.sizeBytes > budget.maxBytes) continue
            bytes += candidate.sizeBytes
            members += WindowMember(candidate.id, reasons)
        }
        return WindowPlan(
            today = today,
            members = members,
            dropped = qualified.size - members.size,
            nextChangeAt = nextStreakDayStart(now, zone, dayStartHour),
        )
    }

    companion object {
        const val DEFAULT_RECENT_DAYS = 14

        /** What someone most likely looks for on the wrist comes first when the budget runs out. */
        private val BUDGET_ORDER = listOf(WindowReason.RECENT, WindowReason.STARRED, WindowReason.ON_THIS_DAY, WindowReason.REWIND)

        /** The same date in an earlier year; on February 28 of a year without February 29, February 29 counts too. */
        fun isOnThisDay(
            day: LocalDate,
            today: LocalDate,
        ): Boolean {
            if (day.year >= today.year) return false
            if (day.month == today.month && day.day == today.day) return true
            val leapDayMissing = today.month == Month.FEBRUARY && today.day == 28 && !isLeapYear(today.year)
            return leapDayMissing && day.month == Month.FEBRUARY && day.day == 29
        }

        private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        private fun tier(reasons: Set<WindowReason>): Int = BUDGET_ORDER.indexOfFirst { it in reasons }
    }
}
```

- [ ] **Step 4: Run the tests again**

Run the Step 2 command again.
Expected: PASS (9 tests).

- [ ] **Step 5: Run the module checks and the iOS compile**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:domain:jvmTest :client:domain:compileKotlinIosSimulatorArm64 :client:domain:compileTestKotlinIosSimulatorArm64 :client:domain:ktlintCheck :client:domain:detekt --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task12-window-policy.txt <<'EOF'
chore(wear): choose what the watch's library window contains

Adds the rule for which notes the redesigned watch app stores, not
used yet. A note is in the window when it was written in the last
fourteen days, on this date in an earlier year, or picked as starred
or for Rewind. Days follow the phone's day-start hour and time zone, so a
note written at 1:30 AM belongs to the day before, and the window
turns over at the next day start even across a clock change. On
February 28 of a year without a leap day, notes from February 29 count
as on this day.

When the window is full, recent notes come first, then starred, then
on this day, then Rewind, newest first within each.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/domain/src/commonMain/kotlin/app/logdate/client/domain/watch/WatchWindowPolicy.kt client/domain/src/commonTest/kotlin/app/logdate/client/domain/watch/WatchWindowPolicyTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task12-window-policy.txt
```

---

### Task 13: Publish the watch's library window from the phone (D5, phone)

While the watch lists `window.v1`, the phone publishes the library window Task 12 chooses: one `RecordEnvelope` data item per note at `WearPaths.windowItem(NOTE, id)` (origin `PHONE`, its reasons, the version 1 note JSON as the body, `media = []` until D6), and, for a note that leaves the window, its item deleted and an urgent tombstone at `WearPaths.windowTombstone(NOTE, id)`. `WindowManifestStore` records what was published, so an unchanged note is not written again, a failed write is tried again on the next publish, and tombstones are deleted after a week. The publisher runs on every change to the notes, when the watch starts taking the window, at each day start, and when a watch asks: `WindowResyncResponder` answers `/logdate/v2/window/resync` with the whole window after publishing it, and a version 1 "send me your notes" message from a watch that takes the window publishes the window instead of every note. The phone's state document now includes its day-start hour and its campfire, and the phone advertises `window.v1`.

**Files:**
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WindowMessages.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocol.kt:18-22` (`WINDOW_SCHEMA`)
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowExtras.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowManifestStore.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowDataItems.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WatchWindowPublisher.kt`
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowResyncResponder.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt` (`CampfireState.toSnapshot`)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt` (`answerRequest`, the `SYNC_REQUEST_PATH` branch at `:124-127`)
- Modify: `app/android-main/src/main/AndroidManifest.xml` (the `REQUEST_RECEIVED` filter Task 9 added)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt`
- Test: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WindowMessagesTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/window/WatchWindowPublisherTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/window/WindowResyncResponderTest.kt`
- Test: `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt` (1 new test)

**Interfaces:**
- Consumes: Task 12's `WatchWindowPolicy`, `WindowCandidate`, `WindowExtraIds`, `WindowMember`; Task 8's `EnvelopeDataMap`, `NoteEnvelopeBody`; Task 9's `PhoneDataLayerListenerService.answerRequest` and `REQUEST_RECEIVED` filter; Task 5's `PhoneStateParts`, `cloudPresence`, `PhoneStatePublisher(features, parts)`; Task 2's `localNodeIdOrNull`; `nextStreakDayStart`, `DEFAULT_STREAK_DAY_START_HOUR`, `ObserveCampfireUseCase`, `CampfireState`; `LogdatePreferencesDataSource.getPreferences().dayStartHour`; `WearProtocolGate.isFeatureActive(WearFeatures.WINDOW)`.
- Produces:
  - `WearProtocol.WINDOW_SCHEMA = 1`
  - `@Serializable data class WindowResyncRequest(val schema: Int = WearProtocol.WINDOW_SCHEMA)`, `@Serializable data class WindowMemberRef(val kind: RecordKind, val id: String, val contentVersion: Long)`, `@Serializable data class WindowResyncResponse(val schema: Int = WearProtocol.WINDOW_SCHEMA, val members: List<WindowMemberRef>, val publishedAt: Long)`, `object WindowTombstoneDataMap { const val KEY_REMOVED_AT = "removedAt"; fun toDataMap(removedAt: Long): Map<String, String> }`
  - `fun interface WindowExtras { suspend fun ids(): WindowExtraIds; companion object { val None: WindowExtras } }`
  - `@Serializable data class PublishedItem(val contentVersion: Long, val reasons: Set<WindowReason>)`, `@Serializable data class PublishedWindow(val items: Map<String, PublishedItem> = emptyMap(), val tombstones: Map<String, Long> = emptyMap())`, `class WindowManifestStore(storage: KeyValueStorage) { suspend fun read(): PublishedWindow; suspend fun write(window: PublishedWindow); companion object { const val KEY: String } }`
  - `interface WindowDataItems { suspend fun put(path: String, data: Map<String, String>, urgent: Boolean): Boolean; suspend fun deleteOwn(path: String): Boolean }`, `class GoogleWindowDataItems(dataClient: DataClient, localNodeId: suspend () -> String?) : WindowDataItems`
  - `data class WindowPublishResult(val members: List<WindowMemberRef>, val written: Int, val removed: Int, val failed: Int, val tooLarge: Int, val publishedAt: Long)`, `class WatchWindowPublisher(active: Flow<Boolean>, notes: JournalNotesRepository, dayStartHour: suspend () -> Int, extras: WindowExtras, manifest: WindowManifestStore, items: WindowDataItems, policy: WatchWindowPolicy = WatchWindowPolicy(), mapper: NoteDataMapper = NoteDataMapper(), zone: () -> TimeZone = { TimeZone.currentSystemDefault() }, clock: Clock = Clock.System) { suspend fun publishIfActive(): WindowPublishResult?; fun start(scope: CoroutineScope): Job }`
  - `class WindowResyncResponder(publisher: WatchWindowPublisher) { suspend fun answer(payload: ByteArray): ByteArray? }`
  - `fun CampfireState.toSnapshot(at: Instant): CampfireSnapshot`

**Failure modes and how they surface:**
- A window item or tombstone that cannot be written: counted in `WindowPublishResult.failed`, logged, left out of the published record, and written again on the next publish; the note is not listed to the watch until it is written.
- An item that cannot be deleted: logged; the tombstone removes the note from the watch regardless.
- A note body over 90 KB: left out of the window, counted in `tooLarge` and logged, until D6 sends large bodies on a channel.
- More notes than the budget: logged with the count (`WindowPlan.dropped`).
- The record of what was published cannot be read: logged as an error and the whole window is published again (the Data Layer drops writes identical to what it already has).
- A resync request from a newer watch, an unreadable one, or one while the window is off on this phone: logged, and the request fails, so the watch leaves its window unchanged and asks again later.
- A publish that throws: logged; the next change publishes again.

- [ ] **Step 1: Write the failing tests**

Create `client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WindowMessagesTest.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Tests the library window's messages: the resync answer and the tombstone. */
class WindowMessagesTest {
    @Test
    fun `a resync answer reads back`() {
        val response = WindowResyncResponse(members = listOf(WindowMemberRef(RecordKind.NOTE, "a", 5)), publishedAt = 9)
        val text = WearJson.encode(WindowResyncResponse.serializer(), response)

        assertEquals(Decoded.Supported(response), WearJson.decode(text, WearProtocol.WINDOW_SCHEMA, WindowResyncResponse.serializer()))
    }

    @Test
    fun `a resync answer from a newer phone is unsupported`() {
        val newer = """{"schema":2,"members":[],"publishedAt":1}"""

        assertIs<Decoded.Unsupported>(WearJson.decode(newer, WearProtocol.WINDOW_SCHEMA, WindowResyncResponse.serializer()))
    }

    @Test
    fun `a tombstone says when its record left the window`() {
        assertEquals(mapOf(WindowTombstoneDataMap.KEY_REMOVED_AT to "7"), WindowTombstoneDataMap.toDataMap(7))
    }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/window/WatchWindowPublisherTest.kt`:

```kotlin
package app.logdate.client.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowReason
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WatchWindowPublisher]: the window goes out once, changes go out as they happen, and leavers get tombstones. */
class WatchWindowPublisherTest {
    private val start = LocalDateTime(2026, 10, 10, 10, 0).toInstant(TimeZone.UTC)
    private var now = start
    private val notesFlow = MutableStateFlow<List<JournalNote>>(emptyList())
    private val notes = mockk<JournalNotesRepository> { every { allNotesObserved } returns notesFlow }
    private val storage = MemoryKeyValueStorage()
    private val items = FakeWindowItems()
    private val active = MutableStateFlow(true)
    private val publisher =
        WatchWindowPublisher(
            active = active,
            notes = notes,
            dayStartHour = { 4 },
            extras = WindowExtras.None,
            manifest = WindowManifestStore(storage),
            items = items,
            zone = { TimeZone.UTC },
            clock =
                object : Clock {
                    override fun now(): Instant = now
                },
        )
    private val recent = text("written yesterday", start - 1.days)
    private val old = text("written long ago", start - 400.days)

    @Test
    fun `while the watch does not take the window nothing is published`() =
        runTest {
            active.value = false
            notesFlow.value = listOf(recent)

            assertNull(publisher.publishIfActive())
            assertTrue(items.puts.isEmpty())
        }

    @Test
    fun `each note in the window is written with its reasons`() =
        runTest {
            notesFlow.value = listOf(recent, old)

            val result = assertNotNull(publisher.publishIfActive())

            val (path, data) = items.puts.single()
            assertEquals(WearPaths.windowItem(RecordKind.NOTE, recent.uid), path)
            val envelope = (EnvelopeDataMap.fromDataMap(data) as Decoded.Supported).value
            assertEquals(setOf(WindowReason.RECENT), envelope.reasons)
            assertEquals(RecordOrigin.PHONE, envelope.origin)
            val expected = WindowMemberRef(RecordKind.NOTE, recent.uid.toString(), recent.lastUpdated.toEpochMilliseconds())
            assertEquals(listOf(expected), result.members)
        }

    @Test
    fun `a note unchanged since the last publish is not written again`() =
        runTest {
            notesFlow.value = listOf(recent)

            publisher.publishIfActive()
            publisher.publishIfActive()

            assertEquals(1, items.puts.size)
        }

    @Test
    fun `a note that left the window is deleted and gets a tombstone`() =
        runTest {
            notesFlow.value = listOf(recent)
            publisher.publishIfActive()
            notesFlow.value = emptyList()

            val result = assertNotNull(publisher.publishIfActive())

            assertEquals(listOf(WearPaths.windowItem(RecordKind.NOTE, recent.uid)), items.deleted)
            assertEquals(WearPaths.windowTombstone(RecordKind.NOTE, recent.uid), items.puts.last().first)
            assertTrue(items.urgent.last())
            assertEquals(1, result.removed)
            assertTrue(result.members.isEmpty())
        }

    @Test
    fun `a note that could not be written is left out and written on the next publish`() =
        runTest {
            items.putOk = false
            notesFlow.value = listOf(recent)

            val failed = assertNotNull(publisher.publishIfActive())

            assertEquals(1, failed.failed)
            assertTrue(failed.members.isEmpty())

            items.putOk = true
            assertEquals(1, assertNotNull(publisher.publishIfActive()).written)
        }

    @Test
    fun `a note too large for one data item is left out and counted`() =
        runTest {
            notesFlow.value = listOf(text("x".repeat(100 * 1024), start - 1.days))

            assertEquals(1, assertNotNull(publisher.publishIfActive()).tooLarge)
            assertTrue(items.puts.isEmpty())
        }

    @Test
    fun `a tombstone is deleted after a week`() =
        runTest {
            notesFlow.value = listOf(recent)
            publisher.publishIfActive()
            notesFlow.value = emptyList()
            publisher.publishIfActive()
            now = start + 8.days

            publisher.publishIfActive()

            assertTrue(WearPaths.windowTombstone(RecordKind.NOTE, recent.uid) in items.deleted)
        }

    @Test
    fun `a note back in the window loses its tombstone`() =
        runTest {
            notesFlow.value = listOf(recent)
            publisher.publishIfActive()
            notesFlow.value = emptyList()
            publisher.publishIfActive()
            notesFlow.value = listOf(recent)

            publisher.publishIfActive()

            assertTrue(WearPaths.windowTombstone(RecordKind.NOTE, recent.uid) in items.deleted)
            assertEquals(WearPaths.windowItem(RecordKind.NOTE, recent.uid), items.puts.last().first)
        }

    @Test
    fun `an unreadable record of what was sent sends the whole window again`() =
        runTest {
            storage.putString(WindowManifestStore.KEY, "nope")
            notesFlow.value = listOf(recent)

            assertEquals(1, assertNotNull(publisher.publishIfActive()).written)
        }

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(uid = Uuid.random(), creationTimestamp = at, lastUpdated = at, content = content)

    private class FakeWindowItems : WindowDataItems {
        var putOk = true
        val puts = mutableListOf<Pair<String, Map<String, String>>>()
        val urgent = mutableListOf<Boolean>()
        val deleted = mutableListOf<String>()

        override suspend fun put(
            path: String,
            data: Map<String, String>,
            urgent: Boolean,
        ): Boolean {
            if (!putOk) return false
            puts += path to data
            this.urgent += urgent
            return true
        }

        override suspend fun deleteOwn(path: String): Boolean {
            deleted += path
            return true
        }
    }
}
```

Create `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/window/WindowResyncResponderTest.kt`:

```kotlin
package app.logdate.client.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowResyncRequest
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Tests [WindowResyncResponder]: a watch asking for the whole window gets it, freshly published. */
class WindowResyncResponderTest {
    private val publisher = mockk<WatchWindowPublisher>()
    private val responder = WindowResyncResponder(publisher)
    private val request = WearJson.encodeToBytes(WindowResyncRequest.serializer(), WindowResyncRequest())

    @Test
    fun `the watch gets the whole window the phone just published`() =
        runTest {
            val members = listOf(WindowMemberRef(RecordKind.NOTE, "a", 5))
            coEvery { publisher.publishIfActive() } returns
                WindowPublishResult(members = members, written = 0, removed = 0, failed = 0, tooLarge = 0, publishedAt = 9)

            val answer = assertNotNull(responder.answer(request))

            assertEquals(
                Decoded.Supported(WindowResyncResponse(members = members, publishedAt = 9)),
                WearJson.decode(answer, WearProtocol.WINDOW_SCHEMA, WindowResyncResponse.serializer()),
            )
        }

    @Test
    fun `a request from a newer watch gets no answer`() =
        runTest {
            assertNull(responder.answer("""{"schema":2}""".encodeToByteArray()))
        }

    @Test
    fun `with the window off on this phone the request gets no answer`() =
        runTest {
            coEvery { publisher.publishIfActive() } returns null

            assertNull(responder.answer(request))
        }
}
```

In `app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt`, add the imports `app.logdate.client.domain.streak.CampfireState`, `app.logdate.client.domain.streak.FirePhase`, `app.logdate.client.sync.peer.state.CampfireSnapshot` and `kotlin.time.Instant` (each only if missing) and:

```kotlin
    @Test
    fun `the phone's campfire crosses as its phase and run and whether it is lit today`() {
        val state =
            CampfireState(
                phase = FirePhase.BURNING,
                loggedToday = true,
                runDays = 6,
                size = null,
                longestRunDays = 9,
                totalDaysJournaled = 40,
                isRekindled = false,
            )

        assertEquals(
            CampfireSnapshot(phase = "BURNING", runDays = 6, litToday = true, computedAt = 5),
            state.toSnapshot(Instant.fromEpochMilliseconds(5)),
        )
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest --tests 'app.logdate.client.sync.datalayer.v2.WindowMessagesTest' :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.v2.window.*' --tests 'app.logdate.client.sync.v2.PhoneStatePublisherTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'WindowResyncResponse'", "Unresolved reference 'WINDOW_SCHEMA'" (the `:client:sync` tests stop the build first; once Step 3's common part exists, the phone tests fail on "Unresolved reference 'WatchWindowPublisher'" and "Unresolved reference 'toSnapshot'").

- [ ] **Step 3: Implement the messages**

In `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocol.kt`, after `const val LIVE_SCHEMA = 1` add `const val WINDOW_SCHEMA = 1`.

Create `client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WindowMessages.kt`:

```kotlin
package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

/** The watch asks for the phone's whole library window, with `MessageClient.sendRequest` to [WearPaths.WINDOW_RESYNC]. */
@Serializable
data class WindowResyncRequest(
    val schema: Int = WearProtocol.WINDOW_SCHEMA,
)

/** One record in the window and the version published, so the watch can tell which copies it lacks. */
@Serializable
data class WindowMemberRef(
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
)

/** The phone's whole window. Anything the watch has from the window and this does not list has left it. */
@Serializable
data class WindowResyncResponse(
    val schema: Int = WearProtocol.WINDOW_SCHEMA,
    val members: List<WindowMemberRef>,
    val publishedAt: Long,
)

/** A tombstone's data item: its path names the record, and its value says when the record left the window. */
object WindowTombstoneDataMap {
    const val KEY_REMOVED_AT = "removedAt"

    fun toDataMap(removedAt: Long): Map<String, String> = mapOf(KEY_REMOVED_AT to removedAt.toString())
}
```

- [ ] **Step 4: Implement the publisher and its parts**

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowExtras.kt`:

```kotlin
package app.logdate.client.sync.v2.window

import app.logdate.client.domain.watch.WindowExtraIds

/** Notes other features pick for the watch's window. Rewind and starred notes supply them in D11; until then there are none. */
fun interface WindowExtras {
    suspend fun ids(): WindowExtraIds

    companion object {
        val None = WindowExtras { WindowExtraIds() }
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowManifestStore.kt`:

```kotlin
package app.logdate.client.sync.v2.window

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowReason
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable

@Serializable
data class PublishedItem(
    val contentVersion: Long,
    val reasons: Set<WindowReason>,
)

/** What the phone last published to the watch's window, by note id, and when each tombstone was written. */
@Serializable
data class PublishedWindow(
    val items: Map<String, PublishedItem> = emptyMap(),
    val tombstones: Map<String, Long> = emptyMap(),
)

class WindowManifestStore(
    private val storage: KeyValueStorage,
) {
    suspend fun read(): PublishedWindow {
        val text = storage.getString(KEY) ?: return PublishedWindow()
        return try {
            WearJson.json.decodeFromString(PublishedWindow.serializer(), text)
        } catch (e: IllegalArgumentException) {
            Napier.e("The record of what this phone sent the watch's window could not be read; the whole window is sent again", e)
            PublishedWindow()
        }
    }

    suspend fun write(window: PublishedWindow) {
        storage.putString(KEY, WearJson.encode(PublishedWindow.serializer(), window))
    }

    companion object {
        const val KEY = "phone.watch_window.published.v1"
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowDataItems.kt`:

```kotlin
package app.logdate.client.sync.v2.window

import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** The phone's own window data items. */
interface WindowDataItems {
    /** An [urgent] item goes out at once; the Data Layer may delay an ordinary one by up to 30 minutes. */
    suspend fun put(
        path: String,
        data: Map<String, String>,
        urgent: Boolean,
    ): Boolean

    /** Deletes the item this phone wrote at [path]; true when it is gone, including when there was none. */
    suspend fun deleteOwn(path: String): Boolean
}

class GoogleWindowDataItems(
    private val dataClient: DataClient,
    private val localNodeId: suspend () -> String?,
) : WindowDataItems {
    override suspend fun put(
        path: String,
        data: Map<String, String>,
        urgent: Boolean,
    ): Boolean =
        try {
            val map = PutDataMapRequest.create(path)
            data.forEach { (key, value) -> map.dataMap.putString(key, value) }
            val request = map.asPutDataRequest()
            if (urgent) request.setUrgent()
            dataClient.putDataItem(request).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Failed to write the watch window item at $path", e)
            false
        }

    override suspend fun deleteOwn(path: String): Boolean {
        val node = localNodeId()
        if (node == null) {
            Napier.w("This phone's Data Layer node is unknown; the window item at $path is deleted on a later publish")
            return false
        }
        return try {
            val uri =
                Uri
                    .Builder()
                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                    .authority(node)
                    .path(path)
                    .build()
            dataClient.deleteDataItems(uri).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Failed to delete the watch window item at $path", e)
            false
        }
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WatchWindowPublisher.kt`:

```kotlin
package app.logdate.client.sync.v2.window

import app.logdate.client.domain.streak.nextStreakDayStart
import app.logdate.client.domain.watch.WatchWindowPolicy
import app.logdate.client.domain.watch.WindowCandidate
import app.logdate.client.domain.watch.WindowMember
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowTombstoneDataMap
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** What one publish did. [members] is the window as the watch can read it now. */
data class WindowPublishResult(
    val members: List<WindowMemberRef>,
    val written: Int,
    val removed: Int,
    val failed: Int,
    val tooLarge: Int,
    val publishedAt: Long,
)

/**
 * Publishes the watch's library window: one data item per note [WatchWindowPolicy] chooses, a tombstone
 * for each note that left, and nothing for a note unchanged since the last publish. [WindowManifestStore]
 * records what went out, so a failed write is tried again and a note that left is removed once.
 */
class WatchWindowPublisher(
    private val active: Flow<Boolean>,
    private val notes: JournalNotesRepository,
    private val dayStartHour: suspend () -> Int,
    private val extras: WindowExtras,
    private val manifest: WindowManifestStore,
    private val items: WindowDataItems,
    private val policy: WatchWindowPolicy = WatchWindowPolicy(),
    private val mapper: NoteDataMapper = NoteDataMapper(),
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val clock: Clock = Clock.System,
) {
    private val publishing = Mutex()

    /** Publishes the window while the watch takes it; null while it does not. */
    suspend fun publishIfActive(): WindowPublishResult? {
        if (!active.first()) return null
        return publishing.withLock { publish() }
    }

    /** Publishes on every change to the notes, when the watch starts taking the window, and at each day start. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            merge(notes.allNotesObserved.map { }, active.filter { it }.map { }, dayStarts())
                .debounce(SETTLE_MS)
                .collect { publishLogged() }
        }

    private suspend fun publish(): WindowPublishResult {
        val now = clock.now()
        val all = notes.allNotesObserved.first().associateBy { it.uid }
        val bodies = all.mapValues { (_, note) -> NoteEnvelopeBody.encode(note, mapper) }
        val candidates = all.values.map { WindowCandidate(it.uid, it.creationTimestamp, bodies.getValue(it.uid).sizeBytes()) }
        val plan = policy.choose(candidates, extras.ids(), now, zone(), dayStartHour())
        if (plan.dropped > 0) Napier.w("${plan.dropped} note(s) do not fit the watch's library window; the most recent are chosen")
        val previous = manifest.read()
        val published = previous.items.toMutableMap()
        val tombstones = previous.tombstones.toMutableMap()
        val counts = Counts()
        plan.members.forEach { member -> write(all.getValue(member.id), member, bodies.getValue(member.id), published, tombstones, counts) }
        val inWindow = plan.members.mapTo(HashSet()) { it.id.toString() }
        (published.keys - inWindow).forEach { id -> remove(id, published, tombstones, now, counts) }
        clearOldTombstones(tombstones, now)
        manifest.write(PublishedWindow(published, tombstones))
        val members =
            plan.members.mapNotNull { member ->
                published[member.id.toString()]?.let { WindowMemberRef(RecordKind.NOTE, member.id.toString(), it.contentVersion) }
            }
        return WindowPublishResult(members, counts.written, counts.removed, counts.failed, counts.tooLarge, now.toEpochMilliseconds())
    }

    private suspend fun write(
        note: JournalNote,
        member: WindowMember,
        body: JsonElement,
        published: MutableMap<String, PublishedItem>,
        tombstones: MutableMap<String, Long>,
        counts: Counts,
    ) {
        val id = member.id.toString()
        val version = note.lastUpdated.toEpochMilliseconds()
        val wanted = PublishedItem(version, member.reasons)
        if (published[id] == wanted) return
        val envelope =
            RecordEnvelope(
                kind = RecordKind.NOTE,
                id = id,
                contentVersion = version,
                syncVersion = note.syncVersion,
                origin = RecordOrigin.PHONE,
                reasons = member.reasons,
                priority = DeliveryPriority.NORMAL,
                body = body,
            )
        val data =
            try {
                // The send time is the version, so a repeat of the same version is identical and the Data Layer sends nothing.
                EnvelopeDataMap.toDataMap(envelope, sentAt = version)
            } catch (e: IllegalArgumentException) {
                counts.tooLarge++
                Napier.w("A note is too large for one data item; it stays out of the watch's window until large bodies travel on a channel")
                return
            }
        if (!items.put(WearPaths.windowItem(RecordKind.NOTE, member.id), data, urgent = false)) {
            counts.failed++
            Napier.w("Could not write a note to the watch's library window; it is written again on the next publish")
            return
        }
        if (tombstones.remove(id) != null) items.deleteOwn(WearPaths.windowTombstone(RecordKind.NOTE, member.id))
        published[id] = wanted
        counts.written++
    }

    private suspend fun remove(
        id: String,
        published: MutableMap<String, PublishedItem>,
        tombstones: MutableMap<String, Long>,
        now: Instant,
        counts: Counts,
    ) {
        val uuid = Uuid.parseOrNull(id)
        if (uuid == null) {
            published.remove(id)
            return
        }
        if (!items.deleteOwn(WearPaths.windowItem(RecordKind.NOTE, uuid))) {
            Napier.w("Could not delete a note's window item; its tombstone removes it from the watch")
        }
        val at = now.toEpochMilliseconds()
        if (!items.put(WearPaths.windowTombstone(RecordKind.NOTE, uuid), WindowTombstoneDataMap.toDataMap(at), urgent = true)) {
            counts.failed++
            Napier.w("Could not tell the watch a note left its library window; it is told on the next publish")
            return
        }
        published.remove(id)
        tombstones[id] = at
        counts.removed++
    }

    /** A week is long enough for every watch to see a tombstone; a watch away longer asks for the whole window. */
    private suspend fun clearOldTombstones(
        tombstones: MutableMap<String, Long>,
        now: Instant,
    ) {
        val cutoff = now.toEpochMilliseconds() - TOMBSTONE_KEEP_MS
        tombstones.filterValues { it < cutoff }.keys.forEach { id ->
            val uuid = Uuid.parseOrNull(id)
            if (uuid == null || items.deleteOwn(WearPaths.windowTombstone(RecordKind.NOTE, uuid))) tombstones.remove(id)
        }
    }

    private fun dayStarts(): Flow<Unit> =
        flow {
            while (true) {
                val next = nextStreakDayStart(clock.now(), zone(), dayStartHour())
                delay(next - clock.now() + DAY_START_SLACK)
                emit(Unit)
            }
        }

    private suspend fun publishLogged() {
        try {
            publishIfActive()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not publish the watch's library window; it is published again on the next change", e)
        }
    }

    private fun JsonElement.sizeBytes(): Long = toString().encodeToByteArray().size.toLong()

    private class Counts {
        var written = 0
        var removed = 0
        var failed = 0
        var tooLarge = 0
    }

    private companion object {
        const val SETTLE_MS = 2_000L
        const val TOMBSTONE_KEEP_MS = 7L * 24 * 60 * 60 * 1000
        val DAY_START_SLACK = 1.seconds
    }
}
```

Create `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowResyncResponder.kt`:

```kotlin
package app.logdate.client.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.datalayer.v2.WindowResyncRequest
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import io.github.aakira.napier.Napier

/** Answers a watch asking for the whole library window, publishing it first so the answer matches the items. */
class WindowResyncResponder(
    private val publisher: WatchWindowPublisher,
) {
    /** Null fails the request; the watch leaves its window unchanged and asks again later. */
    suspend fun answer(payload: ByteArray): ByteArray? {
        when (val decoded = WearJson.decode(payload, WearProtocol.WINDOW_SCHEMA, WindowResyncRequest.serializer())) {
            is Decoded.Supported -> Unit
            is Decoded.Unsupported -> {
                Napier.w("A watch asked for the library window with schema ${decoded.schema}; it gets an answer once this phone updates")
                return null
            }
            is Decoded.Malformed -> {
                Napier.e("A watch's library window request could not be read: ${decoded.reason}")
                return null
            }
        }
        val published = publisher.publishIfActive()
        if (published == null) {
            Napier.w("A watch asked for the library window while it is off on this phone")
            return null
        }
        val response = WindowResyncResponse(members = published.members, publishedAt = published.publishedAt)
        return WearJson.encodeToBytes(WindowResyncResponse.serializer(), response)
    }
}
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt`, after `PhoneStateParts` add:

```kotlin
/** The campfire as the phone computed it; the watch's window is too short to compute a long run itself. */
fun CampfireState.toSnapshot(at: Instant): CampfireSnapshot =
    CampfireSnapshot(phase = phase.name, runDays = runDays, litToday = loggedToday, computedAt = at.toEpochMilliseconds())
```

with imports `app.logdate.client.domain.streak.CampfireState` and `kotlin.time.Instant`.

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt`:

1. In `answerRequest`, add the branch `WearPaths.WINDOW_RESYNC -> koin.get<WindowResyncResponder>().answer(request)` before `else ->`.
2. In `onMessageReceived`, make the first line of the `messageEvent.path == WearAudioRequestPaths.SYNC_REQUEST_PATH ->` branch `if (publishWindowInstead()) return@launch`.
3. Add:

```kotlin
    /** A watch that takes the library window gets the window instead of every note. */
    private suspend fun publishWindowInstead(): Boolean {
        val publisher = resolveKoin()?.getOrNull<WatchWindowPublisher>() ?: return false
        return publisher.publishIfActive() != null
    }
```

4. Add the imports `app.logdate.client.sync.v2.window.WatchWindowPublisher` and `app.logdate.client.sync.v2.window.WindowResyncResponder`.

In `app/android-main/src/main/AndroidManifest.xml`, in the `REQUEST_RECEIVED` filter of `PhoneDataLayerListenerService`, after the `/logdate/v2/receipts` entry add:

```xml
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/v2/window" />
```

In `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt`:

1. Replace the `PhoneStatePublisher` binding with:

```kotlin
        single {
            val sessions = get<SessionStorage>()
            val preferences = get<LogdatePreferencesDataSource>()
            val campfire = get<ObserveCampfireUseCase>()
            PhoneStatePublisher(
                v2Enabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                channel = get(),
                capabilities = get(),
                peerStates = get(),
                features = setOf(WearFeatures.LINK_CLOUD, WearFeatures.RECEIPTS, WearFeatures.WINDOW),
                parts =
                    combine(sessions.getSessionFlow(), campfire()) { session, fire ->
                        PhoneStateParts(
                            cloud = cloudPresence(session),
                            dayStartHour = preferences.getPreferences().dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR,
                            campfire = fire?.toSnapshot(Clock.System.now()),
                        )
                    },
            )
        }
```

2. Add:

```kotlin
        single { WindowManifestStore(get()) }
        single<WindowDataItems> {
            val nodeClient = Wearable.getNodeClient(androidContext())
            GoogleWindowDataItems(Wearable.getDataClient(androidContext())) { nodeClient.localNodeIdOrNull() }
        }
        single<WindowExtras> { WindowExtras.None }
        single {
            val preferences = get<LogdatePreferencesDataSource>()
            WatchWindowPublisher(
                active = get<WearProtocolGate>().isFeatureActive(WearFeatures.WINDOW),
                notes = get(),
                dayStartHour = { preferences.getPreferences().dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR },
                extras = get(),
                manifest = get(),
                items = get(),
            )
        }
        single { WindowResyncResponder(get()) }
```

3. Add the imports `app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR`, `app.logdate.client.domain.streak.ObserveCampfireUseCase`, `app.logdate.client.health.util.LogdatePreferencesDataSource`, `app.logdate.client.sync.v2.toSnapshot`, `app.logdate.client.sync.v2.window.GoogleWindowDataItems`, `app.logdate.client.sync.v2.window.WatchWindowPublisher`, `app.logdate.client.sync.v2.window.WindowDataItems`, `app.logdate.client.sync.v2.window.WindowExtras`, `app.logdate.client.sync.v2.window.WindowManifestStore`, `app.logdate.client.sync.v2.window.WindowResyncResponder`, `kotlinx.coroutines.flow.combine` and `kotlin.time.Clock`, and remove `kotlinx.coroutines.flow.map` if nothing else uses it.

In `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt`, call `startWatchWindow()` after `startWatchReceipts()` and add:

```kotlin
    /** Keeps the watch's library window current; a failure here must not stop startup. */
    private fun startWatchWindow() {
        runCatching {
            get<WatchWindowPublisher>().start(get())
        }.onFailure { error ->
            Napier.w("Failed to start publishing the watch's library window", error)
        }
    }
```

with `import app.logdate.client.sync.v2.window.WatchWindowPublisher`.

- [ ] **Step 5: Run the tests again**

Run the Step 2 command again, then `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:android-main:testDebugUnitTest --tests 'app.logdate.client.sync.PhoneDataLayerListenerServiceTest' --console=plain`.
Expected: PASS (3 message tests, 9 publisher tests, 3 responder tests, the new campfire test, every existing publisher and listener test).

- [ ] **Step 6: Run every affected module's checks and the merged manifest check**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:sync:desktopTest :client:sync:compileKotlinIosSimulatorArm64 :client:sync:compileTestKotlinIosSimulatorArm64 :client:sync:ktlintCheck :client:sync:detekt :app:android-main:testDebugUnitTest :app:compose-main:ktlintCheck :app:compose-main:detekt :app:android-main:ktlintCheck :app:android-main:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL. Then:
```bash
grep -c "/logdate/v2/window" app/android-main/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
```
Expected: `1`.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task13-window-publish.txt <<'EOF'
chore(wear): publish the watch's library window from the phone

With the redesigned watch app on, the phone now sends the watch its
library window instead of every note: recent notes, notes from this
date in earlier years, and later starred and Rewind notes. A note is
sent again only when it changes, a note that leaves the window is
removed from the watch, and the window turns over at the phone's day
start. A watch can ask for the whole window at any time.

The phone's state for the watch now includes its day-start hour and
its campfire, and the phone advertises the window. A note too large to
send this way stays out of the window and is logged.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WindowMessages.kt client/sync/src/commonMain/kotlin/app/logdate/client/sync/datalayer/v2/WearProtocol.kt client/sync/src/commonTest/kotlin/app/logdate/client/sync/datalayer/v2/WindowMessagesTest.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowExtras.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowManifestStore.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowDataItems.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WatchWindowPublisher.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/window/WindowResyncResponder.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt app/android-main/src/main/AndroidManifest.xml app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneWatchSyncV2Module.kt app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/window/WatchWindowPublisherTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/window/WindowResyncResponderTest.kt app/android-main/src/test/kotlin/app/logdate/client/sync/v2/PhoneStatePublisherTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task13-window-publish.txt
```

---

### Task 14: Apply the phone's library window on the watch (D5, watch)

The watch's side of the library window. `WindowApplier` stores the note each window item contains (through `createFromSync`, so nothing is queued back to the phone) unless the watch has a change to that note not yet sent, and removes a note on its tombstone (through `deleteFromSync`, with its recording), again never one with a change not yet sent. `WindowReconciler` asks the phone for the whole window (`/logdate/v2/window/resync`) when the app starts and whenever the phone comes back into reach, applies any item in the Data Layer that the watch missed, and removes the notes that left. `WindowMembershipStore` records which notes the window placed, so only those leave; the first reconciliation also removes the notes an older build copied from the phone (every note on the watch, apart from anything not yet sent, that the window does not list). A marked moment in the window is ignored with a log line (D7). The watch's day boundary now follows the phone's day-start hour from a phone that publishes the window, and the watch advertises `window.v1`.

**Files:**
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowMembershipStore.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowApplier.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowItemSource.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowReconciler.kt`
- Create: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/PhoneDayStart.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt:136-156` (`processEvent`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/WearPhoneMessageHandler.kt` (`onPhoneReachable`)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt` (the `WearPhoneMessageHandler` binding)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt:44-51`
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt` (`startSync`)
- Modify: `app/wear/src/main/AndroidManifest.xml:83-104`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/WindowApplierTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/WindowReconcilerTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/PhoneDayStartTest.kt`
- Test: `app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt` (1 new test)
- Test: `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt` (window item and tombstone paths)

**Interfaces:**
- Consumes: Task 13's `WindowResyncRequest`, `WindowResyncResponse`, `WindowMemberRef`, `WearProtocol.WINDOW_SCHEMA`; Task 11's `DataLayerTransportV2.request` and `WearPhoneMessageHandler(ackHandler, syncManager, receiptsPushed)`; Task 8's `EnvelopeDataMap`, `NoteEnvelopeBody`; `WearPaths.parseWindowItem/parseWindowTombstone/windowItem/windowTombstone`; `SyncableJournalNotesRepository.createFromSync/deleteFromSync`; `SyncMetadataService.hasPending`; `SyncedAudioCache.delete(noteId)`, `deleteLocalAudio(mediaRef, audioDirectory)`; `DataClient.getDataItems(uri, DataClient.FILTER_PREFIX)`, `DataMapItem.fromDataItem`; `PeerStateStore.latest(PeerRole.PHONE)`; `WatchDayBoundaryPreferences(phoneDayStartHour)`.
- Produces:
  - `class WindowMembershipStore(storage: KeyValueStorage) { suspend fun members(): Set<String>?; suspend fun add(id: String); suspend fun remove(id: String); suspend fun replace(ids: Set<String>); companion object { const val KEY = "watch.window.members.v1" } }`
  - `class WindowApplier(active: suspend () -> Boolean, notes: SyncableJournalNotesRepository, outbox: SyncMetadataService, members: WindowMembershipStore, forgetMedia: suspend (JournalNote) -> Unit, mapper: NoteDataMapper = NoteDataMapper()) { suspend fun onItem(path: String, data: Map<String, String>): Boolean; suspend fun onTombstone(path: String); suspend fun remove(id: Uuid): Boolean }`
  - `fun interface WindowItemSource { suspend fun items(): List<Pair<String, Map<String, String>>> }`, `class DataClientWindowItemSource(dataClient: DataClient) : WindowItemSource`
  - `sealed interface ReconcileResult { data class Done(val applied: Int, val removed: Int); data object Failed }`, `class WindowReconciler(active: suspend () -> Boolean, request: suspend (String, ByteArray) -> Result<ByteArray>, source: WindowItemSource, applier: WindowApplier, members: WindowMembershipStore, localNoteIds: suspend () -> Set<String>) { suspend fun resync(): ReconcileResult? }`
  - `suspend fun phoneDayStartHour(peerStates: PeerStateStore): Int?`
  - `WearPhoneMessageHandler(..., phoneReturned: suspend () -> Unit = {})`

**Failure modes and how they surface:**
- A window item from a newer phone, one this build cannot read, one whose body travels on a channel (D6), one whose id does not match its path, or a moment: logged and ignored; the note shows once the watch can read it.
- A note with a change on the watch not yet sent: the window's copy and its tombstone wait, logged; the change goes to the phone first (Task 11) and the next item or reconciliation applies.
- The phone not answering, an unreadable answer, or the window items not readable: `ReconcileResult.Failed`, logged; nothing is removed, and the watch asks again when the phone is next in reach.
- The record of the window's notes cannot be read: logged as an error; the next reconciliation adopts every note on the watch again (leaving anything not yet sent in place).

- [ ] **Step 1: Write the failing tests**

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/WindowApplierTest.kt`:

```kotlin
package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowReason
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WindowApplier]: window items become notes on the watch, tombstones remove them, unsent changes win. */
class WindowApplierTest {
    private val stored = mutableMapOf<Uuid, JournalNote>()
    private val notes = mockk<SyncableJournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private val members = WindowMembershipStore(MemoryKeyValueStorage())
    private val forgotten = mutableListOf<Uuid>()
    private var active = true
    private val applier =
        WindowApplier(
            active = { active },
            notes = notes,
            outbox = outbox,
            members = members,
            forgetMedia = { forgotten += it.uid },
        )
    private val note =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
            content = "from the phone",
        )

    init {
        coEvery { notes.getNoteById(any()) } answers { stored[firstArg()] }
        coEvery { notes.createFromSync(any()) } answers {
            val created = firstArg<JournalNote>()
            stored[created.uid] = created
        }
        coEvery { notes.deleteFromSync(any()) } answers { stored.remove(firstArg<Uuid>()) }
        coEvery { outbox.hasPending(any(), any()) } returns false
    }

    @Test
    fun `a note in the window is stored and recorded as the window's`() =
        runTest {
            members.replace(emptySet())

            assertTrue(applier.onItem(path(note), item(note)))

            assertEquals(note, stored[note.uid])
            assertEquals(setOf(note.uid.toString()), members.members())
        }

    @Test
    fun `before the first reconciliation the window's notes are not recorded yet`() =
        runTest {
            applier.onItem(path(note), item(note))

            assertNull(members.members())
        }

    @Test
    fun `an older copy on the watch is replaced and a newer one stays`() =
        runTest {
            stored[note.uid] = note.copy(content = "older", lastUpdated = Instant.fromEpochMilliseconds(4))
            applier.onItem(path(note), item(note))
            assertEquals("from the phone", (stored.getValue(note.uid) as JournalNote.Text).content)

            stored[note.uid] = note.copy(content = "newer on the watch", lastUpdated = Instant.fromEpochMilliseconds(9))
            applier.onItem(path(note), item(note))
            assertEquals("newer on the watch", (stored.getValue(note.uid) as JournalNote.Text).content)
        }

    @Test
    fun `a note with a change not yet sent from the watch is left alone`() =
        runTest {
            coEvery { outbox.hasPending(EntityType.NOTE, note.uid.toString()) } returns true

            assertFalse(applier.onItem(path(note), item(note)))
            assertNull(stored[note.uid])
        }

    @Test
    fun `a tombstone removes the note and its recording`() =
        runTest {
            members.replace(setOf(note.uid.toString()))
            stored[note.uid] = note

            applier.onTombstone(WearPaths.windowTombstone(RecordKind.NOTE, note.uid))

            assertNull(stored[note.uid])
            assertEquals(listOf(note.uid), forgotten)
            assertEquals(emptySet(), members.members())
        }

    @Test
    fun `a tombstone leaves a note with a change not yet sent`() =
        runTest {
            stored[note.uid] = note
            coEvery { outbox.hasPending(EntityType.NOTE, note.uid.toString()) } returns true

            applier.onTombstone(WearPaths.windowTombstone(RecordKind.NOTE, note.uid))

            assertEquals(note, stored[note.uid])
        }

    @Test
    fun `nothing is applied while the window is off`() =
        runTest {
            active = false
            stored[note.uid] = note

            assertFalse(applier.onItem(path(note), item(note)))
            applier.onTombstone(WearPaths.windowTombstone(RecordKind.NOTE, note.uid))

            coVerify(exactly = 0) { notes.deleteFromSync(any()) }
        }

    @Test
    fun `a marked moment in the window is ignored`() =
        runTest {
            val id = Uuid.random()
            val moment =
                RecordEnvelope(
                    kind = RecordKind.MOMENT,
                    id = id.toString(),
                    contentVersion = 1,
                    origin = RecordOrigin.PHONE,
                    body = JsonObject(emptyMap()),
                )

            assertFalse(applier.onItem(WearPaths.windowItem(RecordKind.MOMENT, id), EnvelopeDataMap.toDataMap(moment, sentAt = 1)))
        }

    @Test
    fun `an item this build cannot read is ignored`() =
        runTest {
            assertFalse(applier.onItem(path(note), mapOf(EnvelopeDataMap.KEY_ENVELOPE to "nope")))
            assertNull(stored[note.uid])
        }

    private fun path(note: JournalNote) = WearPaths.windowItem(RecordKind.NOTE, note.uid)

    private fun item(note: JournalNote) =
        EnvelopeDataMap.toDataMap(
            NoteEnvelopeBody.envelope(note, RecordOrigin.PHONE, DeliveryPriority.NORMAL, reasons = setOf(WindowReason.RECENT)),
            sentAt = 5,
        )
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/WindowReconcilerTest.kt`:

```kotlin
package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WindowReconciler]: the watch's window matches the phone's after a resync, and unsent captures stay. */
class WindowReconcilerTest {
    private val stored = mutableMapOf<Uuid, JournalNote>()
    private val notes = mockk<SyncableJournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private val members = WindowMembershipStore(MemoryKeyValueStorage())
    private val applier = WindowApplier({ true }, notes, outbox, members, forgetMedia = {})
    private var answer: Result<ByteArray> = Result.failure(IllegalStateException("The phone has not answered"))
    private var items = emptyList<Pair<String, Map<String, String>>>()
    private val reconciler =
        WindowReconciler(
            active = { true },
            request = { _, _ -> answer },
            source = WindowItemSource { items },
            applier = applier,
            members = members,
            localNoteIds = { stored.keys.mapTo(HashSet()) { it.toString() } },
        )
    private val inWindow = text("in the window")
    private val copiedBefore = text("copied by an older build")
    private val unsent = text("captured and not yet sent")

    init {
        coEvery { notes.getNoteById(any()) } answers { stored[firstArg()] }
        coEvery { notes.createFromSync(any()) } answers {
            val created = firstArg<JournalNote>()
            stored[created.uid] = created
        }
        coEvery { notes.deleteFromSync(any()) } answers { stored.remove(firstArg<Uuid>()) }
        coEvery { outbox.hasPending(any(), any()) } returns false
        coEvery { outbox.hasPending(EntityType.NOTE, unsent.uid.toString()) } returns true
    }

    @Test
    fun `after the first reconciliation only the window and what is not yet sent remain`() =
        runTest {
            stored[copiedBefore.uid] = copiedBefore
            stored[unsent.uid] = unsent
            answer = window(inWindow)
            items = listOf(WearPaths.windowItem(RecordKind.NOTE, inWindow.uid) to item(inWindow))

            assertEquals(ReconcileResult.Done(applied = 1, removed = 1), reconciler.resync())
            assertEquals(setOf(inWindow.uid, unsent.uid), stored.keys)
            assertEquals(setOf(inWindow.uid.toString()), members.members())
        }

    @Test
    fun `later reconciliations remove only the notes the window placed`() =
        runTest {
            members.replace(setOf(inWindow.uid.toString()))
            stored[inWindow.uid] = inWindow
            stored[copiedBefore.uid] = copiedBefore
            answer = window()

            assertEquals(ReconcileResult.Done(applied = 0, removed = 1), reconciler.resync())
            assertEquals(setOf(copiedBefore.uid), stored.keys)
        }

    @Test
    fun `a phone that does not answer changes nothing`() =
        runTest {
            stored[copiedBefore.uid] = copiedBefore

            assertEquals(ReconcileResult.Failed, reconciler.resync())
            assertEquals(setOf(copiedBefore.uid), stored.keys)
        }

    @Test
    fun `nothing happens while the window is off`() =
        runTest {
            val off = WindowReconciler({ false }, { _, _ -> answer }, WindowItemSource { items }, applier, members, { emptySet() })

            assertNull(off.resync())
        }

    private fun window(vararg listed: JournalNote): Result<ByteArray> {
        val refs = listed.map { WindowMemberRef(RecordKind.NOTE, it.uid.toString(), it.lastUpdated.toEpochMilliseconds()) }
        val response = WindowResyncResponse(members = refs, publishedAt = 1)
        return Result.success(WearJson.encodeToBytes(WindowResyncResponse.serializer(), response))
    }

    private fun item(note: JournalNote) =
        EnvelopeDataMap.toDataMap(NoteEnvelopeBody.envelope(note, RecordOrigin.PHONE, DeliveryPriority.NORMAL), sentAt = 1)

    private fun text(content: String) =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
            content = content,
        )
}
```

Create `app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/PhoneDayStartTest.kt`:

```kotlin
package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests [phoneDayStartHour]: the watch trusts the hour only from a phone that publishes it. */
class PhoneDayStartTest {
    private val store = PeerStateStore()

    @Test
    fun `the hour is read from a phone that publishes the window`() =
        runTest {
            accept(PhoneState(dayStartHour = 5, features = setOf(WearFeatures.WINDOW)))

            assertEquals(5, phoneDayStartHour(store))
        }

    @Test
    fun `an older phone's state says nothing about its hour`() =
        runTest {
            accept(PhoneState(dayStartHour = 0))

            assertNull(phoneDayStartHour(store))
        }

    @Test
    fun `with no phone state there is no hour`() =
        runTest {
            assertNull(phoneDayStartHour(store))
        }

    private fun accept(phone: PhoneState) {
        store.accept("phone-node", Decoded.Supported(StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = phone)), PeerRole.PHONE)
    }
}
```

In `app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt`, add:

```kotlin
    @Test
    fun `the phone coming back also refreshes the library window`() =
        runTest {
            var refreshed = 0

            WearPhoneMessageHandler(ackHandler, syncManager, phoneReturned = { refreshed++ }).onPhoneReachable()

            assertEquals(1, refreshed)
            coVerify(exactly = 1) { syncManager.fullSync() }
        }
```

In `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt`, add `import app.logdate.client.sync.datalayer.v2.RecordKind` and add `WearPaths.windowItem(RecordKind.NOTE, noteId),` and `WearPaths.windowTombstone(RecordKind.NOTE, noteId),` to the list in `every data item the phone writes reaches the listener`.

- [ ] **Step 2: Run the tests to see them fail**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --tests 'app.logdate.wear.sync.v2.window.*' --tests 'app.logdate.wear.sync.WearPhoneMessageHandlerTest' --console=plain
```
Expected: FAIL, compilation errors "Unresolved reference 'WindowApplier'", "Unresolved reference 'WindowMembershipStore'", "Unresolved reference 'phoneDayStartHour'", "No parameter with name 'phoneReturned' found".

- [ ] **Step 3: Implement the window on the watch**

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowMembershipStore.kt`:

```kotlin
package app.logdate.wear.sync.v2.window

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.WearJson
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/**
 * The notes on the watch because the phone's library window lists them; only these leave the watch
 * when they leave the window. Null until the first reconciliation records it, so that one can also remove
 * the notes an older build copied from the phone.
 */
class WindowMembershipStore(
    private val storage: KeyValueStorage,
) {
    private val lock = Mutex()

    suspend fun members(): Set<String>? = lock.withLock { read() }

    /** Before the first reconciliation this records nothing; that reconciliation records the whole window. */
    suspend fun add(id: String) {
        lock.withLock {
            val current = read() ?: return@withLock
            write(current + id)
        }
    }

    suspend fun remove(id: String) {
        lock.withLock {
            val current = read() ?: return@withLock
            write(current - id)
        }
    }

    suspend fun replace(ids: Set<String>) {
        lock.withLock { write(ids) }
    }

    private suspend fun read(): Set<String>? {
        val text = storage.getString(KEY) ?: return null
        return try {
            WearJson.json.decodeFromString(IDS, text)
        } catch (e: IllegalArgumentException) {
            Napier.e("The watch's record of its library window could not be read; the next reconciliation rebuilds it", e)
            null
        }
    }

    private suspend fun write(ids: Set<String>) {
        storage.putString(KEY, WearJson.encode(IDS, ids))
    }

    companion object {
        const val KEY = "watch.window.members.v1"
        private val IDS = SetSerializer(String.serializer())
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowApplier.kt`:

```kotlin
package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Applies the phone's library window to the watch. Notes are written and removed the way synced notes
 * are, so nothing is queued back to the phone, and a note with a change on the watch not yet sent is
 * never overwritten or removed.
 */
class WindowApplier(
    private val active: suspend () -> Boolean,
    private val notes: SyncableJournalNotesRepository,
    private val outbox: SyncMetadataService,
    private val members: WindowMembershipStore,
    private val forgetMedia: suspend (JournalNote) -> Unit,
    private val mapper: NoteDataMapper = NoteDataMapper(),
) {
    /** Returns true when the watch has the item's version of the note, or a newer one, afterwards. */
    suspend fun onItem(
        path: String,
        data: Map<String, String>,
    ): Boolean {
        if (!active()) return false
        val note = readNote(path, data) ?: return false
        val id = note.uid.toString()
        if (outbox.hasPending(EntityType.NOTE, id)) {
            Napier.i("Note $id has a change on this watch not yet sent; the window's copy waits for it")
            return false
        }
        val local = notes.getNoteById(note.uid)
        if (local == null || local.lastUpdated < note.lastUpdated) notes.createFromSync(note)
        members.add(id)
        return true
    }

    suspend fun onTombstone(path: String) {
        if (!active()) return
        val recordPath = WearPaths.parseWindowTombstone(path) ?: return
        if (recordPath.kind != RecordKind.NOTE) return
        remove(recordPath.id)
    }

    /** Removes a note that left the window, with its recording, unless the watch has a change to it not yet sent. */
    suspend fun remove(id: Uuid): Boolean {
        if (outbox.hasPending(EntityType.NOTE, id.toString())) {
            Napier.i("Note $id left the library window but has a change on this watch not yet sent; it stays")
            return false
        }
        val note = notes.getNoteById(id)
        if (note != null) {
            notes.deleteFromSync(id)
            if (notes.getNoteById(id) == null) forgetMedia(note)
        }
        members.remove(id.toString())
        return true
    }

    private fun readNote(
        path: String,
        data: Map<String, String>,
    ): JournalNote? {
        val recordPath = WearPaths.parseWindowItem(path) ?: return null
        if (recordPath.kind != RecordKind.NOTE) {
            Napier.i("Ignoring a ${recordPath.kind} in the library window; this watch shows notes only")
            return null
        }
        val envelope =
            when (val decoded = EnvelopeDataMap.fromDataMap(data)) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> {
                    Napier.w("A note in the library window was written by a newer phone; it shows once this watch updates")
                    return null
                }
                is Decoded.Malformed -> {
                    Napier.e("A note in the library window could not be read: ${decoded.reason}")
                    return null
                }
            }
        val body = envelope.body
        if (body == null) {
            Napier.w("A note in the library window sends its body on a channel, which this watch cannot fetch yet")
            return null
        }
        val note =
            try {
                NoteEnvelopeBody.decode(body, mapper)
            } catch (e: IllegalArgumentException) {
                Napier.e("A library window item's body is not a note", e)
                return null
            }
        if (note.uid != recordPath.id) {
            Napier.e("A library window item names a different note than its path; it is ignored")
            return null
        }
        return note
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowItemSource.kt`:

```kotlin
package app.logdate.wear.sync.v2.window

import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import kotlinx.coroutines.tasks.await

/** The library window items the phone wrote, as the Data Layer stores them on this watch. */
fun interface WindowItemSource {
    suspend fun items(): List<Pair<String, Map<String, String>>>
}

class DataClientWindowItemSource(
    private val dataClient: DataClient,
) : WindowItemSource {
    override suspend fun items(): List<Pair<String, Map<String, String>>> {
        val buffer = dataClient.getDataItems(WINDOW_ITEMS, DataClient.FILTER_PREFIX).await()
        return try {
            buffer.mapNotNull { item ->
                val path = item.uri.path ?: return@mapNotNull null
                val map = DataMapItem.fromDataItem(item).dataMap
                path to map.keySet().associateWith { key -> map.getString(key).orEmpty() }
            }
        } finally {
            buffer.release()
        }
    }

    private companion object {
        val WINDOW_ITEMS: Uri = Uri.parse("wear://*/logdate/v2/p2w/window/")
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowReconciler.kt`:

```kotlin
package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.datalayer.v2.WindowResyncRequest
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

sealed interface ReconcileResult {
    data class Done(
        val applied: Int,
        val removed: Int,
    ) : ReconcileResult

    data object Failed : ReconcileResult
}

/**
 * Brings the watch's library window in line with the phone's: asks for the whole window, applies any item
 * the watch missed, and removes the notes that left it. The first reconciliation treats every note on the
 * watch as the window's, so notes an older build copied from the phone leave too; anything not yet sent stays.
 */
class WindowReconciler(
    private val active: suspend () -> Boolean,
    private val request: suspend (String, ByteArray) -> Result<ByteArray>,
    private val source: WindowItemSource,
    private val applier: WindowApplier,
    private val members: WindowMembershipStore,
    private val localNoteIds: suspend () -> Set<String>,
) {
    private val reconciling = Mutex()

    /** Null while the window is off. */
    suspend fun resync(): ReconcileResult? {
        if (!active()) return null
        return reconciling.withLock { reconcile() }
    }

    private suspend fun reconcile(): ReconcileResult {
        val wanted = askForWindow() ?: return ReconcileResult.Failed
        val items =
            try {
                source.items()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Could not read the library window items on this watch; it reconciles when the phone is next in reach", e)
                return ReconcileResult.Failed
            }
        val managed = members.members() ?: localNoteIds()
        val applied =
            items.count { (path, data) ->
                val id = WearPaths.parseWindowItem(path)?.id?.toString()
                id != null && id in wanted && applier.onItem(path, data)
            }
        val removed = (managed - wanted).count { id -> Uuid.parseOrNull(id)?.let { applier.remove(it) } ?: false }
        val present = localNoteIds()
        members.replace(wanted.filterTo(HashSet()) { it in present })
        return ReconcileResult.Done(applied, removed)
    }

    private suspend fun askForWindow(): Set<String>? {
        val payload = WearJson.encodeToBytes(WindowResyncRequest.serializer(), WindowResyncRequest())
        val answer =
            request(WearPaths.WINDOW_RESYNC, payload).getOrElse { error ->
                Napier.w("The phone did not answer the library window request; the watch asks again when the phone is next in reach", error)
                return null
            }
        return when (val decoded = WearJson.decode(answer, WearProtocol.WINDOW_SCHEMA, WindowResyncResponse.serializer())) {
            is Decoded.Supported -> decoded.value.members.filter { it.kind == RecordKind.NOTE }.mapTo(HashSet()) { it.id }
            is Decoded.Unsupported -> {
                Napier.w("The phone's library window uses schema ${decoded.schema}; this watch changes nothing until it updates")
                null
            }
            is Decoded.Malformed -> {
                Napier.e("The phone's library window answer could not be read: ${decoded.reason}")
                null
            }
        }
    }
}
```

Create `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/PhoneDayStart.kt`:

```kotlin
package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import kotlinx.coroutines.flow.first

/** The phone's day-start hour, from a phone that publishes the library window; null before one has said it. */
suspend fun phoneDayStartHour(peerStates: PeerStateStore): Int? =
    peerStates
        .latest(PeerRole.PHONE)
        .first()
        ?.phone
        ?.takeIf { WearFeatures.WINDOW in it.features }
        ?.dayStartHour
```

- [ ] **Step 4: Plug the window in**

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt`, in `processEvent`'s `when`, before the `NoteDataMapper.isDeletePath(path)` branch, add:

```kotlin
                WearPaths.parseWindowItem(path) != null -> koin.get<WindowApplier>().onItem(path, event.data)
                WearPaths.parseWindowTombstone(path) != null -> koin.get<WindowApplier>().onTombstone(path)
```

and `import app.logdate.wear.sync.v2.window.WindowApplier`.

In `app/wear/src/main/kotlin/app/logdate/wear/sync/WearPhoneMessageHandler.kt`, add the constructor parameter `private val phoneReturned: suspend () -> Unit = {},` after `receiptsPushed`, and replace `suspend fun onPhoneReachable() = sendPendingChanges()` with:

```kotlin
    /** The phone coming back sends what waits and refreshes the library window, which may have changed meanwhile. */
    suspend fun onPhoneReachable() {
        sendPendingChanges()
        try {
            phoneReturned()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Failed to refresh the library window when the phone came back; it is refreshed next time", e)
        }
    }
```

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt`, in the `WearPhoneMessageHandler` binding, resolve `val window = getOrNull<WindowReconciler>()` next to `receipts` and add `phoneReturned = { window?.resync() },`, with `import app.logdate.wear.sync.v2.window.WindowReconciler`.

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt`, change the `WatchOwnState` features to `setOf(WearFeatures.LINK_CLOUD, WearFeatures.RECEIPTS, WearFeatures.WINDOW)` and add:

```kotlin
        single { WindowMembershipStore(get()) }
        single<WindowItemSource> { DataClientWindowItemSource(Wearable.getDataClient(get<Context>())) }
        single {
            val gate = get<WearProtocolGate>()
            val cache = get<SyncedAudioCache>()
            val audioDirectory = File(get<Context>().filesDir, "audio_notes")
            WindowApplier(
                active = { gate.isFeatureActive(WearFeatures.WINDOW).first() },
                // The watch binds OfflineFirstJournalNotesRepository, which applies synced notes without queueing them.
                notes = get<JournalNotesRepository>() as SyncableJournalNotesRepository,
                outbox = get(),
                members = get(),
                forgetMedia = { note ->
                    deleteLocalAudio((note as? JournalNote.Audio)?.mediaRef, audioDirectory)
                    cache.delete(note.uid)
                },
            )
        }
        single {
            val gate = get<WearProtocolGate>()
            val transport = get<DataLayerTransportV2>()
            val notes = get<JournalNotesRepository>()
            WindowReconciler(
                active = { gate.isFeatureActive(WearFeatures.WINDOW).first() },
                request = transport::request,
                source = get(),
                applier = get(),
                members = get(),
                localNoteIds = { notes.allNotesObserved.first().mapTo(HashSet()) { it.uid.toString() } },
            )
        }
```

with imports `app.logdate.client.repository.journals.JournalNote`, `app.logdate.client.repository.journals.JournalNotesRepository`, `app.logdate.client.repository.journals.SyncableJournalNotesRepository`, `app.logdate.wear.sync.SyncedAudioCache`, `app.logdate.wear.sync.deleteLocalAudio`, `app.logdate.wear.sync.v2.window.DataClientWindowItemSource`, `app.logdate.wear.sync.v2.window.WindowApplier`, `app.logdate.wear.sync.v2.window.WindowItemSource`, `app.logdate.wear.sync.v2.window.WindowMembershipStore`, `app.logdate.wear.sync.v2.window.WindowReconciler` and `java.io.File`.

In `app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt`, replace the comment `// The phone shares its day-start hour through the state document (Track A, C2); until then there is none.` and the `ObserveCampfireUseCase` binding below it with:

```kotlin
        // A phone that publishes the library window shares its day-start hour in its state document.
        single {
            val peerStates = get<PeerStateStore>()
            ObserveCampfireUseCase(
                notesRepository = get(),
                streakSettingsRepository = WatchStreakSettings(get()),
                dayBoundaryPreferences = WatchDayBoundaryPreferences(phoneDayStartHour = { phoneDayStartHour(peerStates) }),
            )
        }
```

with imports `app.logdate.client.sync.peer.state.PeerStateStore` and `app.logdate.wear.sync.v2.window.phoneDayStartHour`.

In `app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt`, in `startSync()` after `scope.launch { resumeAccountLink(koin) }` add `scope.launch { refreshLibraryWindow(koin) }`, and add:

```kotlin
    /** The window may have changed while LogDate was not running; a failure waits for the phone's return. */
    private suspend fun refreshLibraryWindow(koin: org.koin.core.Koin) {
        try {
            koin.get<WindowReconciler>().resync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not refresh the library window at start; it is refreshed when the phone is next in reach", e)
        }
    }
```

with `import app.logdate.wear.sync.v2.window.WindowReconciler`.

In `app/wear/src/main/AndroidManifest.xml`, in the `DATA_CHANGED` filter of `WearDataLayerListenerService`, after the `/logdate/camera` entry add:

```xml
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/logdate/v2/p2w" />
```

- [ ] **Step 5: Run the tests again**

Run the Step 2 command again, then `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:testDebugUnitTest --console=plain`.
Expected: PASS (9 applier, 4 reconciler, 3 day-start tests, the new message handler test, and every existing watch test).

- [ ] **Step 6: Run the watch checks, the build and the managed-device tests**

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:ktlintCheck :app:wear:detekt :app:wear:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearManifestFiltersTest --console=plain
```
Expected: BUILD SUCCESSFUL, the window item and tombstone paths reaching the listener.

Run:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearKoinGraphTest --console=plain
```
Expected: BUILD SUCCESSFUL; the graph with the window bindings resolves.

- [ ] **Step 7: Write the commit message**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task14-window-apply.txt <<'EOF'
chore(wear): apply the phone's library window on the watch

With the redesigned watch app on, the watch now stores the library
window its phone publishes. A note in the window is stored, a note
that leaves it is removed with its recording, and a note with a change
on the watch not yet sent is never overwritten or removed. When the
app starts and whenever the phone comes back into reach, the watch
asks for the whole window, stores anything it missed, and removes what
left; the first time, it also removes the notes it copied from the
phone before the window existed.

The watch's day now starts at the phone's day-start hour, and the
watch advertises the window. Marked moments in the window are ignored
until the watch can show them.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Commit**

```bash
git restore --staged . && git add app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowMembershipStore.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowApplier.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowItemSource.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/WindowReconciler.kt app/wear/src/main/kotlin/app/logdate/wear/sync/v2/window/PhoneDayStart.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearDataLayerListenerService.kt app/wear/src/main/kotlin/app/logdate/wear/sync/WearPhoneMessageHandler.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearSyncV2Module.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearDataModule.kt app/wear/src/main/kotlin/app/logdate/wear/di/WearV2Module.kt app/wear/src/main/kotlin/app/logdate/wear/LogDateWearApplication.kt app/wear/src/main/AndroidManifest.xml app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/WindowApplierTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/WindowReconcilerTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/v2/window/PhoneDayStartTest.kt app/wear/src/test/kotlin/app/logdate/wear/sync/WearPhoneMessageHandlerTest.kt app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearManifestFiltersTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/task14-window-apply.txt
```

---

## Self-Review

### Spec coverage

| Requirement (Track A-v2, D1 to D5, and the brief) | Where |
|---|---|
| D1: pairing with no account, the phone initiating, the watch answering in `onRequest`, node migration marks the pairing stale | Tasks 1, 2 |
| D2: cloud link with no sign-in on the wrist: link code, keys, session, owner adoption, journaled install that resumes, typed problems, actionable notifications | Tasks 3, 4, 5 |
| D3: the watch's own cloud engine (`LinkedWatch`, its own stores, revocation recorded), the Bluetooth proxy treated as metered and never used for a direct upload | Tasks 6, 7 |
| D4: durable delivery ledger, routing table, receipts both ways, 15-minute fallback to a create-only upload, exactly one server version in both orders | Tasks 8, 9, 10, 11 (`WatchFallbackBackupE2ETest`) |
| D5: window policy (day start, DST, Feb 29, budget order), phone publisher with tombstones and resync, watch applier and reconciler | Tasks 12, 13, 14 |
| Flag-gated behind `WATCH_EXPERIENCE_V2` through `WearProtocolGate.isV2Active`/`isFeatureActive` | every task: linking checks the flag (1, 2, 4, 5), delivery `RECEIPTS` (9, 11), window `WINDOW` (13, 14) |
| Version 1 working both directions | no version 1 path changes meaning; the seams (`WatchNotesV2`, `publishWindowInstead`, `CreateCollisionPolicy.None`) fall back to version 1 when a feature is not active |
| Never drop errors | each task's "Failure modes" lists the log line and the typed state (`PhoneLinkFailure`, `LinkProblem`, `HoldReason`, receipts, `WindowPublishResult`, `ReconcileResult`) |
| Boundaries with D6 to D10 and C/B/D11 | "Boundaries with the later plans" table |
| Room stays at 53 | Task 8 adds queries only |
| GMD only, no `connected*`, no `adb` | Global Constraints; Tasks 1, 7, 11, 14 use `wearDevicesGroupDebugAndroidTest` |
| iOS compile for every `commonMain` change | Tasks 1, 3, 6, 8, 10, 12, 13 run `compileKotlinIosSimulatorArm64` and `compileTestKotlinIosSimulatorArm64` |

### Placeholder scan

No "TBD", "TODO", "implement later", "similar to Task N" or step without code. Two helpers are copies by instruction rather than by reference, with the full source in Task 1 and Task 8: `MemoryKeyValueStorage`/`MemoryPairKeyVault` (watch and phone test sets) and `MemoryPeerDeliveryDao` (`:client:sync` common tests, phone tests, watch tests). The repository has no shared test fixtures across those source sets, so each set gets its own copy.

### Name consistency (spot checks)

- `PeerDeliveryLedger.track/get/moveTo/recordAttempt/applyReceipt/forget/inState/observe/earliestDue` (Task 8) are the only ledger calls in Tasks 9, 10, 11; `forgetAllExcept` is defined and tested but no later task needs it.
- `DeliveryDirection.FROM_WATCH` with `PeerScopes.WATCHES` on the phone (Tasks 9, 10); `TO_PHONE` with `PeerScopes.PHONE` on the watch (Task 11).
- `WatchNotesV2` (Task 11) is the parameter `notesV2` of `WearDataLayerSyncManager`; `NoteSchedule` (Task 11) is defined in `WatchSyncScheduler.kt` and returned by `WatchNoteDelivery.schedule()`.
- `WearPhoneMessageHandler(ackHandler, syncManager, receiptsPushed, phoneReturned)`: `receiptsPushed` from Task 11, `phoneReturned` from Task 14, both defaulted.
- `WindowResyncRequest/WindowResyncResponse/WindowMemberRef/WearProtocol.WINDOW_SCHEMA` (Task 13) are what Task 14 sends and reads; `WindowExtraIds` (Task 12) is what `WindowExtras` (Task 13) returns.
- `CreateCollisionPolicy`/`CreateCollision` (Task 10) are implemented by `WatchCaptureCollisionPolicy` and passed as `createCollisionPolicy` to `DefaultSyncManager` and `testDefaultSyncManager`.
- `PhoneStateParts(cloud, dayStartHour, campfire)` (Task 5) is filled in Task 13; `phoneDayStartHour` (Task 14) reads it only from a phone listing `window.v1`.

### Commits

1. `chore(wear): answer the phone's pairing offer on the watch`
2. `chore(wear): pair the phone with its watch without an account`
3. `chore(sync): install a linked watch's keys and session in steps`
4. `chore(wear): finish linking the watch to the phone's account`
5. `chore(wear): send the account link from the phone to the watch`
6. `chore(networking): treat the watch's Bluetooth link as metered`
7. `chore(wear): add the watch's own cloud sync engine`
8. `chore(sync): route each watch change and record its delivery`
9. `chore(wear): store watch captures on the phone and send receipts`
10. `chore(sync): settle a capture the watch already backed up`
11. `chore(wear): send watch captures with receipts and back them up`
12. `chore(wear): choose what the watch's library window contains`
13. `chore(wear): publish the watch's library window from the phone`
14. `chore(wear): apply the phone's library window on the watch`

Every title is at most 72 characters, every type is `chore` (the flag is off), and no scope is `build`, `android`, `ios`, `desktop` or `infra`.

### Decisions the developer may want to change

- The phone initiates every link (`PhoneLinkInitiator`), as the shipped `LinkOffer` says; the watch only answers.
- Version 2 delivery moves notes only; journals, links and health snapshots stay on version 1, and a marked moment is refused (`REJECTED_UNSUPPORTED`) until D7.
- A removal made on the watch uses the version 1 removal signal; out of reach, a linked watch also removes the note from the cloud.
- A phone upload refused because the watch already backed the note up is settled; a phone edit made after receiving the note goes up as an edit, so the phone's newer edit is the server's latest (last writer, as for any edit).
- Timings: 15-minute backup grace, 30-second receipt wait, send retries from 30 seconds doubling to six hours, a 15-minute look again for network and backup waits, tombstones deleted from the Data Layer after a week, `FROM_WATCH` backed-up rows dropped after 30 days.
- Window: 14 logical days, budget 400 notes or 8 MB of bodies, order recent, starred, on this day, Rewind; the policy lives in `:client:domain`.
- The first window reconciliation removes notes an older build copied to the watch (anything not yet sent stays).
- The watch's cloud engine only uploads; it never downloads in this plan (see below).
- Notifications only for problems the person can act on (device limit, another account); every other failure is logged and visible as state.
- `PhoneStatePublisher`'s binding lives in `phoneWatchSyncV2Module`, which now also supplies the day-start hour and the campfire.
- No Room migration; the ledger uses the existing `peer_delivery` table.
- The commit trailer follows the brief (`Claude Sonnet 5.5`); change it if the implementing model differs.

### Assumptions

- Play services Wearable 19.0.0 delivers `onRequest` and `onNodeMigrated` for the `REQUEST_RECEIVED` and `NODE_MIGRATED` filters as written (action names read with `javap`); the managed-device manifest tests prove resolution, not delivery.
- `createFromSync` replaces an existing note (version 1 relies on the same DAO insert for updates), and `deleteFromSync` never queues anything.
- `GoogleWearDataLayerClient.sendFile` works for any channel path, and `DataClient.getDataItems("wear://*/logdate/v2/p2w/window/", FILTER_PREFIX)` returns the phone's items on the watch.
- The end-to-end harness server advertises `deviceLinkingV1` and `conditionalCreateV1` (the existing `ConditionalCreateClientServerE2ETest` asserts the second).
- Koin on both devices already binds `KeyValueStorage`, `CoroutineScope`, `PeerDeliveryDao` (`DaosModule`), `SyncedAudioCache` and `LogDateConfigRepository`; the phone binds `LogdatePreferencesDataSource` and `ObserveCampfireUseCase` (`DomainModule`).
- MockK mocks final classes (`WatchWindowPublisher`, `PhoneNodeSelector`) as it does elsewhere in these test sets.

### Known limits

- Until D10, a watch whose session the server revoked records `SESSION_REVOKED`, and the phone may link it again after its backoff while it still plans a cloud link; removal, sign-out and erase are D10's.
- Bodies over 90 KB do not travel this way: a watch capture is marked `REJECTED` (a linked watch uploads it itself), and a window note is left out and counted. D6 adds channel bodies and media.
- The watch does not download from the cloud while its phone is away; whether it should is not assigned to a later step yet.
- A change to the phone's day-start hour alone reaches the watch's state document with the next session or campfire change; the window itself reads the hour on every publish.
- A version 1 note data item the phone wrote before the window stays in the Data Layer; it is never applied again, because the watch acts only on changes.
- The ledger's `attempts` counts every send, so a failure after several resends waits longer than after a first send.
- If a fresh watch's install resumes after a crash before it knows its phone, the problem may be recorded with an empty phone node (Task 4).
