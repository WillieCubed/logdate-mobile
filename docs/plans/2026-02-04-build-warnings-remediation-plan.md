# Build Warnings Remediation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate all Gradle build warnings (including Kotlin deprecations) and resolve build failures without introducing functional regressions.

**Architecture:** Migrate time primitives to `kotlin.time` for `Instant`/`Clock` while keeping `kotlinx.datetime` for calendar types (LocalDate/LocalDateTime/TimeZone) and update crypto platform wiring to use a common `CryptoManager` contract with platform implementations. Centralize AES-GCM access through that contract to avoid platform-type references in common code and fix expect/actual misuses.

**Tech Stack:** Kotlin Multiplatform, Gradle, Koin, Okio, Kotlin/Native, Android/JVM crypto APIs.

---

### Task 1: Capture Failing Baseline

**Files:**
- None

**Step 1: Write the failing test**

Use the build as the failing check (warnings + compile errors):

```bash
./gradlew build --warning-mode all --console=plain
```

**Step 2: Run test to verify it fails**

Expected: build fails with crypto compile errors and deprecation warnings from `kotlinx.datetime` usage.

**Step 3: Write minimal implementation**

No code changes in this task.

**Step 4: Run test to verify it passes**

Not applicable for this baseline task.

**Step 5: Commit**

Not applicable.

---

### Task 2: Fix Crypto Expect/Actual Wiring + AES-GCM Access

**Files:**
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/CryptoManager.kt`
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/ContentEncryptionService.kt`
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/Bip39.kt`
- Modify: `client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/Bip39.android.kt`
- Modify: `client/device/src/jvmMain/kotlin/app/logdate/client/device/crypto/Bip39.jvm.kt`
- Modify: `client/device/src/iosMain/kotlin/app/logdate/client/device/crypto/Bip39.ios.kt`
- Modify: `client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/AndroidCryptoManager.kt`
- Modify: `client/device/src/jvmMain/kotlin/app/logdate/client/device/crypto/DesktopCryptoManager.kt`
- Modify: `client/device/src/iosMain/kotlin/app/logdate/client/device/crypto/IosCryptoManager.kt`
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/IdentityKeyManager.kt`
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/CryptoModule.kt`
- Modify: `client/device/src/iosMain/kotlin/app/logdate/client/device/crypto/CryptoModule.ios.kt`

**Step 1: Write the failing test**

```bash
./gradlew :client:device:compileKotlinIosArm64 --warning-mode all --console=plain
```

Expected: expect/actual errors in `Bip39` and missing crypto manager references.

**Step 2: Run test to verify it fails**

Confirm the above errors.

**Step 3: Write minimal implementation**

1) Convert `CryptoManager` to a common interface and add AES-GCM entry points:

```kotlin
interface CryptoManager {
    suspend fun generateRecoveryPhrase(): List<String>
    suspend fun deriveMasterKey(phrase: List<String>): ByteArray
    fun validateRecoveryPhrase(phrase: List<String>): Boolean
    fun encryptSink(sink: Sink, key: ByteArray, iv: ByteArray): Sink
    fun decryptSource(source: Source, key: ByteArray, iv: ByteArray): Source
    fun generateRandomBytes(size: Int): ByteArray
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray
}
```

2) Remove `actual` keywords from `AndroidCryptoManager`/`DesktopCryptoManager`/`IosCryptoManager` and implement `CryptoManager` directly. Ensure AES-GCM functions are part of the interface implementations.

3) Replace platform-class `when` checks in `ContentEncryptionService` with `cryptoManager.aesGcmEncrypt/aesGcmDecrypt`. Replace `Charsets.UTF_8` usages with `encodeToByteArray()` and `decodeToString()`.

4) Fix `Bip39` expect functions by moving them to top-level extensions:

```kotlin
internal expect fun Bip39.sha256(data: ByteArray): ByteArray
internal expect fun Bip39.mnemonicToSeed(mnemonic: String, passphrase: String): ByteArray
```

Ensure platform files use `internal actual fun Bip39.sha256(...)` and `internal actual fun Bip39.mnemonicToSeed(...)` signatures.

5) Fix `SecureStorage` imports in `CryptoModule.kt` and `IdentityKeyManager.kt` to use `app.logdate.client.device.storage.SecureStorage` and ensure `getBytes`/`putBytes` come from the correct extension source.

**Step 4: Run test to verify it passes**

```bash
./gradlew :client:device:compileKotlinIosArm64 --warning-mode all --console=plain
```

Expected: crypto compile errors resolved.

**Step 5: Commit**

Commit only the above crypto fixes after they compile cleanly.

---

### Task 3: Migrate `client/util` Time Primitives to `kotlin.time`

**Files:**
- Modify: `client/util/src/commonMain/kotlin/app/logdate/util/DateTimeFormatting.kt`
- Modify: `client/util/src/androidMain/kotlin/app/logdate/util/DateTimeFormatting.android.kt`
- Modify: `client/util/src/jvmMain/kotlin/app/logdate/util/DateTimeFormatting.jvm.kt`
- Modify: `client/util/src/iosMain/kotlin/app/logdate/util/DateTimeFormatting.ios.kt`
- Modify: `client/util/src/wasmJsMain/kotlin/app/logdate/util/DateTimeFormatting.wasmJs.kt`

**Step 1: Write the failing test**

```bash
./gradlew :client:util:compileCommonMainKotlinMetadata --warning-mode all --console=plain
```

Expected: deprecation warnings for `kotlinx.datetime.Instant`/`Clock`.

**Step 2: Run test to verify it fails**

Confirm the warnings.

**Step 3: Write minimal implementation**

Replace `kotlinx.datetime.Clock`/`Instant` imports with `kotlin.time.Clock`/`Instant` and keep `kotlinx.datetime` for `LocalDate/LocalDateTime/TimeZone`. Example:

```kotlin
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
```

Update any `Instant` usage to match `kotlin.time` APIs (e.g., `Instant.fromEpochSeconds`, `epochSeconds`, `toEpochMilliseconds`).

**Step 4: Run test to verify it passes**

```bash
./gradlew :client:util:compileCommonMainKotlinMetadata --warning-mode all --console=plain
```

Expected: no deprecation warnings in `client/util`.

**Step 5: Commit**

Commit these changes independently.

---

### Task 4: Migrate Shared Model + Repository Interfaces to `kotlin.time.Instant`

**Files:**
- Modify: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/**/*.kt`
- Modify: `client/repository/src/commonMain/kotlin/app/logdate/client/repository/**/*.kt`

