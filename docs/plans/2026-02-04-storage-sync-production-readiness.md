# Storage + Sync Production Readiness Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Resolve remaining compiler warnings tied to storage/sync changes, finalize safe dependency updates, and verify E2EE media key wiring with real crypto.

**Architecture:** We’ll keep changes scoped to KMP client modules (datastore/device/media/sync) and the version catalog. Warning cleanup is targeted to compiler diagnostics surfaced by K/N metadata builds. E2EE remains client-side (media payloads encrypted before upload), server stores opaque bytes and does not decrypt.

**Tech Stack:** Kotlin Multiplatform, Gradle (KGP 2.3.x), Kotlinx Serialization/Datetime, AES-GCM, Koin DI.

---

### Task 1: Eliminate K/N warning surfaces in datastore + iOS media/device

**Files:**
- Modify: `client/datastore/build.gradle.kts`
- Modify: `client/device/src/iosMain/kotlin/app/logdate/client/device/storage/IosSecureStorage.kt`
- Modify: `client/media/src/iosMain/kotlin/app/logdate/client/media/IosMediaManager.kt`
- Modify: `client/media/src/iosMain/kotlin/app/logdate/client/media/audio/IosAudioRecordingManager.kt`

**Step 1: Reproduce warnings**

Run: `./gradlew :client:logdate-datastore:compileIosMainKotlinMetadata --rerun-tasks`
Expected: Warning about duplicated KLIB `unique_name` in datastore + any iOS warning lines.

**Step 2: Adjust datastore module naming**

Change build logic so metadata/native compilation produces a unique KLIB name (no `datastore_commonMain` collision).

**Step 3: Verify warning removal**

Run: `./gradlew :client:logdate-datastore:compileIosMainKotlinMetadata --rerun-tasks`
Expected: No `unique_name=datastore_commonMain` warning.

**Step 4: Verify iOS warning removal**

Run: `./gradlew :client:media:compileIosMainKotlinMetadata --rerun-tasks`
Expected: No “Redundant call of conversion method” or “Unnecessary safe call” warnings.

**Step 5: Commit**

```bash
git add client/datastore/build.gradle.kts \
  client/device/src/iosMain/kotlin/app/logdate/client/device/storage/IosSecureStorage.kt \
  client/media/src/iosMain/kotlin/app/logdate/client/media/IosMediaManager.kt \
  client/media/src/iosMain/kotlin/app/logdate/client/media/audio/IosAudioRecordingManager.kt
git commit -m "chore(storage): remove K/N warning sources"
```

> Note: Commit step should be skipped if the repo owner requested no commits.

---

### Task 2: Safe version catalog updates

**Files:**
- Modify: `gradle/libs.versions.toml`
- Add/Modify: `scripts/version-catalog-updates.py` (if needed)

**Step 1: Identify safe updates**

Run: `python3 scripts/version-catalog-updates.py`
Expected: Either “No safe stable-lane updates found” or a list of safe updates.

**Step 2: Apply safe updates**

Run: `python3 scripts/version-catalog-updates.py --apply`
Expected: Updates applied in `gradle/libs.versions.toml`.

**Step 3: Verify build configuration still loads**

Run: `./gradlew -q projects`
Expected: Lists projects successfully.

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml scripts/version-catalog-updates.py
git commit -m "chore(deps): update version catalog safely"
```

> Note: Commit step should be skipped if the repo owner requested no commits.

---

### Task 3: Confirm media E2EE wiring + tests

**Files:**
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/crypto/MediaPayloadKeyProvider.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/crypto/StoredMediaPayloadCrypto.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudMediaDataSource.kt`
- Test: `client/sync/src/desktopTest/kotlin/app/logdate/client/sync/crypto/AesGcmMediaPayloadCryptoTest.kt`
- Test: `client/sync/src/desktopTest/kotlin/app/logdate/client/sync/cloud/CloudMediaE2EEncryptionTest.kt`

**Step 1: Add/confirm test coverage**

Ensure tests cover:
- encryption round-trip
- decrypting data from cloud response
- failure on tampered ciphertext

**Step 2: Run targeted tests**

Run: `./gradlew :client:sync:desktopTest`
Expected: All tests pass.

**Step 3: Commit**

```bash
git add client/sync/src/commonMain/kotlin/app/logdate/client/sync \
  client/sync/src/desktopTest/kotlin/app/logdate/client/sync
git commit -m "feat(sync): verify media E2EE wiring"
```

> Note: Commit step should be skipped if the repo owner requested no commits.

---

### Task 4: Document E2EE behavior

**Files:**
- Modify: `docs/audits/storage-sync-production-readiness.md` (or relevant doc)

**Step 1: Explain E2EE flow**

Document:
- client encryption before upload (AES-GCM, `LDCE1` marker)
- server stores opaque bytes, never decrypts
- client decrypts on download

**Step 2: Commit**

```bash
git add docs/audits/storage-sync-production-readiness.md
git commit -m "docs(sync): explain E2EE media flow"
```

> Note: Commit step should be skipped if the repo owner requested no commits.

---

**Implementation note:** Per current request, no worktrees and no commits; execute tasks directly in this working directory and skip commit steps.
