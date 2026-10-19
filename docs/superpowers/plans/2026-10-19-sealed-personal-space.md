# Sealed Personal Space Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every account an owner-only personal space on LogDate Cloud, where every record is stored sealed. The server can store, order and authorize records but can't tell their kind, time or size. Apps reach it through one shared data-home interface.

**Architecture:**
- **`shared/space`, a new KMP module for Android, iOS and JVM,** owns everything a client needs:
  - the space reference;
  - the sealed-envelope codec (AES-256-GCM, HKDF-SHA256, HMAC-SHA256, DAG-CBOR, padding);
  - the `DataHome` interface;
  - an XRPC implementation of `DataHome`.
- **The server adds a space store and its routes.** The store is Postgres plus in-memory, with a per-space sequence, tombstones and blobs. It serves the `app.logdate.space.*` XRPC methods, each defined as a lexicon.
- **Gating.** The routes are behind `LOGDATE_PERSONAL_SPACE_ENABLED`, and the `personalSpaceV1` protocol feature is advertised only when that is on. No app uses this yet. The next task, "Sync every kind of journal data as LogDate schema records", wires it into sync.

**Tech Stack:**
- Kotlin Multiplatform and Ktor (server and client).
- Exposed and Flyway on PostgreSQL.
- whyoleg cryptography-kotlin 0.6.0.
- `shared/atproto-repo` for `DagCborCodec` and `Cid`.
- `shared/atproto-xrpc` for `XrpcClient`.
- `shared/lexicon` for the generated wire models.

**Design:** [Private and shared storage](../specs/2026-10-18-private-and-shared-storage-design.md), decisions 1–4.

## Global Constraints

- **Envelope format version `1`.**
  - Cipher: AES-256-GCM with a 12-byte random nonce and a 16-byte tag.
  - AAD: `app.logdate.crypto.sealed|1|{spaceUri}|{authorDid}|{sealedRkey}` (UTF-8).
- **Key derivation:**
  - Content key: `HKDF-SHA256(ikm = identityKey, salt = "app.logdate.space.personal", info = "sealed-record v1", L = 32)`.
  - Index key: the same with `info = "sealed-rkey v1"`.
  - `keyId`: the first 8 bytes of `HMAC-SHA256(contentKey, "keyId")`, lowercase base32 with no padding.
- **Sealed rkey:** the first 26 characters of lowercase base32 of `HMAC-SHA256(indexKey, collection + "/" + rkey)`. It is never time-based.
- **Padding:**
  - Plaintext is `uint32 big-endian length || DAG-CBOR payload || zeros`.
  - The padded size is the smallest power of two from 512 bytes to 64 KiB that fits. Above 64 KiB, it is the next multiple of 64 KiB.
  - Ciphertext is at most 4,000,000 bytes (from the `app.logdate.crypto.sealed` schema).
- **Space URI:** `at://{authorityDid}/space/{spaceType}/{skey}`. The personal space is `at://{did}/space/app.logdate.space.personal/self`.
- **Personal-space access:** only the owner can read or write it, and only their own author repository (author DID = authority DID). Anyone else gets the same answer as a missing record (`RecordNotFound`).
- **Logging:** Napier only. Use `kotlin.uuid.Uuid` and `kotlin.time.Instant`. No wildcard imports.
- **Commits:**
  - Use the atomic chain `git restore --staged . && git add <paths> && git commit -F <message-file>`.
  - Write the message file in its own step.
  - Use scope `sync`, with type `chore` until the feature is user-visible.
  - End every message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Gradle:** prefix commands with `ANDROID_HOME=$HOME/Library/Android/sdk` when the worktree has no `local.properties`.

## File Structure

| File | Responsibility |
|---|---|
| `lexicons/app/logdate/space/{putRecord,deleteRecord,getRecord,listRepoOps,uploadBlob,getBlob}.json` | XRPC method schemas |
| `lexicons/app/logdate/crypto/sealed.json` (modify) | Payload description: the sealed key is derived, not random |
| `shared/space/build.gradle.kts`, `settings.gradle.kts` (modify) | New module |
| `shared/space/src/commonMain/kotlin/app/logdate/space/SpaceRef.kt` | Space identity and URI |
| `.../Base32.kt` | Lowercase RFC 4648 base32 without padding |
| `.../SealingKeys.kt` | Key derivation |
| `.../SealedPadding.kt` | Size buckets |
| `.../SealedRecordCodec.kt` | Seal and open |
| `.../DataHome.kt` | Interface and result types |
| `.../XrpcDataHome.kt` | LogDate Cloud implementation |
| `server/.../space/SpaceStore.kt` | Server store interface, models, in-memory implementation |
| `server/.../space/SealedRecordCheck.kt` | Server-side shape and padding check |
| `server/.../database/SpaceTables.kt`, `PostgreSQLSpaceStore.kt` | Postgres store |
| `server/src/main/resources/db/migration/V42__Add_personal_spaces.sql` | Tables, cascade, deletion trigger |
| `server/.../routes/SpaceXrpcRoutes.kt`, `routes/docs/XrpcSpaceDocs.kt` | Routes and their OpenAPI docs |
| `server/.../SyncMaintenance.kt` (modify) | Purges space tombstones and unreferenced blobs |
| `shared/model/.../ServerDescriptor.kt`, `server/.../ServerDescriptorConfig.kt` (modify) | `personalSpaceV1` |
| `integration/server-client-e2e/.../journeys/PersonalSpaceE2ETest.kt` | Seal, store and read back end to end |

---

### Task 1: Space method lexicons

**Files:**
- Create: `lexicons/app/logdate/space/putRecord.json`, `deleteRecord.json`, `getRecord.json`, `listRepoOps.json`, `uploadBlob.json`, `getBlob.json`
- Modify: `lexicons/app/logdate/crypto/sealed.json` (the `#payload.rkey` description)

**Interfaces:**
- Produces generated classes in `app.logdate.lexicon.app.logdate.space`:
  - `PutRecordInput(space, rkey, record: Sealed, blobs: List<String>?, swapCid: String?, createOnly: Boolean?)` and `PutRecordOutput(cid, seq: Long)`
  - `DeleteRecordInput(space, rkey, swapCid: String?)` and `DeleteRecordOutput(seq: Long)`
  - `GetRecordOutput(cid, seq: Long, value: Sealed)`
  - `ListRepoOpsOutput(ops: List<ListRepoOpsChange>, cursor: Long, hasMore: Boolean)` and `ListRepoOpsChange(rkey, seq: Long, deleted: Boolean, cid: String?, value: Sealed?)`
  - `UploadBlobOutput(blob: Blob)`

- [ ] **Step 1: Write the six schemas**

`lexicons/app/logdate/space/putRecord.json`:
```json
{
  "lexicon": 1,
  "id": "app.logdate.space.putRecord",
  "description": "Creates or replaces one sealed record in the caller's own repository in a space.",
  "defs": {
    "main": {
      "type": "procedure",
      "description": "Creates or replaces one sealed record in the caller's own repository in a space.",
      "input": {
        "encoding": "application/json",
        "schema": {
          "type": "object",
          "required": ["space", "rkey", "record"],
          "properties": {
            "space": { "type": "string", "maxLength": 512, "description": "The space URI, such as at://did:plc:abc/space/app.logdate.space.personal/self." },
            "rkey": { "type": "string", "format": "record-key", "description": "The sealed record's key." },
            "record": { "type": "ref", "ref": "app.logdate.crypto.sealed" },
            "blobs": { "type": "array", "maxLength": 64, "items": { "type": "string", "format": "cid" }, "description": "Blobs the record refers to, so unreferenced ones can be deleted." },
            "swapCid": { "type": "string", "format": "cid", "description": "Write only if the current record has this CID." },
            "createOnly": { "type": "boolean", "description": "Write only if no live record has this key." }
          }
        }
      },
      "output": {
        "encoding": "application/json",
        "schema": {
          "type": "object",
          "required": ["cid", "seq"],
          "properties": {
            "cid": { "type": "string", "format": "cid" },
            "seq": { "type": "integer", "minimum": 1 }
          }
        }
      },
      "errors": [{ "name": "InvalidSwap" }, { "name": "InvalidRecord" }]
    }
  }
}
```

`deleteRecord.json` uses the same shape. The input has `space`, `rkey` and optional `swapCid`, the output is `{ "seq" }`, and the error is `InvalidSwap`.

`getRecord.json`:
```json
{
  "lexicon": 1,
  "id": "app.logdate.space.getRecord",
  "description": "Reads one sealed record from the caller's own repository in a space.",
  "defs": {
    "main": {
      "type": "query",
      "description": "Reads one sealed record from the caller's own repository in a space.",
      "parameters": {
        "type": "params",
        "required": ["space", "rkey"],
        "properties": {
          "space": { "type": "string", "maxLength": 512 },
          "rkey": { "type": "string", "format": "record-key" }
        }
      },
      "output": {
        "encoding": "application/json",
        "schema": {
          "type": "object",
          "required": ["cid", "seq", "value"],
          "properties": {
            "cid": { "type": "string", "format": "cid" },
            "seq": { "type": "integer", "minimum": 1 },
            "value": { "type": "ref", "ref": "app.logdate.crypto.sealed" }
          }
        }
      },
      "errors": [{ "name": "RecordNotFound" }]
    }
  }
}
```