**Step 1: Write the failing test**

```bash
./gradlew :shared:model:compileCommonMainKotlinMetadata --warning-mode all --console=plain
```

Expected: deprecation warnings for `kotlinx.datetime.Instant`/`Clock`.

**Step 2: Run test to verify it fails**

Confirm the warnings.

**Step 3: Write minimal implementation**

Replace all `kotlinx.datetime.Instant` imports with `kotlin.time.Instant` in model and repository interfaces. Example:

```kotlin
import kotlin.time.Instant
```

Keep `kotlinx.datetime` imports for `LocalDate/LocalDateTime` where needed.

**Step 4: Run test to verify it passes**

```bash
./gradlew :shared:model:compileCommonMainKotlinMetadata --warning-mode all --console=plain
```

Expected: no `Instant`/`Clock` deprecation warnings in shared model.

**Step 5: Commit**

Commit model + repository migration together.

---

### Task 5: Migrate Datastore, Networking, Health-Connect, and Downstream Usage

**Files:**
- Modify: `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/LogdatePreferencesDataSource.kt`
- Modify: `client/networking/src/commonMain/kotlin/app/logdate/client/networking/NetworkState.kt`
- Modify: `client/health-connect/src/commonMain/kotlin/app/logdate/client/health/**/*.kt`
- Modify: Any downstream modules that now type-mismatch on `Instant`

**Step 1: Write the failing test**

```bash
./gradlew :client:health-connect:compileCommonMainKotlinMetadata --warning-mode all --console=plain
```

Expected: `Instant` type mismatch and deprecation warnings.

**Step 2: Run test to verify it fails**

Confirm the mismatch errors and warnings.

**Step 3: Write minimal implementation**

Replace `kotlinx.datetime.Instant` with `kotlin.time.Instant`, update any `Clock` usage, and ensure conversions to/from `LocalDateTime` use the `kotlinx.datetime` extensions that accept `kotlin.time.Instant`.

**Step 4: Run test to verify it passes**

```bash
./gradlew :client:health-connect:compileCommonMainKotlinMetadata --warning-mode all --console=plain
```

Expected: no `Instant` type mismatch and no deprecation warnings in these modules.

**Step 5: Commit**

Commit these migrations after compilation is clean.

---

### Task 6: Update Documentation Guidance

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Write the failing test**

Manual review: confirm docs still recommend `kotlinx.datetime.Instant`.

**Step 2: Run test to verify it fails**

Expected: guidance is now outdated after migration.

**Step 3: Write minimal implementation**

Update the KMP guideline to:

```markdown
- For dates and times, use `kotlin.time.Instant` for instants and `kotlinx.datetime` for calendar types.
```

**Step 4: Run test to verify it passes**

Manual review: guidance matches new code.

**Step 5: Commit**

Commit docs update.

---

### Task 7: Full Warning Sweep

**Files:**
- None (follow-up fixes only if warnings remain)

**Step 1: Write the failing test**

```bash
./gradlew build --warning-mode all --console=plain
```

**Step 2: Run test to verify it fails**

Expected: if any warnings remain, they are surfaced here.

**Step 3: Write minimal implementation**

Fix remaining warnings in place (repeat Tasks 2-6 patterns as needed).

**Step 4: Run test to verify it passes**

```bash
./gradlew build --warning-mode all --console=plain
```

Expected: build succeeds with zero warnings.

**Step 5: Commit**

Commit final warning cleanup if additional files were changed.