`listRepoOps.json`. The `since` and `limit` parameters are integers. `limit` is 1–500 with a default of 200. The `change` def gives `ListRepoOpsChange`.
```json
{
  "lexicon": 1,
  "id": "app.logdate.space.listRepoOps",
  "description": "Lists every change to the caller's own repository in a space after a sequence number, deletions included. Paging from zero exports the whole repository.",
  "defs": {
    "main": {
      "type": "query",
      "description": "Lists every change to the caller's own repository in a space after a sequence number, deletions included.",
      "parameters": {
        "type": "params",
        "required": ["space"],
        "properties": {
          "space": { "type": "string", "maxLength": 512 },
          "since": { "type": "integer", "minimum": 0, "default": 0 },
          "limit": { "type": "integer", "minimum": 1, "maximum": 500, "default": 200 }
        }
      },
      "output": {
        "encoding": "application/json",
        "schema": {
          "type": "object",
          "required": ["ops", "cursor", "hasMore"],
          "properties": {
            "ops": { "type": "array", "items": { "type": "ref", "ref": "#change" } },
            "cursor": { "type": "integer", "minimum": 0 },
            "hasMore": { "type": "boolean" }
          }
        }
      }
    },
    "change": {
      "type": "object",
      "required": ["rkey", "seq", "deleted"],
      "properties": {
        "rkey": { "type": "string", "format": "record-key" },
        "seq": { "type": "integer", "minimum": 1 },
        "deleted": { "type": "boolean" },
        "cid": { "type": "string", "format": "cid" },
        "value": { "type": "ref", "ref": "app.logdate.crypto.sealed" }
      }
    }
  }
}
```

`uploadBlob.json`. This is a procedure with input `{"encoding": "*/*"}`, the `space` parameter, and output `{ "blob": { "type": "blob" } }`, required `["blob"]`.

`getBlob.json`. This is a query with parameters `space` and `cid` (format `cid`), output `{"encoding": "*/*"}`, and error `BlobNotFound`.

In `crypto/sealed.json`, replace the payload `rkey` description with: `"The inner record's key. The sealed record's own key is derived from it with a secret, so the server can't link the two."`

- [ ] **Step 2: Check and generate**

Run: `cd tools/lexicon-check && pnpm run check`, then `./gradlew :shared:lexicon:compileKotlinJvm -q`.
Expected: the check passes with no errors. The build succeeds, and `shared/lexicon/build/generated/lexicon/kotlin/app/logdate/lexicon/app/logdate/space/PutRecordLexicon.kt` contains `PutRecordInput` and `PutRecordOutput`.

- [ ] **Step 3: Commit**

Message file: `chore(sync): Define the XRPC methods for sealed personal spaces`.
```bash
git restore --staged . && git add lexicons/app/logdate/space lexicons/app/logdate/crypto/sealed.json && git commit -F "$MSG"
```

### Task 2: `shared/space` module, space references and base32

**Files:**
- Create: `shared/space/build.gradle.kts`, `shared/space/src/commonMain/kotlin/app/logdate/space/SpaceRef.kt`, `.../Base32.kt`
- Modify: `settings.gradle.kts`, adding `include(":shared:space")` next to `:shared:lexicon`
- Test: `shared/space/src/commonTest/kotlin/app/logdate/space/SpaceRefTest.kt`, `Base32Test.kt`

**Interfaces:**
- Produces:
  - `public data class SpaceRef(val authority: String, val type: String, val skey: String)` with `val uri: String`, `companion fun parse(uri: String): SpaceRef?` and `companion fun personal(did: String): SpaceRef`.
  - `public object SpaceTypes { const val PERSONAL = "app.logdate.space.personal" }`
  - `internal object Base32 { fun encode(bytes: ByteArray): String }`

- [ ] **Step 1: Create the module**

`shared/space/build.gradle.kts`, copied from `shared/lexicon` without the lexicon plugin:
```kotlin
@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

description = "LogDate's private and shared spaces: sealed records, and the data homes that store them."

kotlin {
    explicitApi()

    android {
        namespace = "app.logdate.space"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTestBuilder {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.lexicon)
            implementation(projects.shared.atprotoRepo)
            implementation(projects.shared.atprotoXrpc)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.ktor.client.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
```
Check the exact `libs.ktor.client.*` and `libs.kotlinx.coroutines.test` aliases in `gradle/libs.versions.toml` and adjust the names. Don't add new catalog entries if aliases already exist.

- [ ] **Step 2: Write the failing tests**

`SpaceRefTest.kt`:
```kotlin
package app.logdate.space

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpaceRefTest {
    @Test
    fun `a personal space has the documented URI`() {
        val space = SpaceRef.personal("did:plc:abc123")
        assertEquals("at://did:plc:abc123/space/app.logdate.space.personal/self", space.uri)
        assertEquals(space, SpaceRef.parse(space.uri))
    }

    @Test
    fun `malformed space URIs are refused`() {
        assertNull(SpaceRef.parse("at://did:plc:abc123/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("https://example.com/space/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("at://alice/space/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("at://did:plc:abc123/space/notansid/self"))
    }
}
```

`Base32Test.kt`, using the RFC 4648 vectors, lowercase and unpadded:
```kotlin
package app.logdate.space

import kotlin.test.Test
import kotlin.test.assertEquals

class Base32Test {
    @Test
    fun `encodes the RFC 4648 vectors in lowercase without padding`() {
        assertEquals("", Base32.encode(ByteArray(0)))
        assertEquals("my", Base32.encode("f".encodeToByteArray()))
        assertEquals("mzxq", Base32.encode("fo".encodeToByteArray()))
        assertEquals("mzxw6", Base32.encode("foo".encodeToByteArray()))
        assertEquals("mzxw6yq", Base32.encode("foob".encodeToByteArray()))
        assertEquals("mzxw6ytb", Base32.encode("fooba".encodeToByteArray()))
        assertEquals("mzxw6ytboi", Base32.encode("foobar".encodeToByteArray()))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :shared:space:jvmTest -q`
Expected: compilation fails with unresolved `SpaceRef` and `Base32`.

- [ ] **Step 4: Implement**

`SpaceRef.kt`:
```kotlin
package app.logdate.space

/** The kinds of space LogDate stores records in. */
public object SpaceTypes {
    /** The owner's own space. Only they can read it, and every record in it is sealed. */
    public const val PERSONAL: String = "app.logdate.space.personal"
}

/**
 * A space, named the way AT Protocol Spaces names one: the DID that controls it, its type, and a
 * key that tells apart several spaces of one type.
 */
public data class SpaceRef(
    val authority: String,
    val type: String,
    val skey: String,
) {
    /** `at://{authority}/space/{type}/{skey}`. */
    val uri: String get() = "at://$authority/space/$type/$skey"

    public companion object {
        private val URI = Regex("""^at://(did:[a-z]+:[A-Za-z0-9._:%-]+)/space/([a-zA-Z][a-zA-Z0-9-]*(?:\.[a-zA-Z0-9-]+){2,})/([A-Za-z0-9._:~-]{1,512})$""")

        /** The owner's personal space. */
        public fun personal(did: String): SpaceRef = SpaceRef(did, SpaceTypes.PERSONAL, "self")

        /** Reads a space URI, or returns null if it isn't one. */
        public fun parse(uri: String): SpaceRef? {
            val match = URI.matchEntire(uri) ?: return null
            val (authority, type, skey) = match.destructured
            return SpaceRef(authority, type, skey)
        }
    }
}
```

`Base32.kt`:
```kotlin
package app.logdate.space

/** Lowercase RFC 4648 base32 without padding: the alphabet AT Protocol record keys allow. */
internal object Base32 {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:space:jvmTest -q`
Expected: the build succeeds and the four tests pass.

- [ ] **Step 6: Commit**

Message: `chore(sync): Add the shared space module and space references`.
```bash
git restore --staged . && git add settings.gradle.kts shared/space && git commit -F "$MSG"
```

### Task 3: Sealing keys, padding and the envelope codec

**Files:**
- Create: `shared/space/src/commonMain/kotlin/app/logdate/space/SealingKeys.kt`, `SealedPadding.kt`, `SealedRecordCodec.kt`
- Test: `shared/space/src/commonTest/kotlin/app/logdate/space/SealedPaddingTest.kt`, `SealedRecordCodecTest.kt`

**Interfaces:**
- Consumes: `SpaceRef` and `Base32` (Task 2), `Sealed` and `SealedPayload` (generated), `DagCborCodec` (`studio.hypertext.atproto.repo`).
- Produces:
  - `public class SealingKeys internal constructor(contentKey: ByteArray, indexKey: ByteArray)` with `val keyId: String` and `companion suspend fun personal(identityKey: ByteArray): SealingKeys`
  - `public object SealedPadding { fun paddedSize(length: Int): Int; fun pad(payload: ByteArray): ByteArray; fun unpad(padded: ByteArray): ByteArray; fun isPaddedSize(size: Int): Boolean; const val MAX_CIPHERTEXT: Int = 4_000_000 }`
  - `public object SealedRecordCodec`:
    - `suspend fun sealedRkey(keys: SealingKeys, collection: String, rkey: String): String`
    - `suspend fun seal(keys: SealingKeys, space: SpaceRef, author: String, payload: SealedPayload): SealedWrite`
    - `suspend fun open(keys: SealingKeys, space: SpaceRef, author: String, sealedRkey: String, record: Sealed): SealedPayload`
  - `public data class SealedWrite(val rkey: String, val record: Sealed)`
  - `public class SealedRecordException(message: String, cause: Throwable? = null) : Exception(message, cause)`

- [ ] **Step 1: Write the failing tests**

`SealedPaddingTest.kt`:
```kotlin
package app.logdate.space

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SealedPaddingTest {
    @Test
    fun `sizes round up to powers of two then to 64 KiB steps`() {
        assertEquals(512, SealedPadding.paddedSize(0))
        assertEquals(512, SealedPadding.paddedSize(508))
        assertEquals(1024, SealedPadding.paddedSize(509))
        assertEquals(65_536, SealedPadding.paddedSize(65_532))
        assertEquals(131_072, SealedPadding.paddedSize(65_533))
        assertEquals(196_608, SealedPadding.paddedSize(131_069))
    }

    @Test
    fun `padding round-trips and hides the length`() {
        val short = SealedPadding.pad(ByteArray(10) { 1 })
        val longer = SealedPadding.pad(ByteArray(400) { 2 })
        assertEquals(short.size, longer.size)
        assertContentEquals(ByteArray(10) { 1 }, SealedPadding.unpad(short))
    }

    @Test
    fun `only padded sizes plus the tag are accepted as ciphertext sizes`() {
        assertTrue(SealedPadding.isPaddedSize(512))
        assertTrue(SealedPadding.isPaddedSize(196_608))
        assertTrue(!SealedPadding.isPaddedSize(600))
    }

    @Test
    fun `a payload too large for the schema is refused`() {
        assertFailsWith<SealedRecordException> { SealedPadding.pad(ByteArray(4_000_000)) }
    }
}
```

`SealedRecordCodecTest.kt`:
```kotlin
package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SealedRecordCodecTest {
    private val owner = "did:plc:owner"
    private val space = SpaceRef.personal(owner)
    private val entry =
        SealedPayload(
            collection = "app.logdate.journal.entry",
            rkey = "3k2a",
            record = buildJsonObject {
                put("\$type", "app.logdate.journal.entry")
                put("createdAt", "2026-10-19T10:00:00.000Z")
                put("text", JsonPrimitive("Pancakes at the lake"))
            },
        )

    private suspend fun keys(seed: Int = 7) = SealingKeys.personal(ByteArray(32) { (it + seed).toByte() })

    @Test
    fun `a sealed record opens back to the same payload`() = runTest {
        val keys = keys()
        val write = SealedRecordCodec.seal(keys, space, owner, entry)
        assertEquals(entry, SealedRecordCodec.open(keys, space, owner, write.rkey, write.record))
        assertEquals(1L, write.record.version)
        assertEquals(keys.keyId, write.record.keyId)
    }

    @Test
    fun `the sealed key is stable, opaque and never contains the inner key`() = runTest {
        val keys = keys()
        val first = SealedRecordCodec.sealedRkey(keys, entry.collection, entry.rkey)
        assertEquals(first, SealedRecordCodec.sealedRkey(keys, entry.collection, entry.rkey))
        assertEquals(26, first.length)
        assertTrue(first.all { it in 'a'..'z' || it in '2'..'7' })
        assertNotEquals(first, SealedRecordCodec.sealedRkey(keys, "app.logdate.journal.journal", entry.rkey))
        assertNotEquals(first, SealedRecordCodec.sealedRkey(keys(seed = 8), entry.collection, entry.rkey))
    }

    @Test
    fun `nothing about the inner record is visible in the envelope`() = runTest {
        val write = SealedRecordCodec.seal(keys(), space, owner, entry)
        val visible = write.record.toString() + write.rkey
        listOf("journal", "entry", "2026", "3k2a", "Pancakes").forEach { assertTrue(it !in visible, it) }
        assertTrue(SealedPadding.isPaddedSize(write.record.ciphertext.size - 16))
    }

    @Test
    fun `an envelope moved to another key, space or author can't be opened`() = runTest {
        val keys = keys()
        val write = SealedRecordCodec.seal(keys, space, owner, entry)
        val other = SealedRecordCodec.sealedRkey(keys, entry.collection, "other")
        assertFailsWith<SealedRecordException> { SealedRecordCodec.open(keys, space, owner, other, write.record) }
        assertFailsWith<SealedRecordException> {
            SealedRecordCodec.open(keys, SpaceRef.personal("did:plc:else"), owner, write.rkey, write.record)
        }
        assertFailsWith<SealedRecordException> {
            SealedRecordCodec.open(keys, space, "did:plc:else", write.rkey, write.record)
        }
    }

    @Test
    fun `another account's keys can't open it`() = runTest {
        val write = SealedRecordCodec.seal(keys(), space, owner, entry)
        assertFailsWith<SealedRecordException> {
            SealedRecordCodec.open(keys(seed = 9), space, owner, write.rkey, write.record)
        }
    }

    @Test
    fun `a sealed key that doesn't match the inner record is refused`() = runTest {
        val keys = keys()
        val write = SealedRecordCodec.seal(keys, space, owner, entry)
        val swapped = SealedRecordCodec.seal(keys, space, owner, entry.copy(rkey = "other"))
        // Same envelope stored under the first record's key: the AAD check catches it.
        assertFailsWith<SealedRecordException> { SealedRecordCodec.open(keys, space, owner, write.rkey, swapped.record) }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :shared:space:jvmTest -q`
Expected: compilation fails with unresolved `SealedPadding`, `SealingKeys` and `SealedRecordCodec`.

- [ ] **Step 3: Implement**

`SealingKeys.kt`:
```kotlin
package app.logdate.space

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * The keys that seal one space's records. [contentKey] encrypts records, [indexKey] derives their
 * opaque keys, and [keyId] names the content key inside each envelope so a later rotation can
 * add keys without changing the format.
 */
public class SealingKeys internal constructor(
    internal val contentKey: ByteArray,
    internal val indexKey: ByteArray,
    public val keyId: String,
) {
    public companion object {
        private const val PERSONAL_SALT = "app.logdate.space.personal"

        /** Keys for the owner's personal space, derived from the account's 32-byte identity key. */
        public suspend fun personal(identityKey: ByteArray): SealingKeys {
            require(identityKey.size == 32) { "The identity key must be 32 bytes" }
            val content = hkdf(identityKey, PERSONAL_SALT, "sealed-record v1")
            val index = hkdf(identityKey, PERSONAL_SALT, "sealed-rkey v1")
            return SealingKeys(content, index, Base32.encode(hmac(content, "keyId".encodeToByteArray()).copyOf(8)))
        }

        internal suspend fun hmac(key: ByteArray, value: ByteArray): ByteArray =
            CryptographyProvider.Default
                .get(HMAC)
                .keyDecoder(SHA256)
                .decodeFromByteArray(HMAC.Key.Format.RAW, key)
                .signatureGenerator()
                .generateSignature(value)

        private suspend fun hkdf(ikm: ByteArray, salt: String, info: String): ByteArray =
            CryptographyProvider.Default
                .get(HKDF)
                .secretDerivation(SHA256, 32.bytes, salt.encodeToByteArray(), info.encodeToByteArray())
                .deriveSecret(ikm)
                .toByteArray()
    }
}
```
Check whyoleg 0.6.0's non-blocking HKDF API in `client/sync` usage or the library sources. The blocking form is `deriveSecretToByteArrayBlocking(ikm)`. If the suspending `deriveSecret(...).toByteArray()` doesn't exist, use `deriveSecretToByteArray(ikm)`. Keep the functions `suspend`.

`SealedPadding.kt`:
```kotlin
package app.logdate.space

/**
 * Pads a sealed payload to a size bucket, so a record's size doesn't reveal what kind of record
 * it is. Layout: a 4-byte big-endian length, the payload, then zeros.
 */
public object SealedPadding {
    /** The largest ciphertext the `app.logdate.crypto.sealed` schema allows. */
    public const val MAX_CIPHERTEXT: Int = 4_000_000
    private const val TAG = 16
    private const val SMALLEST = 512
    private const val STEP = 65_536

    public fun paddedSize(length: Int): Int {
        val needed = length + 4
        if (needed > STEP) return ((needed + STEP - 1) / STEP) * STEP
        var size = SMALLEST
        while (size < needed) size *= 2
        return size
    }

    /** Whether [size] is a size [pad] can produce. */
    public fun isPaddedSize(size: Int): Boolean =
        size in SMALLEST..(MAX_CIPHERTEXT - TAG) && (if (size <= STEP) size and (size - 1) == 0 else size % STEP == 0)

    public fun pad(payload: ByteArray): ByteArray {
        val size = paddedSize(payload.size)
        if (size + TAG > MAX_CIPHERTEXT) throw SealedRecordException("The record is too large to seal")
        val out = ByteArray(size)
        out[0] = (payload.size ushr 24).toByte()
        out[1] = (payload.size ushr 16).toByte()
        out[2] = (payload.size ushr 8).toByte()
        out[3] = payload.size.toByte()
        payload.copyInto(out, 4)
        return out
    }

    public fun unpad(padded: ByteArray): ByteArray {
        if (padded.size < 4) throw SealedRecordException("The sealed payload is truncated")
        val length =
            ((padded[0].toInt() and 0xff) shl 24) or ((padded[1].toInt() and 0xff) shl 16) or
                ((padded[2].toInt() and 0xff) shl 8) or (padded[3].toInt() and 0xff)
        if (length < 0 || length > padded.size - 4) throw SealedRecordException("The sealed payload length is invalid")
        return padded.copyOfRange(4, 4 + length)
    }
}
```

`SealedRecordCodec.kt`:
```kotlin
package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import studio.hypertext.atproto.lexicon.data.Bytes
import studio.hypertext.atproto.repo.DagCborCodec

/** A record ready to store: its opaque key and its envelope. */
public data class SealedWrite(val rkey: String, val record: Sealed)

/** A sealed record that can't be produced or opened. */
public class SealedRecordException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Seals records into `app.logdate.crypto.sealed` envelopes and opens them again. The envelope is
 * bound to its space, author and key through the AES-GCM additional data, so a server that moves
 * an envelope anywhere else makes it fail to open.
 */
public object SealedRecordCodec {
    private const val VERSION = 1L
    private const val NONCE = 12
    private const val SEALED_RKEY_LENGTH = 26

    public suspend fun sealedRkey(keys: SealingKeys, collection: String, rkey: String): String =
        Base32.encode(SealingKeys.hmac(keys.indexKey, "$collection/$rkey".encodeToByteArray())).take(SEALED_RKEY_LENGTH)

    @OptIn(DelicateCryptographyApi::class)
    public suspend fun seal(keys: SealingKeys, space: SpaceRef, author: String, payload: SealedPayload): SealedWrite {
        val rkey = sealedRkey(keys, payload.collection, payload.rkey)
        val plaintext = SealedPadding.pad(DagCborCodec.encode(Json.encodeToJsonElement(payload)))
        val nonce = CryptographyRandom.nextBytes(NONCE)
        val ciphertext = cipher(keys).encryptWithIv(nonce, plaintext, aad(space, author, rkey))
        return SealedWrite(rkey, Sealed(ciphertext = Bytes(ciphertext), keyId = keys.keyId, nonce = Bytes(nonce), version = VERSION))
    }

    @OptIn(DelicateCryptographyApi::class)
    public suspend fun open(keys: SealingKeys, space: SpaceRef, author: String, sealedRkey: String, record: Sealed): SealedPayload {
        if (record.version != VERSION) throw SealedRecordException("Envelope version ${record.version} isn't supported")
        if (record.keyId != keys.keyId) throw SealedRecordException("The record was sealed with key ${record.keyId}")
        val plaintext =
            try {
                cipher(keys).decryptWithIv(record.nonce.toByteArray(), record.ciphertext.toByteArray(), aad(space, author, sealedRkey))
            } catch (e: Exception) {
                throw SealedRecordException("The sealed record failed authentication", e)
            }
        val payload =
            try {
                Json.decodeFromJsonElement<SealedPayload>(DagCborCodec.decode(SealedPadding.unpad(plaintext)) as JsonObject)
            } catch (e: SealedRecordException) {
                throw e
            } catch (e: Exception) {
                throw SealedRecordException("The sealed payload isn't a valid record", e)
            }
        if (sealedRkey(keys, payload.collection, payload.rkey) != sealedRkey) {
            throw SealedRecordException("The sealed key doesn't match the record inside")
        }
        return payload
    }

    private fun aad(space: SpaceRef, author: String, rkey: String): ByteArray =
        "app.logdate.crypto.sealed|$VERSION|${space.uri}|$author|$rkey".encodeToByteArray()

    private suspend fun cipher(keys: SealingKeys) =
        CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, keys.contentKey)
            .cipher()
}
```
Check the suspending method names on whyoleg's `IvAuthenticatedCipher` (`encryptWithIv` and `decryptWithIv`, the non-`Blocking` forms) against the 0.6.0 sources. The blocking forms are already used in `client/sync`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:space:jvmTest :shared:space:compileKotlinIosSimulatorArm64 -q`
Expected: the build succeeds, all ten tests pass, and the iOS compile succeeds.

- [ ] **Step 5: Commit**

Message: `chore(sync): Seal personal-space records so the server can't read their kind, time or size`.
```bash
git restore --staged . && git add shared/space && git commit -F "$MSG"
```

### Task 4: `DataHome` interface and the LogDate Cloud implementation

**Files:**
- Create: `shared/space/src/commonMain/kotlin/app/logdate/space/DataHome.kt`, `XrpcDataHome.kt`
- Test: `shared/space/src/commonTest/kotlin/app/logdate/space/XrpcDataHomeTest.kt`

**Interfaces:**
- Consumes: `SpaceRef`, `Sealed`, and the generated `PutRecordInput`, `PutRecordOutput`, `DeleteRecordInput`, `DeleteRecordOutput`, `GetRecordOutput`, `ListRepoOpsOutput`, `ListRepoOpsChange` and `UploadBlobOutput`.
- Produces:
```kotlin
public sealed interface RecordSwap { data object Unconditional; data object Absent; data class Matching(val cid: String) }
public data class StoredRecord(val rkey: String, val cid: String, val seq: Long, val record: Sealed)
public data class RecordChange(val rkey: String, val seq: Long, val deleted: Boolean, val cid: String?, val record: Sealed?)
public data class ChangePage(val changes: List<RecordChange>, val cursor: Long, val hasMore: Boolean)
public class DataHomeConflictException(message: String) : Exception(message)
public interface DataHome {
    suspend fun putRecord(space: SpaceRef, rkey: String, record: Sealed, blobs: List<String> = emptyList(), swap: RecordSwap = RecordSwap.Unconditional): StoredRecord
    suspend fun deleteRecord(space: SpaceRef, rkey: String, swap: RecordSwap = RecordSwap.Unconditional): Long
    suspend fun getRecord(space: SpaceRef, rkey: String): StoredRecord?
    suspend fun listChanges(space: SpaceRef, since: Long, limit: Int = 200): ChangePage
    suspend fun uploadBlob(space: SpaceRef, bytes: ByteArray): String
    suspend fun getBlob(space: SpaceRef, cid: String): ByteArray?
}
public class XrpcDataHome(client: XrpcClient, httpClient: HttpClient, baseUrl: String, auth: suspend () -> XrpcAuth) : DataHome
```

- [ ] **Step 1: Write the failing tests**

`XrpcDataHomeTest.kt` uses Ktor `MockEngine`. It records each request and answers with canned JSON.
```kotlin
package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.lexicon.data.Bytes
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XrpcDataHomeTest {
    private val space = SpaceRef.personal("did:plc:owner")
    private val sealed = Sealed(ciphertext = Bytes(ByteArray(528)), keyId = "abcdefghijklm", nonce = Bytes(ByteArray(12)), version = 1)
    private val requests = mutableListOf<HttpRequestData>()

    private fun home(status: HttpStatusCode, body: String): XrpcDataHome {
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine)
        return XrpcDataHome(KtorXrpcClient(http, "https://cloud.test"), http, "https://cloud.test") { BearerToken("token") }
    }

    @Test
    fun `putRecord sends the space, key, envelope and swap, signed in`() = runTest {
        val stored = home(HttpStatusCode.OK, """{"cid":"bafyrecord","seq":7}""")
            .putRecord(space, "abc", sealed, blobs = listOf("bafyblob"), swap = RecordSwap.Absent)
        assertEquals(StoredRecord("abc", "bafyrecord", 7, sealed), stored)
        val request = requests.single()
        assertEquals("/xrpc/app.logdate.space.putRecord", request.url.encodedPath)
        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
        val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertTrue(""""space":"${space.uri}"""" in body)
        assertTrue(""""createOnly":true""" in body)
        assertTrue(""""blobs":["bafyblob"]""" in body)
    }

    @Test
    fun `a refused swap is a conflict`() = runTest {
        assertFailsWith<DataHomeConflictException> {
            home(HttpStatusCode.Conflict, """{"error":"InvalidSwap","message":"changed"}""")
                .putRecord(space, "abc", sealed, swap = RecordSwap.Matching("bafyold"))
        }
    }

    @Test
    fun `a missing record reads as null`() = runTest {
        assertNull(home(HttpStatusCode.BadRequest, """{"error":"RecordNotFound","message":"none"}""").getRecord(space, "abc"))
    }

    @Test
    fun `listChanges pages by sequence number`() = runTest {
        val page = home(
            HttpStatusCode.OK,
            """{"ops":[{"rkey":"abc","seq":3,"deleted":true}],"cursor":3,"hasMore":false}""",
        ).listChanges(space, since = 2, limit = 50)
        assertEquals(ChangePage(listOf(RecordChange("abc", 3, true, null, null)), 3, false), page)
        val url = requests.single().url
        assertEquals("2", url.parameters["since"])
        assertEquals("50", url.parameters["limit"])
        assertEquals(space.uri, url.parameters["space"])
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :shared:space:jvmTest -q`
Expected: compilation fails with unresolved `XrpcDataHome`, `RecordSwap`, `StoredRecord` and the other new types.

- [ ] **Step 3: Implement**

`DataHome.kt` contains exactly the declarations in **Produces** above, with KDoc on each. Document that exporting a space means paging `listChanges` from zero and reading each referenced blob.

`XrpcDataHome.kt`:
```kotlin
package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.space.DeleteRecordInput
import app.logdate.lexicon.app.logdate.space.DeleteRecordOutput
import app.logdate.lexicon.app.logdate.space.GetRecordOutput
import app.logdate.lexicon.app.logdate.space.ListRepoOpsOutput
import app.logdate.lexicon.app.logdate.space.PutRecordInput
import app.logdate.lexicon.app.logdate.space.PutRecordOutput
import app.logdate.lexicon.app.logdate.space.UploadBlobOutput
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.XrpcAuth
import studio.hypertext.atproto.xrpc.XrpcClient
import studio.hypertext.atproto.xrpc.XrpcProtocolException
import studio.hypertext.atproto.xrpc.procedure
import studio.hypertext.atproto.xrpc.query

/** LogDate Cloud, or a self-hosted LogDate server, as a [DataHome]. */
public class XrpcDataHome(
    private val client: XrpcClient,
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val auth: suspend () -> XrpcAuth,
) : DataHome {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun putRecord(space: SpaceRef, rkey: String, record: Sealed, blobs: List<String>, swap: RecordSwap): StoredRecord {
        val input = PutRecordInput(
            space = space.uri,
            rkey = rkey,
            record = record,
            blobs = blobs.ifEmpty { null },
            swapCid = (swap as? RecordSwap.Matching)?.cid,
            createOnly = if (swap == RecordSwap.Absent) true else null,
        )
        val output = conflicts { client.procedure<PutRecordInput, PutRecordOutput>(PUT, input) { auth = this@XrpcDataHome.auth() } }
        return StoredRecord(rkey, output.cid, output.seq, record)
    }

    override suspend fun deleteRecord(space: SpaceRef, rkey: String, swap: RecordSwap): Long =
        conflicts {
            client.procedure<DeleteRecordInput, DeleteRecordOutput>(
                DELETE,
                DeleteRecordInput(space = space.uri, rkey = rkey, swapCid = (swap as? RecordSwap.Matching)?.cid),
            ) { auth = this@XrpcDataHome.auth() }
        }.seq

    override suspend fun getRecord(space: SpaceRef, rkey: String): StoredRecord? =
        try {
            val output = client.query<GetRecordOutput>(GET) {
                auth = this@XrpcDataHome.auth()
                queryParameter("space", space.uri)
                queryParameter("rkey", rkey)
            }
            StoredRecord(rkey, output.cid, output.seq, output.value)
        } catch (e: XrpcProtocolException) {
            if (e.error == "RecordNotFound") null else throw e
        }

    override suspend fun listChanges(space: SpaceRef, since: Long, limit: Int): ChangePage {
        val output = client.query<ListRepoOpsOutput>(LIST) {
            auth = this@XrpcDataHome.auth()
            queryParameter("space", space.uri)
            queryParameter("since", since.toString())
            queryParameter("limit", limit.toString())
        }
        return ChangePage(output.ops.map { RecordChange(it.rkey, it.seq, it.deleted, it.cid, it.value) }, output.cursor, output.hasMore)
    }

    override suspend fun uploadBlob(space: SpaceRef, bytes: ByteArray): String {
        val response = httpClient.post("${baseUrl.trimEnd('/')}/xrpc/app.logdate.space.uploadBlob") {
            parameter("space", space.uri)
            bearer()
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        if (!response.status.isSuccess()) throw IllegalStateException("Uploading a blob failed: ${response.status} ${response.bodyAsText()}")
        return json.decodeFromString<UploadBlobOutput>(response.bodyAsText()).blob.ref.link
    }

    override suspend fun getBlob(space: SpaceRef, cid: String): ByteArray? {
        val response = httpClient.get("${baseUrl.trimEnd('/')}/xrpc/app.logdate.space.getBlob") {
            parameter("space", space.uri)
            parameter("cid", cid)
            bearer()
        }
        if (response.status.value == 400 && "BlobNotFound" in response.bodyAsText()) return null
        if (!response.status.isSuccess()) throw IllegalStateException("Reading a blob failed: ${response.status}")
        return response.readRawBytes()
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.bearer() {
        (auth() as? BearerToken)?.let { header(HttpHeaders.Authorization, "Bearer ${it.token}") }
    }

    private inline fun <T> conflicts(block: () -> T): T =
        try {
            block()
        } catch (e: XrpcProtocolException) {
            if (e.error == "InvalidSwap") throw DataHomeConflictException(e.message ?: "The record changed") else throw e
        }

    private companion object {
        val PUT = Nsid.require("app.logdate.space.putRecord")
        val DELETE = Nsid.require("app.logdate.space.deleteRecord")
        val GET = Nsid.require("app.logdate.space.getRecord")
        val LIST = Nsid.require("app.logdate.space.listRepoOps")
    }
}
```
Before compiling, check the property names `XrpcProtocolException.error`, `BearerToken.token`, `Blob.ref.link` and `Nsid.require` against `shared/atproto-xrpc`, `shared/atproto-lexicon/.../data/LexiconData.kt` and `shared/atproto-syntax`, and use the real names. `DataHome` itself must never depend on Ktor.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:space:jvmTest :shared:space:compileKotlinIosSimulatorArm64 -q`
Expected: the build succeeds and all `shared/space` tests pass.

- [ ] **Step 5: Commit**

Message: `chore(sync): Reach a data home through one shared interface`.
```bash
git restore --staged . && git add shared/space && git commit -F "$MSG"
```

### Task 5: Server space store, in memory and in Postgres

**Files:**
- Create:
  - `server/src/main/kotlin/app/logdate/server/space/SpaceStore.kt`
  - `server/.../space/SealedRecordCheck.kt`
  - `server/.../database/SpaceTables.kt`
  - `server/.../database/PostgreSQLSpaceStore.kt`
  - `server/src/main/resources/db/migration/V42__Add_personal_spaces.sql` (use the next free number at execution time; `MigrationVersionsTest` enforces it)
- Modify: `server/build.gradle.kts`, adding `implementation(projects.shared.space)`, and `di/ServerModule.kt`
- Test:
  - `server/src/test/kotlin/app/logdate/server/space/SpaceStoreContractTest.kt`, run against in-memory and against H2 through `PostgreSQLSpaceStore`
  - `server/src/test/kotlin/app/logdate/server/database/SpaceStorePostgresTest.kt`, an opt-in test for real PostgreSQL concurrency and cascades

**Interfaces:**
- Consumes: `Cid`, `DagCborCodec`, `SealedPadding`, the generated `Sealed`.
- Produces (package `app.logdate.server.space`):
```kotlin
data class SpaceRecordWrite(val accountId: UUID, val space: String, val author: String, val rkey: String, val record: JsonObject, val blobs: List<String>, val swapCid: String?, val createOnly: Boolean)
data class StoredSpaceRecord(val rkey: String, val cid: String, val seq: Long, val record: JsonObject)
data class SpaceChange(val rkey: String, val seq: Long, val deleted: Boolean, val cid: String?, val record: JsonObject?)
data class SpaceChangePage(val changes: List<SpaceChange>, val cursor: Long, val hasMore: Boolean)
class SpaceSwapException(message: String) : RuntimeException(message)
data class SpaceBlob(val cid: String, val mimeType: String, val size: Long, val storagePath: String)
interface SpaceStore {
    suspend fun put(write: SpaceRecordWrite): StoredSpaceRecord            // throws SpaceSwapException
    suspend fun delete(accountId: UUID, space: String, author: String, rkey: String, swapCid: String?): Long  // throws SpaceSwapException; returns the new seq (or the tombstone's seq if already deleted)
    suspend fun get(space: String, author: String, rkey: String): StoredSpaceRecord?
    suspend fun changes(space: String, author: String, since: Long, limit: Int): SpaceChangePage
    suspend fun putBlob(accountId: UUID, space: String, blob: SpaceBlob)
    suspend fun getBlob(space: String, cid: String): SpaceBlob?
    suspend fun purge(tombstonesBefore: Instant, unreferencedBlobsBefore: Instant): List<String>  // returns deleted blob storage paths
}
class InMemorySpaceStore : SpaceStore
```
- `fun checkSealedRecord(record: JsonObject): String?` (in `SealedRecordCheck.kt`) returns null if the record is a valid envelope, or the reason it isn't.

- [ ] **Step 1: Write the migration**

`V42__Add_personal_spaces.sql`:
```sql
-- Sealed records in spaces (personal now; circle and group spaces later). The server sees only
-- each record's space, author repository, opaque key, sequence number and padded bytes.
CREATE TABLE space_states (
    space_uri TEXT NOT NULL,
    author_did TEXT NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    last_seq BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (space_uri, author_did)
);

CREATE TABLE space_records (
    space_uri TEXT NOT NULL,
    author_did TEXT NOT NULL,
    rkey VARCHAR(512) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    seq BIGINT NOT NULL,
    cid VARCHAR(255),
    record BYTEA,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    PRIMARY KEY (space_uri, author_did, rkey)
);
CREATE INDEX idx_space_records_seq ON space_records(space_uri, author_did, seq);

CREATE TABLE space_record_blobs (
    space_uri TEXT NOT NULL,
    author_did TEXT NOT NULL,
    rkey VARCHAR(512) NOT NULL,
    cid VARCHAR(255) NOT NULL,
    PRIMARY KEY (space_uri, author_did, rkey, cid),
    FOREIGN KEY (space_uri, author_did, rkey) REFERENCES space_records(space_uri, author_did, rkey) ON DELETE CASCADE
);

CREATE TABLE space_blobs (
    space_uri TEXT NOT NULL,
    cid VARCHAR(255) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    mime_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (space_uri, cid)
);

-- Account deletion must also delete space blobs from object storage.
CREATE OR REPLACE FUNCTION enqueue_account_blob_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('account_blob_cleanup:' || OLD.id::text, 0));
    INSERT INTO account_blob_cleanup(owner_id) VALUES (OLD.id) ON CONFLICT DO NOTHING;
    INSERT INTO account_blob_cleanup_paths(owner_id, storage_path)
        SELECT OLD.id, storage_path FROM logdate_media_records WHERE user_id = OLD.id AND storage_path IS NOT NULL
        UNION SELECT OLD.id, storage_path FROM logdate_atproto_blobs WHERE user_id = OLD.id
        UNION SELECT OLD.id, storage_path FROM logdate_backups WHERE user_id = OLD.id
        UNION SELECT OLD.id, storage_path FROM space_blobs WHERE account_id = OLD.id
        ON CONFLICT DO NOTHING;
    RETURN OLD;
END;
$$;
```

- [ ] **Step 2: Write the contract test**

`SpaceStoreContractTest.kt` is an abstract class with two subclasses: `InMemorySpaceStoreTest` (store = `InMemorySpaceStore()`) and `PostgreSQLSpaceStoreH2Test`. The H2 subclass wraps every test in `withH2Database(AccountsTable, SpaceStatesTable, SpaceRecordsTable, SpaceRecordBlobsTable, SpaceBlobsTable)`, inserts an account row, and uses `PostgreSQLSpaceStore()`. The tests:
```kotlin
abstract class SpaceStoreContractTest {
    abstract fun withStore(block: suspend (SpaceStore, UUID) -> Unit)

    private val space = "at://did:plc:owner/space/app.logdate.space.personal/self"
    private val author = "did:plc:owner"
    private fun sealed(fill: Int) = buildJsonObject {
        put("\$type", "app.logdate.crypto.sealed")
        put("version", 1)
        put("keyId", "abcdefghijklm")
        putJsonObject("nonce") { put("\$bytes", Base64.encode(ByteArray(12))) }
        putJsonObject("ciphertext") { put("\$bytes", Base64.encode(ByteArray(528) { fill.toByte() })) }
    }
    private fun write(account: UUID, rkey: String, fill: Int = 1, swapCid: String? = null, createOnly: Boolean = false, blobs: List<String> = emptyList()) =
        SpaceRecordWrite(account, space, author, rkey, sealed(fill), blobs, swapCid, createOnly)

    @Test fun `writes get increasing sequence numbers and read back`() = withStore { store, account ->
        val first = store.put(write(account, "a"))
        val second = store.put(write(account, "b"))
        assertTrue(second.seq > first.seq)
        assertEquals(first, store.get(space, author, "a"))
    }

    @Test fun `createOnly refuses an existing record and swapCid refuses a changed one`() = withStore { store, account ->
        val first = store.put(write(account, "a"))
        assertFailsWith<SpaceSwapException> { store.put(write(account, "a", createOnly = true)) }
        store.put(write(account, "a", fill = 2, swapCid = first.cid))
        assertFailsWith<SpaceSwapException> { store.put(write(account, "a", fill = 3, swapCid = first.cid)) }
    }

    @Test fun `deletions are listed in order as tombstones`() = withStore { store, account ->
        store.put(write(account, "a"))
        val put = store.put(write(account, "b"))
        val deleted = store.delete(account, space, author, "a", swapCid = null)
        assertNull(store.get(space, author, "a"))
        val page = store.changes(space, author, since = put.seq, limit = 10)
        assertEquals(listOf(SpaceChange("a", deleted, true, null, null)), page.changes)
        assertEquals(deleted, page.cursor)
        assertFalse(page.hasMore)
    }

    @Test fun `changes page by limit`() = withStore { store, account ->
        repeat(3) { store.put(write(account, "r$it")) }
        val first = store.changes(space, author, 0, 2)
        assertEquals(2, first.changes.size); assertTrue(first.hasMore)
        val rest = store.changes(space, author, first.cursor, 2)
        assertEquals(listOf("r2"), rest.changes.map { it.rkey }); assertFalse(rest.hasMore)
    }

    @Test fun `purge deletes old tombstones and unreferenced old blobs only`() = withStore { store, account ->
        val now = Clock.System.now()
        store.putBlob(account, space, SpaceBlob("bafyused", "application/octet-stream", 10, "path/used"))
        store.putBlob(account, space, SpaceBlob("bafyorphan", "application/octet-stream", 10, "path/orphan"))
        store.put(write(account, "a", blobs = listOf("bafyused")))
        store.put(write(account, "b"))
        store.delete(account, space, author, "b", null)
        val purged = store.purge(tombstonesBefore = now + 1.hours, unreferencedBlobsBefore = now + 1.hours)
        assertEquals(listOf("path/orphan"), purged)
        assertNotNull(store.getBlob(space, "bafyused"))
        assertNull(store.getBlob(space, "bafyorphan"))
        assertEquals(listOf("a"), store.changes(space, author, 0, 10).changes.map { it.rkey })
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :server:test --tests 'app.logdate.server.space.*' -q`
Expected: compilation fails with unresolved `SpaceStore` and the other new types.

- [ ] **Step 4: Implement**

1. **Tables.** In `database/SpaceTables.kt`, declare `SpaceStatesTable`, `SpaceRecordsTable`, `SpaceRecordBlobsTable` and `SpaceBlobsTable`, mirroring V42 exactly. Use `javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)`, `binary("record").nullable()` and `timestamp("deleted_at").nullable()`. Primary keys match the migration.
2. **`InMemorySpaceStore`** keeps maps guarded by a single `Mutex`:
   - `states: MutableMap<Pair<String,String>, Long>`
   - `records: MutableMap<Triple<String,String,String>, Row>`
   - `recordBlobs: MutableMap<Triple<...>, Set<String>>`
   - `blobs: MutableMap<Pair<String,String>, Pair<SpaceBlob, Instant>>`
3. **Computing the CID.** Both stores call this helper in `SpaceStore.kt`:
```kotlin
internal fun recordCid(record: JsonObject): Pair<String, ByteArray> {
    val bytes = DagCborCodec.encode(record)
    return Cid.sha256(Cid.DAG_CBOR_CODEC, bytes).value to bytes
}
```
   Check where `DAG_CBOR_CODEC` is declared (`Cid.kt:120`) and import it accordingly.
4. **`PostgreSQLSpaceStore.put`** runs in one `transaction { }`:
   1. `insertIgnore` the `space_states` row.
   2. `selectAll().where { space and author }.forUpdate()` to read `last_seq`.
   3. Read the current record row.
   4. Apply `createOnly` (a live row exists → `SpaceSwapException("A record with this key exists")`) and `swapCid` (the live CID differs → `SpaceSwapException("The record changed")`).
   5. Set `next = last_seq + 1` and update the state.
   6. Upsert the record row with `deleted=false`, `deleted_at=null`, `cid`, the CBOR bytes and `size_bytes`.
   7. Replace the `space_record_blobs` rows for that key.
   8. Return the `StoredSpaceRecord`.

   This is the same lock-then-increment pattern as `PostgreSQLLogDateCollectionsMetadataStore.nextState`.
5. **`delete`** takes the same lock.
   - A missing record is a no-op that returns `last_seq`.
   - A tombstone returns its own `seq`.
   - A live record gets the swap check, then becomes a tombstone (`record=null`, `cid=null`, `size_bytes=0`, `deleted=true`, `deleted_at=now`, `seq=next`) and loses its blob rows.
6. **`changes`** selects rows with `seq > since` ordered by `seq`, `limit + 1` of them, decodes `record` with `DagCborCodec.decode(...) as JsonObject`, and sets `hasMore` when there are more than `limit` rows. `cursor` is the last returned `seq`, or `since` when the page is empty.
7. **`purge`**:
   - Deletes tombstones where `deleted_at < tombstonesBefore`.
   - Selects blobs with `created_at < unreferencedBlobsBefore` that no `space_record_blobs` row references, deletes those rows, and returns their `storage_path`s.
8. **`SealedRecordCheck.kt`**:
```kotlin
internal fun checkSealedRecord(record: JsonObject): String? {
    val sealed = try { lenientJson.decodeFromJsonElement<Sealed>(record) } catch (e: SerializationException) { return "Not an app.logdate.crypto.sealed record" }
    if (sealed.lexiconType != Sealed.TYPE) return "Only app.logdate.crypto.sealed records can be stored in a space"
    if (sealed.version != 1L) return "Envelope version ${sealed.version} isn't supported"
    if (sealed.keyId.isEmpty() || sealed.keyId.length > 128) return "keyId must be 1 to 128 characters"
    if (sealed.nonce.size != 12) return "nonce must be 12 bytes"
    if (!SealedPadding.isPaddedSize(sealed.ciphertext.size - 16)) return "ciphertext isn't padded to a size bucket"
    return null
}
private val lenientJson = Json { ignoreUnknownKeys = false }
```
   Add a small unit test for each rejection reason in `SealedRecordCheckTest.kt`.
9. **Wiring.** In `di/ServerModule.kt`, beside the account-key-envelope binding:
```kotlin
single<SpaceStore> { if (isDatabaseAvailable) PostgreSQLSpaceStore() else InMemorySpaceStore() }
```
10. **Postgres test.** `SpaceStorePostgresTest` follows `CollectionVersionPostgresTest`: same env gate, `insertTestAccountRow`, and `deleteTestAccountRow` in `finally`. It runs two concurrent `put`s for different keys and asserts that they get distinct sequence numbers. Then it deletes the account row and asserts that every space table is empty for that account.

- [ ] **Step 5: Run the tests to verify they pass**

Run:
- `./gradlew :server:test --tests 'app.logdate.server.space.*' --tests 'app.logdate.server.database.MigrationVersionsTest' -q`
- The Postgres tests against the local test database: start it with `./run dev` and run `./run test:server`, which points `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD` at it. Without that database they're skipped and say why.

Expected: PASS for both.

- [ ] **Step 6: Commit**

Message: `chore(sync): Store sealed space records on the server`.
```bash
git restore --staged . && git add server/build.gradle.kts server/src/main/kotlin/app/logdate/server/space server/src/main/kotlin/app/logdate/server/database/SpaceTables.kt server/src/main/kotlin/app/logdate/server/database/PostgreSQLSpaceStore.kt server/src/main/kotlin/app/logdate/server/di/ServerModule.kt server/src/main/resources/db/migration/V42__Add_personal_spaces.sql server/src/test/kotlin/app/logdate/server/space server/src/test/kotlin/app/logdate/server/database/SpaceStorePostgresTest.kt && git commit -F "$MSG"
```

### Task 6: `app.logdate.space.*` routes, owner-only and behind a flag

**Files:**
- Create: `server/src/main/kotlin/app/logdate/server/routes/SpaceXrpcRoutes.kt`, `server/.../routes/docs/XrpcSpaceDocs.kt`
- Modify:
  - `server/.../routes/XrpcRoutes.kt`: make `requireAuthenticatedAccount` `internal`.
  - `server/.../routes/ServerRouting.kt`: register the routes.
  - `server/.../ServerDescriptorConfig.kt`: add the env flag and the advertised feature.
  - `shared/model/.../ServerDescriptor.kt`: add `PERSONAL_SPACE_V1 = "personalSpaceV1"`.
  - `server/src/test/kotlin/app/logdate/server/AuthV1TestApp.kt`: install the routes with an `InMemorySpaceStore` and `InMemoryLogDateBlobStorage`.
  - `LogDateBlobStorage.kt` and every `when` over `LogDateBlobNamespace`: add `SPACE`, with path `users/{ownerId}/spaces/{blobId}` and label `"space-blob"`.
- Test: `server/src/test/kotlin/app/logdate/server/routes/SpaceXrpcRoutesTest.kt`

**Interfaces:**
- Consumes: `SpaceStore`, `checkSealedRecord`, `SpaceRef.parse`, `SpaceTypes.PERSONAL`, `requireAuthenticatedAccount`, `LogDateBlobStorage`, the generated input and output classes.
- Produces: `fun Route.spaceXrpcRoutes(enabled: Boolean, store: SpaceStore?, blobStorage: LogDateBlobStorage?, auth: suspend ApplicationCall.() -> Account?)`

- [ ] **Step 1: Write the failing route tests**

`SpaceXrpcRoutesTest.kt` uses `configureAuthV1TestApp()`, with two accounts and their access tokens.
```kotlin
class SpaceXrpcRoutesTest {
    @Test fun `the owner can write, read and list their personal space`() = testApplication {
        val env = configureAuthV1TestApp(personalSpaceEnabled = true)
        val (did, token) = env.signedInAccount("alice")
        val space = "at://$did/space/app.logdate.space.personal/self"
        val put = client.post("/xrpc/app.logdate.space.putRecord") { bearer(token); json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""") }
        assertEquals(HttpStatusCode.OK, put.status)
        assertEquals(HttpStatusCode.OK, client.get("/xrpc/app.logdate.space.getRecord?space=$space&rkey=abc") { bearer(token) }.status)
        val list = client.get("/xrpc/app.logdate.space.listRepoOps?space=$space") { bearer(token) }.bodyAsText()
        assertTrue(""""rkey":"abc"""" in list)
    }

    @Test fun `another account can't read or write it, and it looks missing`() = testApplication {
        val env = configureAuthV1TestApp(personalSpaceEnabled = true)
        val (did, owner) = env.signedInAccount("alice")
        val (_, other) = env.signedInAccount("bob")
        val space = "at://$did/space/app.logdate.space.personal/self"
        client.post("/xrpc/app.logdate.space.putRecord") { bearer(owner); json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""") }
        val read = client.get("/xrpc/app.logdate.space.getRecord?space=$space&rkey=abc") { bearer(other) }
        assertEquals(HttpStatusCode.BadRequest, read.status); assertTrue("RecordNotFound" in read.bodyAsText())
        val write = client.post("/xrpc/app.logdate.space.putRecord") { bearer(other); json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""") }
        assertEquals(HttpStatusCode.BadRequest, write.status); assertTrue("RecordNotFound" in write.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/xrpc/app.logdate.space.getRecord?space=$space&rkey=abc").status)
    }

    @Test fun `records that aren't padded sealed envelopes are refused`() = testApplication {
        val env = configureAuthV1TestApp(personalSpaceEnabled = true)
        val (did, token) = env.signedInAccount("alice")
        val space = "at://$did/space/app.logdate.space.personal/self"
        val response = client.post("/xrpc/app.logdate.space.putRecord") {
            bearer(token); json("""{"space":"$space","rkey":"abc","record":{"${'$'}type":"app.logdate.journal.entry","text":"plain"}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status); assertTrue("InvalidRecord" in response.bodyAsText())
    }

    @Test fun `a changed record is a swap conflict`() = testApplication { /* put, then put with swapCid = "bafybogus" → 409 InvalidSwap */ }

    @Test fun `blobs upload and read back only for the owner`() = testApplication { /* uploadBlob with 600 bytes → blob.ref.$link; owner getBlob returns the bytes; other gets BlobNotFound */ }

    @Test fun `with the flag off every method is not implemented and the feature isn't advertised`() = testApplication {
        val env = configureAuthV1TestApp(personalSpaceEnabled = false)
        val (did, token) = env.signedInAccount("alice")
        val response = client.get("/xrpc/app.logdate.space.listRepoOps?space=at://$did/space/app.logdate.space.personal/self") { bearer(token) }
        assertEquals(HttpStatusCode.NotImplemented, response.status)
        assertTrue("MethodNotImplemented" in response.bodyAsText())
    }
}
```
Write out the two outlined tests in full, in the same style as the others. Put these helpers at the bottom of the file:
- `signedInAccount`: saves an `Account`, ensures its identity, and returns its DID and a `tokenService.generateAccessToken(id)` token.
- `bearer`, `json` and `sealedJson()`: the last builds a valid envelope with a 528-byte ciphertext, the same as the contract test's `sealed(...)`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :server:test --tests 'app.logdate.server.routes.SpaceXrpcRoutesTest' -q`
Expected: compilation fails because `configureAuthV1TestApp` has no `personalSpaceEnabled` parameter.

- [ ] **Step 3: Implement**

`SpaceXrpcRoutes.kt` has one handler per method under `route("/xrpc")`. Shared preamble for every method:
```kotlin
if (!enabled || store == null) return call.respond(HttpStatusCode.NotImplemented, PdsErrorResponse("MethodNotImplemented", "Personal spaces aren't enabled on this server"))
val account = call.auth() ?: return                       // responds 401 itself
val space = SpaceRef.parse(spaceParam) ?: return call.respond(HttpStatusCode.BadRequest, PdsErrorResponse("InvalidRequest", "space must be a space URI"))
if (space.type != SpaceTypes.PERSONAL || space.skey != "self") return call.respond(HttpStatusCode.BadRequest, PdsErrorResponse("InvalidRequest", "Only personal spaces are supported"))
if (space.authority != account.did) return call.respond(HttpStatusCode.BadRequest, PdsErrorResponse(notFoundCode, "Not found"))
```
`notFoundCode` is `RecordNotFound` for record methods and `BlobNotFound` for blob methods. Then, per method:
- **`putRecord`**:
  1. Decode `PutRecordInput` from the body as `JsonObject`, so the record bytes aren't re-serialized through `Sealed`.
  2. Validate with `checkSealedRecord`. A failure gives 400 `InvalidRecord` with the reason.
  3. Call `store.put(SpaceRecordWrite(account.id.toJavaUUID(), space.uri, account.did, rkey, record, blobs, swapCid, createOnly == true))`.
  4. `SpaceSwapException` gives 409 `InvalidSwap`. Success gives `PutRecordOutput(cid, seq)`.
- **`deleteRecord`**: answers `DeleteRecordOutput(seq)`, or 409 on a swap conflict.
- **`getRecord`**: answers `GetRecordOutput(cid, seq, value)`, decoded from the stored `JsonObject` into `Sealed`. If the record is missing, it answers 400 `RecordNotFound`.
- **`listRepoOps`**: parses `since` (default 0) and `limit` (default 200, clamped to 1..500) and answers `ListRepoOpsOutput`.
- **`uploadBlob`**:
  1. Read the body bytes and refuse more than 100 MiB with 413 `BlobTooLarge`.
  2. Compute `cid = Cid.rawSha256(bytes).value`.
  3. Call `blobStorage.putBlob(LogDateBlobWriteRequest(ownerId, LogDateBlobNamespace.SPACE, cid, null, "application/octet-stream", bytes))`, then `store.putBlob(...)`.
  4. Answer `{"blob":{"$type":"blob","ref":{"$link":cid},"mimeType":"application/octet-stream","size":n}}` built from the generated `UploadBlobOutput`.
- **`getBlob`**: `store.getBlob(space.uri, cid)?.let { blobStorage.getBlob(it.storagePath) }`. If there are bytes, respond with them as `ContentType.Application.OctetStream`. Otherwise answer 400 `BlobNotFound`.

Rules for `XrpcSpaceDocs.kt`:
- Each route uses `bearerOrDpopOperation(...)` with `ApiTags.XRPC`, as in `XrpcRepoDocs`.
- Document 400, 401, 409 (put and delete only) and 501.
- Write each summary in plain language. One example: "Store one sealed record in your personal space."

In `ServerRouting.kt`, call `spaceXrpcRoutes(...)` next to `xrpcRoutes(...)`:
- `enabled = descriptorConfig.personalSpaceEnabled`
- the Koin `SpaceStore` and `LogDateBlobStorage`
- `auth = { requireAuthenticatedAccount(...) }`, passing the same services `xrpcRoutes` receives

In `ServerDescriptorConfig`:
- Read `LOGDATE_PERSONAL_SPACE_ENABLED` (default `false`) in `fromEnvironment`.
- Add `ServerProtocolFeature.PERSONAL_SPACE_V1` in `toDescriptor()` only when it is on.
- Add a descriptor test asserting that the feature is absent by default and present when the flag is on.

In `AuthV1TestApp`:
- Add `personalSpaceEnabled: Boolean = false` to `configureAuthV1TestApp`.
- Install `spaceXrpcRoutes` with `InMemorySpaceStore()` and the environment's `blobStorage`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :server:test -q`, then `./gradlew :server:ktlintCheck :server:detekt -q`
Expected: PASS, including `OpenApiContractTest` and `XrpcRouteCoverageTest`. If `XrpcRouteCoverageTest` lists implemented methods, add the six new NSIDs there.

- [ ] **Step 5: Commit**

Message: `chore(sync): Serve personal spaces to their owners behind a server flag`.
```bash
git restore --staged . && git add server shared/model && git commit -F "$MSG"
```
Before running `git add server`, run `git status --short` and confirm it lists only this task's files.

### Task 7: Purge old tombstones and unreferenced blobs

**Files:**
- Modify: `server/src/main/kotlin/app/logdate/server/SyncMaintenance.kt`
- Test: `server/src/test/kotlin/app/logdate/server/SyncMaintenanceSpaceTest.kt`

**Interfaces:**
- Consumes: `SpaceStore.purge` and `LogDateBlobStorage.deleteBlobIfExists`.
- Produces: an internal `suspend fun purgeSpaces(store: SpaceStore, blobs: LogDateBlobStorage?, now: Instant)`, called from the existing maintenance loop.

- [ ] **Step 1: Write the failing test**
```kotlin
class SyncMaintenanceSpaceTest {
    @Test fun `old tombstones and blobs nothing refers to are purged from the store and storage`() = runTest {
        val store = InMemorySpaceStore()
        val storage = InMemoryLogDateBlobStorage()
        val owner = UUID.randomUUID()
        val space = "at://did:plc:owner/space/app.logdate.space.personal/self"
        val path = storage.putBlob(LogDateBlobWriteRequest(owner, LogDateBlobNamespace.SPACE, "bafyorphan", null, "application/octet-stream", byteArrayOf(1)))
        store.putBlob(owner, space, SpaceBlob("bafyorphan", "application/octet-stream", 1, path))
        purgeSpaces(store, storage, now = Clock.System.now() + 2.days)
        assertNull(storage.getBlob(path))
        assertNull(store.getBlob(space, "bafyorphan"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :server:test --tests 'app.logdate.server.SyncMaintenanceSpaceTest' -q`
Expected: compilation fails with unresolved `purgeSpaces`.

- [ ] **Step 3: Implement**
```kotlin
/** Tombstones older than the sync tombstone window go, and so do blobs no record has referred to for a day. */
internal suspend fun purgeSpaces(store: SpaceStore, blobs: LogDateBlobStorage?, now: Instant) {
    val paths = store.purge(tombstonesBefore = now - 30.days, unreferencedBlobsBefore = now - 1.days)
    paths.forEach { path ->
        try {
            blobs?.deleteBlobIfExists(path)
        } catch (e: Exception) {
            Napier.e("SERVER_SPACE_BLOB_PURGE_FAILED", e)
        }
    }
    if (paths.isNotEmpty()) Napier.i("SERVER_SPACE_BLOBS_PURGED count=${paths.size}")
}
```
Call it from the loop in `startSyncMaintenance`, next to the collection tombstone purge, and resolve the store and storage from Koin the same way that function already does. Check whether `SERVER_*` log codes must be registered in an allow-list (search for `SERVER_SIGNING_KEYS_REWRAPPED`), and add both codes there if so.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :server:test -q`
Expected: PASS.

- [ ] **Step 5: Commit**

Message: `chore(sync): Purge old space tombstones and blobs nothing refers to`.
```bash
git restore --staged . && git add server/src/main/kotlin/app/logdate/server/SyncMaintenance.kt server/src/test/kotlin/app/logdate/server/SyncMaintenanceSpaceTest.kt && git commit -F "$MSG"
```

### Task 8: End to end, sealed on a client and stored on a real server

**Files:**
- Create: `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/PersonalSpaceE2ETest.kt`
- Modify:
  - `integration/server-client-e2e/build.gradle.kts`: add `testImplementation(projects.shared.space)`.
  - The harness: enable `LOGDATE_PERSONAL_SPACE_ENABLED` for this test through the same mechanism other journeys use to set server configuration. Search the harness for `ENCRYPTED_ACCOUNT_KEYS` to find it.

**Interfaces:**
- Consumes: `withServerClientHarness`, `createAccountWithSyntheticPasskey`, `SealingKeys`, `SealedRecordCodec`, `XrpcDataHome`, `SpaceRef`.

- [ ] **Step 1: Write the test**
```kotlin
class PersonalSpaceE2ETest {
    @Test
    fun `a record sealed on one device opens on another, and the server never sees inside`() = runTest {
        withServerClientHarness {
            val created = apiClient.createAccountWithSyntheticPasskey("space_${Random.nextInt(100000, 999999)}")
            val did = requireNotNull(created.data.account.did)
            val token = created.data.tokens.accessToken
            val home = XrpcDataHome(KtorXrpcClient(httpClient, baseUrl), httpClient, baseUrl) { BearerToken(token) }
            val identityKey = ByteArray(32) { it.toByte() }
            val space = SpaceRef.personal(did)
            val payload = SealedPayload("app.logdate.journal.entry", "3k2a", buildJsonObject { put("\$type", "app.logdate.journal.entry"); put("text", "Lake day") })

            val write = SealedRecordCodec.seal(SealingKeys.personal(identityKey), space, did, payload)
            home.putRecord(space, write.rkey, write.record, swap = RecordSwap.Absent)

            // A second device has only the identity key.
            val change = home.listChanges(space, since = 0).changes.single()
            val opened = SealedRecordCodec.open(SealingKeys.personal(identityKey.copyOf()), space, did, change.rkey, requireNotNull(change.record))
            assertEquals(payload, opened)
            assertTrue("Lake" !in Json.encodeToString(change.record))
        }
    }
}
```
Check the harness's real property names for the HTTP client, base URL and the created account's DID (`created.data.account.did` or equivalent) in `harness/` and `fixtures/`, and use them.

- [ ] **Step 2: Run it**

Run: `./gradlew :integration:server-client-e2e:test --tests '*PersonalSpaceE2ETest*' -q`
Expected: PASS.

- [ ] **Step 3: Commit**

Message: `test(sync): Seal on one device and open on another through a real server`.
```bash
git restore --staged . && git add integration/server-client-e2e && git commit -F "$MSG"
```
Before running `git add`, confirm with `git status --short` that only this task's files are listed.

### Task 9: Land it

- [ ] **Step 1: Full verification**

Run:
- `./gradlew :shared:space:allTests :server:test :server:ktlintCheck :server:detekt :integration:server-client-e2e:test -q`
- `cd tools/lexicon-check && pnpm run check`
- the opt-in Postgres tests against a disposable local PostgreSQL

Expected: everything passes.

- [ ] **Step 2: Document**

- In `docs/reference/lexicons.md`, add the six `app.logdate.space.*` methods to the family table under `app.logdate.space`.
- In `server/docs/environment-variables.md`, document `LOGDATE_PERSONAL_SPACE_ENABLED` (default off) in the style of `LOGDATE_ENCRYPTED_ACCOUNT_KEYS_ENABLED`.
- Commit both with the message `docs(sync): Document personal-space methods and their server flag`.

- [ ] **Step 3: Rebase, push, open the PR, merge when green**

Rebase onto `origin/main`, renumbering the migration if another branch took V42. Push the branch. Open the PR with `## TL;DR`, `## Changes` and `## Follow-ups and Next Work`. Rebase-merge it once CI passes. The flag stays off in staging and production. Turning it on is part of "Sync every kind of journal data as LogDate schema records".

---

## Self-review

- **Spec coverage:**

  | Design requirement | Task |
  |---|---|
  | Space-store layout (one repository per author per space, a sequence per space, tombstones) | 5 |
  | `app.logdate.space.*` methods defined as lexicons | 1, 6 |
  | Shared data-home interface, with export by paging from zero | 4 |
  | Envelope: AAD binding, padding, derived keys, `keyId` | 3 |
  | Owner-only reads that look missing to everyone else | 6 |
  | Server can't move an envelope (AAD tests) | 3 |
  | Flag, and hidden from people | 6 (flag off by default, no app wiring) |
  | Referenced blobs for cleanup, plus quotas | 5 and 7 (cleanup). Total bytes per account are computable from `size_bytes` and `space_blobs`; enforcing quotas belongs to the sync task, where Settings changes |
  | Account deletion | 5 (cascades plus the blob-cleanup trigger) |

- **Placeholders:** Task 6 outlines two tests by behavior and states that they must be written in full in the same style. Tasks 3, 4 and 8 tell the implementer to confirm three library method names against their sources, naming the file for each, because those names couldn't be verified while writing the plan.
- **Type consistency:** `SpaceRef.uri`, `SealedWrite(rkey, record)`, `StoredRecord(rkey, cid, seq, record)`, `RecordChange`/`ChangePage`, `SpaceRecordWrite`/`StoredSpaceRecord`/`SpaceChange`/`SpaceChangePage` and `checkSealedRecord` are each used with the same signatures across tasks.
