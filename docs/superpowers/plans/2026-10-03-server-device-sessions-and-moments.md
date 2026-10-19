# Server Device Sessions and Moments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the LogDate server what a Wear OS watch needs to sync as its own peer: tokens with a unique ID and tolerant decoding, revocable linked-device sessions that a phone creates with a one-time code (no sign-in on the watch), strict create-only uploads that never overwrite or resurrect a record, and a separate `MOMENT` collection for marked moments. This is "Track A: A-server" (B1 to B4) of the Wear OS redesign master plan and the "Server changes" section of `docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md`.

**Architecture:** Twelve commits, each shippable on its own and invisible to existing clients. Tokens first: every JWT gets a `jti`, and the token service learns `sid`/`scp=sync` claims behind a default-deny rule (`validateAccessToken` refuses device tokens, so every existing route stays full-access only). Then device sessions: a Flyway V34 migration, `DeviceSessionRepository` (in-memory and PostgreSQL), `DeviceSessionGuard` (30 s cache), `AccessTokenVerifier`, `DeviceLinkService`, `/auth/devices` routes, refresh and `/auth/me` handling, a networking client, and an end-to-end journey. Then the delta on top of main's create-only writes: an atomic `createIfAbsent` built on the repo engine's `putRecordIfAbsent`, a strict `?ifAbsent=true` mode that answers `410` for deleted IDs, `HEAD /contents/{id}`, and `POST /associations?ifAbsent=true`. Last, marked moments: `LogDateCollectionKind.MOMENT`, a narrow `LogDateMomentsRepository`, and `/api/v1/moments` routes. New capabilities are announced through additive descriptor flags (`deviceLinkingV1`, `conditionalCreateV1`, `momentsV1`).

**Tech Stack:** Kotlin 2.3 (server JVM 17, shared and client Kotlin Multiplatform), Ktor 3.4 server and client (`MockEngine` for client tests, `testApplication` for route tests), smiley4 ktor-openapi 5.6 with the repo's hand-written route docs and schema registry, Exposed 1.2 (`org.jetbrains.exposed.v1`) with Flyway migrations, H2 in PostgreSQL mode for repository tests plus opt-in disposable-PostgreSQL tests, kotlinx.serialization, `kotlin.time.Clock`/`Instant`, `kotlin.uuid.Uuid`, the in-repo AT Protocol repo engine (`DefaultRepoEngine`), kotlin.test with mockk.

## Global Constraints

- Run every command from the worktree root: `/Users/williecubed/Projects/TheHypertextStudio/logdate-mobile/.claude/worktrees/backup-sync-infinite-notification-dd4c3a`.
- Every Gradle command starts with `export ANDROID_HOME=$HOME/Library/Android/sdk &&`; every `git commit` sets `ANDROID_HOME` because the pre-commit hook runs ktlint through Gradle.
- Gradle tasks used (all verified to exist): `:server:test`, `:server:ktlintFormat`, `:server:ktlintCheck`, `:server:detekt`, `:shared:model:compileKotlinIosSimulatorArm64`, `:shared:model:ktlintCheck`, `:client:networking:jvmTest`, `:client:networking:compileKotlinIosSimulatorArm64`, `:client:networking:compileTestKotlinIosSimulatorArm64`, `:client:networking:ktlintCheck`, `:integration:server-client-e2e:test`, `:integration:server-client-e2e:ktlintCheck`.
- Devices: no task here touches Android. Never `connected*AndroidTest`, `install*` or `adb`.
- Server logging: Napier, and only constant messages. `Slf4jAntilog` (`server/.../logging/ServerLogging.kt:35`) replaces every message with a fixed category (`SERVER_ERROR`, `SERVER_WARNING`, `SERVER_NOTICE`) or a validated `SyncDiagnosticEvent` since `cc90665c4`; new code never interpolates IDs, codes or tokens into a log line.
- Client logging (Task 7): Napier only; the client added here returns `Result` failures and logs nothing.
- IDs: `kotlin.uuid.Uuid.random()` for new IDs; `java.util.UUID` only where the server's storage layer already speaks it (convert with `toJavaUUID()` / `toKotlinUuid()` from `server/.../database/UuidExtensions.kt`).
- Time: the server uses `kotlin.time.Clock` and `kotlin.time.Instant` (migrated off kotlinx.datetime in `13a0163a7`); wire models carry epoch milliseconds as `Long`, like every sync model.
- Serialization: kotlinx.serialization; new wire fields are optional with defaults so old clients and servers ignore them (`serverJson` has `ignoreUnknownKeys = true` and does not encode defaults).
- Default-deny scope: a token with `scp=sync` reaches only the sync data routes (contents, journals, associations, drafts, media, `/ops/sync/status`, moments), `GET /auth/me`, `GET /auth/devices` and `DELETE /auth/devices/self`. Everything else refuses it.
- Copy (error messages, docs): literal, never "we", no vague metaphor verbs ("kept", "held", "carried").
- OpenAPI: route files carry no inline documentation (`OpenApiContractTest.route files carry no inline documentation`); every new operation gets a docs object under `routes/docs/` and every reachable schema an entry in `openapi/schemadocs/`.
- Size limits (detekt): functions ≤ 60 lines, classes ≤ 600; new route files split one function per route. Never add a baseline entry.
- ktlint: max line 140; run `:server:ktlintFormat` (or the module's) before every check; it has the final word on import order and wrapping.
- Shared `commonMain` changes must compile for iOS (`:shared:model:compileKotlinIosSimulatorArm64`; `:client:networking:compileKotlinIosSimulatorArm64` and `:client:networking:compileTestKotlinIosSimulatorArm64` for the client). Backtick test names in common code contain no commas.
- Commit mechanics: one step writes the message with a heredoc into `/private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/<name>.txt`; the next step runs `git restore --staged . && git add <exact paths> && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F <that file>` as one chain. Never `git add -A`, `git add .`, `-m` or `--no-verify`.
- Commit messages: Conventional Commits, title ≤ 72 characters, scope from `allowed-scopes.txt` (`server`, `networking`, `sync`; never `build`, `android`, `ios`, `desktop`, `infra`), never the `style` type, no phase numbers, body wrapped at 72, last line `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. `feat`/`fix` only for behavior a client can use; `chore`/`refactor`/`test` otherwise.
- Line numbers were read at `17490e104` (branch `claude/wearos-android-ux-redesign-081dc0`, which contains main's `8f25cd326` "Accept create-only and strict-version writes from the Mac app" and `cc90665c4` "Stream large backups and accept opt-in diagnostic reports"; `44439ea49` and `a49c5b7d8` are the pre-rebase SHAs of those two). Every line number is as of `17490e104`, before any task in this plan runs. Earlier tasks shift lines in files that later tasks edit again (`SyncHelpers.kt`, `SyncCollectionRoutes.kt`, `ApiTags.kt`, `SyncSchemaDocs.kt`, `ServerModule.kt`, `SyncTestApp.kt`, `JwtTokenServiceTest.kt`), so every edit also quotes its anchor text, and the anchor wins over the number.

---

## Rollout Order and Why

1. **Tolerant decoding must be live before any `sid` token exists.** A linked watch keeps its tokens for up to 30 days. If production ever ran a build whose `JwtPayload` rejected unknown claims, every watch token would fail to decode: refresh would answer `INVALID_REFRESH_TOKEN`, the watch would treat itself as unlinked, and the only way back would be a new link from the phone. `ignoreUnknownKeys` already landed in `c606d8ca4` (2026-10-02); Task 1 pins it with a test and gives access tokens a `jti`. Before Task 6 is deployed, confirm with the developer that production runs a build containing `c606d8ca4`, and from then on never roll production back past it. A rollback to a build that predates Task 2 but contains `c606d8ca4` keeps watches working; such a build does not enforce `scp` or revocation, which is acceptable for a rollback window because device tokens live only on the watch.
2. **Device sessions before conditional create.** The watch uploads directly only when the descriptor advertises both `deviceLinkingV1` and `conditionalCreateV1` (master plan, contract 1). Shipping sessions first changes nothing for anyone, because no client redeems a code until a later client release reads the flag.
3. **Strict create is opt-in.** `If-None-Match: *` keeps exactly the semantics the Mac app relies on (see Scope Notes); the watch uses `?ifAbsent=true`. Old servers ignore the unknown query parameter, so the watch must check `conditionalCreateV1` first; Task 10's docs say so.
4. **Moments last, in their own collection.** Older clients never request `/moments`, so marked moments cannot break their content sync.

## Scope Notes

- **Already on main, not repeated here:**
  - Tolerant JWT decoding (`JwtTokenService.kt:38-40`, `tokenJson = Json { ignoreUnknownKeys = true }`) and a random `jti` on refresh tokens (`:105`), both from `c606d8ca4`. Task 1 adds the missing `jti` on access and sign-up session tokens.
  - `If-None-Match: *` on `PUT /contents/{id}` and `PUT /journals/{id}` with `412 CONTENT_EXISTS` / `412 JOURNAL_EXISTS` (`SyncCollectionRoutes.kt:91-97`, `:252-258`), exact PATCH version checks (`:620-624`), and the association batch restore (`RepoBackedLogDateCollectionsRepository.kt:283-306`), all from `8f25cd326`. That create-only check is a read followed by an upsert, so two concurrent creates can both answer 201; Task 10 moves it onto the atomic path from Task 9.
  - Deletion tombstones carry their server version in every feed (`cc90665c4`).
  - Device enrollment (`/device-enrollments`, Flyway V31) mints full-access sessions for a Mac approved by QR code. It is a different mechanism (full scope, not revocable per device) and stays untouched.
- **`If-None-Match: *` keeps recreating deleted records.** `SyncContentAndJournalLifecycleTest.conditional content creation preserves a newer edit and permits recreation after deletion` (and its journal twin) pins that a Mac retry may recreate a record deleted elsewhere. The spec's "410 for a deleted ID" therefore lives behind `?ifAbsent=true` instead of changing `If-None-Match`.
- **No `reserveIfAbsent` / `SELECT … FOR UPDATE`.** The master plan proposed reserving a metadata row. A reserved row would appear in the change feed before its record exists. The repo engine already has an atomic create (`DefaultRepoEngine.putRecordIfAbsent`, `shared/atproto-repo/.../RepoEngine.kt:217-226`, used by drafts at `RepoBackedLogDateCollectionsRepository.kt:385`): it retries on head conflicts and throws `InvalidSwapException` when the key is present, and `PostgreSQLRepoBlockStore.compareAndSwapHead` serializes writers. Task 9 builds on it and proves it against a real PostgreSQL in an opt-in test.
- **Single-use link codes use a guarded `UPDATE`, not `UPDATE … RETURNING`.** Inside one transaction the code row is read, then `UPDATE … WHERE redeemed_at IS NULL AND expires_at > now` must touch exactly one row before the session is inserted. Under PostgreSQL a concurrent second redeemer re-evaluates the `WHERE` after the first commits and updates nothing, which is the same guarantee as `RETURNING`; the H2 test database supports it, and `DeviceEnrollmentTable.markSessionIssued` already uses the pattern.
- **Link codes are stored only as SHA-256 hashes** of their 32 random bytes. A lost redeem response spends the code; the phone mints another (codes are cheap and live 120 s).
- **Diagnostics:** `/moments` and `/auth/devices` classify as `DiagnosticRoute.UNKNOWN` (`shared/model/.../diagnostics/DiagnosticRoute.kt:29-83`). Adding enum values would change the diagnostic report codec that older clients and servers decode, so this plan leaves it alone.
- **Not in this plan:** every client-side consumer (`CloudApiClient.createContentIfAbsent`, `SyncTokenRefresher`, `LinkedDeviceInstaller`, the watch router), a `createdAfter` change filter (B5), and migrating device enrollment onto device sessions.

---

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `server/src/main/kotlin/app/logdate/server/auth/JwtTokenService.kt` | 1, 2 | `jti` on every token; `sid`/`scp` claims; full vs device decoding. |
| `server/src/main/kotlin/app/logdate/server/auth/AccessTokenClaims.kt` | 2 | `TokenScope`, `AccessTokenClaims`. |
| `server/src/main/kotlin/app/logdate/server/auth/TokenService.kt` | 2 | Device token minting and claim decoding in the interface. |
| `server/src/main/kotlin/app/logdate/server/auth/InMemoryTokenService.kt` | 2 | Test token service learns device tokens. |
| `server/src/main/resources/db/migration/V34__Add_device_sessions.sql` | 3 | `device_sessions`, `device_link_codes`. |
| `server/src/main/kotlin/app/logdate/server/database/Tables.kt` | 3 | `DeviceSessionsTable`, `DeviceLinkCodesTable`. |
| `server/src/main/kotlin/app/logdate/server/devices/DeviceSessionRepository.kt` | 3 | Session and code models, repository interface, in-memory store. |
| `server/src/main/kotlin/app/logdate/server/database/PostgreSQLDeviceSessionRepository.kt` | 3 | PostgreSQL store. |
| `shared/model/src/commonMain/kotlin/app/logdate/shared/model/devices/DeviceLinkModels.kt` | 4 | Wire types and error codes for device linking. |
| `server/src/main/kotlin/app/logdate/server/devices/DeviceSessionGuard.kt` | 4 | Cached "is this session active" check. |
| `server/src/main/kotlin/app/logdate/server/auth/AccessTokenVerifier.kt` | 4 | One place that turns a bearer token into granted claims or a refusal. |
| `server/src/main/kotlin/app/logdate/server/devices/DeviceLinkService.kt` | 4 | Mint, redeem, list, revoke, refresh. |
| `server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt` | 5, 10, 12 | `extractSyncUserId`; scope refusal in `extractUserId`; conditional-create and record-state responses; moment metric names. |
| `server/src/main/kotlin/app/logdate/server/routes/SyncRoutes.kt` | 5, 12 | Verifier and moments wiring. |
| `server/src/main/kotlin/app/logdate/server/routes/sync/{SyncCollectionRoutes,SyncMediaRoutes,SyncStatusRoutes}.kt` | 5, 10 | Accept linked-device tokens; strict create; `HEAD`. |
| `server/src/main/kotlin/app/logdate/server/routes/DeviceRoutes.kt` | 6 | `/auth/devices` routes. |
| `server/src/main/kotlin/app/logdate/server/routes/docs/DeviceLinkDocs.kt` | 6 | Their documentation. |
| `server/src/main/kotlin/app/logdate/server/routes/AuthV1Routes.kt` | 6 | Device refresh, `/auth/me` keeps the session, 403 for linked devices. |
| `server/src/main/kotlin/app/logdate/server/{di/ServerModule.kt,routes/ServerRouting.kt,ServerDescriptorConfig.kt}` | 6, 10, 12 | Wiring and descriptor flags. |
| `shared/model/src/commonMain/kotlin/app/logdate/shared/model/ServerDescriptor.kt` | 6, 10, 12 | Flag constants. |
| `client/networking/src/commonMain/kotlin/app/logdate/client/networking/DeviceLinkApiClient.kt` | 7 | Client for `/auth/devices`. |
| `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/WatchDeviceLinkE2ETest.kt` | 8 | Mint, redeem, upload, revoke, 401. |
| `server/src/main/kotlin/app/logdate/server/logdate/ConditionalCreate.kt` | 9 | `ConditionalCreate`, `LogDateRecordState`, `AssociationCreateResults`. |
| `server/src/main/kotlin/app/logdate/server/logdate/RepoConditionalWriter.kt` | 9 | Atomic create on the repo engine. |
| `server/src/main/kotlin/app/logdate/server/logdate/{LogDateCollectionsMetadataStore,LogDateCollectionsRepository,RepoBackedLogDateCollectionsRepository}.kt`, `database/PostgreSQLLogDateCollectionsMetadataStore.kt` | 9, 11 | Tombstone reads, record state, create-if-absent. |
| `shared/model/src/commonMain/kotlin/app/logdate/shared/model/sync/SyncModels.kt` | 10, 12 | Association skip lists; moment wire types. |
| `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionRecords.kt` | 11 | `MOMENT` kind and its record JSON. |
| `server/src/main/kotlin/app/logdate/server/logdate/{LogDateMomentsRepository,RepoBackedLogDateMomentsRepository}.kt` | 11 | Moment storage. |
| `server/src/main/kotlin/app/logdate/server/routes/sync/SyncMomentRoutes.kt`, `routes/docs/SyncMomentDocs.kt` | 12 | `/moments` routes and docs. |
| `server/src/main/kotlin/app/logdate/server/openapi/{ApiTags.kt,schemadocs/*}` | 6, 10, 12 | Tags and schema prose. |
| `server/src/main/kotlin/app/logdate/server/sync/SyncMetricsRegistry.kt` | 12 | Moment operation names. |
| Tests under `server/src/test/kotlin/app/logdate/server/...` | all | Named in each task. |

---

### Task 1: Give every token its own ID

**Files:**
- Modify: `server/src/main/kotlin/app/logdate/server/auth/JwtTokenService.kt:27`, `:74-83`, `:88`, `:117-126`, `:140-148`
- Test: `server/src/test/kotlin/app/logdate/server/auth/JwtTokenServiceTest.kt` (insert after line 127)

**Interfaces:**
- Consumes: nothing new.
- Produces: every JWT `JwtTokenService` mints carries a non-blank `jti`. Access and sign-up session tokens get `Uuid.random()`; a device-enrollment access token gets the fixed `device-enrollment-access:<enrollmentId>` so `generateEnrollmentTokens` stays reproducible (`JwtTokenServiceTest.kt:36-46`). Signatures are unchanged.

- [ ] **Step 1: Write the failing tests**

In `JwtTokenServiceTest.kt`, insert after line 127 (the closing brace of `refresh tokens minted together for one account stay distinct`):

```kotlin

    @Test
    fun `every token carries its own jti`() {
        val service = JwtTokenService(secret = hmacKey, issuer = issuer, audience = audience)
        val enrollment = service.generateEnrollmentTokens("acc-jti", null, "request-jti", Clock.System.now())
        val tokens =
            listOf(
                service.generateAccessToken("acc-jti"),
                service.generateRefreshToken("acc-jti"),
                service.generateSessionToken("session-jti"),
                enrollment.accessToken,
                enrollment.refreshToken,
            )

        val ids = tokens.map { payloadOf(it)["jti"]?.jsonPrimitive?.content }

        assertTrue(ids.none { it.isNullOrBlank() }, "every token needs a jti: $ids")
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `access tokens minted together for one account stay distinct`() {
        val service = JwtTokenService(secret = hmacKey, issuer = issuer, audience = audience)

        val first = service.generateAccessToken("acc-same-second", "did:plc:alice123")
        val second = service.generateAccessToken("acc-same-second", "did:plc:alice123")

        assertNotEquals(first, second)
        assertEquals("acc-same-second", service.validateAccessToken(first))
        assertEquals("acc-same-second", service.validateAccessToken(second))
    }

    @Test
    fun `claims this build does not know are ignored once the signature verifies`() {
        val now = Clock.System.now().epochSeconds
        val payload =
            "{\"sub\":\"acc-later\",\"iss\":\"$issuer\",\"aud\":\"$audience\",\"exp\":${now + 600},\"iat\":$now," +
                "\"type\":\"access\",\"jti\":\"later-1\",\"x-later\":{\"nested\":true},\"cnf\":[1,2]}"
        val token = buildToken(hmacKey, "{\"alg\":\"HS256\",\"typ\":\"JWT\"}", payload)

        assertEquals("acc-later", JwtTokenService(secret = hmacKey, issuer = issuer, audience = audience).validateAccessToken(token))
        assertNull(JwtTokenService(secret = "another-secret", issuer = issuer, audience = audience).validateAccessToken(token))
    }
```

The file already imports `assertNotEquals`, `assertNull`, `assertTrue`, `jsonPrimitive` and `Clock`.

- [ ] **Step 2: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.auth.JwtTokenServiceTest' --console=plain`
Expected: FAIL. `every token carries its own jti` fails with `every token needs a jti: [null, …, null, device-enrollment:request-jti]`; `access tokens minted together for one account stay distinct` fails in `assertNotEquals` (both tokens are minted in the same second). `claims this build does not know are ignored once the signature verifies` PASSES: it pins the tolerant decoding from `c606d8ca4`. Every other test passes.

- [ ] **Step 3: Add the `jti` claims**

In `JwtTokenService.kt`:

Replace line 27:

```kotlin
class JwtTokenService(
```

with:

```kotlin
@OptIn(ExperimentalUuidApi::class)
class JwtTokenService(
```

In `generateAccessToken` (lines 74-83), replace:

```kotlin
                type = "access",
                did = did,
            )

        return generateToken(payload)
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun generateRefreshToken(
```

with:

```kotlin
                type = "access",
                did = did,
                jti = Uuid.random().toString(),
            )

        return generateToken(payload)
    }

    override fun generateRefreshToken(
```

In `generateEnrollmentTokens` (lines 117-126), replace:

```kotlin
                exp = (issuedAt + ACCESS_TOKEN_DURATION).epochSeconds,
                iat = issuedAt.epochSeconds,
                type = "access",
                did = did,
            )
```

with:

```kotlin
                exp = (issuedAt + ACCESS_TOKEN_DURATION).epochSeconds,
                iat = issuedAt.epochSeconds,
                type = "access",
                did = did,
                jti = "device-enrollment-access:$enrollmentId",
            )
```

In `generateSessionToken` (lines 140-148), replace:

```kotlin
                iat = now.epochSeconds,
                type = "session",
            )
```

with:

```kotlin
                iat = now.epochSeconds,
                type = "session",
                jti = Uuid.random().toString(),
            )
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.auth.*' --console=plain`
Expected: PASS, including `enrollment session is reproducible across issuer restarts and separately revocable` and `DeviceEnrollmentSessionRoutesTest`'s dependency on reproducible enrollment tokens.

- [ ] **Step 5: Format and lint**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:ktlintFormat :server:ktlintCheck --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-token-jti.txt <<'MSG'
chore(server): give every token its own ID

Access tokens and sign-up session tokens now carry a random jti claim,
as refresh tokens already did, so two tokens minted for one account in
the same second are distinct. A device enrollment keeps a fixed ID for
its access token, so a lost reply still replays the same session.

Linked watches will hold tokens with session and scope claims. The
server already ignores claims it does not know once the signature
checks out; a test now pins that, because a build without it would
sign every linked watch out after a rollback.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add server/src/main/kotlin/app/logdate/server/auth/JwtTokenService.kt server/src/test/kotlin/app/logdate/server/auth/JwtTokenServiceTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-token-jti.txt
```

Expected: hooks pass; `git show --stat HEAD` lists exactly the two files.

---

### Task 2: Carry a linked device's session and scope in tokens

**Files:**
- Create: `server/src/main/kotlin/app/logdate/server/auth/AccessTokenClaims.kt`
- Replace: `server/src/main/kotlin/app/logdate/server/auth/TokenService.kt`
- Replace: `server/src/main/kotlin/app/logdate/server/auth/JwtTokenService.kt`
- Replace: `server/src/main/kotlin/app/logdate/server/auth/InMemoryTokenService.kt`
- Modify: `server/src/test/kotlin/app/logdate/server/auth/TokenServiceDefaultMethodsTest.kt:77` (insert before)
- Modify: `server/src/test/kotlin/app/logdate/server/auth/JwtTokenServiceTest.kt` (`jwt internal header and payload getters are accessible`, lines 191-206 at `17490e104`, about 46 lines lower after Task 1) and append tests
- Modify: `server/src/test/kotlin/app/logdate/server/auth/InMemoryAuthComponentsTest.kt` (append a test before the final `}`)

**Interfaces:**
- Consumes: Task 1's `jti`.
- Produces:
  - `enum class TokenScope(val claim: String?) { FULL(null), SYNC("sync") }` with `fun fromClaim(claim: String?): TokenScope?`.
  - `data class AccessTokenClaims(val accountId: String, val did: String?, val sessionId: String?, val scope: TokenScope)` with `AccessTokenClaims.full(accountId, did = null)`.
  - `TokenService.generateDeviceAccessToken(accountId: String, did: String?, sessionId: String): String` and `generateDeviceRefreshToken(...)` (abstract).
  - `TokenService.decodeAccessToken(token: String): AccessTokenClaims?` and `decodeRefreshToken(token: String): AccessTokenClaims?` (defaults wrap the validate methods).
  - Behavior: `validateAccessToken` / `validateRefreshToken` return null for device tokens, so every existing caller (auth, identity, OAuth, XRPC, quota, transcription, enrollment, key envelopes, sync) refuses them until Task 5 opts the sync routes in. A token whose `scp` is unknown, or whose `sid` and `scp` do not come together, decodes to null.

- [ ] **Step 1: Write the failing tests**

In `JwtTokenServiceTest.kt`, inside `jwt internal header and payload getters are accessible`, replace everything from `val payloadClass = Class.forName("app.logdate.server.auth.JwtTokenService\$JwtPayload")` through `assertEquals("did:plc:alice123", payloadClass.getMethod("getDid").invoke(payload))` with:

```kotlin
        val payloadClass = Class.forName("app.logdate.server.auth.JwtTokenService\$JwtPayload")
        val payloadConstructor =
            payloadClass.getDeclaredConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                Long::class.java,
                Long::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
            )
        payloadConstructor.isAccessible = true
        val payload =
            payloadConstructor.newInstance("acc-1", issuer, audience, 1000L, 900L, "access", "did:plc:alice123", null, "session-1", "sync")
        assertEquals(900L, payloadClass.getMethod("getIat").invoke(payload))
        assertEquals("did:plc:alice123", payloadClass.getMethod("getDid").invoke(payload))
        assertEquals("session-1", payloadClass.getMethod("getSid").invoke(payload))
        assertEquals("sync", payloadClass.getMethod("getScp").invoke(payload))
```

Then insert after the `claims this build does not know are ignored once the signature verifies` test from Task 1:

```kotlin

    @Test
    fun `linked device tokens carry their session and sync scope`() {
        val service = JwtTokenService(secret = hmacKey, issuer = issuer, audience = audience)

        val access = service.generateDeviceAccessToken("acc-watch", "did:plc:alice123", "session-1")
        val refresh = service.generateDeviceRefreshToken("acc-watch", "did:plc:alice123", "session-1")

        val expected = AccessTokenClaims("acc-watch", "did:plc:alice123", "session-1", TokenScope.SYNC)
        assertEquals(expected, service.decodeAccessToken(access))
        assertEquals(expected, service.decodeRefreshToken(refresh))
        assertEquals("session-1", payloadOf(access)["sid"]?.jsonPrimitive?.content)
        assertEquals("sync", payloadOf(access)["scp"]?.jsonPrimitive?.content)
        assertNull(service.decodeAccessToken(refresh))
        assertNull(service.decodeRefreshToken(access))
    }

    @Test
    fun `full access checks refuse linked device tokens`() {
        val service = JwtTokenService(secret = hmacKey, issuer = issuer, audience = audience)

        assertNull(service.validateAccessToken(service.generateDeviceAccessToken("acc-watch", null, "session-1")))
        assertNull(service.validateRefreshToken(service.generateDeviceRefreshToken("acc-watch", null, "session-1")))
        assertEquals(
            AccessTokenClaims.full("acc-phone", "did:plc:alice123"),
            service.decodeAccessToken(service.generateAccessToken("acc-phone", "did:plc:alice123")),
        )
        assertEquals(AccessTokenClaims.full("acc-phone"), service.decodeRefreshToken(service.generateRefreshToken("acc-phone")))
    }

    @Test
    fun `unknown scopes and half formed device claims are refused`() {
        val service = JwtTokenService(secret = hmacKey, issuer = issuer, audience = audience)
        val now = Clock.System.now().epochSeconds
        val header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"

        val claims = "{\"sub\":\"acc-x\",\"iss\":\"$issuer\",\"aud\":\"$audience\",\"exp\":${now + 600},\"iat\":$now"

        listOf(",\"scp\":\"admin\",\"sid\":\"s\"", ",\"scp\":\"sync\"", ",\"sid\":\"s\"").forEach { extra ->
            val forged = buildToken(hmacKey, header, "$claims,\"type\":\"access\"$extra}")
            assertNull(service.validateAccessToken(forged), extra)
            assertNull(service.decodeAccessToken(forged), extra)
        }
    }
```

In `InMemoryAuthComponentsTest.kt`, insert before the class's final closing brace:

```kotlin

    @Test
    fun `in-memory device tokens decode with their session and never pass full access checks`() {
        val service = InMemoryTokenService()

        val access = service.generateDeviceAccessToken("acc-1", null, "session-1")
        val refresh = service.generateDeviceRefreshToken("acc-1", null, "session-1")

        assertNull(service.validateAccessToken(access))
        assertNull(service.validateRefreshToken(refresh))
        assertEquals(AccessTokenClaims("acc-1", null, "session-1", TokenScope.SYNC), service.decodeAccessToken(access))
        assertEquals("session-1", service.decodeRefreshToken(refresh)?.sessionId)
        assertEquals(AccessTokenClaims.full("acc-1"), service.decodeAccessToken(service.generateAccessToken("acc-1")))
    }
```

In `TokenServiceDefaultMethodsTest.kt`, insert before line 77 (`override fun generateSessionToken(sessionId: String): String = sessionId`):

```kotlin
        override fun generateDeviceAccessToken(
            accountId: String,
            did: String?,
            sessionId: String,
        ): String = error("Device sessions are unused in this fixture")

        override fun generateDeviceRefreshToken(
            accountId: String,
            did: String?,
            sessionId: String,
        ): String = error("Device sessions are unused in this fixture")

```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.auth.*' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'AccessTokenClaims'`, `'TokenScope'`, `'generateDeviceAccessToken'`, `'decodeAccessToken'`, and in `TokenServiceDefaultMethodsTest` `'generateDeviceAccessToken' overrides nothing`.

- [ ] **Step 3: Create `AccessTokenClaims.kt`**

```kotlin
package app.logdate.server.auth

/**
 * Which routes a token may call.
 *
 * [FULL] tokens come from signing in. [SYNC] tokens belong to a linked device session and reach only the
 * sync data routes. [claim] is the JWT `scp` value; full tokens carry none.
 */
enum class TokenScope(
    val claim: String?,
) {
    FULL(null),
    SYNC("sync"),
    ;

    companion object {
        /** The scope an `scp` claim names, or null for a scope this build does not know. */
        fun fromClaim(claim: String?): TokenScope? = entries.firstOrNull { it.claim == claim }
    }
}

/** What a verified access or refresh token says about its holder. */
data class AccessTokenClaims(
    val accountId: String,
    val did: String?,
    val sessionId: String?,
    val scope: TokenScope,
) {
    companion object {
        fun full(
            accountId: String,
            did: String? = null,
        ): AccessTokenClaims = AccessTokenClaims(accountId, did, sessionId = null, scope = TokenScope.FULL)
    }
}
```

- [ ] **Step 4: Replace `TokenService.kt`**

```kotlin
package app.logdate.server.auth

import app.logdate.shared.model.AccountTokens
import kotlin.time.Instant

/**
 * Service for generating and validating JWT tokens for LogDate Cloud authentication.
 *
 * This interface abstracts token operations to allow production JWT and in-memory
 * test implementations.
 */
interface TokenService {
    /**
     * Generate a new access token for the given account ID.
     * Access tokens are short-lived (1 hour) and used for API authentication.
     */
    fun generateAccessToken(
        accountId: String,
        did: String? = null,
    ): String

    /**
     * Generate a new refresh token for the given account ID.
     * Refresh tokens are long-lived (30 days) and used to obtain new access tokens.
     */
    fun generateRefreshToken(
        accountId: String,
        did: String? = null,
    ): String

    /** Reproduce a pending enrollment's session after a lost response or server restart. */
    fun generateEnrollmentTokens(
        accountId: String,
        did: String?,
        enrollmentId: String,
        issuedAt: Instant,
    ): AccountTokens

    /** A 1-hour access token for linked device session [sessionId]; it carries `sid` and `scp=sync`. */
    fun generateDeviceAccessToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String

    /** A 30-day refresh token for linked device session [sessionId]; it carries `sid` and `scp=sync`. */
    fun generateDeviceRefreshToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String

    /**
     * Validate a full-access token and extract the account ID.
     * Returns null if the token is invalid or expired, and for every linked device token.
     */
    fun validateAccessToken(token: String): String?

    /**
     * Validate a full-access refresh token and extract the account ID.
     * Returns null if the token is invalid or expired, and for every linked device token.
     */
    fun validateRefreshToken(token: String): String?

    /** Every claim of a valid access token, full or linked device; null when invalid or expired. */
    fun decodeAccessToken(token: String): AccessTokenClaims? = validateAccessToken(token)?.let { AccessTokenClaims.full(it) }

    /** Every claim of a valid refresh token, full or linked device; null when invalid or expired. */
    fun decodeRefreshToken(token: String): AccessTokenClaims? = validateRefreshToken(token)?.let { AccessTokenClaims.full(it) }

    /**
     * Generate a temporary session token for account creation flow.
     * These are very short-lived (15 minutes) and used during passkey registration.
     */
    fun generateSessionToken(sessionId: String): String

    /**
     * Validate a session token and extract the session ID.
     * Returns null if the token is invalid or expired.
     */
    fun validateSessionToken(token: String): String?
}
```

- [ ] **Step 5: Replace `JwtTokenService.kt`**

```kotlin
package app.logdate.server.auth

import app.logdate.shared.model.AccountTokens
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Production JWT token service for LogDate Cloud authentication.
 *
 * This implementation generates and validates JWT tokens using HMAC-SHA256.
 * Tokens are structured as standard JWT with header, payload, and signature.
 */
@OptIn(ExperimentalUuidApi::class)
class JwtTokenService(
    private val secret: String = generateSecret(),
    private val issuer: String = "logdate.app",
    private val audience: String = "logdate-api",
) : TokenService {
    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private val ACCESS_TOKEN_DURATION = 1.hours
        private val REFRESH_TOKEN_DURATION = 30.days
        private val SESSION_TOKEN_DURATION = 15.minutes

        // Claims are read only after the signature verifies, so tolerating claims this build does not know
        // lets a later release add one without invalidating tokens during a rollback.
        private val tokenJson = Json { ignoreUnknownKeys = true }

        fun generateSecret(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getEncoder().encodeToString(bytes)
        }
    }

    @Serializable
    private data class JwtHeader(
        val alg: String = "HS256",
        val typ: String = "JWT",
    )

    @Serializable
    private data class JwtPayload(
        val sub: String, // subject (account ID or session ID)
        val iss: String, // issuer
        val aud: String, // audience
        val exp: Long, // expiration time (seconds since epoch)
        val iat: Long, // issued at (seconds since epoch)
        val type: String, // token type: "access", "refresh", "session"
        val did: String? = null, // AT Protocol DID for user-bound tokens
        val jti: String? = null, // unique ID, so two tokens minted in the same second stay separately revocable
        val sid: String? = null, // linked device session; only on device tokens
        val scp: String? = null, // scope; "sync" on device tokens, absent on full-access tokens
    )

    override fun generateAccessToken(
        accountId: String,
        did: String?,
    ): String = generateToken(payload(accountId, did, "access", ACCESS_TOKEN_DURATION))

    override fun generateRefreshToken(
        accountId: String,
        did: String?,
    ): String = generateToken(payload(accountId, did, "refresh", REFRESH_TOKEN_DURATION))

    override fun generateDeviceAccessToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String =
        generateToken(
            payload(accountId, did, "access", ACCESS_TOKEN_DURATION).copy(sid = sessionId, scp = TokenScope.SYNC.claim),
        )

    override fun generateDeviceRefreshToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String =
        generateToken(
            payload(accountId, did, "refresh", REFRESH_TOKEN_DURATION).copy(sid = sessionId, scp = TokenScope.SYNC.claim),
        )

    override fun generateEnrollmentTokens(
        accountId: String,
        did: String?,
        enrollmentId: String,
        issuedAt: Instant,
    ): AccountTokens {
        val access =
            payload(accountId, did, "access", ACCESS_TOKEN_DURATION, issuedAt, jti = "device-enrollment-access:$enrollmentId")
        val refresh = payload(accountId, did, "refresh", REFRESH_TOKEN_DURATION, issuedAt, jti = "device-enrollment:$enrollmentId")
        return AccountTokens(generateToken(access), generateToken(refresh))
    }

    override fun generateSessionToken(sessionId: String): String =
        generateToken(payload(sessionId, null, "session", SESSION_TOKEN_DURATION))

    override fun validateAccessToken(token: String): String? =
        decode(token, "access")?.takeIf { it.scope == TokenScope.FULL }?.accountId

    override fun validateRefreshToken(token: String): String? =
        decode(token, "refresh")?.takeIf { it.scope == TokenScope.FULL }?.accountId

    override fun decodeAccessToken(token: String): AccessTokenClaims? = decode(token, "access")

    override fun decodeRefreshToken(token: String): AccessTokenClaims? = decode(token, "refresh")

    override fun validateSessionToken(token: String): String? = verifiedPayload(token, "session")?.sub

    private fun payload(
        subject: String,
        did: String?,
        type: String,
        lifetime: Duration,
        issuedAt: Instant = Clock.System.now(),
        jti: String = Uuid.random().toString(),
    ): JwtPayload =
        JwtPayload(
            sub = subject,
            iss = issuer,
            aud = audience,
            exp = (issuedAt + lifetime).epochSeconds,
            iat = issuedAt.epochSeconds,
            type = type,
            did = did,
            jti = jti,
        )

    private fun decode(
        token: String,
        expectedType: String,
    ): AccessTokenClaims? {
        val payload = verifiedPayload(token, expectedType) ?: return null
        val scope = TokenScope.fromClaim(payload.scp) ?: return null
        if ((scope == TokenScope.SYNC) != (payload.sid != null)) return null
        return AccessTokenClaims(payload.sub, payload.did, payload.sid, scope)
    }

    private fun generateToken(payload: JwtPayload): String {
        try {
            val header = JwtHeader()

            // Encode header and payload
            val encodedHeader = base64UrlEncode(Json.encodeToString(header))
            val encodedPayload = base64UrlEncode(Json.encodeToString(payload))

            // Create signature
            val message = "$encodedHeader.$encodedPayload"
            val signature = createSignature(message)

            return "$message.$signature"
        } catch (e: Exception) {
            Napier.e("Failed to generate JWT token")
            throw IllegalStateException("Failed to generate token", e)
        }
    }

    private fun verifiedPayload(
        token: String,
        expectedType: String,
    ): JwtPayload? {
        try {
            val parts = token.split(".")
            if (parts.size != 3) {
                Napier.w("Invalid JWT format")
                return null
            }

            val (encodedHeader, encodedPayload, providedSignature) = parts

            // Verify signature
            val message = "$encodedHeader.$encodedPayload"
            val expectedSignature = createSignature(message)

            if (providedSignature != expectedSignature) {
                Napier.w("JWT signature verification failed")
                return null
            }

            // Decode and validate payload
            val payloadJson = base64UrlDecode(encodedPayload)
            val payload = tokenJson.decodeFromString<JwtPayload>(payloadJson)

            // Validate token type
            if (payload.type != expectedType) {
                Napier.w("JWT type mismatch")
                return null
            }

            // Validate issuer and audience
            if (payload.iss != issuer || payload.aud != audience) {
                Napier.w("JWT issuer/audience validation failed")
                return null
            }

            // Check expiration
            val now = Clock.System.now().epochSeconds
            if (payload.exp <= now) {
                Napier.d("JWT token expired")
                return null
            }

            return payload
        } catch (_: Exception) {
            Napier.e("Failed to validate JWT token")
            return null
        }
    }

    private fun createSignature(message: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM)
        mac.init(secretKey)
        val signature = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return base64UrlEncode(signature)
    }

    private fun base64UrlEncode(data: String): String = base64UrlEncode(data.toByteArray(StandardCharsets.UTF_8))

    private fun base64UrlEncode(data: ByteArray): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(data)

    private fun base64UrlDecode(data: String): String {
        val decoded = Base64.getUrlDecoder().decode(data)
        return String(decoded, StandardCharsets.UTF_8)
    }
}
```

- [ ] **Step 6: Replace `InMemoryTokenService.kt`**

```kotlin
package app.logdate.server.auth

import app.logdate.shared.model.AccountTokens
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Deterministic [TokenService] for local tests and in-memory server fixtures.
 */
class InMemoryTokenService(
    private val issuer: String = "logdate.app",
) : TokenService {
    // Token expiration times
    private val accessTokenExpiration = 1.hours
    private val refreshTokenExpiration = 720.hours // 30 days
    private val sessionTokenExpiration = 15.minutes

    // The readable test format has no room for a session or scope, so device tokens are looked up here.
    private val deviceTokens = ConcurrentHashMap<String, AccessTokenClaims>()

    override fun generateAccessToken(
        accountId: String,
        did: String?,
    ): String {
        val now = Clock.System.now()
        val expiresAt = now + accessTokenExpiration

        // Simple token format: type.accountId.expiresAt.hash
        val tokenData = "access.$accountId.${expiresAt.epochSeconds}"
        return "memory_${tokenData.hashCode().toString(16)}_$tokenData"
    }

    override fun generateRefreshToken(
        accountId: String,
        did: String?,
    ): String {
        val now = Clock.System.now()
        val expiresAt = now + refreshTokenExpiration

        val tokenData = "refresh.$accountId.${expiresAt.epochSeconds}"
        return "memory_${tokenData.hashCode().toString(16)}_$tokenData"
    }

    override fun generateEnrollmentTokens(
        accountId: String,
        did: String?,
        enrollmentId: String,
        issuedAt: Instant,
    ): AccountTokens {
        fun token(
            type: String,
            expiry: Instant,
        ): String {
            val data = "$type.$accountId.${expiry.epochSeconds}"
            return "memory_${("$enrollmentId:$data").hashCode().toString(16)}_$data"
        }
        return AccountTokens(
            token("access", issuedAt + accessTokenExpiration),
            token("refresh", issuedAt + refreshTokenExpiration),
        )
    }

    override fun generateDeviceAccessToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String = deviceToken("access", accountId, did, sessionId, accessTokenExpiration)

    override fun generateDeviceRefreshToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String = deviceToken("refresh", accountId, did, sessionId, refreshTokenExpiration)

    override fun generateSessionToken(sessionId: String): String {
        val now = Clock.System.now()
        val expiresAt = now + sessionTokenExpiration

        val tokenData = "session.$sessionId.${expiresAt.epochSeconds}"
        return "memory_${tokenData.hashCode().toString(16)}_$tokenData"
    }

    override fun validateAccessToken(token: String): String? = validateToken(token, "access")?.takeUnless { token in deviceTokens }

    override fun validateRefreshToken(token: String): String? = validateToken(token, "refresh")?.takeUnless { token in deviceTokens }

    override fun decodeAccessToken(token: String): AccessTokenClaims? = decode(token, "access")

    override fun decodeRefreshToken(token: String): AccessTokenClaims? = decode(token, "refresh")

    override fun validateSessionToken(token: String): String? =
        validateToken(token, "session")?.let {
            // For session tokens, return the session ID instead of account ID.
            token.substringAfter("session.").substringBefore(".")
        }

    private fun decode(
        token: String,
        type: String,
    ): AccessTokenClaims? {
        val accountId = validateToken(token, type) ?: return null
        return deviceTokens[token] ?: AccessTokenClaims.full(accountId)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun deviceToken(
        type: String,
        accountId: String,
        did: String?,
        sessionId: String,
        lifetime: Duration,
    ): String {
        val expiresAt = Clock.System.now() + lifetime
        val token = "memory_${Uuid.random().toHexString()}_$type.$accountId.${expiresAt.epochSeconds}"
        deviceTokens[token] = AccessTokenClaims(accountId, did, sessionId, TokenScope.SYNC)
        return token
    }

    private fun validateToken(
        token: String,
        expectedType: String,
    ): String? {
        if (!token.startsWith("memory_")) return null

        val tokenData = token.substringAfter("memory_").substringAfter("_")
        val parts = tokenData.split(".")

        if (parts.size != 3) return null

        val type = parts[0]
        val accountId = parts[1]
        val expiresAtSeconds = parts[2].toLongOrNull() ?: return null

        if (type != expectedType) return null

        val now = Clock.System.now()
        val expiresAt = Instant.fromEpochSeconds(expiresAtSeconds)
        if (now > expiresAt) return null

        return accountId
    }
}
```

- [ ] **Step 7: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.auth.*' --tests 'app.logdate.server.routes.DeviceEnrollmentSessionRoutesTest' --tests 'app.logdate.server.routes.auth.*' --console=plain`
Expected: PASS. No route mints a device token yet, so every route behaves as before.

- [ ] **Step 8: Run the whole server suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Format and lint**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:ktlintFormat :server:ktlintCheck :server:detekt --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-token-claims.txt <<'MSG'
chore(server): carry a linked device session and scope in tokens

A watch set up from the phone will get tokens that name its own
session (sid) and a sync-only scope (scp=sync), so the account can
remove that one device and so the watch can never change account
settings.

Full-access checks refuse these tokens, which keeps every existing
route closed to them until a route opts in. A scope this build does
not know, or a session without a scope, is refused as well.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add server/src/main/kotlin/app/logdate/server/auth/AccessTokenClaims.kt server/src/main/kotlin/app/logdate/server/auth/TokenService.kt server/src/main/kotlin/app/logdate/server/auth/JwtTokenService.kt server/src/main/kotlin/app/logdate/server/auth/InMemoryTokenService.kt server/src/test/kotlin/app/logdate/server/auth/TokenServiceDefaultMethodsTest.kt server/src/test/kotlin/app/logdate/server/auth/JwtTokenServiceTest.kt server/src/test/kotlin/app/logdate/server/auth/InMemoryAuthComponentsTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-token-claims.txt
```

Expected: hooks pass; seven files in the commit.

---

### Task 3: Store linked device sessions and link codes

**Files:**
- Create: `server/src/main/resources/db/migration/V34__Add_device_sessions.sql`
- Modify: `server/src/main/kotlin/app/logdate/server/database/Tables.kt:1-6` (imports) and append after the last line
- Create: `server/src/main/kotlin/app/logdate/server/devices/DeviceSessionRepository.kt`
- Create: `server/src/main/kotlin/app/logdate/server/database/PostgreSQLDeviceSessionRepository.kt`
- Test: `server/src/test/kotlin/app/logdate/server/database/PostgreSQLDeviceSessionRepositoryTest.kt`
- Test: `server/src/test/kotlin/app/logdate/server/database/DeviceLinkCodePostgresTest.kt`

**Interfaces:**
- Consumes: `AccountsTable` (`Tables.kt:14`), `withH2Database` (`server/src/test/.../database/support`), `DatabaseConfig.createDataSource` / `initializeDatabase`.
- Produces (package `app.logdate.server.devices`):
  - `data class DeviceSession(id: UUID, accountId: UUID, deviceName: String, platform: String, createdAt: Long, lastRefreshedAt: Long, expiresAt: Long, revokedAt: Long? = null)` with `isActive(now: Long): Boolean`.
  - `data class DeviceLinkCode(codeHash: String, accountId: UUID, createdAt: Long, expiresAt: Long)`, `data class NewDeviceSession(id: UUID, deviceName: String, platform: String, createdAt: Long, expiresAt: Long)`, `data class DeviceSessionLimits(maxActiveSessions: Int, maxCodesPerHour: Int)`.
  - `enum class LinkCodeCreation { CREATED, DEVICE_LIMIT_REACHED, RATE_LIMITED }`; `sealed interface LinkCodeRedemption { Redeemed(session), Invalid, DeviceLimitReached }`.
  - `interface DeviceSessionRepository { createLinkCode(code, limits): LinkCodeCreation; redeemLinkCode(codeHash, session, limits): LinkCodeRedemption; find(sessionId): DeviceSession?; listActive(accountId, now): List<DeviceSession>; revoke(accountId, sessionId, now): Boolean; extend(sessionId, expiresAt, now): Boolean }` (all `suspend`).
  - `InMemoryDeviceSessionRepository`, `app.logdate.server.database.PostgreSQLDeviceSessionRepository`, `DeviceSessionsTable`, `DeviceLinkCodesTable`, `internal const val LINK_CODE_RATE_WINDOW_MS = 3_600_000`.

- [ ] **Step 1: Confirm the next free migration number**

Run: `ls server/src/main/resources/db/migration | sort -V | tail -3`
Expected: `V32__Add_account_key_vault.sql`, `V33__Add_encrypted_account_key_envelopes.sql`, `beforeMigrate.sql`. If a `V34__…` already exists on the branch, use the next free number in every path and command of this task.

- [ ] **Step 2: Write the failing repository tests**

Create `server/src/test/kotlin/app/logdate/server/database/PostgreSQLDeviceSessionRepositoryTest.kt`:

```kotlin
package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.devices.DeviceLinkCode
import app.logdate.server.devices.DeviceSession
import app.logdate.server.devices.DeviceSessionLimits
import app.logdate.server.devices.DeviceSessionRepository
import app.logdate.server.devices.InMemoryDeviceSessionRepository
import app.logdate.server.devices.LINK_CODE_RATE_WINDOW_MS
import app.logdate.server.devices.LinkCodeCreation
import app.logdate.server.devices.LinkCodeRedemption
import app.logdate.server.devices.NewDeviceSession
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Runs one contract against the PostgreSQL store (on H2) and the in-memory store. */
@OptIn(ExperimentalUuidApi::class)
class PostgreSQLDeviceSessionRepositoryTest {
    private val now = 1_800_000_000_000L
    private val roomy = DeviceSessionLimits(maxActiveSessions = 5, maxCodesPerHour = 10)

    @Test
    fun `both stores spend each link code once and keep sessions per account`() =
        withDeviceTables { owner, other ->
            assertLinkCodesSpendOnce(PostgreSQLDeviceSessionRepository(), owner, other)
            assertLinkCodesSpendOnce(InMemoryDeviceSessionRepository(), owner, other)
        }

    @Test
    fun `both stores enforce the device and hourly code limits`() =
        withDeviceTables { owner, _ ->
            assertLimits(PostgreSQLDeviceSessionRepository(), owner)
            assertLimits(InMemoryDeviceSessionRepository(), owner)
        }

    @Test
    fun `both stores revoke and extend only the right sessions`() =
        withDeviceTables { owner, other ->
            assertRevokeAndExtend(PostgreSQLDeviceSessionRepository(), owner, other)
            assertRevokeAndExtend(InMemoryDeviceSessionRepository(), owner, other)
        }

    private suspend fun assertLinkCodesSpendOnce(
        repository: DeviceSessionRepository,
        owner: UUID,
        other: UUID,
    ) {
        val hash = randomHash()
        assertEquals(LinkCodeCreation.CREATED, repository.createLinkCode(DeviceLinkCode(hash, owner, now, now + 120_000), roomy))

        val redeemed = repository.redeemLinkCode(hash, newSession("Pixel Watch 3", now + 1_000), roomy)
        val session = assertIs<LinkCodeRedemption.Redeemed>(redeemed).session

        assertEquals(owner, session.accountId)
        assertEquals("Pixel Watch 3", session.deviceName)
        assertEquals(now + 1_000, session.lastRefreshedAt)
        assertEquals(LinkCodeRedemption.Invalid, repository.redeemLinkCode(hash, newSession("Second watch", now + 2_000), roomy))
        assertEquals(LinkCodeRedemption.Invalid, repository.redeemLinkCode(randomHash(), newSession("Unknown", now + 2_000), roomy))

        val expired = randomHash()
        repository.createLinkCode(DeviceLinkCode(expired, owner, now, now + 120_000), roomy)
        assertEquals(LinkCodeRedemption.Invalid, repository.redeemLinkCode(expired, newSession("Late watch", now + 120_000), roomy))

        assertEquals(session, repository.find(session.id))
        assertEquals(listOf(session), repository.listActive(owner, now + 3_000))
        assertTrue(repository.listActive(other, now + 3_000).isEmpty())
    }

    private suspend fun assertLimits(
        repository: DeviceSessionRepository,
        owner: UUID,
    ) {
        val oneDevice = DeviceSessionLimits(maxActiveSessions = 1, maxCodesPerHour = 3)
        val first = randomHash()
        val pending = randomHash()
        repository.createLinkCode(DeviceLinkCode(first, owner, now, now + 120_000), oneDevice)
        assertEquals(LinkCodeCreation.CREATED, repository.createLinkCode(DeviceLinkCode(pending, owner, now + 1, now + 120_001), oneDevice))
        assertIs<LinkCodeRedemption.Redeemed>(repository.redeemLinkCode(first, newSession("Watch", now + 2), oneDevice))

        assertEquals(LinkCodeRedemption.DeviceLimitReached, repository.redeemLinkCode(pending, newSession("Second", now + 3), oneDevice))
        assertEquals(
            LinkCodeCreation.DEVICE_LIMIT_REACHED,
            repository.createLinkCode(DeviceLinkCode(randomHash(), owner, now + 4, now + 120_004), oneDevice),
        )

        val hourly = DeviceSessionLimits(maxActiveSessions = 5, maxCodesPerHour = 3)
        assertEquals(
            LinkCodeCreation.CREATED,
            repository.createLinkCode(DeviceLinkCode(randomHash(), owner, now + 5, now + 120_005), hourly),
        )
        assertEquals(
            LinkCodeCreation.RATE_LIMITED,
            repository.createLinkCode(DeviceLinkCode(randomHash(), owner, now + 6, now + 120_006), hourly),
        )
        val nextHour = now + LINK_CODE_RATE_WINDOW_MS + 200_000
        assertEquals(
            LinkCodeCreation.CREATED,
            repository.createLinkCode(DeviceLinkCode(randomHash(), owner, nextHour, nextHour + 120_000), hourly),
        )
    }

    private suspend fun assertRevokeAndExtend(
        repository: DeviceSessionRepository,
        owner: UUID,
        other: UUID,
    ) {
        val session = redeemOne(repository, owner, now)
        assertFalse(repository.revoke(other, session.id, now + 10))
        assertTrue(repository.revoke(owner, session.id, now + 10))
        assertTrue(repository.revoke(owner, session.id, now + 20))
        assertEquals(now + 10, repository.find(session.id)?.revokedAt)
        assertTrue(repository.listActive(owner, now + 30).isEmpty())
        assertFalse(repository.extend(session.id, now + 999_999, now + 40))

        val live = redeemOne(repository, owner, now + 50)
        assertTrue(repository.extend(live.id, now + 9_999_999, now + 60))
        val extended = repository.find(live.id)
        assertEquals(now + 9_999_999, extended?.expiresAt)
        assertEquals(now + 60, extended?.lastRefreshedAt)
        assertEquals(listOf(live.id), repository.listActive(owner, now + 9_999_998).map(DeviceSession::id))
        assertTrue(repository.listActive(owner, now + 9_999_999).isEmpty())
    }

    private suspend fun redeemOne(
        repository: DeviceSessionRepository,
        owner: UUID,
        at: Long,
    ): DeviceSession {
        val hash = randomHash()
        repository.createLinkCode(DeviceLinkCode(hash, owner, at, at + 120_000), roomy)
        return assertIs<LinkCodeRedemption.Redeemed>(repository.redeemLinkCode(hash, newSession("Pixel Watch 3", at + 1), roomy)).session
    }

    private fun newSession(
        name: String,
        at: Long,
    ): NewDeviceSession = NewDeviceSession(Uuid.random().toJavaUUID(), name, "wear_os", at, at + 30L * 24 * 60 * 60 * 1_000)

    private fun randomHash(): String = Uuid.random().toHexString() + Uuid.random().toHexString()

    private fun withDeviceTables(block: suspend (owner: UUID, other: UUID) -> Unit) =
        withH2Database(AccountsTable, DeviceSessionsTable, DeviceLinkCodesTable) {
            val owner = Uuid.random().toJavaUUID()
            val other = Uuid.random().toJavaUUID()
            transaction {
                listOf(owner, other).forEachIndexed { index, id ->
                    AccountsTable.insert {
                        it[AccountsTable.id] = id
                        it[username] = "device-session-$index"
                        it[displayName] = "Test account $index"
                        it[createdAt] = Clock.System.now()
                        it[isActive] = true
                        it[preferences] = "{}"
                    }
                }
            }
            runBlocking { block(owner, other) }
        }
}
```

Create `server/src/test/kotlin/app/logdate/server/database/DeviceLinkCodePostgresTest.kt` (it runs only against a disposable local PostgreSQL, like `CollectionVersionPostgresTest`):

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.devices.DeviceLinkCode
import app.logdate.server.devices.DeviceSessionLimits
import app.logdate.server.devices.LinkCodeRedemption
import app.logdate.server.devices.NewDeviceSession
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Runs against the local test PostgreSQL only; races real transactions on one link code. */
class DeviceLinkCodePostgresTest {
    @Test
    fun `concurrent redemptions of one link code create exactly one session`() =
        runBlocking {
            val url = localTestPostgresUrl()
            val previous = TransactionManager.defaultDatabase
            val source = localTestPostgresDataSource(url)
            val owner = Uuid.random().toJavaUUID()
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                transaction {
                    AccountsTable.insert {
                        it[AccountsTable.id] = owner
                        it[username] = "link_race_${owner.toString().take(8)}"
                        it[displayName] = "Link race"
                        it[createdAt] = Clock.System.now()
                        it[isActive] = true
                        it[preferences] = "{}"
                    }
                }
                val repository = PostgreSQLDeviceSessionRepository()
                val limits = DeviceSessionLimits(maxActiveSessions = 5, maxCodesPerHour = 10)
                val now = System.currentTimeMillis()
                val hash = "race-${Uuid.random().toHexString()}"
                repository.createLinkCode(DeviceLinkCode(hash, owner, now, now + 120_000), limits)

                val sessions =
                    (1..8).map { index ->
                        NewDeviceSession(Uuid.random().toJavaUUID(), "Watch $index", "wear_os", now + index, now + 86_400_000)
                    }
                val outcomes =
                    sessions
                        .map { session ->
                            async(Dispatchers.IO) { runCatching { repository.redeemLinkCode(hash, session, limits) }.getOrNull() }
                        }.awaitAll()

                assertEquals(1, outcomes.count { it is LinkCodeRedemption.Redeemed })
                assertEquals(1, repository.listActive(owner, now + 100).size)
            } finally {
                transaction { AccountsTable.deleteWhere { AccountsTable.id eq owner } }
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }
}
```

- [ ] **Step 3: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.database.PostgreSQLDeviceSessionRepositoryTest' --tests 'app.logdate.server.database.DeviceLinkCodePostgresTest' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'devices'`, `'DeviceSessionsTable'`, `'DeviceLinkCodesTable'`, `'PostgreSQLDeviceSessionRepository'`.

- [ ] **Step 4: Add the migration**

Create `server/src/main/resources/db/migration/V34__Add_device_sessions.sql`:

```sql
-- A linked device (a watch set up from the phone) holds its own revocable session. Link codes are
-- stored only as SHA-256 hashes; the code itself exists only in the phone-to-device exchange.
CREATE TABLE device_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    device_name VARCHAR(120) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    last_refreshed_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    revoked_at BIGINT
);
CREATE INDEX idx_device_sessions_account ON device_sessions(account_id);

CREATE TABLE device_link_codes (
    code_hash VARCHAR(64) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    redeemed_at BIGINT,
    session_id UUID
);
CREATE INDEX idx_device_link_codes_account_created ON device_link_codes(account_id, created_at);
CREATE INDEX idx_device_link_codes_expires ON device_link_codes(expires_at);
```

- [ ] **Step 5: Add the Exposed tables**

In `Tables.kt`, replace lines 3-6:

```kotlin
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.ExperimentalUuidApi
```

with:

```kotlin
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.uuid.ExperimentalUuidApi
```

Append to the end of `Tables.kt` (after `LogDateAtprotoBlobsTable`):

```kotlin

/** Mirrors V34: one row per linked device session. */
object DeviceSessionsTable : Table("device_sessions") {
    val id = javaUUID("id")
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceName = varchar("device_name", 120)
    val platform = varchar("platform", 32)
    val createdAt = long("created_at")
    val lastRefreshedAt = long("last_refreshed_at")
    val expiresAt = long("expires_at")
    val revokedAt = long("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_device_sessions_account", false, accountId)
    }
}

/** Mirrors V34: one row per minted link code, keyed by the SHA-256 hash of the code's bytes. */
object DeviceLinkCodesTable : Table("device_link_codes") {
    val codeHash = varchar("code_hash", 64)
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val redeemedAt = long("redeemed_at").nullable()
    val sessionId = javaUUID("session_id").nullable()

    override val primaryKey = PrimaryKey(codeHash)

    init {
        index("idx_device_link_codes_account_created", false, accountId, createdAt)
        index("idx_device_link_codes_expires", false, expiresAt)
    }
}
```

- [ ] **Step 6: Create `DeviceSessionRepository.kt`**

`server/src/main/kotlin/app/logdate/server/devices/DeviceSessionRepository.kt`:

```kotlin
package app.logdate.server.devices

import java.util.UUID

/** Codes created more than this long ago no longer count toward the hourly limit and may be deleted. */
internal const val LINK_CODE_RATE_WINDOW_MS = 60 * 60 * 1_000L

/** A linked device's revocable session. Times are epoch milliseconds. */
data class DeviceSession(
    val id: UUID,
    val accountId: UUID,
    val deviceName: String,
    val platform: String,
    val createdAt: Long,
    val lastRefreshedAt: Long,
    val expiresAt: Long,
    val revokedAt: Long? = null,
) {
    fun isActive(now: Long): Boolean = revokedAt == null && expiresAt > now
}

/** A one-time link code, identified only by the SHA-256 hash of its bytes. */
data class DeviceLinkCode(
    val codeHash: String,
    val accountId: UUID,
    val createdAt: Long,
    val expiresAt: Long,
)

/** The session a redeemed link code creates; its account comes from the code. */
data class NewDeviceSession(
    val id: UUID,
    val deviceName: String,
    val platform: String,
    val createdAt: Long,
    val expiresAt: Long,
)

data class DeviceSessionLimits(
    val maxActiveSessions: Int,
    val maxCodesPerHour: Int,
)

enum class LinkCodeCreation {
    CREATED,
    DEVICE_LIMIT_REACHED,
    RATE_LIMITED,
}

sealed interface LinkCodeRedemption {
    data class Redeemed(
        val session: DeviceSession,
    ) : LinkCodeRedemption

    data object Invalid : LinkCodeRedemption

    data object DeviceLimitReached : LinkCodeRedemption
}

interface DeviceSessionRepository {
    /**
     * Stores [code] unless its account already has [DeviceSessionLimits.maxActiveSessions] active sessions or
     * minted [DeviceSessionLimits.maxCodesPerHour] codes in the last hour.
     */
    suspend fun createLinkCode(
        code: DeviceLinkCode,
        limits: DeviceSessionLimits,
    ): LinkCodeCreation

    /**
     * Spends the unexpired, unspent code with [codeHash] and creates [session] for its account in one step:
     * of any number of concurrent calls with one code, exactly one returns [LinkCodeRedemption.Redeemed].
     */
    suspend fun redeemLinkCode(
        codeHash: String,
        session: NewDeviceSession,
        limits: DeviceSessionLimits,
    ): LinkCodeRedemption

    suspend fun find(sessionId: UUID): DeviceSession?

    suspend fun listActive(
        accountId: UUID,
        now: Long,
    ): List<DeviceSession>

    /** Marks the session revoked. True when [accountId] owns the session, including one already revoked. */
    suspend fun revoke(
        accountId: UUID,
        sessionId: UUID,
        now: Long,
    ): Boolean

    /** Moves an unrevoked session's expiry to [expiresAt]; false when the session is gone or revoked. */
    suspend fun extend(
        sessionId: UUID,
        expiresAt: Long,
        now: Long,
    ): Boolean
}

class InMemoryDeviceSessionRepository : DeviceSessionRepository {
    private data class StoredCode(
        val code: DeviceLinkCode,
        val redeemedAt: Long? = null,
    )

    private val lock = Any()
    private val codes = mutableMapOf<String, StoredCode>()
    private val sessions = mutableMapOf<UUID, DeviceSession>()

    override suspend fun createLinkCode(
        code: DeviceLinkCode,
        limits: DeviceSessionLimits,
    ): LinkCodeCreation =
        synchronized(lock) {
            val windowStart = code.createdAt - LINK_CODE_RATE_WINDOW_MS
            codes.values.removeAll { it.code.expiresAt < windowStart }
            if (activeCount(code.accountId, code.createdAt) >= limits.maxActiveSessions) {
                return@synchronized LinkCodeCreation.DEVICE_LIMIT_REACHED
            }
            val recent = codes.values.count { it.code.accountId == code.accountId && it.code.createdAt > windowStart }
            if (recent >= limits.maxCodesPerHour) return@synchronized LinkCodeCreation.RATE_LIMITED
            codes[code.codeHash] = StoredCode(code)
            LinkCodeCreation.CREATED
        }

    override suspend fun redeemLinkCode(
        codeHash: String,
        session: NewDeviceSession,
        limits: DeviceSessionLimits,
    ): LinkCodeRedemption =
        synchronized(lock) {
            val now = session.createdAt
            val stored =
                codes[codeHash]?.takeIf { it.redeemedAt == null && it.code.expiresAt > now }
                    ?: return@synchronized LinkCodeRedemption.Invalid
            if (activeCount(stored.code.accountId, now) >= limits.maxActiveSessions) {
                return@synchronized LinkCodeRedemption.DeviceLimitReached
            }
            codes[codeHash] = stored.copy(redeemedAt = now)
            val created =
                DeviceSession(
                    id = session.id,
                    accountId = stored.code.accountId,
                    deviceName = session.deviceName,
                    platform = session.platform,
                    createdAt = now,
                    lastRefreshedAt = now,
                    expiresAt = session.expiresAt,
                )
            sessions[created.id] = created
            LinkCodeRedemption.Redeemed(created)
        }

    override suspend fun find(sessionId: UUID): DeviceSession? = synchronized(lock) { sessions[sessionId] }

    override suspend fun listActive(
        accountId: UUID,
        now: Long,
    ): List<DeviceSession> =
        synchronized(lock) {
            sessions.values.filter { it.accountId == accountId && it.isActive(now) }.sortedBy(DeviceSession::createdAt)
        }

    override suspend fun revoke(
        accountId: UUID,
        sessionId: UUID,
        now: Long,
    ): Boolean =
        synchronized(lock) {
            val existing = sessions[sessionId]?.takeIf { it.accountId == accountId } ?: return@synchronized false
            if (existing.revokedAt == null) sessions[sessionId] = existing.copy(revokedAt = now)
            true
        }

    override suspend fun extend(
        sessionId: UUID,
        expiresAt: Long,
        now: Long,
    ): Boolean =
        synchronized(lock) {
            val existing = sessions[sessionId]?.takeIf { it.revokedAt == null } ?: return@synchronized false
            sessions[sessionId] = existing.copy(expiresAt = expiresAt, lastRefreshedAt = now)
            true
        }

    private fun activeCount(
        accountId: UUID,
        now: Long,
    ): Int = sessions.values.count { it.accountId == accountId && it.isActive(now) }
}
```

- [ ] **Step 7: Create `PostgreSQLDeviceSessionRepository.kt`**

`server/src/main/kotlin/app/logdate/server/database/PostgreSQLDeviceSessionRepository.kt`:

```kotlin
package app.logdate.server.database

import app.logdate.server.devices.DeviceLinkCode
import app.logdate.server.devices.DeviceSession
import app.logdate.server.devices.DeviceSessionLimits
import app.logdate.server.devices.DeviceSessionRepository
import app.logdate.server.devices.LINK_CODE_RATE_WINDOW_MS
import app.logdate.server.devices.LinkCodeCreation
import app.logdate.server.devices.LinkCodeRedemption
import app.logdate.server.devices.NewDeviceSession
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class PostgreSQLDeviceSessionRepository : DeviceSessionRepository {
    override suspend fun createLinkCode(
        code: DeviceLinkCode,
        limits: DeviceSessionLimits,
    ): LinkCodeCreation =
        transaction {
            val windowStart = code.createdAt - LINK_CODE_RATE_WINDOW_MS
            DeviceLinkCodesTable.deleteWhere { expiresAt less windowStart }
            if (activeSessionCount(code.accountId, code.createdAt) >= limits.maxActiveSessions) {
                return@transaction LinkCodeCreation.DEVICE_LIMIT_REACHED
            }
            val recent =
                DeviceLinkCodesTable
                    .selectAll()
                    .where { (DeviceLinkCodesTable.accountId eq code.accountId) and (DeviceLinkCodesTable.createdAt greater windowStart) }
                    .count()
                    .toInt()
            if (recent >= limits.maxCodesPerHour) return@transaction LinkCodeCreation.RATE_LIMITED
            DeviceLinkCodesTable.insert {
                it[DeviceLinkCodesTable.codeHash] = code.codeHash
                it[DeviceLinkCodesTable.accountId] = code.accountId
                it[DeviceLinkCodesTable.createdAt] = code.createdAt
                it[DeviceLinkCodesTable.expiresAt] = code.expiresAt
            }
            LinkCodeCreation.CREATED
        }

    override suspend fun redeemLinkCode(
        codeHash: String,
        session: NewDeviceSession,
        limits: DeviceSessionLimits,
    ): LinkCodeRedemption =
        transaction {
            val now = session.createdAt
            val accountId =
                DeviceLinkCodesTable
                    .selectAll()
                    .where { spendable(codeHash, now) }
                    .singleOrNull()
                    ?.get(DeviceLinkCodesTable.accountId)
                    ?: return@transaction LinkCodeRedemption.Invalid
            if (activeSessionCount(accountId, now) >= limits.maxActiveSessions) {
                return@transaction LinkCodeRedemption.DeviceLimitReached
            }
            // The guarded update is what makes a code single use: a concurrent redeemer re-reads
            // redeemed_at after this transaction commits and updates no row.
            val spent =
                DeviceLinkCodesTable.update({ spendable(codeHash, now) }) {
                    it[DeviceLinkCodesTable.redeemedAt] = now
                    it[DeviceLinkCodesTable.sessionId] = session.id
                }
            if (spent != 1) return@transaction LinkCodeRedemption.Invalid
            DeviceSessionsTable.insert {
                it[DeviceSessionsTable.id] = session.id
                it[DeviceSessionsTable.accountId] = accountId
                it[DeviceSessionsTable.deviceName] = session.deviceName
                it[DeviceSessionsTable.platform] = session.platform
                it[DeviceSessionsTable.createdAt] = now
                it[DeviceSessionsTable.lastRefreshedAt] = now
                it[DeviceSessionsTable.expiresAt] = session.expiresAt
            }
            LinkCodeRedemption.Redeemed(
                DeviceSession(
                    id = session.id,
                    accountId = accountId,
                    deviceName = session.deviceName,
                    platform = session.platform,
                    createdAt = now,
                    lastRefreshedAt = now,
                    expiresAt = session.expiresAt,
                ),
            )
        }

    override suspend fun find(sessionId: UUID): DeviceSession? =
        transaction {
            DeviceSessionsTable
                .selectAll()
                .where { DeviceSessionsTable.id eq sessionId }
                .singleOrNull()
                ?.toDeviceSession()
        }

    override suspend fun listActive(
        accountId: UUID,
        now: Long,
    ): List<DeviceSession> =
        transaction {
            DeviceSessionsTable
                .selectAll()
                .where { active(accountId, now) }
                .orderBy(DeviceSessionsTable.createdAt to SortOrder.ASC)
                .map { it.toDeviceSession() }
        }

    override suspend fun revoke(
        accountId: UUID,
        sessionId: UUID,
        now: Long,
    ): Boolean =
        transaction {
            DeviceSessionsTable.update({ owned(accountId, sessionId) and DeviceSessionsTable.revokedAt.isNull() }) {
                it[DeviceSessionsTable.revokedAt] = now
            }
            DeviceSessionsTable.selectAll().where { owned(accountId, sessionId) }.any()
        }

    override suspend fun extend(
        sessionId: UUID,
        expiresAt: Long,
        now: Long,
    ): Boolean =
        transaction {
            DeviceSessionsTable.update({ (DeviceSessionsTable.id eq sessionId) and DeviceSessionsTable.revokedAt.isNull() }) {
                it[DeviceSessionsTable.expiresAt] = expiresAt
                it[DeviceSessionsTable.lastRefreshedAt] = now
            } == 1
        }

    private fun spendable(
        codeHash: String,
        now: Long,
    ): Op<Boolean> =
        (DeviceLinkCodesTable.codeHash eq codeHash) and
            DeviceLinkCodesTable.redeemedAt.isNull() and
            (DeviceLinkCodesTable.expiresAt greater now)

    private fun active(
        accountId: UUID,
        now: Long,
    ): Op<Boolean> =
        (DeviceSessionsTable.accountId eq accountId) and
            DeviceSessionsTable.revokedAt.isNull() and
            (DeviceSessionsTable.expiresAt greater now)

    private fun owned(
        accountId: UUID,
        sessionId: UUID,
    ): Op<Boolean> = (DeviceSessionsTable.id eq sessionId) and (DeviceSessionsTable.accountId eq accountId)

    private fun activeSessionCount(
        accountId: UUID,
        now: Long,
    ): Int =
        DeviceSessionsTable
            .selectAll()
            .where { active(accountId, now) }
            .count()
            .toInt()
}

private fun ResultRow.toDeviceSession(): DeviceSession =
    DeviceSession(
        id = this[DeviceSessionsTable.id],
        accountId = this[DeviceSessionsTable.accountId],
        deviceName = this[DeviceSessionsTable.deviceName],
        platform = this[DeviceSessionsTable.platform],
        createdAt = this[DeviceSessionsTable.createdAt],
        lastRefreshedAt = this[DeviceSessionsTable.lastRefreshedAt],
        expiresAt = this[DeviceSessionsTable.expiresAt],
        revokedAt = this[DeviceSessionsTable.revokedAt],
    )
```

- [ ] **Step 8: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.database.PostgreSQLDeviceSessionRepositoryTest' --tests 'app.logdate.server.database.DeviceLinkCodePostgresTest' --console=plain`
Expected: PASS; `DeviceLinkCodePostgresTest` runs against the local test database (`./run dev`, then `./run test:server`) and is skipped, saying why, without one.

- [ ] **Step 9: Format, lint, and run the suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:ktlintFormat :server:ktlintCheck :server:detekt :server:test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-device-session-store.txt <<'MSG'
chore(server): store linked device sessions and link codes

Flyway V34 adds device_sessions and device_link_codes, with in-memory
and PostgreSQL stores. A link code is kept only as the SHA-256 hash of
its bytes and can be spent once: the spending update is guarded on
redeemed_at, so concurrent redemptions of one code create one session.
Each account is limited in active sessions and in codes per hour.

Nothing reads these tables yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add server/src/main/resources/db/migration/V34__Add_device_sessions.sql server/src/main/kotlin/app/logdate/server/database/Tables.kt server/src/main/kotlin/app/logdate/server/devices/DeviceSessionRepository.kt server/src/main/kotlin/app/logdate/server/database/PostgreSQLDeviceSessionRepository.kt server/src/test/kotlin/app/logdate/server/database/PostgreSQLDeviceSessionRepositoryTest.kt server/src/test/kotlin/app/logdate/server/database/DeviceLinkCodePostgresTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-device-session-store.txt
```

Expected: hooks pass; six files in the commit.

---

### Task 4: Add session checks, a token verifier and the link service

**Files:**
- Create: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/devices/DeviceLinkModels.kt`
- Create: `server/src/main/kotlin/app/logdate/server/devices/DeviceSessionGuard.kt`
- Create: `server/src/main/kotlin/app/logdate/server/auth/AccessTokenVerifier.kt`
- Create: `server/src/main/kotlin/app/logdate/server/devices/DeviceLinkService.kt`
- Test: `server/src/test/kotlin/app/logdate/server/devices/DeviceLinkServiceTest.kt`
- Test: `server/src/test/kotlin/app/logdate/server/auth/AccessTokenVerifierTest.kt`

**Interfaces:**
- Consumes: Task 2's `TokenService` device methods and `AccessTokenClaims`; Task 3's `DeviceSessionRepository`; `AccountRepository.findById(Uuid)` (`server/.../auth/AccountRepository.kt:24`); `MutableClock` (`server/src/test/.../oauth/OAuthTestSupport.kt:13`, internal to the server test source set).
- Produces:
  - `app.logdate.shared.model.devices`: `DeviceLinkPlatform.WEAR_OS = "wear_os"`, `DeviceLinkErrorCodes` (`DEVICE_SESSION_REVOKED`, `DEVICE_SCOPE_DENIED`, `LINK_CODE_INVALID`, `DEVICE_LIMIT_REACHED`, `DEVICE_LINK_RATE_LIMITED`, `NOT_A_LINKED_DEVICE`, `DEVICE_NOT_FOUND`), `DeviceLinkCodeResponse(code, expiresAt)`, `DeviceLinkRedeemRequest(code, deviceName, platform = "wear_os")`, `DeviceLinkRedeemResponse(accountId, sessionId, tokens: AccountTokens)`, `LinkedDevice(id, deviceName, platform, createdAt, lastRefreshedAt, expiresAt, current = false)`, `LinkedDeviceListResponse(devices)`. Every type holding a secret redacts it in `toString()`.
  - `class DeviceSessionGuard(repository: DeviceSessionRepository?, cacheTtl: Duration = 30.seconds, clock: Clock = Clock.System)` with `suspend fun isActive(accountId: String, sessionId: String): Boolean`, `fun invalidate(sessionId: String)`, `DeviceSessionGuard.Disabled`.
  - `sealed interface AccessCheck { Granted(claims), Missing, Invalid, SessionRevoked, ScopeDenied }`, `class AccessTokenVerifier(tokenService, guard)` with `suspend fun verify(token: String?, allowSyncScope: Boolean): AccessCheck`, `internal fun bearerToken(authorizationHeader: String?): String?`.
  - `class DeviceLinkService(repository, tokenService, accountRepository, guard, clock = Clock.System, random = SecureRandom())` with `mint(accountId: UUID): DeviceLinkMint`, `redeem(request): DeviceLinkRedemption`, `list(accountId: UUID, currentSessionId: String?): List<LinkedDevice>`, `revoke(accountId: UUID, sessionId: UUID): Boolean`, `refresh(claims: AccessTokenClaims): DeviceSessionRefresh`; constants `LINK_CODE_LIFETIME_MS = 120_000`, `MAX_ACTIVE_DEVICES = 5`, `MAX_LINK_CODES_PER_HOUR = 10`, `SESSION_LIFETIME_MS` (30 days), `SESSION_RENEWAL_WINDOW_MS` (15 days).

- [ ] **Step 1: Write the failing tests**

Create `server/src/test/kotlin/app/logdate/server/devices/DeviceLinkServiceTest.kt`:

```kotlin
package app.logdate.server.devices

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccessTokenClaims
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.TokenScope
import app.logdate.server.database.toJavaUUID
import app.logdate.server.oauth.MutableClock
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class DeviceLinkServiceTest {
    private val start = Instant.parse("2026-10-03T12:00:00Z")
    private val clock = MutableClock(start)
    private val tokens = JwtTokenService("device-link-service-secret")
    private val accounts = InMemoryAccountRepository()
    private val repository = InMemoryDeviceSessionRepository()
    private val guard = DeviceSessionGuard(repository, cacheTtl = 30.seconds, clock = clock)
    private val service = DeviceLinkService(repository, tokens, accounts, guard, clock)
    private var accountCount = 0

    @Test
    fun `a code links one device and then stops working`() =
        runTest {
            val owner = newAccount()
            val minted = assertIs<DeviceLinkMint.Minted>(service.mint(owner)).code

            assertEquals(43, minted.code.length)
            assertEquals(start.toEpochMilliseconds() + DeviceLinkService.LINK_CODE_LIFETIME_MS, minted.expiresAt)
            assertFalse(minted.code in minted.toString())

            val linked = redeem(minted.code, "Pixel Watch 3")
            assertEquals(owner.toString(), linked.accountId)
            assertEquals(
                AccessTokenClaims(owner.toString(), null, linked.sessionId, TokenScope.SYNC),
                tokens.decodeAccessToken(linked.tokens.accessToken),
            )
            assertEquals(linked.sessionId, tokens.decodeRefreshToken(linked.tokens.refreshToken)?.sessionId)
            assertFalse(linked.tokens.accessToken in linked.toString())
            assertEquals(DeviceLinkRedemption.InvalidCode, service.redeem(DeviceLinkRedeemRequest(minted.code, "Second watch")))
        }

    @Test
    fun `expired codes and malformed requests are refused without spending the code`() =
        runTest {
            val owner = newAccount()
            val stale = assertIs<DeviceLinkMint.Minted>(service.mint(owner)).code
            clock.nowValue = start + 121.seconds
            assertEquals(DeviceLinkRedemption.InvalidCode, service.redeem(DeviceLinkRedeemRequest(stale.code, "Pixel Watch 3")))

            val fresh = assertIs<DeviceLinkMint.Minted>(service.mint(owner)).code
            listOf("not-a-code", fresh.code + "=", fresh.code.dropLast(1)).forEach { code ->
                assertEquals(DeviceLinkRedemption.InvalidCode, service.redeem(DeviceLinkRedeemRequest(code, "Pixel Watch 3")), code)
            }
            assertEquals(DeviceLinkRedemption.InvalidDevice, service.redeem(DeviceLinkRedeemRequest(fresh.code, "   ")))
            assertEquals(DeviceLinkRedemption.InvalidDevice, service.redeem(DeviceLinkRedeemRequest(fresh.code, "Bell\u0007")))
            assertEquals(DeviceLinkRedemption.InvalidDevice, service.redeem(DeviceLinkRedeemRequest(fresh.code, "Watch", "Wear OS")))
            assertIs<DeviceLinkRedemption.Redeemed>(service.redeem(DeviceLinkRedeemRequest(fresh.code, "  Pixel Watch 3 ")))
        }

    @Test
    fun `an account links at most five devices and mints at most ten codes an hour`() =
        runTest {
            val crowded = newAccount()
            val linked = (1..DeviceLinkService.MAX_ACTIVE_DEVICES).map { link(crowded, "Watch $it") }
            assertEquals(DeviceLinkMint.DeviceLimitReached, service.mint(crowded))
            assertTrue(service.revoke(crowded, UUID.fromString(linked.first().sessionId)))
            assertIs<DeviceLinkMint.Minted>(service.mint(crowded))

            val busy = newAccount()
            repeat(DeviceLinkService.MAX_LINK_CODES_PER_HOUR) { assertIs<DeviceLinkMint.Minted>(service.mint(busy)) }
            assertEquals(DeviceLinkMint.RateLimited, service.mint(busy))
            clock.nowValue = start + 61.minutes
            assertIs<DeviceLinkMint.Minted>(service.mint(busy))
        }

    @Test
    fun `refresh renews a session only in its last fifteen days and stops after revocation`() =
        runTest {
            val owner = newAccount()
            val linked = link(owner)
            val claims = assertNotNull(tokens.decodeRefreshToken(linked.tokens.refreshToken))

            val early = assertIs<DeviceSessionRefresh.Refreshed>(service.refresh(claims))
            assertNull(early.refreshToken)
            assertEquals(linked.sessionId, tokens.decodeAccessToken(early.accessToken)?.sessionId)

            clock.nowValue = start + 16.days
            val renewed = assertIs<DeviceSessionRefresh.Refreshed>(service.refresh(claims))
            assertEquals(linked.sessionId, tokens.decodeRefreshToken(assertNotNull(renewed.refreshToken))?.sessionId)
            val device = service.list(owner, currentSessionId = null).single()
            assertEquals((start + 16.days).toEpochMilliseconds() + DeviceLinkService.SESSION_LIFETIME_MS, device.expiresAt)
            assertEquals((start + 16.days).toEpochMilliseconds(), device.lastRefreshedAt)

            assertEquals(DeviceSessionRefresh.Revoked, service.refresh(claims.copy(accountId = newAccount().toString())))
            assertTrue(service.revoke(owner, UUID.fromString(linked.sessionId)))
            assertEquals(DeviceSessionRefresh.Revoked, service.refresh(claims))
        }

    @Test
    fun `the guard caches answers but revoking through the service applies at once`() =
        runTest {
            val owner = newAccount()
            val first = link(owner)
            assertTrue(guard.isActive(owner.toString(), first.sessionId))
            assertFalse(guard.isActive(newAccount().toString(), first.sessionId))

            repository.revoke(owner, UUID.fromString(first.sessionId), start.toEpochMilliseconds())
            assertTrue(guard.isActive(owner.toString(), first.sessionId), "a cached answer lasts up to 30 seconds")
            clock.nowValue = start + 31.seconds
            assertFalse(guard.isActive(owner.toString(), first.sessionId))

            val second = link(owner)
            assertTrue(guard.isActive(owner.toString(), second.sessionId))
            service.revoke(owner, UUID.fromString(second.sessionId))
            assertFalse(guard.isActive(owner.toString(), second.sessionId))
            assertFalse(DeviceSessionGuard.Disabled.isActive(owner.toString(), second.sessionId))
        }

    @Test
    fun `the device list marks the calling session`() =
        runTest {
            val owner = newAccount()
            val watch = link(owner, "Pixel Watch 3")
            link(owner, "Galaxy Watch")

            val devices = service.list(owner, currentSessionId = watch.sessionId)

            assertEquals(listOf("Pixel Watch 3", "Galaxy Watch"), devices.map { it.deviceName })
            assertEquals(listOf(true, false), devices.map { it.current })
            assertTrue(devices.all { it.platform == "wear_os" })
        }

    private suspend fun link(
        accountId: UUID,
        name: String = "Pixel Watch 3",
    ): DeviceLinkRedeemResponse = redeem(assertIs<DeviceLinkMint.Minted>(service.mint(accountId)).code.code, name)

    private suspend fun redeem(
        code: String,
        name: String,
    ): DeviceLinkRedeemResponse = assertIs<DeviceLinkRedemption.Redeemed>(service.redeem(DeviceLinkRedeemRequest(code, name))).response

    private suspend fun newAccount(): UUID {
        val id = Uuid.random()
        accountCount += 1
        accounts.create(Account(id = id, username = "owner_$accountCount", displayName = "Owner", createdAt = Clock.System.now()))
        return id.toJavaUUID()
    }
}
```

Create `server/src/test/kotlin/app/logdate/server/auth/AccessTokenVerifierTest.kt`:

```kotlin
package app.logdate.server.auth

import app.logdate.server.database.toJavaUUID
import app.logdate.server.devices.DeviceLinkMint
import app.logdate.server.devices.DeviceLinkRedemption
import app.logdate.server.devices.DeviceLinkService
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.devices.InMemoryDeviceSessionRepository
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AccessTokenVerifierTest {
    private val tokens = JwtTokenService("access-token-verifier-secret")
    private val accounts = InMemoryAccountRepository()
    private val repository = InMemoryDeviceSessionRepository()
    private val guard = DeviceSessionGuard(repository, cacheTtl = Duration.ZERO)
    private val verifier = AccessTokenVerifier(tokens, guard)
    private val linkService = DeviceLinkService(repository, tokens, accounts, guard)

    @Test
    fun `full tokens pass and linked device tokens need sync scope and a live session`() =
        runTest {
            val owner = Uuid.random()
            accounts.create(Account(id = owner, username = "verifier_owner", displayName = "Owner", createdAt = Clock.System.now()))
            val code = assertIs<DeviceLinkMint.Minted>(linkService.mint(owner.toJavaUUID())).code.code
            val watch = assertIs<DeviceLinkRedemption.Redeemed>(linkService.redeem(DeviceLinkRedeemRequest(code, "Pixel Watch 3"))).response
            val phoneToken = tokens.generateAccessToken(owner.toString())

            assertEquals(AccessCheck.Missing, verifier.verify(null, allowSyncScope = true))
            assertEquals(AccessCheck.Invalid, verifier.verify("not-a-token", allowSyncScope = true))
            assertEquals(AccessCheck.Invalid, verifier.verify(watch.tokens.refreshToken, allowSyncScope = true))
            assertEquals(
                AccessCheck.Granted(AccessTokenClaims.full(owner.toString())),
                verifier.verify(phoneToken, allowSyncScope = false),
            )
            assertEquals(AccessCheck.ScopeDenied, verifier.verify(watch.tokens.accessToken, allowSyncScope = false))
            val granted = assertIs<AccessCheck.Granted>(verifier.verify(watch.tokens.accessToken, allowSyncScope = true))
            assertEquals(watch.sessionId, granted.claims.sessionId)
            assertEquals(TokenScope.SYNC, granted.claims.scope)
            assertEquals(
                AccessCheck.SessionRevoked,
                AccessTokenVerifier(tokens, DeviceSessionGuard.Disabled).verify(watch.tokens.accessToken, allowSyncScope = true),
            )

            linkService.revoke(owner.toJavaUUID(), UUID.fromString(watch.sessionId))
            assertEquals(AccessCheck.SessionRevoked, verifier.verify(watch.tokens.accessToken, allowSyncScope = true))
        }

    @Test
    fun `bearer tokens are read only from a Bearer header`() {
        assertEquals("abc", bearerToken("Bearer abc"))
        assertEquals("", bearerToken("Bearer   "))
        assertNull(bearerToken("Basic abc"))
        assertNull(bearerToken(null))
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.devices.DeviceLinkServiceTest' --tests 'app.logdate.server.auth.AccessTokenVerifierTest' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'DeviceLinkService'`, `'DeviceSessionGuard'`, `'AccessTokenVerifier'`, `'devices'` (shared model package).

- [ ] **Step 3: Create the shared wire models**

`shared/model/src/commonMain/kotlin/app/logdate/shared/model/devices/DeviceLinkModels.kt`:

```kotlin
package app.logdate.shared.model.devices

import app.logdate.shared.model.AccountTokens
import kotlinx.serialization.Serializable

/** Values for [DeviceLinkRedeemRequest.platform]. Other short lowercase identifiers are accepted for future devices. */
object DeviceLinkPlatform {
    const val WEAR_OS = "wear_os"
}

/** Error codes the device linking routes and linked-device sessions answer with. */
object DeviceLinkErrorCodes {
    /** The device's session was removed; the device must stop syncing and be linked again. */
    const val DEVICE_SESSION_REVOKED = "DEVICE_SESSION_REVOKED"

    /** A linked device asked for something only a signed-in account may do. */
    const val DEVICE_SCOPE_DENIED = "DEVICE_SCOPE_DENIED"

    /** The link code is unknown, expired or already spent. */
    const val LINK_CODE_INVALID = "LINK_CODE_INVALID"

    /** The account already has the most linked devices it may have. */
    const val DEVICE_LIMIT_REACHED = "DEVICE_LIMIT_REACHED"

    /** The account minted too many link codes in the last hour. */
    const val DEVICE_LINK_RATE_LIMITED = "DEVICE_LINK_RATE_LIMITED"

    /** A full-access token tried to remove "this device". */
    const val NOT_A_LINKED_DEVICE = "NOT_A_LINKED_DEVICE"

    /** No linked device with that ID belongs to the account. */
    const val DEVICE_NOT_FOUND = "DEVICE_NOT_FOUND"
}

/** A one-time code a signed-in phone hands to a device it is linking. Works once, until [expiresAt] (epoch ms). */
@Serializable
data class DeviceLinkCodeResponse(
    val code: String,
    val expiresAt: Long,
) {
    override fun toString(): String = "DeviceLinkCodeResponse(code=<redacted>, expiresAt=$expiresAt)"
}

/** What a device sends to exchange a link code for its own session. */
@Serializable
data class DeviceLinkRedeemRequest(
    val code: String,
    val deviceName: String,
    val platform: String = DeviceLinkPlatform.WEAR_OS,
) {
    override fun toString(): String = "DeviceLinkRedeemRequest(code=<redacted>, deviceName=$deviceName, platform=$platform)"
}

/** The new device session: the account it belongs to, its ID, and sync-only tokens. */
@Serializable
data class DeviceLinkRedeemResponse(
    val accountId: String,
    val sessionId: String,
    val tokens: AccountTokens,
) {
    override fun toString(): String = "DeviceLinkRedeemResponse(accountId=$accountId, sessionId=$sessionId, tokens=<redacted>)"
}

/** One active linked device. Times are epoch milliseconds. */
@Serializable
data class LinkedDevice(
    val id: String,
    val deviceName: String,
    val platform: String,
    val createdAt: Long,
    val lastRefreshedAt: Long,
    val expiresAt: Long,
    val current: Boolean = false,
)

@Serializable
data class LinkedDeviceListResponse(
    val devices: List<LinkedDevice>,
)
```

- [ ] **Step 4: Create `DeviceSessionGuard.kt`**

`server/src/main/kotlin/app/logdate/server/devices/DeviceSessionGuard.kt`:

```kotlin
package app.logdate.server.devices

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Answers whether a linked device session may still use its access tokens.
 *
 * Every request from a linked device asks, so answers are cached for [cacheTtl]. Revoking through
 * [DeviceLinkService] clears the cached answer on this server at once; another server instance notices
 * within [cacheTtl]. Refreshing a token always reads the store. Without a [repository] no session is
 * active, so a server or test fixture without device sessions refuses every linked device token.
 */
class DeviceSessionGuard(
    private val repository: DeviceSessionRepository?,
    private val cacheTtl: Duration = 30.seconds,
    private val clock: Clock = Clock.System,
) {
    private data class Answer(
        val accountId: UUID?,
        val active: Boolean,
        val checkedAt: Instant,
    )

    private val answers = ConcurrentHashMap<String, Answer>()

    suspend fun isActive(
        accountId: String,
        sessionId: String,
    ): Boolean {
        val store = repository ?: return false
        val now = clock.now()
        val cached = answers[sessionId]?.takeIf { now - it.checkedAt < cacheTtl }
        val answer = cached ?: lookUp(store, sessionId, now).also { if (cacheTtl.isPositive()) answers[sessionId] = it }
        return answer.active && answer.accountId?.toString() == accountId
    }

    fun invalidate(sessionId: String) {
        answers.remove(sessionId)
    }

    private suspend fun lookUp(
        store: DeviceSessionRepository,
        sessionId: String,
        now: Instant,
    ): Answer {
        val id = runCatching { UUID.fromString(sessionId) }.getOrNull() ?: return Answer(null, false, now)
        val session = store.find(id)
        return Answer(session?.accountId, session?.isActive(now.toEpochMilliseconds()) == true, now)
    }

    companion object {
        /** For servers and fixtures without device sessions: every linked device token is refused. */
        val Disabled: DeviceSessionGuard = DeviceSessionGuard(repository = null)
    }
}
```

- [ ] **Step 5: Create `AccessTokenVerifier.kt`**

`server/src/main/kotlin/app/logdate/server/auth/AccessTokenVerifier.kt`:

```kotlin
package app.logdate.server.auth

import app.logdate.server.devices.DeviceSessionGuard

/** The outcome of checking an access token for one route. */
sealed interface AccessCheck {
    data class Granted(
        val claims: AccessTokenClaims,
    ) : AccessCheck

    /** No `Authorization: Bearer` header. */
    data object Missing : AccessCheck

    /** The token is malformed, expired, signed by someone else, or a refresh token. */
    data object Invalid : AccessCheck

    /** A linked device token whose session was removed, expired, or is unknown to this server. */
    data object SessionRevoked : AccessCheck

    /** A valid linked device token on a route only a signed-in account may use. */
    data object ScopeDenied : AccessCheck
}

/**
 * Turns a bearer token into granted claims or a reason to refuse it.
 *
 * Full-access tokens pass everywhere. Linked device tokens (`scp=sync`) pass only where the route sets
 * `allowSyncScope`, and only while [guard] says their session is active.
 */
class AccessTokenVerifier(
    private val tokenService: TokenService,
    private val guard: DeviceSessionGuard,
) {
    suspend fun verify(
        token: String?,
        allowSyncScope: Boolean,
    ): AccessCheck {
        if (token == null) return AccessCheck.Missing
        tokenService.validateAccessToken(token)?.let { return AccessCheck.Granted(AccessTokenClaims.full(it)) }
        val claims = tokenService.decodeAccessToken(token) ?: return AccessCheck.Invalid
        val sessionId = claims.sessionId
        if (claims.scope != TokenScope.SYNC || sessionId == null) return AccessCheck.Invalid
        if (!allowSyncScope) return AccessCheck.ScopeDenied
        if (!guard.isActive(claims.accountId, sessionId)) return AccessCheck.SessionRevoked
        return AccessCheck.Granted(claims)
    }
}

/** The token from an `Authorization: Bearer <token>` header, or null when there is no such header. */
internal fun bearerToken(authorizationHeader: String?): String? =
    authorizationHeader
        ?.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.trim()
```

- [ ] **Step 6: Create `DeviceLinkService.kt`**

`server/src/main/kotlin/app/logdate/server/devices/DeviceLinkService.kt`:

```kotlin
package app.logdate.server.devices

import app.logdate.server.auth.AccessTokenClaims
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.database.toJavaUUID
import app.logdate.server.database.toKotlinUuid
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.devices.DeviceLinkCodeResponse
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import app.logdate.shared.model.devices.LinkedDevice
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface DeviceLinkMint {
    data class Minted(
        val code: DeviceLinkCodeResponse,
    ) : DeviceLinkMint

    data object DeviceLimitReached : DeviceLinkMint

    data object RateLimited : DeviceLinkMint
}

sealed interface DeviceLinkRedemption {
    data class Redeemed(
        val response: DeviceLinkRedeemResponse,
    ) : DeviceLinkRedemption

    data object InvalidCode : DeviceLinkRedemption

    data object InvalidDevice : DeviceLinkRedemption

    data object DeviceLimitReached : DeviceLinkRedemption
}

sealed interface DeviceSessionRefresh {
    /** [refreshToken] is a new 30-day refresh token, present only when the session was just renewed. */
    data class Refreshed(
        val accessToken: String,
        val refreshToken: String?,
    ) : DeviceSessionRefresh

    data object Revoked : DeviceSessionRefresh
}

/**
 * Links devices to an account without a sign-in on the device.
 *
 * A signed-in phone mints a one-time code and hands it to the device; the device redeems it for its own
 * session, whose tokens carry `sid` and `scp=sync`. A code works once and only for
 * [LINK_CODE_LIFETIME_MS]; the store keeps only its SHA-256 hash. An account keeps at most
 * [MAX_ACTIVE_DEVICES] active sessions and mints at most [MAX_LINK_CODES_PER_HOUR] codes an hour. A
 * session lasts [SESSION_LIFETIME_MS] and is renewed by a refresh that arrives with less than
 * [SESSION_RENEWAL_WINDOW_MS] left.
 */
@OptIn(ExperimentalUuidApi::class)
class DeviceLinkService(
    private val repository: DeviceSessionRepository,
    private val tokenService: TokenService,
    private val accountRepository: AccountRepository,
    private val guard: DeviceSessionGuard,
    private val clock: Clock = Clock.System,
    private val random: SecureRandom = SecureRandom(),
) {
    suspend fun mint(accountId: UUID): DeviceLinkMint {
        val bytes = ByteArray(LINK_CODE_BYTES).also(random::nextBytes)
        val now = nowMillis()
        val code = DeviceLinkCode(hashLinkCode(bytes), accountId, now, now + LINK_CODE_LIFETIME_MS)
        return when (repository.createLinkCode(code, LIMITS)) {
            LinkCodeCreation.CREATED -> DeviceLinkMint.Minted(DeviceLinkCodeResponse(encodeLinkCode(bytes), code.expiresAt))
            LinkCodeCreation.DEVICE_LIMIT_REACHED -> DeviceLinkMint.DeviceLimitReached
            LinkCodeCreation.RATE_LIMITED -> DeviceLinkMint.RateLimited
        }
    }

    suspend fun redeem(request: DeviceLinkRedeemRequest): DeviceLinkRedemption {
        val bytes = decodeLinkCode(request.code) ?: return DeviceLinkRedemption.InvalidCode
        val deviceName = request.deviceName.trim()
        if (!isValidDeviceName(deviceName) || !PLATFORM.matches(request.platform)) return DeviceLinkRedemption.InvalidDevice
        val now = nowMillis()
        val newSession = NewDeviceSession(Uuid.random().toJavaUUID(), deviceName, request.platform, now, now + SESSION_LIFETIME_MS)
        val session =
            when (val outcome = repository.redeemLinkCode(hashLinkCode(bytes), newSession, LIMITS)) {
                is LinkCodeRedemption.Redeemed -> outcome.session
                LinkCodeRedemption.Invalid -> return DeviceLinkRedemption.InvalidCode
                LinkCodeRedemption.DeviceLimitReached -> return DeviceLinkRedemption.DeviceLimitReached
            }
        val account = accountRepository.findById(session.accountId.toKotlinUuid())
        if (account == null) {
            repository.revoke(session.accountId, session.id, now)
            return DeviceLinkRedemption.InvalidCode
        }
        val accountId = session.accountId.toString()
        val sessionId = session.id.toString()
        val tokens =
            AccountTokens(
                accessToken = tokenService.generateDeviceAccessToken(accountId, account.did, sessionId),
                refreshToken = tokenService.generateDeviceRefreshToken(accountId, account.did, sessionId),
            )
        return DeviceLinkRedemption.Redeemed(DeviceLinkRedeemResponse(accountId, sessionId, tokens))
    }

    suspend fun list(
        accountId: UUID,
        currentSessionId: String?,
    ): List<LinkedDevice> =
        repository.listActive(accountId, nowMillis()).map { session ->
            LinkedDevice(
                id = session.id.toString(),
                deviceName = session.deviceName,
                platform = session.platform,
                createdAt = session.createdAt,
                lastRefreshedAt = session.lastRefreshedAt,
                expiresAt = session.expiresAt,
                current = session.id.toString() == currentSessionId,
            )
        }

    suspend fun revoke(
        accountId: UUID,
        sessionId: UUID,
    ): Boolean {
        val revoked = repository.revoke(accountId, sessionId, nowMillis())
        guard.invalidate(sessionId.toString())
        return revoked
    }

    suspend fun refresh(claims: AccessTokenClaims): DeviceSessionRefresh {
        val sessionId = claims.sessionId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return DeviceSessionRefresh.Revoked
        val now = nowMillis()
        val session =
            repository.find(sessionId)?.takeIf { it.isActive(now) && it.accountId.toString() == claims.accountId }
                ?: return DeviceSessionRefresh.Revoked
        val accessToken = tokenService.generateDeviceAccessToken(claims.accountId, claims.did, session.id.toString())
        if (session.expiresAt - now >= SESSION_RENEWAL_WINDOW_MS) return DeviceSessionRefresh.Refreshed(accessToken, refreshToken = null)
        if (!repository.extend(session.id, now + SESSION_LIFETIME_MS, now)) return DeviceSessionRefresh.Revoked
        val refreshToken = tokenService.generateDeviceRefreshToken(claims.accountId, claims.did, session.id.toString())
        return DeviceSessionRefresh.Refreshed(accessToken, refreshToken)
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()

    companion object {
        const val LINK_CODE_LIFETIME_MS = 120_000L
        const val MAX_ACTIVE_DEVICES = 5
        const val MAX_LINK_CODES_PER_HOUR = 10
        const val SESSION_LIFETIME_MS = 30L * 24 * 60 * 60 * 1_000
        const val SESSION_RENEWAL_WINDOW_MS = 15L * 24 * 60 * 60 * 1_000
        private const val LINK_CODE_BYTES = 32
        private const val MAX_DEVICE_NAME_LENGTH = 120
        private val PLATFORM = Regex("[a-z][a-z0-9_]{0,31}")
        private val LIMITS = DeviceSessionLimits(MAX_ACTIVE_DEVICES, MAX_LINK_CODES_PER_HOUR)

        private fun isValidDeviceName(name: String): Boolean = name.length in 1..MAX_DEVICE_NAME_LENGTH && name.none(Char::isISOControl)
    }
}

internal fun hashLinkCode(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun encodeLinkCode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

private fun decodeLinkCode(code: String): ByteArray? =
    runCatching { Base64.getUrlDecoder().decode(code) }
        .getOrNull()
        ?.takeIf { it.size == 32 && encodeLinkCode(it) == code }
```

- [ ] **Step 7: Check the shared model compiles for iOS**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :shared:model:compileKotlinIosSimulatorArm64 --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.devices.*' --tests 'app.logdate.server.auth.AccessTokenVerifierTest' --console=plain`
Expected: PASS.

- [ ] **Step 9: Format, lint, and run the suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :shared:model:ktlintFormat :shared:model:ktlintCheck :server:ktlintFormat :server:ktlintCheck :server:detekt :server:test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-device-link-service.txt <<'MSG'
chore(server): add linked device sessions with one-time link codes

DeviceLinkService mints a 256-bit code that a signed-in phone hands to
a watch; the watch redeems it within two minutes for its own 30-day
session with sync-only tokens. An account keeps at most five linked
devices and mints at most ten codes an hour. A refresh in the last
fifteen days of a session renews it and returns a new refresh token.

AccessTokenVerifier is the one check every route can share: full
tokens pass, and a linked device token passes only where a route
allows sync scope and while DeviceSessionGuard, which caches answers
for 30 seconds, says its session is active.

No route uses them yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add shared/model/src/commonMain/kotlin/app/logdate/shared/model/devices/DeviceLinkModels.kt server/src/main/kotlin/app/logdate/server/devices/DeviceSessionGuard.kt server/src/main/kotlin/app/logdate/server/auth/AccessTokenVerifier.kt server/src/main/kotlin/app/logdate/server/devices/DeviceLinkService.kt server/src/test/kotlin/app/logdate/server/devices/DeviceLinkServiceTest.kt server/src/test/kotlin/app/logdate/server/auth/AccessTokenVerifierTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-device-link-service.txt
```

Expected: hooks pass; six files in the commit.

---

### Task 5: Let sync routes accept linked device tokens

**Files:**
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt:3-33` (imports) and `:195-242` (auth helpers)
- Replace: `server/src/main/kotlin/app/logdate/server/routes/SyncRoutes.kt`
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt` (imports, five parameter lists, 18 calls)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncMediaRoutes.kt` (imports, `:41`, four calls)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncStatusRoutes.kt:3`, `:23-27`, `:32`
- Modify: `server/src/test/kotlin/app/logdate/server/SyncTestApp.kt:41-47`, `:83-91`
- Create: `server/src/test/kotlin/app/logdate/server/routes/support/DeviceLinkTestKit.kt`
- Test: `server/src/test/kotlin/app/logdate/server/routes/sync/SyncLinkedDeviceAccessTest.kt`

**Interfaces:**
- Consumes: Task 4's `AccessTokenVerifier`, `AccessCheck`, `bearerToken`, `DeviceSessionGuard`, `DeviceLinkService`, `DeviceLinkErrorCodes`.
- Produces:
  - `internal suspend fun extractSyncUserId(call: ApplicationCall, verifier: AccessTokenVerifier?): UUID?` in `SyncHelpers.kt`: full tokens and active linked-device tokens pass; a removed session answers `401 DEVICE_SESSION_REVOKED`; a missing verifier answers `500 SERVER_MISCONFIGURED`; messages and codes for every other case match today's `extractUserId`.
  - `extractUserId` (still used by backups, location history, diagnostics, maintenance, metrics, device enrollment and key envelopes) answers `403 DEVICE_SCOPE_DENIED` to a linked device token.
  - `syncRoutes(…, accessTokenVerifier: AccessTokenVerifier? = tokenService?.let { AccessTokenVerifier(it, DeviceSessionGuard.Disabled) })` as its last parameter, so all 22 existing callers compile unchanged and keep refusing device tokens.
  - `syncCollectionRoutes(accessTokenVerifier, metrics, collectionsRepository)`, `syncMediaRoutes(accessTokenVerifier = …, …)`, `syncStatusRoutes(tokenService, accessTokenVerifier, metrics, collectionsRepository)`.
  - Test support: `DeviceLinkTestKit(tokenService, accounts, clock)` with `sessions`, `guard` (no cache), `verifier`, `linkService`, `newAccount(username): Uuid`, `linkWatch(accountId, deviceName): DeviceLinkRedeemResponse`; `configureSyncTestApp(accessTokenVerifier = …)`.

- [ ] **Step 1: Write the test support kit**

Create `server/src/test/kotlin/app/logdate/server/routes/support/DeviceLinkTestKit.kt`:

```kotlin
package app.logdate.server.routes.support

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.database.toJavaUUID
import app.logdate.server.devices.DeviceLinkMint
import app.logdate.server.devices.DeviceLinkRedemption
import app.logdate.server.devices.DeviceLinkService
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.devices.InMemoryDeviceSessionRepository
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Device sessions for route tests: an uncached guard, so a revocation applies to the next request. */
@OptIn(ExperimentalUuidApi::class)
internal class DeviceLinkTestKit(
    tokenService: TokenService,
    private val accounts: AccountRepository,
    clock: Clock = Clock.System,
) {
    val sessions = InMemoryDeviceSessionRepository()
    val guard = DeviceSessionGuard(sessions, cacheTtl = Duration.ZERO, clock = clock)
    val verifier = AccessTokenVerifier(tokenService, guard)
    val linkService = DeviceLinkService(sessions, tokenService, accounts, guard, clock)

    suspend fun newAccount(username: String): Uuid {
        val id = Uuid.random()
        accounts.create(Account(id = id, username = username, displayName = username, createdAt = Clock.System.now()))
        return id
    }

    suspend fun linkWatch(
        accountId: Uuid,
        deviceName: String = "Pixel Watch 3",
    ): DeviceLinkRedeemResponse {
        val code = assertIs<DeviceLinkMint.Minted>(linkService.mint(accountId.toJavaUUID())).code.code
        return assertIs<DeviceLinkRedemption.Redeemed>(linkService.redeem(DeviceLinkRedeemRequest(code, deviceName))).response
    }
}
```

- [ ] **Step 2: Write the failing route test**

Create `server/src/test/kotlin/app/logdate/server/routes/sync/SyncLinkedDeviceAccessTest.kt`:

```kotlin
package app.logdate.server.routes.sync

import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.database.toJavaUUID
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.support.DeviceLinkTestKit
import app.logdate.server.routes.support.contentUploadBody
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class SyncLinkedDeviceAccessTest {
    private val tokenService = JwtTokenService("linked-device-sync-secret")

    @Test
    fun `a linked device writes and reads entries until its session is removed`() =
        testApplication {
            val kit = DeviceLinkTestKit(tokenService, InMemoryAccountRepository())
            syncApp(kit.verifier)
            val owner = kit.newAccount("sync_owner")
            val watch = kit.linkWatch(owner)
            val watchBearer = "Bearer ${watch.tokens.accessToken}"
            val phoneBearer = "Bearer ${tokenService.generateAccessToken(owner.toString())}"

            assertEquals(HttpStatusCode.Created, client.putNote(watchBearer, "watch-note-1").status)
            assertEquals(HttpStatusCode.OK, client.getAs(watchBearer, "/api/v1/contents/watch-note-1").status)
            assertEquals(HttpStatusCode.OK, client.getAs(watchBearer, "/api/v1/ops/sync/status").status)
            assertEquals(HttpStatusCode.OK, client.getAs(phoneBearer, "/api/v1/contents/watch-note-1").status)

            kit.linkService.revoke(owner.toJavaUUID(), UUID.fromString(watch.sessionId))

            assertSyncError(
                client.putNote(watchBearer, "watch-note-2"),
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
            assertEquals(HttpStatusCode.Created, client.putNote(phoneBearer, "phone-note-1").status)
        }

    @Test
    fun `sync routes without a session store refuse linked device tokens`() =
        testApplication {
            val kit = DeviceLinkTestKit(tokenService, InMemoryAccountRepository())
            syncApp(AccessTokenVerifier(tokenService, DeviceSessionGuard.Disabled))
            val watch = kit.linkWatch(kit.newAccount("no_store_owner"))

            assertSyncError(
                client.putNote("Bearer ${watch.tokens.accessToken}", "watch-note-1"),
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
        }

    @Test
    fun `account wide sync routes refuse linked device tokens`() =
        testApplication {
            val kit = DeviceLinkTestKit(tokenService, InMemoryAccountRepository())
            syncApp(kit.verifier)
            val owner = kit.newAccount("account_wide_owner")
            val watchBearer = "Bearer ${kit.linkWatch(owner).tokens.accessToken}"

            val phoneBearer = "Bearer ${tokenService.generateAccessToken(owner.toString())}"

            val denied = DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED
            assertSyncError(client.getAs(watchBearer, "/api/v1/backups"), HttpStatusCode.Forbidden, denied)
            assertSyncError(client.getAs(watchBearer, "/api/v1/ops/sync/metrics"), HttpStatusCode.Forbidden, denied)
            assertEquals(HttpStatusCode.OK, client.getAs(phoneBearer, "/api/v1/backups").status)
        }

    private fun ApplicationTestBuilder.syncApp(verifier: AccessTokenVerifier) {
        val repository = InMemorySyncRepository()
        application {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1") {
                    syncRoutes(
                        tokenService = tokenService,
                        metrics = SyncMetricsRegistry(),
                        collectionsRepository = repository.asLogDateCollectionsRepository(),
                        mediaBlobRepository = repository.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                        backupRepository = repository.asLogDateBackupRepository(),
                        accessTokenVerifier = verifier,
                    )
                }
            }
        }
    }

    private suspend fun HttpClient.putNote(
        bearer: String,
        id: String,
    ): HttpResponse =
        put("/api/v1/contents/$id") {
            header(HttpHeaders.Authorization, bearer)
            contentType(ContentType.Application.Json)
            setBody(contentUploadBody(id = id))
        }

    private suspend fun HttpClient.getAs(
        bearer: String,
        path: String,
    ): HttpResponse = get(path) { header(HttpHeaders.Authorization, bearer) }

    private suspend fun assertSyncError(
        response: HttpResponse,
        status: HttpStatusCode,
        code: String,
    ) {
        assertEquals(status, response.status)
        assertEquals(code, Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("code").jsonPrimitive.content)
    }
}
```

- [ ] **Step 3: Run the test and watch it fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.routes.sync.SyncLinkedDeviceAccessTest' --console=plain`
Expected: FAIL at compile time: `No parameter with name 'accessTokenVerifier' found` in the `syncRoutes(…)` call.

- [ ] **Step 4: Teach `SyncHelpers.kt` both checks**

Add these imports to `SyncHelpers.kt` (ktlintFormat orders them):

```kotlin
import app.logdate.server.auth.AccessCheck
import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.TokenScope
import app.logdate.server.auth.bearerToken
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
```

Replace lines 195-242 (from `// --- Auth helpers` through the closing brace of `extractUserId`) with:

```kotlin
// --- Auth helpers -------------------------------------------------------------------------------

private const val DEVICE_SESSION_REVOKED_MESSAGE = "This device was removed from the account"
private const val DEVICE_SCOPE_DENIED_MESSAGE = "A linked device cannot do this"

/**
 * Extracts the user ID from a full-access token. The routes that call this act on the whole account
 * (backups, location history, diagnostics, maintenance, device enrollment, key envelopes), so a linked
 * device token answers 403. Returns null and responds with 401/403/500 if authentication fails, so
 * callers just early-return.
 */
internal suspend fun extractUserId(
    call: ApplicationCall,
    tokenService: TokenService?,
): UUID? {
    if (tokenService == null) {
        call.respond(
            HttpStatusCode.InternalServerError,
            error("SERVER_MISCONFIGURED", "Token service is not configured"),
        )
        return null
    }

    val authHeader = call.request.header(HttpHeaders.Authorization)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respond(
            HttpStatusCode.Unauthorized,
            error("UNAUTHORIZED", "Missing or invalid Authorization header"),
        )
        return null
    }

    val token = authHeader.removePrefix("Bearer ").trim()
    val accountId = tokenService.validateAccessToken(token)
    if (accountId == null) {
        if (tokenService.decodeAccessToken(token)?.scope == TokenScope.SYNC) {
            return call.rejectAccess(HttpStatusCode.Forbidden, DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED, DEVICE_SCOPE_DENIED_MESSAGE)
        }
        return call.rejectAccess(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Invalid or expired token")
    }

    return call.parseAccountId(accountId)
}

/**
 * Extracts the user ID for a sync data route (contents, journals, associations, drafts, media, sync
 * status, moments). Full-access tokens pass, and so does a linked device token while its session is
 * active; a removed session answers `401 DEVICE_SESSION_REVOKED` so the device knows to stop.
 */
internal suspend fun extractSyncUserId(
    call: ApplicationCall,
    verifier: AccessTokenVerifier?,
): UUID? {
    if (verifier == null) {
        call.respond(
            HttpStatusCode.InternalServerError,
            error("SERVER_MISCONFIGURED", "Token service is not configured"),
        )
        return null
    }
    val token = bearerToken(call.request.header(HttpHeaders.Authorization))
    val claims =
        when (val check = verifier.verify(token, allowSyncScope = true)) {
            is AccessCheck.Granted -> check.claims
            AccessCheck.Missing ->
                return call.rejectAccess(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Missing or invalid Authorization header")
            AccessCheck.Invalid ->
                return call.rejectAccess(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Invalid or expired token")
            AccessCheck.SessionRevoked ->
                return call.rejectAccess(
                    HttpStatusCode.Unauthorized,
                    DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
                    DEVICE_SESSION_REVOKED_MESSAGE,
                )
            AccessCheck.ScopeDenied ->
                return call.rejectAccess(HttpStatusCode.Forbidden, DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED, DEVICE_SCOPE_DENIED_MESSAGE)
        }
    return call.parseAccountId(claims.accountId)
}

private suspend fun ApplicationCall.rejectAccess(
    status: HttpStatusCode,
    code: String,
    message: String,
): UUID? {
    respond(status, error(code, message))
    return null
}

private suspend fun ApplicationCall.parseAccountId(accountId: String): UUID? =
    try {
        UUID.fromString(accountId)
    } catch (_: IllegalArgumentException) {
        Napier.e("Account reference validation failed")
        respond(
            HttpStatusCode.Unauthorized,
            error("UNAUTHORIZED", "Invalid token payload"),
        )
        null
    }
```

- [ ] **Step 5: Point the sync data routes at the verifier**

Run:

```bash
perl -0pi -e 's/import app\.logdate\.server\.auth\.TokenService\n/import app.logdate.server.auth.AccessTokenVerifier\n/; s/tokenService: TokenService\?/accessTokenVerifier: AccessTokenVerifier?/g; s/extractUserId\(call, tokenService\)/extractSyncUserId(call, accessTokenVerifier)/g; s/Routes\(tokenService, /Routes(accessTokenVerifier, /g' server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncMediaRoutes.kt
grep -c "extractSyncUserId(call, accessTokenVerifier)" server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncMediaRoutes.kt
grep -n "tokenService" server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncMediaRoutes.kt
```

Expected: the counts are `SyncCollectionRoutes.kt:18` and `SyncMediaRoutes.kt:4`, and the last `grep` prints nothing. `SyncCollectionRoutes.kt` now starts its functions with `internal fun Route.syncCollectionRoutes(accessTokenVerifier: AccessTokenVerifier?, metrics: SyncMetricsRegistry, collectionsRepository: LogDateCollectionsRepository)` and calls `contentRoutes(accessTokenVerifier, metrics, collectionsRepository)` and its three siblings.

In `SyncStatusRoutes.kt`, replace line 3:

```kotlin
import app.logdate.server.auth.TokenService
```

with:

```kotlin
import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.TokenService
```

replace lines 23-27:

```kotlin
internal fun Route.syncStatusRoutes(
    tokenService: TokenService?,
    metrics: SyncMetricsRegistry,
    collectionsRepository: LogDateCollectionsRepository,
) {
```

with:

```kotlin
internal fun Route.syncStatusRoutes(
    tokenService: TokenService?,
    accessTokenVerifier: AccessTokenVerifier?,
    metrics: SyncMetricsRegistry,
    collectionsRepository: LogDateCollectionsRepository,
) {
```

and replace line 32:

```kotlin
            val userId = extractUserId(call, tokenService) ?: return@get
```

with:

```kotlin
            val userId = extractSyncUserId(call, accessTokenVerifier) ?: return@get
```

The two metrics routes keep `extractUserId(call, tokenService)`: they expose server-wide numbers.

- [ ] **Step 6: Replace `SyncRoutes.kt`**

```kotlin
package app.logdate.server.routes

import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.TokenService
import app.logdate.server.crypto.EncryptionService
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.diagnostics.DiagnosticReportService
import app.logdate.server.entitlements.EntitlementEnforcer
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logdate.LogDateMediaBlobRepository
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.routes.sync.SyncRouteConfig
import app.logdate.server.routes.sync.diagnosticReportRoutes
import app.logdate.server.routes.sync.syncBackupRoutes
import app.logdate.server.routes.sync.syncCollectionRoutes
import app.logdate.server.routes.sync.syncMaintenanceRoutes
import app.logdate.server.routes.sync.syncMediaRoutes
import app.logdate.server.routes.sync.syncStatusRoutes
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.SyncMetricsRegistry
import io.ktor.server.routing.Route

/**
 * Sync routes with JWT authentication. All endpoints require a valid Bearer token and scope data
 * by user ID.
 *
 * The actual route handlers live in sibling files under `routes/sync/`:
 *
 *  - [syncStatusRoutes] — `/ops/sync/status`, `/ops/sync/metrics`, Prometheus export.
 *  - [syncCollectionRoutes] — `/contents`, `/journals`, `/associations`, `/drafts`.
 *  - [syncMediaRoutes] — `/media` CRUD + binary download.
 *  - [syncBackupRoutes] — `/backups` CRUD + binary download.
 *  - [syncMaintenanceRoutes] — `/ops/sync/tombstones:purge`, `/ops/backups:purge`.
 *
 * The data routes (status, collections, media) accept a linked device token through
 * [accessTokenVerifier]; every other route needs a full-access token. Without an explicit verifier,
 * linked device tokens are refused everywhere.
 *
 * This function is the single entry point so `Application.module` doesn't need to know about the
 * split and existing callers (including `testApplication` harnesses) stay untouched. Each
 * sub-route takes only the collaborators it uses as individual parameters; the one shared bundle
 * is [SyncRouteConfig], which carries passive policy (currently just [MediaAccessPolicy]).
 */
fun Route.syncRoutes(
    tokenService: TokenService? = null,
    mediaStorage: LogDateBlobStorage? = null,
    metrics: SyncMetricsRegistry,
    mediaAccessPolicy: MediaAccessPolicy = MediaAccessPolicy.fromEnvironment(),
    encryptionService: EncryptionService = EncryptionService.fromEnvironment(),
    collectionsRepository: LogDateCollectionsRepository,
    mediaBlobRepository: LogDateMediaBlobRepository,
    backupRepository: LogDateBackupRepository,
    entitlementEnforcer: EntitlementEnforcer? = null,
    rateLimiter: SlidingWindowRateLimiter? = SlidingWindowRateLimiter(),
    diagnosticReportService: DiagnosticReportService? = null,
    accessTokenVerifier: AccessTokenVerifier? = tokenService?.let { AccessTokenVerifier(it, DeviceSessionGuard.Disabled) },
) {
    val config = SyncRouteConfig(mediaAccessPolicy = mediaAccessPolicy)
    syncStatusRoutes(tokenService, accessTokenVerifier, metrics, collectionsRepository)
    syncCollectionRoutes(accessTokenVerifier, metrics, collectionsRepository)
    syncMediaRoutes(
        accessTokenVerifier = accessTokenVerifier,
        mediaStorage = mediaStorage,
        metrics = metrics,
        encryptionService = encryptionService,
        mediaBlobRepository = mediaBlobRepository,
        entitlementEnforcer = entitlementEnforcer,
        rateLimiter = rateLimiter,
        config = config,
    )
    syncBackupRoutes(
        tokenService = tokenService,
        mediaStorage = mediaStorage,
        metrics = metrics,
        encryptionService = encryptionService,
        backupRepository = backupRepository,
        entitlementEnforcer = entitlementEnforcer,
        rateLimiter = rateLimiter,
        config = config,
    )
    syncMaintenanceRoutes(tokenService, mediaStorage, metrics, collectionsRepository, backupRepository)
    diagnosticReportRoutes(tokenService, diagnosticReportService)
}
```

- [ ] **Step 7: Let `configureSyncTestApp` take a verifier**

In `server/src/test/kotlin/app/logdate/server/SyncTestApp.kt`, add the imports `app.logdate.server.auth.AccessTokenVerifier` and `app.logdate.server.devices.DeviceSessionGuard`; replace lines 46-47:

```kotlin
    mediaAccessPolicy: MediaAccessPolicy = MediaAccessPolicy(useSignedUrls = false, signedUrlTtlHours = 1),
): SyncTestEnvironment {
```

with:

```kotlin
    mediaAccessPolicy: MediaAccessPolicy = MediaAccessPolicy(useSignedUrls = false, signedUrlTtlHours = 1),
    accessTokenVerifier: AccessTokenVerifier? = null,
): SyncTestEnvironment {
```

and replace lines 89-90:

```kotlin
                    mediaBlobRepository = mediaRepository.asLogDateMediaBlobRepository(),
                    backupRepository = backupRepository,
```

with:

```kotlin
                    mediaBlobRepository = mediaRepository.asLogDateMediaBlobRepository(),
                    backupRepository = backupRepository,
                    accessTokenVerifier = accessTokenVerifier ?: AccessTokenVerifier(tokenService, DeviceSessionGuard.Disabled),
```

- [ ] **Step 8: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.routes.sync.*' --tests 'app.logdate.server.routes.Sync*' --console=plain`
Expected: PASS, including `SyncAuthorizationAndOpsPolicyTest` (`Invalid token payload` and `SERVER_MISCONFIGURED` behave as before).

- [ ] **Step 9: Format, lint, and run the suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:ktlintFormat :server:ktlintCheck :server:detekt :server:test --console=plain`
Expected: BUILD SUCCESSFUL. detekt reports nothing new: `contentRoutes`, `syncMediaRoutes` and `syncStatusRoutes` stay in the existing baseline under the same IDs.

- [ ] **Step 10: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-sync-linked-tokens.txt <<'MSG'
chore(server): let sync routes accept linked device tokens

Contents, journals, associations, drafts, media and the sync status
route now check tokens through AccessTokenVerifier, so a watch with a
linked device session can sync until its session is removed; then they
answer 401 DEVICE_SESSION_REVOKED. Backups, location history,
diagnostics, maintenance, device enrollment and key envelopes answer
403 DEVICE_SCOPE_DENIED to a linked device.

Production passes no verifier yet, so every route still refuses linked
device tokens.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt server/src/main/kotlin/app/logdate/server/routes/SyncRoutes.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncMediaRoutes.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncStatusRoutes.kt server/src/test/kotlin/app/logdate/server/SyncTestApp.kt server/src/test/kotlin/app/logdate/server/routes/support/DeviceLinkTestKit.kt server/src/test/kotlin/app/logdate/server/routes/sync/SyncLinkedDeviceAccessTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-sync-linked-tokens.txt
```

Expected: hooks pass; eight files in the commit.

---

### Task 6: Link a device to an account with a one-time code

**Files:**
- Modify: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/ServerDescriptor.kt:27` (insert after)
- Modify: `server/src/main/kotlin/app/logdate/server/ServerDescriptorConfig.kt:50` (insert after)
- Create: `server/src/main/kotlin/app/logdate/server/routes/DeviceRoutes.kt`
- Create: `server/src/main/kotlin/app/logdate/server/routes/docs/DeviceLinkDocs.kt`
- Modify: `server/src/main/kotlin/app/logdate/server/routes/AuthV1Routes.kt:3-20` (imports), `:233-236`, `:317-320`, `:1053-1061`, `:1131-1145`, `:1723-1741`, `:1906-1910`, and insert after `:1919`
- Modify: `server/src/main/kotlin/app/logdate/server/routes/docs/AuthSessionDocs.kt:3-24`, `:169-197`
- Modify: `server/src/main/kotlin/app/logdate/server/routes/docs/AccountDocs.kt:83-89`
- Modify: `server/src/main/kotlin/app/logdate/server/openapi/schemadocs/DeviceSchemaDocs.kt:61` (insert after)
- Modify: `server/src/main/kotlin/app/logdate/server/openapi/schemadocs/AuthSchemaDocs.kt:296-297`
- Modify: `server/src/main/kotlin/app/logdate/server/openapi/ApiTags.kt:100-109`
- Modify: `server/src/main/kotlin/app/logdate/server/di/ServerModule.kt` (imports, after `:284`, after `:447`)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/ServerRouting.kt` (imports, `:126`, `:131`, `:147`, `:175`, `:196`)
- Modify: `server/src/test/kotlin/app/logdate/server/AuthV1TestApp.kt:105`, `:239`
- Modify: `server/src/test/kotlin/app/logdate/server/routes/support/DeviceLinkTestKit.kt` (append)
- Modify: `server/src/test/kotlin/app/logdate/server/routes/auth/AuthProfileAndMetricsRouteContractsTest.kt:66`, `:133`, `:217` (insert after each)
- Modify: `server/src/test/kotlin/app/logdate/server/openapi/OpenApiContractTest.kt` (`PUBLIC_OPERATIONS`, `RATE_LIMITED_WITHOUT_RETRY_AFTER`)
- Modify: `server/src/test/kotlin/app/logdate/server/ServerDescriptorConfigTest.kt` (append a test)
- Test: `server/src/test/kotlin/app/logdate/server/routes/DeviceLinkRoutesTest.kt`
- Test: `server/src/test/kotlin/app/logdate/server/routes/DeviceSessionRevocationTest.kt`

**Interfaces:**
- Consumes: Tasks 2-5.
- Produces:
  - Routes under `/api/v1/auth/devices` (Authentication-family `ApiErrorResponse` envelope):
    - `POST /link` (full access only) → `200 DeviceLinkCodeResponse`; `403 DEVICE_SCOPE_DENIED`, `409 DEVICE_LIMIT_REACHED`, `429 DEVICE_LINK_RATE_LIMITED`.
    - `POST /link/redeem` (public, 10 per minute per IP) → `200 DeviceLinkRedeemResponse`; `400 INVALID_REQUEST | LINK_CODE_INVALID | VALIDATION_ERROR`, `409 DEVICE_LIMIT_REACHED`, `429 RATE_LIMIT_EXCEEDED`.
    - `GET /` (full or linked) → `LinkedDeviceListResponse` with `current = true` on the caller's session.
    - `DELETE /self` (linked only) → `204`; `400 NOT_A_LINKED_DEVICE` for a full token.
    - `DELETE /{id}` (full only) → `204`; `400 VALIDATION_ERROR`, `403`, `404 DEVICE_NOT_FOUND`.
  - `POST /auth/token/refresh` with a device refresh token → `200 RefreshTokenResponseV1` whose `data.refreshToken` is present only when the session was renewed; `401 DEVICE_SESSION_REVOKED` once removed.
  - `GET /auth/me` with a device token → the account plus tokens that keep the same `sid` and `scp=sync`.
  - `PUT/DELETE /auth/me`, `/auth/me/passkeys*`, `/auth/me/email/verify/*`, `/auth/me/entitlement`, `/auth/me/identities` → `403 DEVICE_SCOPE_DENIED` for a device token.
  - `RefreshTokenDataV1.refreshToken: String? = null`; `ServerProtocolFeature.DEVICE_LINKING_V1 = "deviceLinkingV1"`, advertised by every deployment.
  - `internal fun Route.deviceRoutes(linkService: DeviceLinkService, verifier: AccessTokenVerifier, rateLimiter: SlidingWindowRateLimiter = SlidingWindowRateLimiter())`, `internal val DEVICE_LINK_REDEEM_RATE_LIMIT`.
  - `authV1Routes(…, deviceLinkService: DeviceLinkService? = null, accessTokenVerifier: AccessTokenVerifier = AccessTokenVerifier(tokenService, DeviceSessionGuard.Disabled))`; `configureAuthV1TestApp(…, deviceLinkService = null, accessTokenVerifier = null)`.
  - Koin singletons: `DeviceSessionRepository`, `DeviceSessionGuard`, `AccessTokenVerifier`, `DeviceLinkService`.
  - Test support: `configureDeviceLinkTestApp(tokenService, clock)`, `HttpClient.redeemLinkCode`, `HttpClient.refreshTokens`, `HttpResponse.jsonBody()`, `assertApiErrorCode`, `assertSyncErrorCode`.

- [ ] **Step 1: Confirm tolerant decoding is in production**

Ask the developer which commit production runs, then run `git merge-base --is-ancestor c606d8ca4 <that commit> && echo deployed`.
Expected: `deployed`. If it is not, stop: Task 6 may merge, but must not be deployed before a build containing `c606d8ca4` (see Rollout Order). Record the answer in the PR or hand-off notes.

- [ ] **Step 2: Extend the test support**

Append to `server/src/test/kotlin/app/logdate/server/routes/support/DeviceLinkTestKit.kt` and add the imports it needs (`app.logdate.server.auth.InMemoryAccountRepository`, `app.logdate.server.auth.JwtTokenService`, `app.logdate.server.configureAuthV1TestApp`, `app.logdate.server.logdate.asLogDateBackupRepository`, `app.logdate.server.logdate.asLogDateCollectionsRepository`, `app.logdate.server.logdate.asLogDateMediaBlobRepository`, `app.logdate.server.logdate.asLogDateMediaRepository`, `app.logdate.server.routes.deviceRoutes`, `app.logdate.server.routes.syncRoutes`, `app.logdate.server.sync.InMemorySyncRepository`, `app.logdate.server.sync.SyncMetricsRegistry`, `io.ktor.client.HttpClient`, `io.ktor.client.request.post`, `io.ktor.client.request.setBody`, `io.ktor.client.statement.HttpResponse`, `io.ktor.client.statement.bodyAsText`, `io.ktor.http.ContentType`, `io.ktor.http.HttpStatusCode`, `io.ktor.http.contentType`, `io.ktor.server.routing.route`, `io.ktor.server.routing.routing`, `io.ktor.server.testing.ApplicationTestBuilder`, `kotlinx.serialization.json.Json`, `kotlinx.serialization.json.JsonObject`, `kotlinx.serialization.json.jsonObject`, `kotlinx.serialization.json.jsonPrimitive`, `kotlin.test.assertEquals`):

```kotlin

/** The auth routes, `/auth/devices` and the sync routes in one test app, sharing tokens and device sessions. */
internal fun ApplicationTestBuilder.configureDeviceLinkTestApp(
    tokenService: JwtTokenService,
    clock: Clock = Clock.System,
): DeviceLinkTestKit {
    val accounts = InMemoryAccountRepository()
    val kit = DeviceLinkTestKit(tokenService, accounts, clock)
    configureAuthV1TestApp(
        tokenService = tokenService,
        accountRepository = accounts,
        deviceLinkService = kit.linkService,
        accessTokenVerifier = kit.verifier,
    )
    val sync = InMemorySyncRepository()
    application {
        routing {
            route("/api/v1") {
                deviceRoutes(kit.linkService, kit.verifier)
                syncRoutes(
                    tokenService = tokenService,
                    metrics = SyncMetricsRegistry(),
                    collectionsRepository = sync.asLogDateCollectionsRepository(),
                    mediaBlobRepository = sync.asLogDateMediaRepository().asLogDateMediaBlobRepository(),
                    backupRepository = sync.asLogDateBackupRepository(),
                    accessTokenVerifier = kit.verifier,
                )
            }
        }
    }
    return kit
}

internal suspend fun HttpClient.redeemLinkCode(
    code: String,
    deviceName: String = "Pixel Watch 3",
): HttpResponse =
    post("/api/v1/auth/devices/link/redeem") {
        contentType(ContentType.Application.Json)
        setBody("""{"code":"$code","deviceName":"$deviceName"}""")
    }

internal suspend fun HttpClient.refreshTokens(refreshToken: String): HttpResponse =
    post("/api/v1/auth/token/refresh") {
        contentType(ContentType.Application.Json)
        setBody("""{"refreshToken":"$refreshToken"}""")
    }

internal suspend fun HttpResponse.jsonBody(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

/** An Authentication-family error: `{"error":{"code":…}}`. */
internal suspend fun assertApiErrorCode(
    response: HttpResponse,
    status: HttpStatusCode,
    code: String,
) {
    assertEquals(status, response.status)
    assertEquals(code, response.jsonBody().getValue("error").jsonObject.getValue("code").jsonPrimitive.content)
}

/** A sync-family error: `{"code":…}`. */
internal suspend fun assertSyncErrorCode(
    response: HttpResponse,
    status: HttpStatusCode,
    code: String,
) {
    assertEquals(status, response.status)
    assertEquals(code, response.jsonBody().getValue("code").jsonPrimitive.content)
}
```

In `server/src/test/kotlin/app/logdate/server/AuthV1TestApp.kt`, add the imports `app.logdate.server.auth.AccessTokenVerifier`, `app.logdate.server.devices.DeviceLinkService`, `app.logdate.server.devices.DeviceSessionGuard`; replace line 105:

```kotlin
    entitlementService: EntitlementService? = null,
): AuthV1TestEnvironment {
```

with:

```kotlin
    entitlementService: EntitlementService? = null,
    deviceLinkService: DeviceLinkService? = null,
    accessTokenVerifier: AccessTokenVerifier? = null,
): AuthV1TestEnvironment {
```

and replace line 239:

```kotlin
                    emailVerificationService = emailVerificationService,
                )
                identityApiRoutes(
```

with:

```kotlin
                    emailVerificationService = emailVerificationService,
                    deviceLinkService = deviceLinkService,
                    accessTokenVerifier = accessTokenVerifier ?: AccessTokenVerifier(tokenService, DeviceSessionGuard.Disabled),
                )
                identityApiRoutes(
```

- [ ] **Step 3: Write the failing route tests**

Create `server/src/test/kotlin/app/logdate/server/routes/DeviceLinkRoutesTest.kt`:

```kotlin
package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.TokenScope
import app.logdate.server.routes.support.assertApiErrorCode
import app.logdate.server.routes.support.configureDeviceLinkTestApp
import app.logdate.server.routes.support.jsonBody
import app.logdate.server.routes.support.redeemLinkCode
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class DeviceLinkRoutesTest {
    private val tokenService = JwtTokenService("device-link-routes-secret")

    @Test
    fun `a phone mints a one-time code and a watch redeems it for a sync session`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("link_owner")

            val minted = client.post("/api/v1/auth/devices/link") { header(HttpHeaders.Authorization, phone(owner)) }
            assertEquals(HttpStatusCode.OK, minted.status)
            assertEquals("no-store", minted.headers[HttpHeaders.CacheControl])
            val code = minted.jsonBody().getValue("code").jsonPrimitive.content
            assertEquals(43, code.length)

            val redeemed = client.redeemLinkCode(code)
            assertEquals(HttpStatusCode.OK, redeemed.status)
            assertEquals("no-store", redeemed.headers[HttpHeaders.CacheControl])
            val body = redeemed.jsonBody()
            assertEquals(owner.toString(), body.getValue("accountId").jsonPrimitive.content)
            val access = body.getValue("tokens").jsonObject.getValue("accessToken").jsonPrimitive.content
            val claims = tokenService.decodeAccessToken(access)
            assertEquals(TokenScope.SYNC, claims?.scope)
            assertEquals(body.getValue("sessionId").jsonPrimitive.content, claims?.sessionId)

            assertApiErrorCode(client.redeemLinkCode(code), HttpStatusCode.BadRequest, DeviceLinkErrorCodes.LINK_CODE_INVALID)
        }

    @Test
    fun `minting needs a signed-in account and redeeming needs a well formed request`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("refusals_owner")
            val watch = kit.linkWatch(owner)

            assertApiErrorCode(client.post("/api/v1/auth/devices/link"), HttpStatusCode.Unauthorized, "INVALID_TOKEN")
            assertApiErrorCode(
                client.post("/api/v1/auth/devices/link") { header(HttpHeaders.Authorization, "Bearer ${watch.tokens.accessToken}") },
                HttpStatusCode.Forbidden,
                DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED,
            )
            val malformed =
                client.post("/api/v1/auth/devices/link/redeem") {
                    contentType(ContentType.Application.Json)
                    setBody("{not json")
                }
            assertApiErrorCode(malformed, HttpStatusCode.BadRequest, "INVALID_REQUEST")

            val code = client.mint(phone(owner))
            assertApiErrorCode(client.redeemLinkCode(code, deviceName = ""), HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            assertEquals(HttpStatusCode.OK, client.redeemLinkCode(code).status)
        }

    @Test
    fun `the device list marks the caller and removing another device needs full access`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("list_owner")
            val watch = kit.linkWatch(owner, "Pixel Watch 3")
            val watchBearer = "Bearer ${watch.tokens.accessToken}"

            val fromPhone = client.devices(phone(owner))
            assertEquals("Pixel Watch 3", fromPhone.single().jsonObject.getValue("deviceName").jsonPrimitive.content)
            assertNull(fromPhone.single().jsonObject["current"])
            assertTrue(client.devices(watchBearer).single().jsonObject.getValue("current").jsonPrimitive.boolean)

            val path = "/api/v1/auth/devices/${watch.sessionId}"
            assertApiErrorCode(client.deleteAs(watchBearer, path), HttpStatusCode.Forbidden, DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED)
            val unknown = client.deleteAs(phone(owner), "/api/v1/auth/devices/${Uuid.random()}")
            assertApiErrorCode(unknown, HttpStatusCode.NotFound, DeviceLinkErrorCodes.DEVICE_NOT_FOUND)
            val malformedId = client.deleteAs(phone(owner), "/api/v1/auth/devices/not-a-uuid")
            assertApiErrorCode(malformedId, HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            val stranger = kit.newAccount("list_stranger")
            assertApiErrorCode(client.deleteAs(phone(stranger), path), HttpStatusCode.NotFound, DeviceLinkErrorCodes.DEVICE_NOT_FOUND)

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(phone(owner), path).status)
            assertTrue(client.devices(phone(owner)).isEmpty())
        }

    @Test
    fun `a linked device can unlink itself but a signed-in phone cannot use that route`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("self_owner")
            val watchBearer = "Bearer ${kit.linkWatch(owner).tokens.accessToken}"

            assertApiErrorCode(
                client.deleteAs(phone(owner), "/api/v1/auth/devices/self"),
                HttpStatusCode.BadRequest,
                DeviceLinkErrorCodes.NOT_A_LINKED_DEVICE,
            )
            assertEquals(HttpStatusCode.NoContent, client.deleteAs(watchBearer, "/api/v1/auth/devices/self").status)
            assertApiErrorCode(
                client.get("/api/v1/auth/devices") { header(HttpHeaders.Authorization, watchBearer) },
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
        }

    @Test
    fun `account passkey email identity and plan routes refuse linked devices`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("scope_owner")
            val watch = kit.linkWatch(owner)
            val bearer = "Bearer ${watch.tokens.accessToken}"

            listOf(
                client.put("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, bearer)
                    contentType(ContentType.Application.Json)
                    setBody("""{"displayName":"Watch"}""")
                },
                client.deleteAs(bearer, "/api/v1/auth/me"),
                client.get("/api/v1/auth/me/passkeys") { header(HttpHeaders.Authorization, bearer) },
                client.post("/api/v1/auth/me/passkeys/begin") { header(HttpHeaders.Authorization, bearer) },
                client.post("/api/v1/auth/me/email/verify/begin") { header(HttpHeaders.Authorization, bearer) },
                client.get("/api/v1/auth/me/entitlement") { header(HttpHeaders.Authorization, bearer) },
                client.get("/api/v1/auth/me/identities") { header(HttpHeaders.Authorization, bearer) },
            ).forEach { assertApiErrorCode(it, HttpStatusCode.Forbidden, DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED) }
            assertApiErrorCode(
                client.get("/api/v1/identity/plc/operations") { header(HttpHeaders.Authorization, bearer) },
                HttpStatusCode.Unauthorized,
                "INVALID_TOKEN",
            )

            val me = client.get("/api/v1/auth/me") { header(HttpHeaders.Authorization, bearer) }
            assertEquals(HttpStatusCode.OK, me.status)
            val tokens = me.jsonBody().getValue("data").jsonObject.getValue("tokens").jsonObject
            val access = tokenService.decodeAccessToken(tokens.getValue("accessToken").jsonPrimitive.content)
            assertEquals(TokenScope.SYNC, access?.scope)
            assertEquals(watch.sessionId, access?.sessionId)
            assertEquals(watch.sessionId, tokenService.decodeRefreshToken(tokens.getValue("refreshToken").jsonPrimitive.content)?.sessionId)
        }

    @Test
    fun `redeeming is limited per address`() =
        testApplication {
            configureDeviceLinkTestApp(tokenService)

            repeat(10) {
                val guess = client.redeemLinkCode("not-a-real-code")
                assertApiErrorCode(guess, HttpStatusCode.BadRequest, DeviceLinkErrorCodes.LINK_CODE_INVALID)
            }
            assertApiErrorCode(client.redeemLinkCode("not-a-real-code"), HttpStatusCode.TooManyRequests, "RATE_LIMIT_EXCEEDED")
        }

    @Test
    fun `an account links at most five devices and mints at most ten codes an hour`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val crowded = kit.newAccount("crowded_owner")
            repeat(5) { kit.linkWatch(crowded, "Watch $it") }
            assertApiErrorCode(client.mintResponse(phone(crowded)), HttpStatusCode.Conflict, DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED)

            val busy = kit.newAccount("busy_owner")
            repeat(10) { assertEquals(HttpStatusCode.OK, client.mintResponse(phone(busy)).status) }
            val eleventh = client.mintResponse(phone(busy))
            assertApiErrorCode(eleventh, HttpStatusCode.TooManyRequests, DeviceLinkErrorCodes.DEVICE_LINK_RATE_LIMITED)
        }

    private fun phone(accountId: Uuid): String = "Bearer ${tokenService.generateAccessToken(accountId.toString())}"

    private suspend fun HttpClient.mintResponse(bearer: String): HttpResponse =
        post("/api/v1/auth/devices/link") { header(HttpHeaders.Authorization, bearer) }

    private suspend fun HttpClient.mint(bearer: String): String = mintResponse(bearer).jsonBody().getValue("code").jsonPrimitive.content

    private suspend fun HttpClient.devices(bearer: String): JsonArray =
        get("/api/v1/auth/devices") { header(HttpHeaders.Authorization, bearer) }.jsonBody().getValue("devices").jsonArray

    private suspend fun HttpClient.deleteAs(
        bearer: String,
        path: String,
    ): HttpResponse = delete(path) { header(HttpHeaders.Authorization, bearer) }
}
```

Create `server/src/test/kotlin/app/logdate/server/routes/DeviceSessionRevocationTest.kt`:

```kotlin
package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.oauth.MutableClock
import app.logdate.server.routes.support.assertApiErrorCode
import app.logdate.server.routes.support.assertSyncErrorCode
import app.logdate.server.routes.support.configureDeviceLinkTestApp
import app.logdate.server.routes.support.contentUploadBody
import app.logdate.server.routes.support.jsonBody
import app.logdate.server.routes.support.refreshTokens
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DeviceSessionRevocationTest {
    private val tokenService = JwtTokenService("device-session-revocation-secret")

    @Test
    fun `removing a device ends its sync access and refresh and account read`() =
        testApplication {
            val kit = configureDeviceLinkTestApp(tokenService)
            val owner = kit.newAccount("revoke_owner")
            val watch = kit.linkWatch(owner)
            val watchBearer = "Bearer ${watch.tokens.accessToken}"
            val phoneBearer = "Bearer ${tokenService.generateAccessToken(owner.toString())}"
            assertEquals(HttpStatusCode.Created, client.putNote(watchBearer, "watch-before-removal").status)
            assertEquals(HttpStatusCode.OK, client.refreshTokens(watch.tokens.refreshToken).status)

            val removed = client.delete("/api/v1/auth/devices/${watch.sessionId}") { header(HttpHeaders.Authorization, phoneBearer) }
            assertEquals(HttpStatusCode.NoContent, removed.status)

            assertSyncErrorCode(
                client.putNote(watchBearer, "watch-after-removal"),
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
            assertApiErrorCode(
                client.refreshTokens(watch.tokens.refreshToken),
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
            assertApiErrorCode(
                client.get("/api/v1/auth/me") { header(HttpHeaders.Authorization, watchBearer) },
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
            )
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/auth/me") { header(HttpHeaders.Authorization, phoneBearer) }.status)
            val kept = client.get("/api/v1/contents/watch-before-removal") { header(HttpHeaders.Authorization, phoneBearer) }
            assertEquals(HttpStatusCode.OK, kept.status)
        }

    @Test
    fun `refreshing renews a linked session only in its last fifteen days`() =
        testApplication {
            val clock = MutableClock(Instant.parse("2026-10-03T12:00:00Z"))
            val kit = configureDeviceLinkTestApp(tokenService, clock)
            val watch = kit.linkWatch(kit.newAccount("renew_owner"))

            val early = client.refreshTokens(watch.tokens.refreshToken)
            assertEquals(HttpStatusCode.OK, early.status)
            assertNull(early.jsonBody().getValue("data").jsonObject["refreshToken"])

            clock.nowValue += 16.days
            val renewed = client.refreshTokens(watch.tokens.refreshToken).jsonBody().getValue("data").jsonObject
            val newRefresh = renewed.getValue("refreshToken").jsonPrimitive.content
            assertEquals(watch.sessionId, tokenService.decodeRefreshToken(newRefresh)?.sessionId)
            assertEquals(watch.sessionId, tokenService.decodeAccessToken(renewed.getValue("accessToken").jsonPrimitive.content)?.sessionId)
            assertEquals(HttpStatusCode.OK, client.refreshTokens(newRefresh).status)
        }

    private suspend fun HttpClient.putNote(
        bearer: String,
        id: String,
    ): HttpResponse =
        put("/api/v1/contents/$id") {
            header(HttpHeaders.Authorization, bearer)
            contentType(ContentType.Application.Json)
            setBody(contentUploadBody(id = id))
        }
}
```

In `AuthProfileAndMetricsRouteContractsTest.kt`, the relaxed `JwtTokenService` mock would otherwise answer the new `decodeAccessToken` / `decodeRefreshToken` calls with mock claims. Insert after line 66 (`every { tokenService.validateRefreshToken(any()) } throws IllegalStateException("refresh-boom")`), after line 133 (`every { tokenService.validateAccessToken("missing-account") } returns Uuid.random().toString()`) and after line 217 (`every { tokenService.validateAccessToken("invalid-token") } returns null`), each time:

```kotlin
            every { tokenService.decodeAccessToken(any()) } returns null
            every { tokenService.decodeRefreshToken(any()) } returns null
```

In `ServerDescriptorConfigTest.kt`, insert before the class's final closing brace:

```kotlin

    @Test
    fun `device linking is advertised on every deployment`() {
        val identity = AtprotoIdentityConfig()
        listOf(DeploymentKind.FIRST_PARTY, DeploymentKind.SELF_HOSTED).forEach { kind ->
            val descriptor = ServerDescriptorConfig(deploymentKind = kind).toDescriptor(identity, "logdate.app", "LogDate")
            assertTrue(descriptor.hasProtocolFeature(ServerProtocolFeature.DEVICE_LINKING_V1), kind.name)
        }
    }
```

In `OpenApiContractTest.kt`, replace:

```kotlin
                "claimDeviceEnrollment",
            )
```

with:

```kotlin
                "claimDeviceEnrollment",
                "redeemDeviceLink",
            )
```

and replace:

```kotlin
                "completePasskeySignin",
                "signinWithGoogle",
            )
        private val RATE_LIMITED_WITH_RETRY_AFTER
```

with:

```kotlin
                "completePasskeySignin",
                "signinWithGoogle",
                "createDeviceLink",
                "redeemDeviceLink",
            )
        private val RATE_LIMITED_WITH_RETRY_AFTER
```

- [ ] **Step 4: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.routes.DeviceLinkRoutesTest' --tests 'app.logdate.server.routes.DeviceSessionRevocationTest' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'deviceRoutes'` in `DeviceLinkTestKit.kt` and `No parameter with name 'deviceLinkService' found` in `AuthV1TestApp.kt`.

- [ ] **Step 5: Add the descriptor flag**

In `ServerDescriptor.kt`, insert after line 27 (`const val DIAGNOSTIC_REPORTS_V1 = "diagnosticReportsV1"`):

```kotlin

    /**
     * Devices link to an account with a one-time code and no sign-in (`/auth/devices`). Their tokens carry
     * `sid` and `scp=sync`, refreshes check that the session still exists, and removed devices answer
     * `401 DEVICE_SESSION_REVOKED`.
     */
    const val DEVICE_LINKING_V1 = "deviceLinkingV1"
```

In `ServerDescriptorConfig.kt`, insert after line 50 (`add(ServerProtocolFeature.RICH_DRAFTS_V1)`):

```kotlin
                    add(ServerProtocolFeature.DEVICE_LINKING_V1)
```

- [ ] **Step 6: Create `DeviceRoutes.kt`**

`server/src/main/kotlin/app/logdate/server/routes/DeviceRoutes.kt`:

```kotlin
package app.logdate.server.routes

import app.logdate.server.auth.AccessCheck
import app.logdate.server.auth.AccessTokenClaims
import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.bearerToken
import app.logdate.server.auth.sha256
import app.logdate.server.devices.DeviceLinkMint
import app.logdate.server.devices.DeviceLinkRedemption
import app.logdate.server.devices.DeviceLinkService
import app.logdate.server.ratelimit.RateLimitPolicy
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.routes.docs.DeviceLinkDocs
import app.logdate.shared.model.ApiError
import app.logdate.shared.model.ApiErrorResponse
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import app.logdate.shared.model.devices.LinkedDeviceListResponse
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import java.util.UUID

/** Per IP address. A link code carries 256 bits, so this only keeps guessing traffic off the database. */
internal val DEVICE_LINK_REDEEM_RATE_LIMIT = RateLimitPolicy(maxRequests = 10, windowSeconds = 60)

private const val REVOKED_MESSAGE = "This device was removed from the account"
private const val SCOPE_MESSAGE = "A linked device cannot do this"

/**
 * `/auth/devices`: a signed-in phone links another device with a one-time code, lists linked devices and
 * removes them; a linked device can remove itself.
 */
internal fun Route.deviceRoutes(
    linkService: DeviceLinkService,
    verifier: AccessTokenVerifier,
    rateLimiter: SlidingWindowRateLimiter = SlidingWindowRateLimiter(),
) {
    route("/auth/devices") {
        createLinkRoute(linkService, verifier)
        redeemLinkRoute(linkService, rateLimiter)
        listDevicesRoute(linkService, verifier)
        unlinkSelfRoute(linkService, verifier)
        revokeDeviceRoute(linkService, verifier)
    }
}

private fun Route.createLinkRoute(
    linkService: DeviceLinkService,
    verifier: AccessTokenVerifier,
) {
    post("/link", DeviceLinkDocs.createLink) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val claims = call.requireAccess(verifier, allowSyncScope = false) ?: return@post
        val accountId = call.accountUuid(claims) ?: return@post
        when (val minted = linkService.mint(accountId)) {
            is DeviceLinkMint.Minted -> call.respond(HttpStatusCode.OK, minted.code)
            DeviceLinkMint.DeviceLimitReached ->
                call.respondDeviceError(
                    HttpStatusCode.Conflict,
                    DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED,
                    "This account already has ${DeviceLinkService.MAX_ACTIVE_DEVICES} linked devices",
                )
            DeviceLinkMint.RateLimited ->
                call.respondDeviceError(
                    HttpStatusCode.TooManyRequests,
                    DeviceLinkErrorCodes.DEVICE_LINK_RATE_LIMITED,
                    "Too many link codes this hour",
                )
        }
    }
}

private fun Route.redeemLinkRoute(
    linkService: DeviceLinkService,
    rateLimiter: SlidingWindowRateLimiter,
) {
    post("/link/redeem", DeviceLinkDocs.redeemLink) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val address = call.request.local.remoteHost
            .ifBlank { "unknown" }
            .sha256()
        if (!rateLimiter.allow("device-link-redeem:$address", DEVICE_LINK_REDEEM_RATE_LIMIT)) {
            return@post call.respondDeviceError(
                HttpStatusCode.TooManyRequests,
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please retry later.",
            )
        }
        val request =
            call.receiveRedeemRequest()
                ?: return@post call.respondDeviceError(HttpStatusCode.BadRequest, "INVALID_REQUEST", "Request body is invalid")
        when (val redeemed = linkService.redeem(request)) {
            is DeviceLinkRedemption.Redeemed -> call.respond(HttpStatusCode.OK, redeemed.response)
            DeviceLinkRedemption.InvalidCode ->
                call.respondDeviceError(
                    HttpStatusCode.BadRequest,
                    DeviceLinkErrorCodes.LINK_CODE_INVALID,
                    "The link code is invalid, expired, or already used",
                )
            DeviceLinkRedemption.InvalidDevice ->
                call.respondDeviceError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Device name or platform is invalid")
            DeviceLinkRedemption.DeviceLimitReached ->
                call.respondDeviceError(
                    HttpStatusCode.Conflict,
                    DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED,
                    "This account already has ${DeviceLinkService.MAX_ACTIVE_DEVICES} linked devices",
                )
        }
    }
}

private fun Route.listDevicesRoute(
    linkService: DeviceLinkService,
    verifier: AccessTokenVerifier,
) {
    get(DeviceLinkDocs.listDevices) {
        val claims = call.requireAccess(verifier, allowSyncScope = true) ?: return@get
        val accountId = call.accountUuid(claims) ?: return@get
        call.respond(LinkedDeviceListResponse(linkService.list(accountId, claims.sessionId)))
    }
}

private fun Route.unlinkSelfRoute(
    linkService: DeviceLinkService,
    verifier: AccessTokenVerifier,
) {
    delete("/self", DeviceLinkDocs.unlinkSelf) {
        val claims = call.requireAccess(verifier, allowSyncScope = true) ?: return@delete
        val accountId = call.accountUuid(claims) ?: return@delete
        val sessionId =
            claims.sessionId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respondDeviceError(
                    HttpStatusCode.BadRequest,
                    DeviceLinkErrorCodes.NOT_A_LINKED_DEVICE,
                    "This token does not belong to a linked device",
                )
        linkService.revoke(accountId, sessionId)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.revokeDeviceRoute(
    linkService: DeviceLinkService,
    verifier: AccessTokenVerifier,
) {
    delete("/{id}", DeviceLinkDocs.revokeDevice) {
        val claims = call.requireAccess(verifier, allowSyncScope = false) ?: return@delete
        val accountId = call.accountUuid(claims) ?: return@delete
        val sessionId =
            call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respondDeviceError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Device ID must be a UUID")
        if (!linkService.revoke(accountId, sessionId)) {
            return@delete call.respondDeviceError(HttpStatusCode.NotFound, DeviceLinkErrorCodes.DEVICE_NOT_FOUND, "Linked device not found")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun ApplicationCall.requireAccess(
    verifier: AccessTokenVerifier,
    allowSyncScope: Boolean,
): AccessTokenClaims? {
    val token = bearerToken(request.header(HttpHeaders.Authorization))
    if (token.isNullOrBlank()) {
        respondDeviceError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Access token is required")
        return null
    }
    when (val check = verifier.verify(token, allowSyncScope)) {
        is AccessCheck.Granted -> return check.claims
        AccessCheck.SessionRevoked ->
            respondDeviceError(HttpStatusCode.Unauthorized, DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED, REVOKED_MESSAGE)
        AccessCheck.ScopeDenied -> respondDeviceError(HttpStatusCode.Forbidden, DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED, SCOPE_MESSAGE)
        AccessCheck.Missing, AccessCheck.Invalid ->
            respondDeviceError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Invalid or expired access token")
    }
    return null
}

private suspend fun ApplicationCall.accountUuid(claims: AccessTokenClaims): UUID? {
    val accountId = runCatching { UUID.fromString(claims.accountId) }.getOrNull()
    if (accountId == null) respondDeviceError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Invalid or expired access token")
    return accountId
}

private suspend fun ApplicationCall.receiveRedeemRequest(): DeviceLinkRedeemRequest? =
    try {
        receive<DeviceLinkRedeemRequest>()
    } catch (_: BadRequestException) {
        null
    } catch (_: ContentTransformationException) {
        null
    } catch (_: ContentConvertException) {
        null
    } catch (_: SerializationException) {
        null
    }

private suspend fun ApplicationCall.respondDeviceError(
    status: HttpStatusCode,
    code: String,
    message: String,
) {
    respond(status, ApiErrorResponse(ApiError(code, message)))
}
```

- [ ] **Step 7: Create `DeviceLinkDocs.kt`**

`server/src/main/kotlin/app/logdate/server/routes/docs/DeviceLinkDocs.kt`:

```kotlin
package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.DEVICE_LINK_REDEEM_RATE_LIMIT
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.apiError
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.noContent
import app.logdate.server.routes.ok
import app.logdate.server.routes.publicOperation
import app.logdate.server.routes.rateLimited
import app.logdate.shared.model.devices.DeviceLinkCodeResponse
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import app.logdate.shared.model.devices.LinkedDevice
import app.logdate.shared.model.devices.LinkedDeviceListResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

private const val EXAMPLE_SESSION_ID = "8b0f5a4e-2c1d-4f3a-9e8b-7c6d5e4f3a2b"
private const val EXAMPLE_LINK_CODE = "q3J9xV0mH5tY2bW8nK1pR6sD4fG7hL0zC2vB5nM8aQ4"
private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1_000

private val scopeDenied =
    ErrorCase(
        DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED,
        "A linked device sent this request. Only a signed-in phone or computer can do it.",
        "A linked device cannot do this",
    )
private val deviceLimit =
    ErrorCase(
        DeviceLinkErrorCodes.DEVICE_LIMIT_REACHED,
        "The account already has five linked devices. Remove one, then try again.",
        "This account already has 5 linked devices",
    )
private val pixelWatch =
    LinkedDevice(
        id = EXAMPLE_SESSION_ID,
        deviceName = "Pixel Watch 3",
        platform = "wear_os",
        createdAt = DocExamples.EPOCH_NOW,
        lastRefreshedAt = DocExamples.EPOCH_NOW,
        expiresAt = DocExamples.EPOCH_NOW + THIRTY_DAYS_MS,
    )

/** Documentation for `/auth/devices` in `DeviceRoutes.kt`. */
internal object DeviceLinkDocs {
    val createLink: RouteConfig.() -> Unit = {
        bearerOperation(
            "createDeviceLink",
            ApiTags.DEVICES,
            "Create a device link code",
            """
            Mints a one-time code that links another device, such as a watch, to the signed-in account with no
            sign-in on that device. Pass the code to the device over a private channel. It works once and
            expires at `expiresAt`, two minutes after it is minted; the server stores only a hash of it.

            Only a full sign-in can mint codes. An account keeps at most five linked devices and mints at most
            ten codes an hour.
            """,
        )
        response {
            ok("A new link code.", DeviceLinkCodeResponse(EXAMPLE_LINK_CODE, DocExamples.EPOCH_NOW + 120_000))
            apiError(HttpStatusCode.Forbidden, scopeDenied)
            apiError(HttpStatusCode.Conflict, deviceLimit)
            apiError(
                HttpStatusCode.TooManyRequests,
                ErrorCase(
                    DeviceLinkErrorCodes.DEVICE_LINK_RATE_LIMITED,
                    "The account minted ten codes in the last hour. Wait, then try again.",
                    "Too many link codes this hour",
                ),
            )
            bearerUnauthorized(ErrorEnvelope.API)
            apiServerError()
        }
    }

    val redeemLink: RouteConfig.() -> Unit = {
        publicOperation(
            "redeemDeviceLink",
            ApiTags.DEVICES,
            "Redeem a device link code",
            """
            The device being linked exchanges the code from **Create a device link code** for its own session.
            Its tokens carry `scp=sync`: they reach the sync routes (entries, journals, links, drafts, media,
            moments, sync status), **Get the signed-in account** and the linked-device routes, and nothing
            else. Refresh them with **Refresh the access token**. The session lasts 30 days and renews itself
            when a refresh arrives in its last 15 days.

            No `Authorization` header is needed; the code is the credential. A lost response spends the code,
            so ask the phone for a new one.
            """,
        )
        request {
            jsonBody(
                DeviceLinkRedeemRequest(code = EXAMPLE_LINK_CODE, deviceName = "Pixel Watch 3"),
                "The code from the phone, a name the account owner will recognize, and the platform (`wear_os`).",
            )
        }
        response {
            ok(
                "The new device session.",
                DeviceLinkRedeemResponse(accountId = DocExamples.ACCOUNT_ID, sessionId = EXAMPLE_SESSION_ID, tokens = DocExamples.tokens),
            )
            apiError(
                HttpStatusCode.BadRequest,
                DocExamples.apiInvalidRequest,
                ErrorCase(
                    DeviceLinkErrorCodes.LINK_CODE_INVALID,
                    "The code is unknown, expired, or was already used. Ask the phone for a new one.",
                    "The link code is invalid, expired, or already used",
                ),
                ErrorCase(
                    "VALIDATION_ERROR",
                    "`deviceName` is blank, longer than 120 characters or has control characters, or `platform` is not a short " +
                        "lowercase identifier.",
                    "Device name or platform is invalid",
                ),
            )
            apiError(HttpStatusCode.Conflict, deviceLimit)
            rateLimited(DEVICE_LINK_REDEEM_RATE_LIMIT, ErrorEnvelope.API)
            apiServerError()
        }
    }

    val listDevices: RouteConfig.() -> Unit = {
        bearerOperation(
            "listLinkedDevices",
            ApiTags.DEVICES,
            "List linked devices",
            """
            Returns the account's active linked devices, oldest first. A linked device may call it too; its own
            entry has `current: true`. Removed and expired devices are not listed.
            """,
        )
        response {
            ok("The active linked devices.", LinkedDeviceListResponse(listOf(pixelWatch)))
            bearerUnauthorized(ErrorEnvelope.API)
            apiServerError()
        }
    }

    val unlinkSelf: RouteConfig.() -> Unit = {
        bearerOperation(
            "unlinkThisDevice",
            ApiTags.DEVICES,
            "Unlink this device",
            """
            Ends the calling linked device's own session. Its tokens stop working at once on this server and
            within 30 seconds everywhere. Repeating the call after success answers `401 DEVICE_SESSION_REVOKED`.
            """,
        )
        response {
            noContent("The device's session has ended.")
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    DeviceLinkErrorCodes.NOT_A_LINKED_DEVICE,
                    "The token belongs to a full sign-in, not a linked device. Use **Log out** instead.",
                    "This token does not belong to a linked device",
                ),
            )
            bearerUnauthorized(ErrorEnvelope.API)
            apiServerError()
        }
    }

    val revokeDevice: RouteConfig.() -> Unit = {
        bearerOperation(
            "revokeLinkedDevice",
            ApiTags.DEVICES,
            "Remove a linked device",
            """
            Ends a linked device's session from a signed-in phone or computer. The device's next request or
            refresh answers `401 DEVICE_SESSION_REVOKED` (within 30 seconds on every server). Removing a device
            that was already removed succeeds again.
            """,
        )
        request {
            pathParameter<String>("id") {
                description = "The linked device's session ID, from **List linked devices**."
                example("Example") { value = EXAMPLE_SESSION_ID }
            }
        }
        response {
            noContent("The device's session has ended.")
            apiError(HttpStatusCode.BadRequest, ErrorCase("VALIDATION_ERROR", "`id` is not a UUID.", "Device ID must be a UUID"))
            apiError(HttpStatusCode.Forbidden, scopeDenied)
            apiError(
                HttpStatusCode.NotFound,
                ErrorCase(
                    DeviceLinkErrorCodes.DEVICE_NOT_FOUND,
                    "No linked device with this ID belongs to the account.",
                    "Linked device not found",
                ),
            )
            bearerUnauthorized(ErrorEnvelope.API)
            apiServerError()
        }
    }
}
```

- [ ] **Step 8: Update the auth routes**

In `AuthV1Routes.kt`, add these imports (ktlintFormat orders them):

```kotlin
import app.logdate.server.auth.AccessCheck
import app.logdate.server.auth.AccessTokenClaims
import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.auth.TokenScope
import app.logdate.server.auth.bearerToken
import app.logdate.server.devices.DeviceLinkService
import app.logdate.server.devices.DeviceSessionGuard
import app.logdate.server.devices.DeviceSessionRefresh
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
```

Replace lines 233-236:

```kotlin
@Serializable
data class RefreshTokenDataV1(
    val accessToken: String,
)
```

with:

```kotlin
@Serializable
data class RefreshTokenDataV1(
    val accessToken: String,
    /** A new refresh token, sent only when a linked device's session was just renewed. Store it. */
    val refreshToken: String? = null,
)
```

Replace lines 317-320:

```kotlin
    emailVerificationService: EmailVerificationService? = null,
) {
    val rateLimiter = InMemoryAuthRateLimiter()
```

with:

```kotlin
    emailVerificationService: EmailVerificationService? = null,
    deviceLinkService: DeviceLinkService? = null,
    accessTokenVerifier: AccessTokenVerifier = AccessTokenVerifier(tokenService, DeviceSessionGuard.Disabled),
) {
    val rateLimiter = InMemoryAuthRateLimiter()
```

In the refresh route, replace lines 1053-1061:

```kotlin
                    val accountId = tokenService.validateRefreshToken(request.refreshToken)
                    if (accountId == null) {
                        return@post call.respondApiError(
                            HttpStatusCode.Unauthorized,
                            "INVALID_REFRESH_TOKEN",
                            "Invalid or expired refresh token",
                            metrics,
                        )
                    }
```

with:

```kotlin
                    val accountId = tokenService.validateRefreshToken(request.refreshToken)
                    if (accountId == null) {
                        success = call.respondDeviceRefresh(request.refreshToken, tokenService, deviceLinkService, metrics)
                        return@post
                    }
```

Replace lines 1131-1145 (the start of `get("/me", …)` through `call.respond(HttpStatusCode.OK, response)`):

```kotlin
        get("/me", AccountDocs.getCurrentAccount) {
            try {
                val account =
                    resolveAuthenticatedAccount(call, accountRepository, tokenService, metrics)
                        ?: return@get
                val response =
                    issueAuthResponse(
                        account = account,
                        accountRepository = accountRepository,
                        identityRepository = identityRepository,
                        webAuthnService = webAuthnService,
                        atprotoIdentityService = atprotoIdentityService,
                        tokenService = tokenService,
                    )
                call.respond(HttpStatusCode.OK, response)
```

with:

```kotlin
        get("/me", AccountDocs.getCurrentAccount) {
            try {
                val (account, claims) =
                    resolveAuthenticatedSession(call, accountRepository, accessTokenVerifier, metrics)
                        ?: return@get
                val response =
                    issueAuthResponse(
                        account = account,
                        accountRepository = accountRepository,
                        identityRepository = identityRepository,
                        webAuthnService = webAuthnService,
                        atprotoIdentityService = atprotoIdentityService,
                        tokenService = tokenService,
                        deviceSessionId = claims.sessionId,
                    )
                call.respond(HttpStatusCode.OK, response)
```

In `issueAuthResponse` (lines 1723-1741), replace:

```kotlin
    tokenService: TokenService,
): AuthResponse {
    val fresh = atprotoIdentityService.ensureIdentity(accountRepository.findById(account.id) ?: account)
    val identities = identityRepository.findByAccountId(fresh.id)
    val linkedProviders = identities.map { it.provider.name.lowercase() }.distinct().sorted()
    val passkeyCredentialIds = webAuthnService.getPasskeysForUser(fresh.id).map { it.credentialId }

    val tokens =
        AccountTokens(
            accessToken = tokenService.generateAccessToken(fresh.id.toString(), fresh.did),
            refreshToken = tokenService.generateRefreshToken(fresh.id.toString(), fresh.did),
        )
```

with:

```kotlin
    tokenService: TokenService,
    deviceSessionId: String? = null,
): AuthResponse {
    val fresh = atprotoIdentityService.ensureIdentity(accountRepository.findById(account.id) ?: account)
    val identities = identityRepository.findByAccountId(fresh.id)
    val linkedProviders = identities.map { it.provider.name.lowercase() }.distinct().sorted()
    val passkeyCredentialIds = webAuthnService.getPasskeysForUser(fresh.id).map { it.credentialId }

    val accountId = fresh.id.toString()
    // A linked device keeps its session and scope, so this call can never turn it into a full sign-in.
    val tokens =
        if (deviceSessionId == null) {
            AccountTokens(
                accessToken = tokenService.generateAccessToken(accountId, fresh.did),
                refreshToken = tokenService.generateRefreshToken(accountId, fresh.did),
            )
        } else {
            AccountTokens(
                accessToken = tokenService.generateDeviceAccessToken(accountId, fresh.did, deviceSessionId),
                refreshToken = tokenService.generateDeviceRefreshToken(accountId, fresh.did, deviceSessionId),
            )
        }
```

In `resolveAuthenticatedAccount`, replace lines 1906-1910:

```kotlin
    val accountId = tokenService.validateAccessToken(token)
    if (accountId == null) {
        call.respondApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Invalid or expired access token", metrics)
        return null
    }
```

with:

```kotlin
    val accountId = tokenService.validateAccessToken(token)
    if (accountId == null) {
        if (tokenService.decodeAccessToken(token)?.scope == TokenScope.SYNC) {
            call.respondApiError(
                HttpStatusCode.Forbidden,
                DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED,
                "A linked device cannot do this",
                metrics,
            )
            return null
        }
        call.respondApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Invalid or expired access token", metrics)
        return null
    }
```

Insert after line 1919 (the closing brace of `resolveAuthenticatedAccount`):

```kotlin

/** Like [resolveAuthenticatedAccount], but also accepts a linked device while its session is active. */
@OptIn(ExperimentalUuidApi::class)
private suspend fun resolveAuthenticatedSession(
    call: ApplicationCall,
    accountRepository: AccountRepository,
    verifier: AccessTokenVerifier,
    metrics: AuthMetricsRegistry?,
): Pair<Account, AccessTokenClaims>? {
    val token = bearerToken(call.request.header(HttpHeaders.Authorization))
    if (token.isNullOrBlank()) {
        call.respondApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Access token is required", metrics)
        return null
    }
    val claims =
        when (val check = verifier.verify(token, allowSyncScope = true)) {
            is AccessCheck.Granted -> check.claims
            AccessCheck.SessionRevoked -> {
                call.respondApiError(
                    HttpStatusCode.Unauthorized,
                    DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
                    "This device was removed from the account",
                    metrics,
                )
                return null
            }
            AccessCheck.Missing, AccessCheck.Invalid, AccessCheck.ScopeDenied -> {
                call.respondApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Invalid or expired access token", metrics)
                return null
            }
        }
    val account = runCatching { Uuid.parse(claims.accountId) }.getOrNull()?.let { accountRepository.findById(it) }
    if (account == null) {
        call.respondApiError(HttpStatusCode.NotFound, "ACCOUNT_NOT_FOUND", "Account not found", metrics)
        return null
    }
    return account to claims
}

/** Refreshes a linked device's tokens, or answers why it cannot. Returns whether it answered 200. */
private suspend fun ApplicationCall.respondDeviceRefresh(
    refreshToken: String,
    tokenService: TokenService,
    deviceLinkService: DeviceLinkService?,
    metrics: AuthMetricsRegistry,
): Boolean {
    val claims = tokenService.decodeRefreshToken(refreshToken)?.takeIf { it.sessionId != null }
    if (claims == null) {
        respondApiError(HttpStatusCode.Unauthorized, "INVALID_REFRESH_TOKEN", "Invalid or expired refresh token", metrics)
        return false
    }
    when (val refreshed = deviceLinkService?.refresh(claims) ?: DeviceSessionRefresh.Revoked) {
        DeviceSessionRefresh.Revoked -> {
            respondApiError(
                HttpStatusCode.Unauthorized,
                DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
                "This device was removed from the account",
                metrics,
            )
            return false
        }
        is DeviceSessionRefresh.Refreshed ->
            respond(HttpStatusCode.OK, RefreshTokenResponseV1(true, RefreshTokenDataV1(refreshed.accessToken, refreshed.refreshToken)))
    }
    return true
}
```

- [ ] **Step 9: Update the documentation**

In `AuthSessionDocs.kt`, add the import `app.logdate.shared.model.devices.DeviceLinkErrorCodes`. Replace lines 169-175:

```kotlin
            """
            Exchanges a refresh token for a new access token. This is the one authentication call a client makes
            routinely: whenever a request answers `401`, refresh once and retry it. The refresh token itself is
            not rotated; keep using it until it expires or the person logs out.

            No `Authorization` header is needed; the refresh token in the body is the credential.
            """,
```

with:

```kotlin
            """
            Exchanges a refresh token for a new access token. This is the one authentication call a client makes
            routinely: whenever a request answers `401`, refresh once and retry it. The refresh token itself is
            not rotated; keep using it until it expires or the person logs out.

            A linked device's refresh also checks that its session still exists. In the last 15 days of the
            session the response carries a new `refreshToken` as well; store it and use it from then on.

            No `Authorization` header is needed; the refresh token in the body is the credential.
            """,
```

and replace lines 191-195:

```kotlin
                ErrorCase(
                    "REFRESH_TOKEN_REVOKED",
                    "This refresh token was invalidated by **Log out**. Sign the person in again; do not retry.",
                    "Refresh token has been revoked",
                ),
```

with:

```kotlin
                ErrorCase(
                    "REFRESH_TOKEN_REVOKED",
                    "This refresh token was invalidated by **Log out**. Sign the person in again; do not retry.",
                    "Refresh token has been revoked",
                ),
                ErrorCase(
                    DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
                    "The linked device was removed from the account or its session expired. Stop syncing; the device " +
                        "needs a new link from the phone.",
                    "This device was removed from the account",
                ),
```

In `AccountDocs.kt`, replace lines 83-89:

```kotlin
            """
            Returns the account behind the access token you sent, together with a brand-new pair of tokens.
            Apps call it on launch to refresh what they show on the profile screen and to confirm the stored
            token still works.

            Because the response includes fresh tokens, treat it like a sign-in: replace the ones you stored.
            """,
```

with:

```kotlin
            """
            Returns the account behind the access token you sent, together with a brand-new pair of tokens.
            Apps call it on launch to refresh what they show on the profile screen and to confirm the stored
            token still works.

            Because the response includes fresh tokens, treat it like a sign-in: replace the ones you stored.
            A linked device gets tokens for its own session and scope again, never full-access tokens.
            """,
```

In `DeviceSchemaDocs.kt`, insert after line 61 (the closing `),` of `"AccountKeyEnvelope"`):

```kotlin
            "DeviceLinkCodeResponse" to
                SchemaDoc(
                    "A one-time code that links another device to the signed-in account.",
                    mapOf(
                        "code" to "43 characters of unpadded base64url (256 random bits). Works once; pass it to the device privately.",
                        "expiresAt" to "When the code stops working, in Unix epoch milliseconds. Two minutes after it was minted.",
                    ),
                ),
            "DeviceLinkRedeemRequest" to
                SchemaDoc(
                    "A device exchanging a link code for its own session.",
                    mapOf(
                        "code" to "The code from **Create a device link code**.",
                        "deviceName" to
                            "A name the account owner will recognize in the device list. 1–120 characters, no control characters.",
                        "platform" to "A short lowercase identifier for the device type. Watches send `wear_os`.",
                    ),
                ),
            "DeviceLinkRedeemResponse" to
                SchemaDoc(
                    "The new linked device session.",
                    mapOf(
                        "accountId" to "The account the device now belongs to.",
                        "sessionId" to "The device's session ID; the `id` in **List linked devices** and **Remove a linked device**.",
                        "tokens" to "Sync-only tokens (`scp=sync`). Store both; refresh them like any other tokens.",
                    ),
                ),
            "LinkedDevice" to
                SchemaDoc(
                    "One active linked device.",
                    mapOf(
                        "id" to "The device's session ID.",
                        "deviceName" to "The name the device sent when it was linked.",
                        "platform" to "The device type, such as `wear_os`.",
                        "createdAt" to "When the device was linked, in Unix epoch milliseconds.",
                        "lastRefreshedAt" to "When its session was last renewed, in Unix epoch milliseconds.",
                        "expiresAt" to "When its session ends unless renewed, in Unix epoch milliseconds.",
                        "current" to "`true` on the entry for the device that made the request; absent otherwise.",
                    ),
                ),
            "LinkedDeviceListResponse" to
                SchemaDoc("The account's active linked devices.", mapOf("devices" to "Oldest first.")),
```

In `AuthSchemaDocs.kt`, replace lines 296-297:

```kotlin
            "RefreshTokenDataV1" to
                SchemaDoc("The new access token.", mapOf("accessToken" to "Send as `Authorization: Bearer <accessToken>`.")),
```

with:

```kotlin
            "RefreshTokenDataV1" to
                SchemaDoc(
                    "The new access token, and for a linked device whose session was just renewed, a new refresh token.",
                    mapOf(
                        "accessToken" to "Send as `Authorization: Bearer <accessToken>`.",
                        "refreshToken" to "Present only when a linked device's session was renewed. Replace the stored refresh token.",
                    ),
                ),
```

In `ApiTags.kt`, replace lines 106-108:

```kotlin
                    Account-key envelopes contain only client-encrypted ciphertext. A compatible passkey unlocks
                    them locally; the server never receives a journal key or a passkey unlock secret.
                    """.trimIndent(),
```

with:

```kotlin
                    Account-key envelopes contain only client-encrypted ciphertext. A compatible passkey unlocks
                    them locally; the server never receives a journal key or a passkey unlock secret.

                    A watch links differently: the signed-in phone mints a one-time code and the watch redeems it
                    for its own session, with no sign-in on the watch. That session reaches only the sync routes,
                    and the phone can remove it at any time.
                    """.trimIndent(),
```

- [ ] **Step 10: Wire production**

In `ServerModule.kt`, add the imports `app.logdate.server.auth.AccessTokenVerifier`, `app.logdate.server.database.PostgreSQLDeviceSessionRepository`, `app.logdate.server.devices.DeviceLinkService`, `app.logdate.server.devices.DeviceSessionGuard`, `app.logdate.server.devices.DeviceSessionRepository`, `app.logdate.server.devices.InMemoryDeviceSessionRepository`. Insert after line 284 (the closing `}` of `single<DeviceEnrollmentRepository> { … }`):

```kotlin
        single<DeviceSessionRepository> {
            if (isDatabaseAvailable) PostgreSQLDeviceSessionRepository() else InMemoryDeviceSessionRepository()
        }
        single { DeviceSessionGuard(repository = get()) }
```

Insert after line 447 (the closing `}` of `single<TokenService> { … }`):

```kotlin

        single { AccessTokenVerifier(tokenService = get(), guard = get()) }
        single { DeviceLinkService(repository = get(), tokenService = get(), accountRepository = get(), guard = get()) }
```

In `ServerRouting.kt`, add the imports `app.logdate.server.auth.AccessTokenVerifier` and `app.logdate.server.devices.DeviceLinkService`. Insert after line 126 (`val descriptorConfig by inject<ServerDescriptorConfig>()`):

```kotlin
    val deviceLinkService by inject<DeviceLinkService>()
    val accessTokenVerifier by inject<AccessTokenVerifier>()
```

Insert after line 131 (`deviceEnrollmentRoutes(tokenService, deviceEnrollmentRepository, accountRepository)`):

```kotlin
            deviceRoutes(deviceLinkService, accessTokenVerifier)
```

Replace line 147:

```kotlin
                emailVerificationService = emailVerificationService,
            )
            identityApiRoutes(
```

with:

```kotlin
                emailVerificationService = emailVerificationService,
                deviceLinkService = deviceLinkService,
                accessTokenVerifier = accessTokenVerifier,
            )
            identityApiRoutes(
```

Insert after line 175 (`val httpClient by inject<HttpClient>()`):

```kotlin
    val accessTokenVerifier by inject<AccessTokenVerifier>()
```

and replace lines 194-197:

```kotlin
                            app.logdate.server.diagnostics
                                .DiagnosticReportService(diagnosticReportStore, it)
                        },
            )
```

with:

```kotlin
                            app.logdate.server.diagnostics
                                .DiagnosticReportService(diagnosticReportStore, it)
                        },
                accessTokenVerifier = accessTokenVerifier,
            )
```

- [ ] **Step 11: Run the new tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.routes.DeviceLinkRoutesTest' --tests 'app.logdate.server.routes.DeviceSessionRevocationTest' --tests 'app.logdate.server.ServerDescriptorConfigTest' --tests 'app.logdate.server.routes.auth.*' --console=plain`
Expected: PASS.

- [ ] **Step 12: Run the OpenAPI gates and the whole suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.openapi.*' --console=plain`
Expected: PASS. If `SchemaDocumentationTest` names an undocumented schema, the generator chose a different readable name than the class name; add the entry under the name it prints.

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :shared:model:compileKotlinIosSimulatorArm64 :shared:model:ktlintFormat :shared:model:ktlintCheck :server:ktlintFormat :server:ktlintCheck :server:detekt :server:test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 13: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-device-routes.txt <<'MSG'
feat(server): link a device to an account with a one-time code

A signed-in phone can now link a watch without a sign-in on the watch:
POST /auth/devices/link mints a one-time code, and the watch redeems
it at /auth/devices/link/redeem for its own sync-only session. The
phone lists linked devices and removes one with DELETE
/auth/devices/{id}; a watch can remove itself with /auth/devices/self.

A removed device's next request, refresh or /auth/me answers 401
DEVICE_SESSION_REVOKED. /auth/me hands a linked device tokens for its
own session again, and account, passkey, email, identity and plan
routes answer 403 DEVICE_SCOPE_DENIED. A refresh in the last 15 days of
a device session renews it and returns a new refresh token.

Servers advertise this as deviceLinkingV1. Clients that do not read the
flag see no change.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 14: Commit**

```bash
git restore --staged . && git add shared/model/src/commonMain/kotlin/app/logdate/shared/model/ServerDescriptor.kt server/src/main/kotlin/app/logdate/server/ServerDescriptorConfig.kt server/src/main/kotlin/app/logdate/server/routes/DeviceRoutes.kt server/src/main/kotlin/app/logdate/server/routes/docs/DeviceLinkDocs.kt server/src/main/kotlin/app/logdate/server/routes/AuthV1Routes.kt server/src/main/kotlin/app/logdate/server/routes/docs/AuthSessionDocs.kt server/src/main/kotlin/app/logdate/server/routes/docs/AccountDocs.kt server/src/main/kotlin/app/logdate/server/openapi/schemadocs/DeviceSchemaDocs.kt server/src/main/kotlin/app/logdate/server/openapi/schemadocs/AuthSchemaDocs.kt server/src/main/kotlin/app/logdate/server/openapi/ApiTags.kt server/src/main/kotlin/app/logdate/server/di/ServerModule.kt server/src/main/kotlin/app/logdate/server/routes/ServerRouting.kt server/src/test/kotlin/app/logdate/server/AuthV1TestApp.kt server/src/test/kotlin/app/logdate/server/routes/support/DeviceLinkTestKit.kt server/src/test/kotlin/app/logdate/server/routes/auth/AuthProfileAndMetricsRouteContractsTest.kt server/src/test/kotlin/app/logdate/server/openapi/OpenApiContractTest.kt server/src/test/kotlin/app/logdate/server/ServerDescriptorConfigTest.kt server/src/test/kotlin/app/logdate/server/routes/DeviceLinkRoutesTest.kt server/src/test/kotlin/app/logdate/server/routes/DeviceSessionRevocationTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-device-routes.txt
```

Expected: hooks pass; nineteen files in the commit.

---

### Task 7: Add a client for linking devices

**Files:**
- Create: `client/networking/src/commonMain/kotlin/app/logdate/client/networking/DeviceLinkApiClient.kt`
- Test: `client/networking/src/commonTest/kotlin/app/logdate/client/networking/DeviceLinkApiClientTest.kt`

**Interfaces:**
- Consumes: Task 4's shared models; `LogDateConfigRepository.apiBaseUrl` (`TestConfigRepository` answers `http://localhost/api/v1`).
- Produces (package `app.logdate.client.networking`, mirroring `DeviceEnrollmentApiClient`):
  - `class DeviceLinkApiException(val status: Int, val code: String? = null)` with `val isSessionRevoked: Boolean`.
  - `interface DeviceLinkApiClientContract { suspend fun mintLinkCode(accessToken: String): Result<DeviceLinkCodeResponse>; suspend fun redeem(code: String, deviceName: String, platform: String = DeviceLinkPlatform.WEAR_OS): Result<DeviceLinkRedeemResponse>; suspend fun listDevices(accessToken: String): Result<List<LinkedDevice>>; suspend fun revoke(deviceId: String, accessToken: String): Result<Unit>; suspend fun unlinkSelf(accessToken: String): Result<Unit> }`.
  - `class DeviceLinkApiClient(httpClient: HttpClient, configRepository: LogDateConfigRepository) : DeviceLinkApiClientContract`. Not registered in Koin: the watch's linking work (master plan C5/D2) wires it.

- [ ] **Step 1: Write the failing test**

Create `client/networking/src/commonTest/kotlin/app/logdate/client/networking/DeviceLinkApiClientTest.kt`:

```kotlin
package app.logdate.client.networking

import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLinkApiClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val sessionId = "8b0f5a4e-2c1d-4f3a-9e8b-7c6d5e4f3a2b"

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        DeviceLinkApiClient(HttpClient(MockEngine(handler)), TestConfigRepository())

    @Test
    fun `the phone mints a code with its bearer token`() =
        runTest {
            val client =
                client { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/v1/auth/devices/link", request.url.encodedPath)
                    assertEquals("Bearer phone-access", request.headers[HttpHeaders.Authorization])
                    respond("""{"code":"one-time-code","expiresAt":1789221911412}""", HttpStatusCode.OK, jsonHeaders)
                }

            val code = client.mintLinkCode("phone-access").getOrThrow()

            assertEquals("one-time-code", code.code)
            assertEquals(1_789_221_911_412, code.expiresAt)
            assertFalse("one-time-code" in code.toString())
        }

    @Test
    fun `the watch redeems a code without an Authorization header`() =
        runTest {
            val client =
                client { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/v1/auth/devices/link/redeem", request.url.encodedPath)
                    assertNull(request.headers[HttpHeaders.Authorization])
                    assertEquals("""{"code":"one-time-code","deviceName":"Pixel Watch 3"}""", (request.body as TextContent).text)
                    val tokens = """{"accessToken":"watch-access","refreshToken":"watch-refresh"}"""
                    respond("""{"accountId":"account-1","sessionId":"$sessionId","tokens":$tokens}""", HttpStatusCode.OK, jsonHeaders)
                }

            val linked = client.redeem("one-time-code", "Pixel Watch 3").getOrThrow()

            assertEquals("account-1", linked.accountId)
            assertEquals(sessionId, linked.sessionId)
            assertEquals("watch-access", linked.tokens.accessToken)
            assertFalse("watch-access" in linked.toString())
        }

    @Test
    fun `a spent code reports the server error code`() =
        runTest {
            val client =
                client {
                    respond(
                        """{"error":{"code":"LINK_CODE_INVALID","message":"The link code is invalid, expired, or already used"}}""",
                        HttpStatusCode.BadRequest,
                        jsonHeaders,
                    )
                }

            val failure = assertIs<DeviceLinkApiException>(client.redeem("spent", "Pixel Watch 3").exceptionOrNull())

            assertEquals(400, failure.status)
            assertEquals(DeviceLinkErrorCodes.LINK_CODE_INVALID, failure.code)
            assertFalse(failure.isSessionRevoked)
        }

    @Test
    fun `the device list decodes and an absent current flag means another device`() =
        runTest {
            val client =
                client { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/api/v1/auth/devices", request.url.encodedPath)
                    val pixel =
                        """{"id":"a","deviceName":"Pixel Watch 3","platform":"wear_os",""" +
                            """"createdAt":1,"lastRefreshedAt":2,"expiresAt":3}"""
                    val galaxy =
                        """{"id":"b","deviceName":"Galaxy Watch","platform":"wear_os",""" +
                            """"createdAt":4,"lastRefreshedAt":5,"expiresAt":6,"current":true}"""
                    respond("""{"devices":[$pixel,$galaxy]}""", HttpStatusCode.OK, jsonHeaders)
                }

            val devices = client.listDevices("watch-access").getOrThrow()

            assertEquals(listOf("Pixel Watch 3", "Galaxy Watch"), devices.map { it.deviceName })
            assertEquals(listOf(false, true), devices.map { it.current })
        }

    @Test
    fun `removing a device and unlinking this device send DELETE`() =
        runTest {
            val paths = mutableListOf<String>()
            val client =
                client { request ->
                    assertEquals(HttpMethod.Delete, request.method)
                    paths += request.url.encodedPath
                    respond("", HttpStatusCode.NoContent)
                }

            client.revoke(sessionId, "phone-access").getOrThrow()
            client.unlinkSelf("watch-access").getOrThrow()

            assertEquals(listOf("/api/v1/auth/devices/$sessionId", "/api/v1/auth/devices/self"), paths)
        }

    @Test
    fun `a removed device learns its session was revoked`() =
        runTest {
            val client =
                client {
                    respond(
                        """{"error":{"code":"DEVICE_SESSION_REVOKED","message":"This device was removed from the account"}}""",
                        HttpStatusCode.Unauthorized,
                        jsonHeaders,
                    )
                }

            val failure = assertIs<DeviceLinkApiException>(client.listDevices("watch-access").exceptionOrNull())

            assertEquals(401, failure.status)
            assertTrue(failure.isSessionRevoked)
        }

    @Test
    fun `an error without a JSON body still reports the status`() =
        runTest {
            val client = client { respond("upstream unavailable", HttpStatusCode.BadGateway) }

            val failure = assertIs<DeviceLinkApiException>(client.mintLinkCode("phone-access").exceptionOrNull())

            assertEquals(502, failure.status)
            assertNull(failure.code)
        }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:networking:jvmTest --tests 'app.logdate.client.networking.DeviceLinkApiClientTest' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'DeviceLinkApiClient'` and `'DeviceLinkApiException'`.

- [ ] **Step 3: Create `DeviceLinkApiClient.kt`**

```kotlin
package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.devices.DeviceLinkCodeResponse
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import app.logdate.shared.model.devices.DeviceLinkPlatform
import app.logdate.shared.model.devices.DeviceLinkRedeemRequest
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import app.logdate.shared.model.devices.LinkedDevice
import app.logdate.shared.model.devices.LinkedDeviceListResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/** A non-success answer from the device linking API. [code] is the server's error code when it sent one. */
class DeviceLinkApiException(
    val status: Int,
    val code: String? = null,
) : Exception("Device link request failed (HTTP $status${code?.let { ", $it" } ?: ""})") {
    /** The device's session was removed: it must stop syncing and wait for a new link from the phone. */
    val isSessionRevoked: Boolean get() = code == DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED
}

/** `/auth/devices`: a phone links, lists and removes devices; a linked device redeems a code and can unlink itself. */
interface DeviceLinkApiClientContract {
    /** Mints a one-time link code with the phone's full-access [accessToken]. */
    suspend fun mintLinkCode(accessToken: String): Result<DeviceLinkCodeResponse>

    /** Exchanges [code] for this device's own sync-only session. Sends no `Authorization` header. */
    suspend fun redeem(
        code: String,
        deviceName: String,
        platform: String = DeviceLinkPlatform.WEAR_OS,
    ): Result<DeviceLinkRedeemResponse>

    /** The account's active linked devices; the caller's own entry has `current = true`. */
    suspend fun listDevices(accessToken: String): Result<List<LinkedDevice>>

    /** Removes linked device [deviceId] with the phone's full-access [accessToken]. */
    suspend fun revoke(
        deviceId: String,
        accessToken: String,
    ): Result<Unit>

    /** Ends the calling linked device's own session. */
    suspend fun unlinkSelf(accessToken: String): Result<Unit>
}

class DeviceLinkApiClient(
    private val httpClient: HttpClient,
    private val configRepository: LogDateConfigRepository,
) : DeviceLinkApiClientContract {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun mintLinkCode(accessToken: String): Result<DeviceLinkCodeResponse> =
        request {
            val response = httpClient.post("${devicesUrl()}/link") { header(HttpHeaders.Authorization, "Bearer $accessToken") }
            json.decodeFromString<DeviceLinkCodeResponse>(successBody(response))
        }

    override suspend fun redeem(
        code: String,
        deviceName: String,
        platform: String,
    ): Result<DeviceLinkRedeemResponse> =
        request {
            val response =
                httpClient.post("${devicesUrl()}/link/redeem") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(DeviceLinkRedeemRequest.serializer(), DeviceLinkRedeemRequest(code, deviceName, platform)))
                }
            json.decodeFromString<DeviceLinkRedeemResponse>(successBody(response))
        }

    override suspend fun listDevices(accessToken: String): Result<List<LinkedDevice>> =
        request {
            val response = httpClient.get(devicesUrl()) { header(HttpHeaders.Authorization, "Bearer $accessToken") }
            json.decodeFromString<LinkedDeviceListResponse>(successBody(response)).devices
        }

    override suspend fun revoke(
        deviceId: String,
        accessToken: String,
    ): Result<Unit> =
        request {
            successBody(httpClient.delete("${devicesUrl()}/$deviceId") { header(HttpHeaders.Authorization, "Bearer $accessToken") })
            Unit
        }

    override suspend fun unlinkSelf(accessToken: String): Result<Unit> =
        request {
            successBody(httpClient.delete("${devicesUrl()}/self") { header(HttpHeaders.Authorization, "Bearer $accessToken") })
            Unit
        }

    private suspend fun devicesUrl(): String = "${configRepository.apiBaseUrl.first()}/auth/devices"

    private suspend fun successBody(response: HttpResponse): String {
        val body = response.bodyAsText()
        if (response.status.value in 200..299) return body
        val code = runCatching { json.decodeFromString<ErrorEnvelope>(body).error?.code }.getOrNull()
        throw DeviceLinkApiException(response.status.value, code)
    }

    private suspend fun <T> request(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Result.failure(failure)
        }

    @Serializable
    private data class ErrorEnvelope(
        val error: ErrorDetail? = null,
    )

    @Serializable
    private data class ErrorDetail(
        val code: String? = null,
    )
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:networking:jvmTest --tests 'app.logdate.client.networking.DeviceLinkApiClientTest' --console=plain`
Expected: PASS (7 tests).

- [ ] **Step 5: Check iOS compilation and lint**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :client:networking:compileKotlinIosSimulatorArm64 :client:networking:compileTestKotlinIosSimulatorArm64 :client:networking:ktlintFormat :client:networking:ktlintCheck --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-device-link-client.txt <<'MSG'
chore(networking): add a client for linking devices

DeviceLinkApiClient covers /auth/devices: the phone mints a link code,
lists linked devices and removes one; a watch redeems a code without a
sign-in and can unlink itself. Failures keep the server's error code,
and isSessionRevoked tells a watch that it was removed.

Nothing uses the client yet; the watch linking work wires it.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 7: Commit**

```bash
git restore --staged . && git add client/networking/src/commonMain/kotlin/app/logdate/client/networking/DeviceLinkApiClient.kt client/networking/src/commonTest/kotlin/app/logdate/client/networking/DeviceLinkApiClientTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-device-link-client.txt
```

Expected: hooks pass; two files in the commit.

---

### Task 8: Cover linking and removing a watch end to end

**Files:**
- Test: `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/WatchDeviceLinkE2ETest.kt`

**Interfaces:**
- Consumes: `withServerClientHarness` (real Netty server running `Application.module`, so the Koin wiring from Task 6 is exercised), `createAccountWithSyntheticPasskey`, Task 7's `DeviceLinkApiClient`.
- Produces: the master plan's `WatchDeviceLinkE2ETest` journey: mint → redeem → upload a capture made before the link → revoke → 401.

- [ ] **Step 1: Write the journey**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val CAPTURED_BEFORE_LINK = "watch-capture-before-link"
private const val CAPTURED_AFTER_REMOVAL = "watch-capture-after-removal"

class WatchDeviceLinkE2ETest {
    @Test
    fun `a watch linked by code uploads its captures and loses access once removed`() =
        runTest {
            withServerClientHarness {
                val config = DefaultLogDateConfigRepository(initialBackendUrl = baseUrl.removeSuffix("/api/v1"))
                val links = DeviceLinkApiClient(httpClient, config)
                val phone = apiClient.createAccountWithSyntheticPasskey("watch_owner_${Random.nextInt(100000, 999999)}").data
                val phoneToken = phone.tokens.accessToken

                val code = links.mintLinkCode(phoneToken).getOrThrow()
                val watch = links.redeem(code.code, "Pixel Watch 3").getOrThrow()
                assertEquals(phone.account.id.toString(), watch.accountId)
                val reused = assertIs<DeviceLinkApiException>(links.redeem(code.code, "Second watch").exceptionOrNull())
                assertEquals(400, reused.status)
                assertEquals(DeviceLinkErrorCodes.LINK_CODE_INVALID, reused.code)

                assertEquals(HttpStatusCode.Created, putCapture(watch.tokens.accessToken, CAPTURED_BEFORE_LINK).status)
                assertEquals(HttpStatusCode.OK, getCapture(phoneToken, CAPTURED_BEFORE_LINK).status)
                assertEquals(listOf("Pixel Watch 3"), links.listDevices(phoneToken).getOrThrow().map { it.deviceName })
                assertTrue(links.listDevices(watch.tokens.accessToken).getOrThrow().single().current)
                val minting = assertIs<DeviceLinkApiException>(links.mintLinkCode(watch.tokens.accessToken).exceptionOrNull())
                assertEquals(403, minting.status)

                links.revoke(watch.sessionId, phoneToken).getOrThrow()

                val refused = putCapture(watch.tokens.accessToken, CAPTURED_AFTER_REMOVAL)
                assertEquals(HttpStatusCode.Unauthorized, refused.status)
                assertEquals(DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED, refused.json().getValue("code").jsonPrimitive.content)
                val refresh =
                    httpClient.post("$baseUrl/auth/token/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"refreshToken":"${watch.tokens.refreshToken}"}""")
                    }
                assertEquals(HttpStatusCode.Unauthorized, refresh.status)
                assertEquals(
                    DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED,
                    refresh.json().getValue("error").jsonObject.getValue("code").jsonPrimitive.content,
                )
                val unlink = assertIs<DeviceLinkApiException>(links.unlinkSelf(watch.tokens.accessToken).exceptionOrNull())
                assertTrue(unlink.isSessionRevoked)

                assertEquals(HttpStatusCode.OK, getCapture(phoneToken, CAPTURED_BEFORE_LINK).status)
                assertTrue(links.listDevices(phoneToken).getOrThrow().isEmpty())
            }
        }

    private suspend fun ServerClientE2EHarness.putCapture(
        accessToken: String,
        id: String,
    ): HttpResponse {
        val now = System.currentTimeMillis()
        return httpClient.put("$baseUrl/contents/$id") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"$id","type":"AUDIO","content":"LDSE2:opaque","mediaUri":null,""" +
                    """"createdAt":$now,"lastUpdated":$now,"deviceId":"pixel-watch-3"}""",
            )
        }
    }

    private suspend fun ServerClientE2EHarness.getCapture(
        accessToken: String,
        id: String,
    ): HttpResponse = httpClient.get("$baseUrl/contents/$id") { header(HttpHeaders.Authorization, "Bearer $accessToken") }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject
}
```

Save it as `integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/WatchDeviceLinkE2ETest.kt`.

- [ ] **Step 2: Run the journey**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :integration:server-client-e2e:test --tests 'app.logdate.integration.e2e.journeys.WatchDeviceLinkE2ETest' --console=plain`
Expected: PASS. (This is a test-after journey over code Tasks 2-7 already drove test-first; if it fails, the failure is a wiring bug in `ServerModule`/`ServerRouting` from Task 6. Fix it there in a separate `fix(server)` commit before continuing.) To prove the journey can fail, temporarily comment out `deviceRoutes(deviceLinkService, accessTokenVerifier)` in `ServerRouting.kt`, rerun and expect `mintLinkCode` to fail with `DeviceLinkApiException` status 404, then restore the line and rerun to PASS.

- [ ] **Step 3: Run the whole end-to-end suite and lint**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :integration:server-client-e2e:ktlintFormat :integration:server-client-e2e:ktlintCheck :integration:server-client-e2e:test --console=plain`
Expected: BUILD SUCCESSFUL; `DeviceEnrollmentClientServerE2ETest` and `MacTextClientServerE2ETest` still pass.

- [ ] **Step 4: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-watch-link-e2e.txt <<'MSG'
test(server): cover linking and removing a watch end to end

A real server and the networking client now run the whole journey: the
phone mints a code, the watch redeems it once, uploads what it captured
before the link, sees itself in the device list and cannot mint codes.
After the phone removes it, its uploads, refresh and self-unlink all
answer DEVICE_SESSION_REVOKED, while the phone keeps the capture.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 5: Commit**

```bash
git restore --staged . && git add integration/server-client-e2e/src/test/kotlin/app/logdate/integration/e2e/journeys/WatchDeviceLinkE2ETest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-watch-link-e2e.txt
```

Expected: hooks pass; one file in the commit.

---

### Task 9: Add an atomic create-if-absent to the collection store

**Files:**
- Create: `server/src/main/kotlin/app/logdate/server/logdate/ConditionalCreate.kt`
- Create: `server/src/main/kotlin/app/logdate/server/logdate/RepoConditionalWriter.kt`
- Modify: `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsMetadataStore.kt:41` and `:137` (insert after each)
- Modify: `server/src/main/kotlin/app/logdate/server/database/PostgreSQLLogDateCollectionsMetadataStore.kt:92` (insert after)
- Modify: `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsRepository.kt:204` (insert after)
- Modify: `server/src/main/kotlin/app/logdate/server/logdate/RepoBackedLogDateCollectionsRepository.kt:34`, `:71` (insert after each)
- Modify: `server/src/test/kotlin/app/logdate/server/database/PostgreSQLLogDateCollectionsMetadataStoreTest.kt` (append a test)
- Test: `server/src/test/kotlin/app/logdate/server/logdate/ConditionalCreateProbeTest.kt`
- Test: `server/src/test/kotlin/app/logdate/server/database/ConditionalCreatePostgresTest.kt`

**Interfaces:**
- Consumes: `DefaultRepoEngine.putRecordIfAbsent(recordId, value): Result<RepoWriteResult>` (throws `InvalidSwapException` when the key exists), `LogDateCollectionsMetadataStore.metadata` (live rows only, `LogDateCollectionsMetadataStore.kt:37-41`).
- Produces (package `app.logdate.server.logdate`):
  - `sealed interface ConditionalCreate<out T> { Created(record: T), Exists(serverVersion: Long?), Deleted(deletedAt: Long, serverVersion: Long) }`, `internal inline fun <T> ConditionalCreate<Long>.mapCreated(record: (Long) -> T): ConditionalCreate<T>`.
  - `sealed interface LogDateRecordState { Live(serverVersion), Deleted(deletedAt, serverVersion), Absent }`.
  - `data class AssociationCreateResults(created: List<LogDateAssociation>, existing: List<LogDateAssociationRef>, deleted: List<LogDateAssociationDeletion>)`.
  - `LogDateCollectionsMetadataStore.tombstone(userId, collection, recordKey): LogDateCollectionMetadata?` (deleted rows only).
  - `LogDateCollectionsRepository` defaults: `entryState`, `journalState`, `createEntryIfAbsent(userId, entry, respectTombstones)`, `createJournalIfAbsent(userId, journal, respectTombstones)`, `createAssociationsIfAbsent(userId, associations)` (always respects tombstones). `RepoBackedLogDateCollectionsRepository` overrides all five on top of `RepoConditionalWriter`.
  - `internal class RepoConditionalWriter(repoEngine: DefaultRepoEngine, metadataStore: LogDateCollectionsMetadataStore)` with `state(userId, collection, recordKey)` and `createIfAbsent(userId, repoDid, collection, recordId, value, respectTombstones): ConditionalCreate<Long>`.
  - Guarantees: a duplicate create writes no commit, leaves the version and the change feed untouched; a create after a delete with `respectTombstones = true` writes nothing; concurrent creates of one ID produce one record.

- [ ] **Step 1: Write the failing tests**

Create `server/src/test/kotlin/app/logdate/server/logdate/ConditionalCreateProbeTest.kt`:

```kotlin
package app.logdate.server.logdate

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A create-only upload that finds its ID taken must leave no trace: no repo commit, no new server
 * version, nothing in the change feed. A create after a delete must not bring the record back.
 */
@OptIn(ExperimentalUuidApi::class)
class ConditionalCreateProbeTest {
    private class Fixture(
        val repository: LogDateCollectionsRepository,
        val userId: UUID,
        private val blockStore: InMemoryRepoBlockStore?,
        private val repoDid: AtprotoDid?,
    ) {
        suspend fun commits(): Int = blockStore?.listCommits(requireNotNull(repoDid))?.getOrThrow()?.size ?: 0
    }

    private val accountRepository = InMemoryAccountRepository()
    private val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
    private val identityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = signingKeyService,
            config = AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
        )
    private var accounts = 0

    @Test
    fun `a duplicate create makes no commit and leaves the version and change feed alone`() =
        runTest {
            bothStores { store ->
                val first = store.repository.createEntryIfAbsent(store.userId, entry("e1", "first"), respectTombstones = true)
                val version = first.createdRecord().version
                val commits = store.commits()

                val duplicate = store.repository.createEntryIfAbsent(store.userId, entry("e1", "replayed"), respectTombstones = true)

                assertEquals(ConditionalCreate.Exists(version), duplicate)
                assertEquals(commits, store.commits())
                assertEquals(LogDateRecordState.Live(version), store.repository.entryState(store.userId, "e1"))
                val feed = store.repository.entryChanges(store.userId, since = version, limit = 10)
                assertTrue(feed.changes.isEmpty() && feed.deletions.isEmpty(), "the duplicate must not reach the change feed")
                assertEquals("first", store.repository.getEntry(store.userId, "e1")?.content)
            }
        }

    @Test
    fun `a strict create after a delete answers deleted and writes nothing`() =
        runTest {
            bothStores { store ->
                store.repository.createEntryIfAbsent(store.userId, entry("e2", "first"), respectTombstones = true).createdRecord()
                store.repository.deleteEntry(store.userId, "e2", deletedAt = 42L)
                val tombstone = assertIs<LogDateRecordState.Deleted>(store.repository.entryState(store.userId, "e2"))
                assertEquals(42L, tombstone.deletedAt)
                val commits = store.commits()

                val outcome = store.repository.createEntryIfAbsent(store.userId, entry("e2", "resurrected"), respectTombstones = true)

                assertEquals(ConditionalCreate.Deleted(42L, tombstone.serverVersion), outcome)
                assertEquals(commits, store.commits())
                assertNull(store.repository.getEntry(store.userId, "e2"))
                assertEquals(LogDateRecordState.Absent, store.repository.entryState(store.userId, "never-written"))
            }
        }

    @Test
    fun `a create that does not respect tombstones may recreate a deleted entry`() =
        runTest {
            bothStores { store ->
                store.repository.createEntryIfAbsent(store.userId, entry("e3", "first"), respectTombstones = false).createdRecord()
                store.repository.deleteEntry(store.userId, "e3", deletedAt = 43L)

                val recreated = store.repository.createEntryIfAbsent(store.userId, entry("e3", "again"), respectTombstones = false)

                assertEquals("again", recreated.createdRecord().content)
                assertEquals("again", store.repository.getEntry(store.userId, "e3")?.content)
            }
        }

    @Test
    fun `journals follow the same rules`() =
        runTest {
            bothStores { store ->
                val first = store.repository.createJournalIfAbsent(store.userId, journal("j1", "Walks"), respectTombstones = true)
                val version = first.createdRecord().version
                val duplicate = store.repository.createJournalIfAbsent(store.userId, journal("j1", "Other"), respectTombstones = true)
                assertEquals(ConditionalCreate.Exists(version), duplicate)
                assertEquals("Walks", store.repository.getJournal(store.userId, "j1")?.title)

                store.repository.deleteJournal(store.userId, "j1", deletedAt = 44L)
                val outcome = store.repository.createJournalIfAbsent(store.userId, journal("j1", "Back"), respectTombstones = true)
                assertEquals(44L, assertIs<ConditionalCreate.Deleted>(outcome).deletedAt)
                assertIs<LogDateRecordState.Deleted>(store.repository.journalState(store.userId, "j1"))
            }
        }

    @Test
    fun `strict link creation skips live and deleted links`() =
        runTest {
            bothStores { store ->
                val first = store.repository.createAssociationsIfAbsent(store.userId, listOf(link("a"), link("b")))
                assertEquals(listOf("a", "b"), first.created.map { it.entryId })
                store.repository.deleteAssociations(store.userId, listOf(LogDateAssociationRef("j", "b")), deletedAt = 50L)

                val second = store.repository.createAssociationsIfAbsent(store.userId, listOf(link("a"), link("b"), link("c")))

                assertEquals(listOf("c"), second.created.map { it.entryId })
                assertEquals(listOf(LogDateAssociationRef("j", "a")), second.existing)
                assertEquals(listOf("b"), second.deleted.map { it.association.entryId })
                assertEquals(setOf("a", "c"), store.repository.listAssociations(store.userId).map { it.entryId }.toSet())
            }
        }

    private suspend fun bothStores(block: suspend (Fixture) -> Unit) {
        accounts += 1
        val account =
            identityService.ensureIdentity(
                accountRepository.save(
                    Account(id = Uuid.random(), username = "probe_$accounts", displayName = "Probe", createdAt = Clock.System.now()),
                ),
            )
        val blockStore = InMemoryRepoBlockStore()
        val repoBacked =
            RepoBackedLogDateCollectionsRepository(
                accountRepository = accountRepository,
                identityService = identityService,
                signingKeyService = signingKeyService,
                blockStore = blockStore,
                metadataStore = InMemoryLogDateCollectionsMetadataStore(),
            )
        block(Fixture(repoBacked, account.id.toJavaUUID(), blockStore, AtprotoDid.require(requireNotNull(account.did))))
        block(Fixture(SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()), Uuid.random().toJavaUUID(), null, null))
    }

    private fun <T> ConditionalCreate<T>.createdRecord(): T =
        when (this) {
            is ConditionalCreate.Created -> record
            else -> fail("expected a create, got $this")
        }

    private fun entry(
        id: String,
        content: String,
    ) = LogDateEntry(
        id = id,
        type = "TEXT",
        content = content,
        mediaUri = null,
        durationMs = 0L,
        createdAt = 10L,
        lastUpdated = 10L,
        version = 0L,
        deviceId = DeviceId("pixel-watch-3"),
    )

    private fun journal(
        id: String,
        title: String,
    ) = LogDateJournal(
        id = id,
        title = title,
        description = "",
        createdAt = 10L,
        lastUpdated = 10L,
        version = 0L,
        deviceId = DeviceId("pixel-watch-3"),
    )

    private fun link(entryId: String) =
        LogDateAssociation(journalId = "j", entryId = entryId, createdAt = 10L, version = 0L, deviceId = DeviceId("pixel-watch-3"))
}
```

Append to `PostgreSQLLogDateCollectionsMetadataStoreTest.kt`, before the class's final closing brace (add the import `app.logdate.server.logdate.LogDateCollectionMetadata`):

```kotlin

    @Test
    fun `tombstones are read only for deleted rows`() {
        withH2Database(LogDateCollectionStatesTable, LogDateCollectionRecordsTable) {
            runBlocking {
                assertTombstones(PostgreSQLLogDateCollectionsMetadataStore())
                assertTombstones(InMemoryLogDateCollectionsMetadataStore())
            }
        }
    }

    private suspend fun assertTombstones(store: LogDateCollectionsMetadataStore) {
        val userId = UUID.randomUUID()
        val repoDid = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
        store.upsert(userId, repoDid, LogDateCollectionKind.ENTRY, "live")
        store.upsert(userId, repoDid, LogDateCollectionKind.ENTRY, "gone")
        val deleted = assertNotNull(store.delete(userId, repoDid, LogDateCollectionKind.ENTRY, "gone", 1_234L))

        assertNull(store.tombstone(userId, LogDateCollectionKind.ENTRY, "live"))
        assertNull(store.tombstone(userId, LogDateCollectionKind.ENTRY, "never"))
        assertNull(store.tombstone(userId, LogDateCollectionKind.JOURNAL, "gone"))
        assertEquals(
            LogDateCollectionMetadata("gone", deleted.version, 1_234L),
            store.tombstone(userId, LogDateCollectionKind.ENTRY, "gone"),
        )
    }
```

Create `server/src/test/kotlin/app/logdate/server/database/ConditionalCreatePostgresTest.kt` (opt-in, like `CollectionVersionPostgresTest`):

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.ConditionalCreate
import app.logdate.server.logdate.LogDateEntry
import app.logdate.server.logdate.LogDateRecordState
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.shared.model.sync.DeviceId
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume.assumeTrue
import studio.hypertext.atproto.repo.RepoHeadConflictException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Runs against the local test PostgreSQL only; races real repo-head swaps on one entry ID. */
class ConditionalCreatePostgresTest {
    @Test
    fun `concurrent strict creates of one entry produce exactly one record`() =
        runBlocking {
            val url = localTestPostgresUrl()
            val previous = TransactionManager.defaultDatabase
            val source = localTestPostgresDataSource(url)
            val owner = Uuid.random()
            val ownerId = owner.toJavaUUID()
            var did: String? = null
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                val accounts = InMemoryAccountRepository()
                val signing = SigningKeyService(InMemorySigningKeyRepository(), "race-kek")
                val config = AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test")
                val identity = AtprotoIdentityService(accounts, signing, config)
                val username = "race_${owner.toHexString().take(8)}"
                val account =
                    identity.ensureIdentity(
                        accounts.save(Account(id = owner, username = username, displayName = "Race", createdAt = Clock.System.now())),
                    )
                did = account.did
                val repository =
                    RepoBackedLogDateCollectionsRepository(
                        accounts,
                        identity,
                        signing,
                        PostgreSQLRepoBlockStore(),
                        PostgreSQLLogDateCollectionsMetadataStore(),
                    )
                val entry =
                    LogDateEntry("race-entry", "TEXT", "LDSE2:opaque", null, 0L, 10L, 10L, 0L, DeviceId("pixel-watch-3"))

                suspend fun create() = runCatching { repository.createEntryIfAbsent(ownerId, entry, respectTombstones = true) }
                val outcomes = (1..4).map { async(Dispatchers.IO) { create() } }.awaitAll()

                assertEquals(1, outcomes.count { it.getOrNull() is ConditionalCreate.Created }, outcomes.toString())
                assertTrue(outcomes.all { it.isSuccess || it.exceptionOrNull() is RepoHeadConflictException }, outcomes.toString())
                assertIs<LogDateRecordState.Live>(repository.entryState(ownerId, "race-entry"))
                assertEquals(1, repository.entryChanges(ownerId, since = 0L, limit = 10).changes.size)
            } finally {
                transaction {
                    LogDateCollectionRecordsTable.deleteWhere { userId eq ownerId }
                    LogDateCollectionStatesTable.deleteWhere { userId eq ownerId }
                    did?.let { repo ->
                        AtprotoRepoHeadsTable.deleteWhere { repoDid eq repo }
                        AtprotoRepoCommitsTable.deleteWhere { repoDid eq repo }
                        AtprotoRepoBlockLinksTable.deleteWhere { repoDid eq repo }
                    }
                }
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.logdate.ConditionalCreateProbeTest' --tests 'app.logdate.server.database.PostgreSQLLogDateCollectionsMetadataStoreTest' --tests 'app.logdate.server.database.ConditionalCreatePostgresTest' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'createEntryIfAbsent'`, `'ConditionalCreate'`, `'LogDateRecordState'`, `'tombstone'`.

- [ ] **Step 3: Create `ConditionalCreate.kt`**

```kotlin
package app.logdate.server.logdate

/** What a create-only write did. */
sealed interface ConditionalCreate<out T> {
    /** Nothing held the ID, so [record] was written. */
    data class Created<T>(
        val record: T,
    ) : ConditionalCreate<T>

    /** A live record holds the ID and was left alone. [serverVersion] is null only while a racing create finishes. */
    data class Exists(
        val serverVersion: Long?,
    ) : ConditionalCreate<Nothing>

    /** The ID was deleted, and the caller asked not to bring it back. */
    data class Deleted(
        val deletedAt: Long,
        val serverVersion: Long,
    ) : ConditionalCreate<Nothing>
}

/** Whether a record ID is live, deleted, or was never written (or its tombstone was purged). */
sealed interface LogDateRecordState {
    data class Live(
        val serverVersion: Long,
    ) : LogDateRecordState

    data class Deleted(
        val deletedAt: Long,
        val serverVersion: Long,
    ) : LogDateRecordState

    data object Absent : LogDateRecordState
}

/** A create-only association batch, split by what happened to each link. */
data class AssociationCreateResults(
    val created: List<LogDateAssociation>,
    val existing: List<LogDateAssociationRef>,
    val deleted: List<LogDateAssociationDeletion>,
)

internal inline fun <T> ConditionalCreate<Long>.mapCreated(record: (Long) -> T): ConditionalCreate<T> =
    when (this) {
        is ConditionalCreate.Created -> ConditionalCreate.Created(record(this.record))
        is ConditionalCreate.Exists -> this
        is ConditionalCreate.Deleted -> this
    }
```

- [ ] **Step 4: Read tombstones in both metadata stores**

In `LogDateCollectionsMetadataStore.kt`, insert after line 41 (the end of `metadata(…)` in the interface):

```kotlin

    /** The deleted row for [recordKey], or null when the record is live, unknown, or its tombstone was purged. */
    suspend fun tombstone(
        userId: UUID,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata?
```

and after line 137 (the end of `InMemoryLogDateCollectionsMetadataStore.metadata`):

```kotlin

    override suspend fun tombstone(
        userId: UUID,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata? =
        metadataForUser(userId)[LogDateCollectionKey(collection = collection, recordKey = recordKey)]
            ?.takeIf(LogDateCollectionRow::deleted)
            ?.toMetadata()
```

In `PostgreSQLLogDateCollectionsMetadataStore.kt`, insert after line 92 (the end of `metadata(…)`):

```kotlin

    override suspend fun tombstone(
        userId: UUID,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata? =
        withContext(Dispatchers.IO) {
            transaction {
                LogDateCollectionRecordsTable
                    .selectAll()
                    .where {
                        (LogDateCollectionRecordsTable.userId eq userId) and
                            (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                            (LogDateCollectionRecordsTable.recordKey eq recordKey) and
                            (LogDateCollectionRecordsTable.deleted eq true)
                    }.singleOrNull()
                    ?.toCollectionMetadata()
            }
        }
```

- [ ] **Step 5: Create `RepoConditionalWriter.kt`**

```kotlin
package app.logdate.server.logdate

import io.github.aakira.napier.Napier
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InvalidSwapException
import studio.hypertext.atproto.repo.RepoRecordId
import java.util.UUID

/**
 * Create-only writes on the repo engine.
 *
 * The metadata row is read first, so a duplicate or a deleted ID is answered without touching the repo.
 * The write itself is [DefaultRepoEngine.putRecordIfAbsent], which fails with [InvalidSwapException] when
 * the key appeared in the meantime; the repo head's compare-and-swap makes that check atomic across
 * concurrent writers and server instances, so of two racing creates exactly one writes.
 */
internal class RepoConditionalWriter(
    private val repoEngine: DefaultRepoEngine,
    private val metadataStore: LogDateCollectionsMetadataStore,
) {
    suspend fun state(
        userId: UUID,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateRecordState {
        metadataStore.metadata(userId, collection, recordKey)?.let { return LogDateRecordState.Live(it.version) }
        val tombstone = metadataStore.tombstone(userId, collection, recordKey) ?: return LogDateRecordState.Absent
        return LogDateRecordState.Deleted(requireNotNull(tombstone.deletedAt), tombstone.version)
    }

    /** Writes [value] at [recordId] only if no live record holds it; [ConditionalCreate.Created] carries the new version. */
    suspend fun createIfAbsent(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordId: RepoRecordId,
        value: JsonObject,
        respectTombstones: Boolean,
    ): ConditionalCreate<Long> {
        val recordKey = recordId.recordKey.toString()
        when (val state = state(userId, collection, recordKey)) {
            is LogDateRecordState.Live -> return ConditionalCreate.Exists(state.serverVersion)
            is LogDateRecordState.Deleted -> if (respectTombstones) return ConditionalCreate.Deleted(state.deletedAt, state.serverVersion)
            LogDateRecordState.Absent -> Unit
        }
        val written =
            repoEngine.putRecordIfAbsent(recordId, value).getOrElse { failure ->
                if (failure !is InvalidSwapException) throw failure
                return ConditionalCreate.Exists(metadataStore.metadata(userId, collection, recordKey)?.version)
            }
        val metadata =
            try {
                metadataStore.upsert(userId, repoDid, collection, recordKey)
            } catch (failure: Throwable) {
                runCatching { repoEngine.deleteRecord(recordId, swapRecord = written.cid).getOrThrow() }
                    .onFailure { Napier.e("Conditional create could not remove its record after the metadata write failed", it) }
                throw failure
            }
        return ConditionalCreate.Created(metadata.version)
    }
}
```

- [ ] **Step 6: Add the fallbacks to `LogDateCollectionsRepository`**

In `LogDateCollectionsRepository.kt`, insert after line 204 (the end of `associationChanges(…)`):

```kotlin

    /**
     * Whether entry [id] is live, deleted, or unknown. This fallback reads the record and then scans the
     * whole change feed for a tombstone; stores with an index of tombstones override it.
     */
    suspend fun entryState(
        userId: UUID,
        id: String,
    ): LogDateRecordState {
        getEntry(userId, id)?.let { return LogDateRecordState.Live(it.version) }
        val tombstone =
            entryChanges(userId, since = 0L, limit = Int.MAX_VALUE).deletions.lastOrNull { it.id == id }
                ?: return LogDateRecordState.Absent
        return LogDateRecordState.Deleted(tombstone.deletedAt, tombstone.serverVersion)
    }

    /** Whether journal [id] is live, deleted, or unknown. See [entryState]. */
    suspend fun journalState(
        userId: UUID,
        id: String,
    ): LogDateRecordState {
        getJournal(userId, id)?.let { return LogDateRecordState.Live(it.version) }
        val tombstone =
            journalChanges(userId, since = 0L, limit = Int.MAX_VALUE).deletions.lastOrNull { it.id == id }
                ?: return LogDateRecordState.Absent
        return LogDateRecordState.Deleted(tombstone.deletedAt, tombstone.serverVersion)
    }

    /**
     * Writes [entry] only if no live entry holds its ID. A deleted ID answers [ConditionalCreate.Deleted]
     * when [respectTombstones] is set and is recreated otherwise. This fallback reads, then writes; stores
     * that can create atomically override it.
     */
    suspend fun createEntryIfAbsent(
        userId: UUID,
        entry: LogDateEntry,
        respectTombstones: Boolean,
    ): ConditionalCreate<LogDateEntry> {
        val state = entryState(userId, entry.id)
        if (state is LogDateRecordState.Live) return ConditionalCreate.Exists(state.serverVersion)
        if (respectTombstones && state is LogDateRecordState.Deleted) return ConditionalCreate.Deleted(state.deletedAt, state.serverVersion)
        return ConditionalCreate.Created(upsertEntry(userId, entry))
    }

    /** Writes [journal] only if no live journal holds its ID. See [createEntryIfAbsent]. */
    suspend fun createJournalIfAbsent(
        userId: UUID,
        journal: LogDateJournal,
        respectTombstones: Boolean,
    ): ConditionalCreate<LogDateJournal> {
        val state = journalState(userId, journal.id)
        if (state is LogDateRecordState.Live) return ConditionalCreate.Exists(state.serverVersion)
        if (respectTombstones && state is LogDateRecordState.Deleted) return ConditionalCreate.Deleted(state.deletedAt, state.serverVersion)
        return ConditionalCreate.Created(upsertJournal(userId, journal))
    }

    /** Writes only the links that were never written; live and deleted links are reported and left alone. */
    suspend fun createAssociationsIfAbsent(
        userId: UUID,
        associations: List<LogDateAssociation>,
    ): AssociationCreateResults {
        val live = listAssociations(userId).mapTo(mutableSetOf()) { LogDateAssociationRef(it.journalId, it.entryId) }
        val tombstones = associationChanges(userId, since = 0L, limit = Int.MAX_VALUE).deletions.associateBy { it.association }
        val existing = mutableListOf<LogDateAssociationRef>()
        val deleted = mutableListOf<LogDateAssociationDeletion>()
        val fresh = mutableListOf<LogDateAssociation>()
        associations.forEach { association ->
            val ref = LogDateAssociationRef(association.journalId, association.entryId)
            val tombstone = tombstones[ref]
            when {
                ref in live -> existing += ref
                tombstone != null -> deleted += tombstone
                else -> fresh += association
            }
        }
        val created = if (fresh.isEmpty()) emptyList() else upsertAssociations(userId, fresh)
        return AssociationCreateResults(created, existing, deleted)
    }
```

- [ ] **Step 7: Make the repo-backed store atomic**

In `RepoBackedLogDateCollectionsRepository.kt`, insert after line 34 (`private val repoEngine = DefaultRepoEngine(…)`):

```kotlin
    private val conditionalWriter = RepoConditionalWriter(repoEngine, metadataStore)
```

and after line 71 (the end of `journalExists`):

```kotlin

    override suspend fun entryState(
        userId: UUID,
        id: String,
    ): LogDateRecordState = conditionalWriter.state(userId, LogDateCollectionKind.ENTRY, id)

    override suspend fun journalState(
        userId: UUID,
        id: String,
    ): LogDateRecordState = conditionalWriter.state(userId, LogDateCollectionKind.JOURNAL, id)

    override suspend fun createEntryIfAbsent(
        userId: UUID,
        entry: LogDateEntry,
        respectTombstones: Boolean,
    ): ConditionalCreate<LogDateEntry> {
        val repoDid = canonicalRepoDid(userId)
        return conditionalWriter
            .createIfAbsent(
                userId = userId,
                repoDid = repoDid,
                collection = LogDateCollectionKind.ENTRY,
                recordId = entryRecordId(repoDid, entry.id),
                value = entry.toRepoJson(),
                respectTombstones = respectTombstones,
            ).mapCreated { version -> entry.copy(version = version, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun createJournalIfAbsent(
        userId: UUID,
        journal: LogDateJournal,
        respectTombstones: Boolean,
    ): ConditionalCreate<LogDateJournal> {
        val repoDid = canonicalRepoDid(userId)
        return conditionalWriter
            .createIfAbsent(
                userId = userId,
                repoDid = repoDid,
                collection = LogDateCollectionKind.JOURNAL,
                recordId = journalRecordId(repoDid, journal.id),
                value = journal.toRepoJson(),
                respectTombstones = respectTombstones,
            ).mapCreated { version -> journal.copy(version = version, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun createAssociationsIfAbsent(
        userId: UUID,
        associations: List<LogDateAssociation>,
    ): AssociationCreateResults {
        val repoDid = canonicalRepoDid(userId)
        val created = mutableListOf<LogDateAssociation>()
        val existing = mutableListOf<LogDateAssociationRef>()
        val deleted = mutableListOf<LogDateAssociationDeletion>()
        associations.forEach { association ->
            val ref = LogDateAssociationRef(association.journalId, association.entryId)
            val outcome =
                conditionalWriter.createIfAbsent(
                    userId = userId,
                    repoDid = repoDid,
                    collection = LogDateCollectionKind.ASSOCIATION,
                    recordId = associationRecordId(repoDid, association.journalId, association.entryId),
                    value = association.toRepoJson(),
                    respectTombstones = true,
                )
            when (outcome) {
                is ConditionalCreate.Created -> created += association.copy(version = outcome.record)
                is ConditionalCreate.Exists -> existing += ref
                is ConditionalCreate.Deleted -> deleted += LogDateAssociationDeletion(ref, outcome.deletedAt, outcome.serverVersion)
            }
        }
        return AssociationCreateResults(created, existing, deleted)
    }
```

A batch writes one commit per new link (each through `putRecordIfAbsent`), unlike `upsertAssociations`, which writes one commit per batch. Create-only batches come from the watch and hold a handful of links.

- [ ] **Step 8: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.logdate.*' --tests 'app.logdate.server.database.*' --console=plain`
Expected: PASS. `ConditionalCreatePostgresTest` runs against the local test database (`./run dev`, then `./run test:server`) and is skipped, saying why, without one.

- [ ] **Step 9: Format, lint, and run the suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:ktlintFormat :server:ktlintCheck :server:detekt :server:test --console=plain`
Expected: BUILD SUCCESSFUL. `RepoBackedLogDateCollectionsRepository` stays under detekt's 600-line class limit.

- [ ] **Step 10: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-create-if-absent.txt <<'MSG'
chore(server): add an atomic create-if-absent to the collection store

Entries, journals and links can now be created only when their ID is
free. A taken ID is answered from the metadata row, so a duplicate
upload writes no commit and adds nothing to the change feed. The write
itself uses the repo engine's putRecordIfAbsent, whose head swap lets
exactly one of two racing creates win, even across server instances.
A caller can also ask that a deleted ID stay deleted.

No route uses it yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add server/src/main/kotlin/app/logdate/server/logdate/ConditionalCreate.kt server/src/main/kotlin/app/logdate/server/logdate/RepoConditionalWriter.kt server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsMetadataStore.kt server/src/main/kotlin/app/logdate/server/database/PostgreSQLLogDateCollectionsMetadataStore.kt server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsRepository.kt server/src/main/kotlin/app/logdate/server/logdate/RepoBackedLogDateCollectionsRepository.kt server/src/test/kotlin/app/logdate/server/logdate/ConditionalCreateProbeTest.kt server/src/test/kotlin/app/logdate/server/database/PostgreSQLLogDateCollectionsMetadataStoreTest.kt server/src/test/kotlin/app/logdate/server/database/ConditionalCreatePostgresTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-create-if-absent.txt
```

Expected: hooks pass; nine files in the commit.

---

### Task 10: Accept strict create-only uploads and existence checks

**Files:**
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt` (imports; append a section after the auth helpers from Task 5)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt` (imports, `:91-117`, `:252-273`, before `patch("/{contentId}", …)`, `:391-407`)
- Modify: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/sync/SyncModels.kt:227-231`
- Modify: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/ServerDescriptor.kt` (after `DEVICE_LINKING_V1`)
- Modify: `server/src/main/kotlin/app/logdate/server/ServerDescriptorConfig.kt` (after `DEVICE_LINKING_V1`)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/docs/SyncDocSupport.kt` (append)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/docs/SyncCollectionDocs.kt:50`, `:55`, `:95-102`, `:235`, `:240`, `:279-286`
- Modify: `server/src/main/kotlin/app/logdate/server/routes/docs/SyncAssociationDocs.kt:37-39`, `:54-56`
- Modify: `server/src/main/kotlin/app/logdate/server/openapi/schemadocs/SyncSchemaDocs.kt:262-269`
- Modify: `server/src/test/kotlin/app/logdate/server/ServerDescriptorConfigTest.kt` (append a test)
- Test: `server/src/test/kotlin/app/logdate/server/routes/sync/SyncConditionalCreateRoutesTest.kt`

**Interfaces:**
- Consumes: Task 9's repository methods; Task 5's `extractSyncUserId`.
- Produces:
  - `PUT /contents/{id}?ifAbsent=true`, `PUT /journals/{id}?ifAbsent=true`: `201` + `Location` when created; `412 CONTENT_EXISTS` / `JOURNAL_EXISTS` with `details.serverVersion`; `410 CONTENT_DELETED` / `JOURNAL_DELETED` with `details.deletedAt` and `details.serverVersion`. Nothing is written in the 412 and 410 cases.
  - `If-None-Match: *` keeps today's semantics (deleted IDs may be recreated) but now goes through the atomic path, and its `412` gains `details.serverVersion`.
  - `HEAD /contents/{id}`: `200` + `X-LogDate-Server-Version`; `410` + `X-LogDate-Server-Version` + `X-LogDate-Deleted-At`; `404`.
  - `POST /associations?ifAbsent=true`: `200 AssociationUploadResponse(uploadedCount = created, existing = [...], deleted = [...])`.
  - `AssociationUploadResponse.existing: List<AssociationDeleteItem> = emptyList()`, `.deleted: List<AssociationDeletion> = emptyList()` (omitted from JSON when empty).
  - `ServerProtocolFeature.CONDITIONAL_CREATE_V1 = "conditionalCreateV1"`.
  - In `SyncHelpers.kt`: `SERVER_VERSION_HEADER`, `DELETED_AT_HEADER`, `ApplicationCall.isStrictCreate()`, `ApplicationCall.isCreateOnly()`, `ApplicationCall.respondConditionalCreate(outcome, noun, location, created)`, `ApplicationCall.respondRecordState(state)`. Task 12 reuses them for moments.

- [ ] **Step 1: Write the failing tests**

Create `server/src/test/kotlin/app/logdate/server/routes/sync/SyncConditionalCreateRoutesTest.kt`:

```kotlin
package app.logdate.server.routes.sync

import app.logdate.server.configureSyncTestApp
import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.support.configureInMemorySyncApp
import app.logdate.server.routes.support.contentUploadBody
import app.logdate.server.routes.support.journalUploadBody
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Strict create-only uploads, run against the in-memory sync app and the repo-backed app. */
class SyncConditionalCreateRoutesTest {
    @Test
    fun `a duplicate strict create changes nothing`() =
        bothHarnesses { auth ->
            val created = client.putContent(auth, "note-1", "first", strict = true)
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals("/api/v1/contents/note-1", created.headers[HttpHeaders.Location])
            val version = created.json().getValue("serverVersion").jsonPrimitive.long

            val duplicate = client.putContent(auth, "note-1", "replayed", strict = true)

            assertEquals(HttpStatusCode.PreconditionFailed, duplicate.status)
            val error = duplicate.json()
            assertEquals("CONTENT_EXISTS", error.getValue("code").jsonPrimitive.content)
            assertEquals(version.toString(), error.getValue("details").jsonObject.getValue("serverVersion").jsonPrimitive.content)
            val feed = client.getAs(auth, "/api/v1/contents?since=$version").json()
            assertTrue(feed.getValue("changes").jsonArray.isEmpty(), "a duplicate must not reach the change feed")
            assertTrue(feed.getValue("deletions").jsonArray.isEmpty())
            assertEquals("first", client.getAs(auth, "/api/v1/contents/note-1").json().getValue("content").jsonPrimitive.content)
            val head = client.headAs(auth, "/api/v1/contents/note-1")
            assertEquals(HttpStatusCode.OK, head.status)
            assertEquals(version.toString(), head.headers["X-LogDate-Server-Version"])
        }

    @Test
    fun `a strict create after a delete answers gone and the record stays deleted`() =
        bothHarnesses { auth ->
            assertEquals(HttpStatusCode.Created, client.putContent(auth, "note-2", "first", strict = true).status)
            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/contents/note-2").status)

            val gone = client.putContent(auth, "note-2", "resurrected", strict = true)

            assertEquals(HttpStatusCode.Gone, gone.status)
            val error = gone.json()
            assertEquals("CONTENT_DELETED", error.getValue("code").jsonPrimitive.content)
            assertNotNull(error.getValue("details").jsonObject["deletedAt"])
            assertEquals(HttpStatusCode.NotFound, client.getAs(auth, "/api/v1/contents/note-2").status)
            val head = client.headAs(auth, "/api/v1/contents/note-2")
            assertEquals(HttpStatusCode.Gone, head.status)
            assertNotNull(head.headers["X-LogDate-Deleted-At"])
            assertEquals(HttpStatusCode.NotFound, client.headAs(auth, "/api/v1/contents/never-written").status)
        }

    @Test
    fun `If-None-Match still recreates deleted content for the Mac app`() =
        bothHarnesses { auth ->
            assertEquals(HttpStatusCode.Created, client.putContent(auth, "mac-1", "first", ifNoneMatch = true).status)
            val duplicate = client.putContent(auth, "mac-1", "again", ifNoneMatch = true)
            assertEquals(HttpStatusCode.PreconditionFailed, duplicate.status)
            assertNotNull(duplicate.json().getValue("details").jsonObject["serverVersion"])

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/contents/mac-1").status)

            assertEquals(HttpStatusCode.Created, client.putContent(auth, "mac-1", "recreated", ifNoneMatch = true).status)
        }

    @Test
    fun `strict journal creation follows the same rules`() =
        bothHarnesses { auth ->
            assertEquals(HttpStatusCode.Created, client.putJournal(auth, "journal-1", "Walks").status)
            val duplicate = client.putJournal(auth, "journal-1", "Other")
            assertEquals(HttpStatusCode.PreconditionFailed, duplicate.status)
            assertEquals("JOURNAL_EXISTS", duplicate.json().getValue("code").jsonPrimitive.content)

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/journals/journal-1").status)
            val gone = client.putJournal(auth, "journal-1", "Back")

            assertEquals(HttpStatusCode.Gone, gone.status)
            assertEquals("JOURNAL_DELETED", gone.json().getValue("code").jsonPrimitive.content)
        }

    @Test
    fun `strict link creation skips links that exist or were deleted`() =
        bothHarnesses { auth ->
            val first = client.postLinks(auth, "a", "b")
            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(2, first.json().getValue("uploadedCount").jsonPrimitive.int)
            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/associations/journal-1/b").status)

            val second = client.postLinks(auth, "a", "b", "c").json()

            assertEquals(1, second.getValue("uploadedCount").jsonPrimitive.int)
            assertEquals(listOf("a"), second.contentIds("existing"))
            assertEquals(listOf("b"), second.contentIds("deleted"))
        }

    private fun bothHarnesses(block: suspend ApplicationTestBuilder.(auth: String) -> Unit) {
        testApplication { block(authHeader(configureInMemorySyncApp())) }
        testApplication { block(authHeader(configureSyncTestApp().tokenService)) }
    }

    private suspend fun HttpClient.putContent(
        auth: String,
        id: String,
        content: String,
        strict: Boolean = false,
        ifNoneMatch: Boolean = false,
    ): HttpResponse =
        put("/api/v1/contents/$id" + (if (strict) "?ifAbsent=true" else "")) {
            header(HttpHeaders.Authorization, auth)
            if (ifNoneMatch) header(HttpHeaders.IfNoneMatch, "*")
            contentType(ContentType.Application.Json)
            setBody(contentUploadBody(id = id, content = content))
        }

    private suspend fun HttpClient.putJournal(
        auth: String,
        id: String,
        title: String,
    ): HttpResponse =
        put("/api/v1/journals/$id?ifAbsent=true") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(journalUploadBody(id = id, title = title))
        }

    private suspend fun HttpClient.postLinks(
        auth: String,
        vararg contentIds: String,
    ): HttpResponse =
        post("/api/v1/associations?ifAbsent=true") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            val links = contentIds.joinToString(",") { """{"journalId":"journal-1","contentId":"$it","createdAt":1,"deviceId":"watch"}""" }
            setBody("""{"associations":[$links]}""")
        }

    private suspend fun HttpClient.getAs(
        auth: String,
        path: String,
    ): HttpResponse = get(path) { header(HttpHeaders.Authorization, auth) }

    private suspend fun HttpClient.headAs(
        auth: String,
        path: String,
    ): HttpResponse = head(path) { header(HttpHeaders.Authorization, auth) }

    private suspend fun HttpClient.deleteAs(
        auth: String,
        path: String,
    ): HttpResponse = delete(path) { header(HttpHeaders.Authorization, auth) }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun JsonObject.contentIds(list: String): List<String> =
        getValue(list).jsonArray.map { it.jsonObject.getValue("contentId").jsonPrimitive.content }
}
```

In `ServerDescriptorConfigTest.kt`, insert before the class's final closing brace:

```kotlin

    @Test
    fun `strict create-only uploads are advertised on every deployment`() {
        val identity = AtprotoIdentityConfig()
        listOf(DeploymentKind.FIRST_PARTY, DeploymentKind.SELF_HOSTED).forEach { kind ->
            val descriptor = ServerDescriptorConfig(deploymentKind = kind).toDescriptor(identity, "logdate.app", "LogDate")
            assertTrue(descriptor.hasProtocolFeature(ServerProtocolFeature.CONDITIONAL_CREATE_V1), kind.name)
        }
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.routes.sync.SyncConditionalCreateRoutesTest' --tests 'app.logdate.server.ServerDescriptorConfigTest' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'CONDITIONAL_CREATE_V1'`. After Step 3 adds the constant, the route tests fail at runtime: `a duplicate strict create changes nothing` gets `200` instead of `412` (the server ignores `ifAbsent` and replaces the entry), and the `HEAD` assertions get `405 Method Not Allowed`.

- [ ] **Step 3: Add the descriptor flag and the wire fields**

In `ServerDescriptor.kt`, insert after `const val DEVICE_LINKING_V1 = "deviceLinkingV1"`:

```kotlin

    /**
     * `?ifAbsent=true` on `PUT /contents/{id}`, `PUT /journals/{id}`, `PUT /moments/{id}` and
     * `POST /associations` creates only: a live ID answers `412` with `details.serverVersion`, a deleted
     * one answers `410` with `details.deletedAt`, and nothing is written. Also `HEAD /contents/{id}`.
     * Servers without this flag ignore `ifAbsent` and overwrite, so never send it to them.
     */
    const val CONDITIONAL_CREATE_V1 = "conditionalCreateV1"
```

In `ServerDescriptorConfig.kt`, insert after `add(ServerProtocolFeature.DEVICE_LINKING_V1)`:

```kotlin
                    add(ServerProtocolFeature.CONDITIONAL_CREATE_V1)
```

In `SyncModels.kt`, replace lines 227-231:

```kotlin
@Serializable
data class AssociationUploadResponse(
    val uploadedCount: Int,
    val uploadedAt: Long,
)
```

with:

```kotlin
@Serializable
data class AssociationUploadResponse(
    val uploadedCount: Int,
    val uploadedAt: Long,
    /** With `?ifAbsent=true`: links that already existed and were left alone. */
    val existing: List<AssociationDeleteItem> = emptyList(),
    /** With `?ifAbsent=true`: links that were deleted and were not brought back. */
    val deleted: List<AssociationDeletion> = emptyList(),
)
```

- [ ] **Step 4: Add the response helpers**

Add the imports `app.logdate.server.logdate.ConditionalCreate` and `app.logdate.server.logdate.LogDateRecordState` to `SyncHelpers.kt`, and append after `parseAccountId` (the end of the auth helpers from Task 5):

```kotlin

// --- Create-only writes --------------------------------------------------------------------------

internal const val SERVER_VERSION_HEADER = "X-LogDate-Server-Version"
internal const val DELETED_AT_HEADER = "X-LogDate-Deleted-At"

/** `?ifAbsent=true`: create only, and answer 410 instead of recreating a deleted record. */
internal fun ApplicationCall.isStrictCreate(): Boolean = request.queryParameters["ifAbsent"] == "true"

/** `If-None-Match: *`: create only; a deleted record may be recreated, which the Mac app relies on. */
internal fun ApplicationCall.isCreateOnly(): Boolean = request.header(HttpHeaders.IfNoneMatch) == "*"

/**
 * Answers a create-only write: `201` with a `Location`, `412 <NOUN>_EXISTS` with `details.serverVersion`,
 * or `410 <NOUN>_DELETED` with `details.deletedAt` and `details.serverVersion`.
 */
internal suspend inline fun <T, reified R : Any> ApplicationCall.respondConditionalCreate(
    outcome: ConditionalCreate<T>,
    noun: String,
    location: String,
    created: (T) -> R,
) {
    val label = noun.replaceFirstChar(Char::uppercaseChar)
    val code = noun.uppercase()
    when (outcome) {
        is ConditionalCreate.Created -> {
            response.headers.append(HttpHeaders.Location, location)
            respond(HttpStatusCode.Created, created(outcome.record))
        }
        is ConditionalCreate.Exists -> {
            val details = outcome.serverVersion?.let { mapOf("serverVersion" to it.toString()) }.orEmpty()
            respond(HttpStatusCode.PreconditionFailed, error("${code}_EXISTS", "$label already exists", details))
        }
        is ConditionalCreate.Deleted -> {
            val details = mapOf("deletedAt" to outcome.deletedAt.toString(), "serverVersion" to outcome.serverVersion.toString())
            respond(HttpStatusCode.Gone, error("${code}_DELETED", "$label was deleted", details))
        }
    }
}

/** Answers `HEAD` for one record: `200` or `410` with its server version, or `404`. */
internal suspend fun ApplicationCall.respondRecordState(state: LogDateRecordState) {
    when (state) {
        is LogDateRecordState.Live -> {
            response.headers.append(SERVER_VERSION_HEADER, state.serverVersion.toString())
            respond(HttpStatusCode.OK)
        }
        is LogDateRecordState.Deleted -> {
            response.headers.append(SERVER_VERSION_HEADER, state.serverVersion.toString())
            response.headers.append(DELETED_AT_HEADER, state.deletedAt.toString())
            respond(HttpStatusCode.Gone)
        }
        LogDateRecordState.Absent -> respond(HttpStatusCode.NotFound)
    }
}
```

- [ ] **Step 5: Route the create-only writes**

In `SyncCollectionRoutes.kt`, remove the import `io.ktor.server.request.header` (no longer used) and add `app.logdate.shared.model.sync.AssociationDeleteItem` and `io.ktor.server.routing.head`.

Replace lines 91-117 (from `val wasCreated = !collectionsRepository.entryExists(userId, contentId)` through the closing `)` of `collectionsRepository.upsertEntry(…)`):

```kotlin
                val wasCreated = !collectionsRepository.entryExists(userId, contentId)
                if (call.request.header(HttpHeaders.IfNoneMatch) == "*" && !wasCreated) {
                    return@put call.respond(
                        HttpStatusCode.PreconditionFailed,
                        error("CONTENT_EXISTS", "Content already exists"),
                    )
                }
                Napier.d("Content upsert completed")
                val stored =
                    collectionsRepository.upsertEntry(
                        userId = userId,
                        entry =
                            LogDateEntry(
                                id = contentId,
                                type = req.type,
                                content = req.content,
                                mediaUri = req.mediaUri,
                                durationMs = req.durationMs,
                                createdAt = req.createdAt,
                                lastUpdated = req.lastUpdated,
                                version = 0L,
                                deviceId = req.deviceId,
                                caption = req.caption,
                                photoPresentation = req.photoPresentation,
                                location = req.location,
                            ),
                    )
```

with:

```kotlin
                val entry =
                    LogDateEntry(
                        id = contentId,
                        type = req.type,
                        content = req.content,
                        mediaUri = req.mediaUri,
                        durationMs = req.durationMs,
                        createdAt = req.createdAt,
                        lastUpdated = req.lastUpdated,
                        version = 0L,
                        deviceId = req.deviceId,
                        caption = req.caption,
                        photoPresentation = req.photoPresentation,
                        location = req.location,
                    )
                val strictCreate = call.isStrictCreate()
                if (strictCreate || call.isCreateOnly()) {
                    call.respondConditionalCreate(
                        outcome = collectionsRepository.createEntryIfAbsent(userId, entry, respectTombstones = strictCreate),
                        noun = "content",
                        location = "/api/v1/contents/$contentId",
                    ) { ContentUploadResponse(id = it.id, serverVersion = it.version, uploadedAt = it.lastUpdated) }
                    success = true
                    return@put
                }
                val wasCreated = !collectionsRepository.entryExists(userId, contentId)
                Napier.d("Content upsert completed")
                val stored = collectionsRepository.upsertEntry(userId = userId, entry = entry)
```

Insert immediately before `        patch("/{contentId}", SyncCollectionDocs.patchContent) {`:

```kotlin
        head("/{contentId}") {
            val userId = extractSyncUserId(call, accessTokenVerifier) ?: return@head
            call.respondRecordState(collectionsRepository.entryState(userId, call.requiredPathParam("contentId")))
        }

```

Replace lines 252-273 (from `val wasCreated = !collectionsRepository.journalExists(userId, journalId)` through the closing `)` of `collectionsRepository.upsertJournal(…)`):

```kotlin
                val wasCreated = !collectionsRepository.journalExists(userId, journalId)
                if (call.request.header(HttpHeaders.IfNoneMatch) == "*" && !wasCreated) {
                    return@put call.respond(
                        HttpStatusCode.PreconditionFailed,
                        error("JOURNAL_EXISTS", "Journal already exists"),
                    )
                }
                Napier.d("Journal upsert completed")
                val stored =
                    collectionsRepository.upsertJournal(
                        userId = userId,
                        journal =
                            LogDateJournal(
                                id = journalId,
                                title = req.title,
                                description = req.description,
                                createdAt = req.createdAt,
                                lastUpdated = req.lastUpdated,
                                version = 0L,
                                deviceId = req.deviceId,
                            ),
                    )
```

with:

```kotlin
                val journal =
                    LogDateJournal(
                        id = journalId,
                        title = req.title,
                        description = req.description,
                        createdAt = req.createdAt,
                        lastUpdated = req.lastUpdated,
                        version = 0L,
                        deviceId = req.deviceId,
                    )
                val strictCreate = call.isStrictCreate()
                if (strictCreate || call.isCreateOnly()) {
                    call.respondConditionalCreate(
                        outcome = collectionsRepository.createJournalIfAbsent(userId, journal, respectTombstones = strictCreate),
                        noun = "journal",
                        location = "/api/v1/journals/$journalId",
                    ) { JournalUploadResponse(journalId, it.version, it.lastUpdated) }
                    success = true
                    return@put
                }
                val wasCreated = !collectionsRepository.journalExists(userId, journalId)
                Napier.d("Journal upsert completed")
                val stored = collectionsRepository.upsertJournal(userId = userId, journal = journal)
```

Replace lines 392-407 (in `POST /associations`, from `val req = call.receive<AssociationUploadRequest>()` through `call.respond(AssociationUploadResponse(req.associations.size, uploadedAt))`):

```kotlin
                val req = call.receive<AssociationUploadRequest>()
                val uploadedAt = System.currentTimeMillis()
                collectionsRepository.upsertAssociations(
                    userId = userId,
                    associations =
                        req.associations.map {
                            LogDateAssociation(
                                journalId = it.journalId,
                                entryId = it.contentId,
                                createdAt = it.createdAt,
                                version = 0L,
                                deviceId = it.deviceId,
                            )
                        },
                )
                call.respond(AssociationUploadResponse(req.associations.size, uploadedAt))
```

with:

```kotlin
                val req = call.receive<AssociationUploadRequest>()
                val uploadedAt = System.currentTimeMillis()
                val associations =
                    req.associations.map {
                        LogDateAssociation(
                            journalId = it.journalId,
                            entryId = it.contentId,
                            createdAt = it.createdAt,
                            version = 0L,
                            deviceId = it.deviceId,
                        )
                    }
                if (call.isStrictCreate()) {
                    val results = collectionsRepository.createAssociationsIfAbsent(userId, associations)
                    call.respond(
                        AssociationUploadResponse(
                            uploadedCount = results.created.size,
                            uploadedAt = uploadedAt,
                            existing = results.existing.map { AssociationDeleteItem(it.journalId, it.entryId) },
                            deleted =
                                results.deleted.map {
                                    AssociationDeletion(it.association.journalId, it.association.entryId, it.deletedAt, it.serverVersion)
                                },
                        ),
                    )
                    success = true
                    return@post
                }
                collectionsRepository.upsertAssociations(userId = userId, associations = associations)
                call.respond(AssociationUploadResponse(req.associations.size, uploadedAt))
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.routes.sync.*' --tests 'app.logdate.server.ServerDescriptorConfigTest' --console=plain`
Expected: PASS, including `SyncContentAndJournalLifecycleTest`'s two `If-None-Match` tests (the Mac contract).

- [ ] **Step 7: Document the new behavior**

Append to `SyncDocSupport.kt`:

```kotlin

/** The `ifAbsent` query parameter on the create-only uploads. [what] says what a taken or deleted ID answers. */
internal fun RequestConfig.ifAbsentQuery(what: String) {
    queryParameter<Boolean>("ifAbsent") {
        description =
            "Send `true` to create only: $what Servers that list `conditionalCreateV1` in their descriptor honor it; " +
            "older servers ignore it and save normally, so check the descriptor first."
        required = false
        example("Create only") { value = true }
    }
}
```

In `SyncCollectionDocs.kt`:

Insert before line 50 (`            The \`id\` in the body must equal the \`contentId\` in the path. …`):

```text
            Send `?ifAbsent=true` instead for a strict create, as linked watches do: nothing is written if the ID
            is live (`412 CONTENT_EXISTS`, with `details.serverVersion`) or was deleted (`410 CONTENT_DELETED`,
            with `details.deletedAt`). `If-None-Match: *` alone may recreate a deleted entry.

```

Insert after line 55 (`            contentId("Must match \`id\` in the body.")`):

```kotlin
            ifAbsentQuery("a live entry answers `412 CONTENT_EXISTS` and a deleted one `410 CONTENT_DELETED`; neither writes anything.")
```

Replace lines 95-102 (the `412` block of `upsertContent`):

```kotlin
            syncError(
                HttpStatusCode.PreconditionFailed,
                ErrorCase(
                    "CONTENT_EXISTS",
                    "An entry already exists when `If-None-Match: *` is sent.",
                    "Content already exists",
                ),
            )
```

with:

```kotlin
            syncError(
                HttpStatusCode.PreconditionFailed,
                ErrorCase(
                    "CONTENT_EXISTS",
                    "An entry already holds this ID and `If-None-Match: *` or `?ifAbsent=true` was sent. Nothing was " +
                        "written; `details.serverVersion` is the stored version.",
                    "Content already exists",
                ),
            )
            syncError(
                HttpStatusCode.Gone,
                ErrorCase(
                    "CONTENT_DELETED",
                    "The entry was deleted and `?ifAbsent=true` was sent. Nothing was written; `details.deletedAt` says " +
                        "when. Drop the local copy instead of uploading it again.",
                    "Content was deleted",
                ),
            )
```

Insert before line 235 (`            The \`id\` in the body must equal the \`journalId\` in the path, …`):

```text
            Send `?ifAbsent=true` for a strict create: a live ID answers `412 JOURNAL_EXISTS` and a deleted one
            `410 JOURNAL_DELETED`, and nothing is written.
```

Insert after line 240 (`            journalId("Must match \`id\` in the body.")`):

```kotlin
            ifAbsentQuery("a live journal answers `412 JOURNAL_EXISTS` and a deleted one `410 JOURNAL_DELETED`; neither writes anything.")
```

Replace lines 279-286 (the `412` block of `upsertJournal`):

```kotlin
            syncError(
                HttpStatusCode.PreconditionFailed,
                ErrorCase(
                    "JOURNAL_EXISTS",
                    "A journal already exists when `If-None-Match: *` is sent.",
                    "Journal already exists",
                ),
            )
```

with:

```kotlin
            syncError(
                HttpStatusCode.PreconditionFailed,
                ErrorCase(
                    "JOURNAL_EXISTS",
                    "A journal already holds this ID and `If-None-Match: *` or `?ifAbsent=true` was sent. Nothing was " +
                        "written; `details.serverVersion` is the stored version.",
                    "Journal already exists",
                ),
            )
            syncError(
                HttpStatusCode.Gone,
                ErrorCase(
                    "JOURNAL_DELETED",
                    "The journal was deleted and `?ifAbsent=true` was sent. Nothing was written; `details.deletedAt` says when.",
                    "Journal was deleted",
                ),
            )
```

In `SyncAssociationDocs.kt`, replace lines 37-39:

```text
            The server does not check that the journal or entry exist, so you can upload links before, after,
            or alongside the things they connect.
            """,
```

with:

```text
            The server does not check that the journal or entry exist, so you can upload links before, after,
            or alongside the things they connect.

            With `?ifAbsent=true` only links that were never written are saved: links that already exist are
            listed in `existing` and deleted links in `deleted`, and neither is touched. `uploadedCount` then
            counts only the new links.
            """,
```

and replace lines 54-56:

```kotlin
                "The links to create or refresh.",
            )
        }
```

with:

```kotlin
                "The links to create or refresh.",
            )
            ifAbsentQuery("links that exist or were deleted are reported in the response and left alone.")
        }
```

In `SyncSchemaDocs.kt`, replace lines 262-269:

```kotlin
            "AssociationUploadResponse" to
                SchemaDoc(
                    "Confirmation that links were saved.",
                    mapOf(
                        "uploadedCount" to "How many links were in the request.",
                        "uploadedAt" to "When the server accepted them. $EPOCH_MS",
                    ),
                ),
```

with:

```kotlin
            "AssociationUploadResponse" to
                SchemaDoc(
                    "Confirmation that links were saved.",
                    mapOf(
                        "uploadedCount" to "How many links were in the request; with `?ifAbsent=true`, how many were new.",
                        "uploadedAt" to "When the server accepted them. $EPOCH_MS",
                        "existing" to "With `?ifAbsent=true`: links that already existed and were left alone. Absent otherwise.",
                        "deleted" to "With `?ifAbsent=true`: links that were deleted and were not brought back. Absent otherwise.",
                    ),
                ),
```

- [ ] **Step 8: Run the OpenAPI gates, iOS compile and the suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :shared:model:compileKotlinIosSimulatorArm64 :shared:model:ktlintFormat :shared:model:ktlintCheck :server:ktlintFormat :server:ktlintCheck :server:detekt :server:test --console=plain`
Expected: BUILD SUCCESSFUL, including `OpenApiContractTest` and `SchemaDocumentationTest`.

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :integration:server-client-e2e:test --tests 'app.logdate.integration.e2e.journeys.MacTextClientServerE2ETest' --console=plain`
Expected: PASS (the Mac's `If-None-Match` uploads behave as before).

- [ ] **Step 9: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-strict-create.txt <<'MSG'
feat(server): accept strict create-only uploads and existence checks

PUT /contents/{id} and PUT /journals/{id} take ?ifAbsent=true: a taken
ID answers 412 with details.serverVersion, a deleted ID answers 410
with details.deletedAt, and neither writes anything, so a watch can
retry an upload without replacing a newer copy or bringing back an
entry deleted on the phone. POST /associations?ifAbsent=true saves only
new links and lists the existing and deleted ones. HEAD
/contents/{id} reports a record's state and server version.

If-None-Match: * keeps recreating deleted records, as the Mac app
expects, and now runs on the same atomic create, so two racing retries
can no longer both answer 201. Its 412 carries details.serverVersion.

Servers advertise this as conditionalCreateV1.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 10: Commit**

```bash
git restore --staged . && git add server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt shared/model/src/commonMain/kotlin/app/logdate/shared/model/sync/SyncModels.kt shared/model/src/commonMain/kotlin/app/logdate/shared/model/ServerDescriptor.kt server/src/main/kotlin/app/logdate/server/ServerDescriptorConfig.kt server/src/main/kotlin/app/logdate/server/routes/docs/SyncDocSupport.kt server/src/main/kotlin/app/logdate/server/routes/docs/SyncCollectionDocs.kt server/src/main/kotlin/app/logdate/server/routes/docs/SyncAssociationDocs.kt server/src/main/kotlin/app/logdate/server/openapi/schemadocs/SyncSchemaDocs.kt server/src/test/kotlin/app/logdate/server/ServerDescriptorConfigTest.kt server/src/test/kotlin/app/logdate/server/routes/sync/SyncConditionalCreateRoutesTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-strict-create.txt
```

Expected: hooks pass; eleven files in the commit.

---

### Task 11: Store marked moments in their own collection

**Files:**
- Modify: `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionRecords.kt:39-43`, and append after `draftRecordId` (`:81-89`)
- Create: `server/src/main/kotlin/app/logdate/server/logdate/LogDateMomentsRepository.kt`
- Create: `server/src/main/kotlin/app/logdate/server/logdate/RepoBackedLogDateMomentsRepository.kt`
- Modify: `server/src/main/kotlin/app/logdate/server/logdate/RepoBackedLogDateCollectionsRepository.kt` (`private suspend fun canonicalRepoDid`, line 483 at `17490e104`)
- Test: `server/src/test/kotlin/app/logdate/server/logdate/RepoBackedLogDateMomentsRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 9's `RepoConditionalWriter`, `ConditionalCreate`, `LogDateRecordState`, `mapCreated`.
- Produces:
  - `LogDateCollectionKind.MOMENT` (`storageName = "moment"`, NSID `studio.hypertext.logdate.moment`). It needs no migration: `logdate_collection_records.collection` is a free `VARCHAR(32)` (V8), and XRPC exposes only the adapters listed in `LogDateRepoStore.supportedCollections`, so moments stay out of AT Protocol reads like drafts do.
  - `data class LogDateMoment(id, capturedAt, lastUpdated, version, deviceId: DeviceId, payload, schema: Int)`, `data class LogDateMomentDeletion(id, deletedAt, serverVersion)`.
  - `interface LogDateMomentsRepository { momentState, upsertMoment, createMomentIfAbsent, getMoment, deleteMoment, momentChanges }`, kept apart from `LogDateCollectionsRepository` so the in-memory `SyncBackedLogDateCollectionsRepository` needs no moment stubs.
  - `internal class RepoBackedLogDateMomentsRepository(accountRepository, signingKeyService, blockStore, metadataStore, repoDidFor: suspend (UUID) -> AtprotoDid)`.
  - `RepoBackedLogDateCollectionsRepository.canonicalRepoDid` becomes `internal` so the moments store writes to the same repo.
  - Isolation: `status()` counts only entries, journals and links (`LogDateCollectionsMetadataStore.kt:108-116`, `PostgreSQLLogDateCollectionsMetadataStore.kt:29-45`), and feeds filter by collection, so moments never appear in them. The existing tombstone purges delete every deleted row whatever its collection (`purgeTombstones`, `purgeAllTombstones`), so moment tombstones expire with the rest.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/app/logdate/server/logdate/RepoBackedLogDateMomentsRepositoryTest.kt`:

```kotlin
package app.logdate.server.logdate

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RepoBackedLogDateMomentsRepositoryTest {
    private val accountRepository = InMemoryAccountRepository()
    private val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
    private val identityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = signingKeyService,
            config = AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
        )
    private val blockStore = InMemoryRepoBlockStore()
    private val metadataStore = InMemoryLogDateCollectionsMetadataStore()
    private val collections =
        RepoBackedLogDateCollectionsRepository(accountRepository, identityService, signingKeyService, blockStore, metadataStore)
    private val moments =
        RepoBackedLogDateMomentsRepository(accountRepository, signingKeyService, blockStore, metadataStore, collections::canonicalRepoDid)
    private var users = 0

    @Test
    fun `a moment round trips and its deletion reaches the moment feed`() =
        runTest {
            val (user, _) = newUser()
            val stored = moments.upsertMoment(user, moment("m1"))

            assertEquals(moment("m1").copy(version = stored.version), moments.getMoment(user, "m1"))
            assertEquals(listOf("m1"), moments.momentChanges(user, since = 0L, limit = 10).changes.map { it.id })

            moments.deleteMoment(user, "m1", deletedAt = 99L)

            assertNull(moments.getMoment(user, "m1"))
            val after = moments.momentChanges(user, since = stored.version, limit = 10)
            assertTrue(after.changes.isEmpty())
            assertEquals(listOf("m1" to 99L), after.deletions.map { it.id to it.deletedAt })
        }

    @Test
    fun `moments stay out of entries journals links and status counts`() =
        runTest {
            val (user, _) = newUser()
            collections.upsertEntry(user, entry("e1"))

            moments.upsertMoment(user, moment("m1"))
            moments.upsertMoment(user, moment("m2"))
            moments.deleteMoment(user, "m2", deletedAt = 5L)

            val status = collections.status(user)
            assertEquals(1, status.entryCount)
            assertEquals(0, status.journalCount)
            assertEquals(0, status.associationCount)
            val entries = collections.entryChanges(user, since = 0L, limit = 50)
            assertEquals(listOf("e1"), entries.changes.map { it.id })
            assertTrue(entries.deletions.isEmpty())
            val journals = collections.journalChanges(user, since = 0L, limit = 50)
            assertTrue(journals.changes.isEmpty() && journals.deletions.isEmpty())
            val links = collections.associationChanges(user, since = 0L, limit = 50)
            assertTrue(links.changes.isEmpty() && links.deletions.isEmpty())
            assertEquals(listOf("e1"), collections.listEntries(user).map { it.id })
        }

    @Test
    fun `strict moment creation never brings back a deleted moment`() =
        runTest {
            val (user, did) = newUser()
            val created = moments.createMomentIfAbsent(user, moment("m3"), respectTombstones = true)
            val version = if (created is ConditionalCreate.Created) created.record.version else fail("expected a create, got $created")
            val commits = blockStore.listCommits(did).getOrThrow().size

            val duplicate = moments.createMomentIfAbsent(user, moment("m3"), respectTombstones = true)

            assertEquals(ConditionalCreate.Exists(version), duplicate)
            assertEquals(commits, blockStore.listCommits(did).getOrThrow().size)

            moments.deleteMoment(user, "m3", deletedAt = 7L)
            val outcome = moments.createMomentIfAbsent(user, moment("m3"), respectTombstones = true)
            assertEquals(7L, assertIs<ConditionalCreate.Deleted>(outcome).deletedAt)
            assertIs<LogDateRecordState.Deleted>(moments.momentState(user, "m3"))
        }

    @Test
    fun `purging tombstones removes expired moment tombstones`() =
        runTest {
            val (user, _) = newUser()
            moments.upsertMoment(user, moment("m4"))
            moments.deleteMoment(user, "m4", deletedAt = 10L)
            assertEquals(1, moments.momentChanges(user, since = 0L, limit = 10).deletions.size)

            collections.purgeTombstones(user, olderThan = 11L)

            assertTrue(moments.momentChanges(user, since = 0L, limit = 10).deletions.isEmpty())
            assertEquals(LogDateRecordState.Absent, moments.momentState(user, "m4"))
        }

    private suspend fun newUser(): Pair<UUID, AtprotoDid> {
        users += 1
        val account =
            identityService.ensureIdentity(
                accountRepository.save(
                    Account(id = Uuid.random(), username = "moments_$users", displayName = "Moments", createdAt = Clock.System.now()),
                ),
            )
        return account.id.toJavaUUID() to AtprotoDid.require(requireNotNull(account.did))
    }

    private fun moment(id: String) =
        LogDateMoment(
            id = id,
            capturedAt = 1_789_221_791_412L,
            lastUpdated = 1_789_221_791_412L,
            version = 0L,
            deviceId = DeviceId("pixel-watch-3"),
            payload = "LDSE2:opaque-$id",
            schema = 1,
        )

    private fun entry(id: String) =
        LogDateEntry(
            id = id,
            type = "TEXT",
            content = "LDSE2:entry-$id",
            mediaUri = null,
            durationMs = 0L,
            createdAt = 10L,
            lastUpdated = 10L,
            version = 0L,
            deviceId = DeviceId("pixel-9"),
        )
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.logdate.RepoBackedLogDateMomentsRepositoryTest' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'RepoBackedLogDateMomentsRepository'`, `'LogDateMoment'`, and `Cannot access 'canonicalRepoDid': it is private`.

- [ ] **Step 3: Add the collection kind and its record shape**

In `LogDateCollectionRecords.kt`, replace lines 39-43:

```kotlin
    DRAFT(
        storageName = "draft",
        nsid = Nsid.require("studio.hypertext.logdate.draft"),
    ),
    ;
```

with:

```kotlin
    DRAFT(
        storageName = "draft",
        nsid = Nsid.require("studio.hypertext.logdate.draft"),
    ),
    MOMENT(
        storageName = "moment",
        nsid = Nsid.require("studio.hypertext.logdate.moment"),
    ),
    ;
```

Insert after `draftRecordId` (after line 89, before `internal fun associationRecordKey(`):

```kotlin

internal fun momentRecordId(
    repo: AtprotoDid,
    id: String,
): RepoRecordId =
    RepoRecordId(
        repo = repo,
        collection = LogDateCollectionKind.MOMENT.nsid,
        recordKey = RecordKey.require(id),
    )

internal fun LogDateMoment.toRepoJson(): JsonObject =
    buildJsonObject {
        put(TYPE_FIELD_NAME, LogDateCollectionKind.MOMENT.nsid.toString())
        put("id", id)
        put("capturedAt", capturedAt)
        put("lastUpdated", lastUpdated)
        put("deviceId", deviceId.value)
        put("payload", payload)
        put("schema", schema)
    }

internal fun JsonObject.toLogDateMoment(
    recordKey: RecordKey,
    version: Long,
): LogDateMoment =
    LogDateMoment(
        id = stringValue("id") ?: recordKey.toString(),
        capturedAt = longValue("capturedAt") ?: 0L,
        lastUpdated = longValue("lastUpdated") ?: 0L,
        version = version,
        deviceId = deviceIdOrDefault(),
        payload = stringValue("payload").orEmpty(),
        schema = this["schema"]?.jsonPrimitive?.intOrNull ?: 1,
    )
```

- [ ] **Step 4: Create `LogDateMomentsRepository.kt`**

```kotlin
package app.logdate.server.logdate

import app.logdate.shared.model.sync.DeviceId
import java.util.UUID

/**
 * A marked moment as the server stores it. [payload] is one client-encrypted `LDSE2:` value holding
 * everything but the times; the server never reads it. [schema] versions that payload.
 */
data class LogDateMoment(
    val id: String,
    val capturedAt: Long,
    val lastUpdated: Long,
    val version: Long,
    val deviceId: DeviceId,
    val payload: String,
    val schema: Int,
)

data class LogDateMomentDeletion(
    val id: String,
    val deletedAt: Long,
    val serverVersion: Long,
)

/**
 * Marked moments live in their own collection: the entry and journal feeds never carry them, so clients
 * that do not know moments never download one.
 */
interface LogDateMomentsRepository {
    suspend fun momentState(
        userId: UUID,
        id: String,
    ): LogDateRecordState

    suspend fun upsertMoment(
        userId: UUID,
        moment: LogDateMoment,
    ): LogDateMoment

    /** See [LogDateCollectionsRepository.createEntryIfAbsent]. */
    suspend fun createMomentIfAbsent(
        userId: UUID,
        moment: LogDateMoment,
        respectTombstones: Boolean,
    ): ConditionalCreate<LogDateMoment>

    suspend fun getMoment(
        userId: UUID,
        id: String,
    ): LogDateMoment?

    suspend fun deleteMoment(
        userId: UUID,
        id: String,
        deletedAt: Long,
    )

    suspend fun momentChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateMoment, LogDateMomentDeletion>
}
```

- [ ] **Step 5: Create `RepoBackedLogDateMomentsRepository.kt`**

```kotlin
package app.logdate.server.logdate

import app.logdate.server.atproto.HostedRepoCommitSigner
import app.logdate.server.auth.AccountRepository
import app.logdate.server.identity.SigningKeyService
import io.github.aakira.napier.Napier
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.syntax.RecordKey
import java.util.UUID

/**
 * Marked moments in the account's repo, versioned and tombstoned through the same metadata store as entries:
 * they share the account's version sequence and the scheduled tombstone purge. [repoDidFor] is
 * [RepoBackedLogDateCollectionsRepository.canonicalRepoDid], so both stores write to one repo.
 */
internal class RepoBackedLogDateMomentsRepository(
    accountRepository: AccountRepository,
    signingKeyService: SigningKeyService,
    blockStore: RepoBlockStore,
    private val metadataStore: LogDateCollectionsMetadataStore,
    private val repoDidFor: suspend (UUID) -> AtprotoDid,
) : LogDateMomentsRepository {
    private val repoEngine = DefaultRepoEngine(blockStore, signer = HostedRepoCommitSigner(accountRepository, signingKeyService))
    private val conditionalWriter = RepoConditionalWriter(repoEngine, metadataStore)

    override suspend fun momentState(
        userId: UUID,
        id: String,
    ): LogDateRecordState = conditionalWriter.state(userId, LogDateCollectionKind.MOMENT, id)

    override suspend fun upsertMoment(
        userId: UUID,
        moment: LogDateMoment,
    ): LogDateMoment {
        val repoDid = repoDidFor(userId)
        repoEngine.putRecord(momentRecordId(repoDid, moment.id), moment.toRepoJson()).getOrThrow()
        val metadata = metadataStore.upsert(userId, repoDid, LogDateCollectionKind.MOMENT, moment.id)
        return moment.copy(version = metadata.version, lastUpdated = System.currentTimeMillis())
    }

    override suspend fun createMomentIfAbsent(
        userId: UUID,
        moment: LogDateMoment,
        respectTombstones: Boolean,
    ): ConditionalCreate<LogDateMoment> {
        val repoDid = repoDidFor(userId)
        return conditionalWriter
            .createIfAbsent(
                userId = userId,
                repoDid = repoDid,
                collection = LogDateCollectionKind.MOMENT,
                recordId = momentRecordId(repoDid, moment.id),
                value = moment.toRepoJson(),
                respectTombstones = respectTombstones,
            ).mapCreated { version -> moment.copy(version = version, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun getMoment(
        userId: UUID,
        id: String,
    ): LogDateMoment? {
        val metadata = metadataStore.metadata(userId, LogDateCollectionKind.MOMENT, id) ?: return null
        return repoEngine
            .getRecord(momentRecordId(repoDidFor(userId), id))
            .getOrThrow()
            ?.value
            ?.toLogDateMoment(RecordKey.require(id), metadata.version)
    }

    override suspend fun deleteMoment(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        val existing = metadataStore.metadata(userId, LogDateCollectionKind.MOMENT, id) ?: return
        val repoDid = repoDidFor(userId)
        if (!repoEngine.deleteRecord(momentRecordId(repoDid, id)).getOrThrow()) {
            Napier.w("A moment's repo record was already missing when it was deleted")
        }
        metadataStore.delete(userId, repoDid, LogDateCollectionKind.MOMENT, existing.recordKey, deletedAt)
    }

    override suspend fun momentChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateMoment, LogDateMomentDeletion> {
        val repoDid = repoDidFor(userId)
        val metadata = metadataStore.changes(userId, LogDateCollectionKind.MOMENT, since, limit)
        val records = repoEngine.getRecords(metadata.changes.map { momentRecordId(repoDid, it.recordKey) }).getOrThrow()
        return LogDateChangeSet(
            changes =
                records.zip(metadata.changes).mapNotNull { (record, change) ->
                    record?.value?.toLogDateMoment(RecordKey.require(change.recordKey), change.version)
                },
            deletions = metadata.deletions.map { LogDateMomentDeletion(it.recordKey, requireNotNull(it.deletedAt), it.version) },
            lastTimestamp = metadata.lastTimestamp,
            hasMore = metadata.hasMore,
        )
    }
}
```

- [ ] **Step 6: Share the canonical repo DID**

In `RepoBackedLogDateCollectionsRepository.kt`, replace:

```kotlin
    private suspend fun canonicalRepoDid(userId: UUID): AtprotoDid = metadataStore.state(userId)?.repoDid ?: preferredRepoDid(userId)
```

with:

```kotlin
    internal suspend fun canonicalRepoDid(userId: UUID): AtprotoDid = metadataStore.state(userId)?.repoDid ?: preferredRepoDid(userId)
```

- [ ] **Step 7: Run the test and watch it pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.logdate.*' --tests 'app.logdate.server.atproto.*' --console=plain`
Expected: PASS; the XRPC tests confirm AT Protocol reads still list only entries, journals and links.

- [ ] **Step 8: Format, lint, and run the suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:ktlintFormat :server:ktlintCheck :server:detekt :server:test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-moment-store.txt <<'MSG'
chore(server): store marked moments in their own collection

Marked moments get a MOMENT collection in the account's repo and their
own repository, versioned and tombstoned like entries. They never show
up in the entry, journal or link feeds or the sync status counts, and
their tombstones expire with the rest. A moment's contents stay one
encrypted payload the server never reads.

No route serves them yet.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 10: Commit**

```bash
git restore --staged . && git add server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionRecords.kt server/src/main/kotlin/app/logdate/server/logdate/LogDateMomentsRepository.kt server/src/main/kotlin/app/logdate/server/logdate/RepoBackedLogDateMomentsRepository.kt server/src/main/kotlin/app/logdate/server/logdate/RepoBackedLogDateCollectionsRepository.kt server/src/test/kotlin/app/logdate/server/logdate/RepoBackedLogDateMomentsRepositoryTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-moment-store.txt
```

Expected: hooks pass; five files in the commit.

---

### Task 12: Sync marked moments

**Files:**
- Modify: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/sync/SyncModels.kt` (append)
- Modify: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/ServerDescriptor.kt` (after `CONDITIONAL_CREATE_V1`)
- Modify: `server/src/main/kotlin/app/logdate/server/ServerDescriptorConfig.kt` (after `CONDITIONAL_CREATE_V1`)
- Create: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncMomentRoutes.kt`
- Create: `server/src/main/kotlin/app/logdate/server/routes/docs/SyncMomentDocs.kt`
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt:57` (insert after)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt:605`, `:616` (`private` → `internal`)
- Modify: `server/src/main/kotlin/app/logdate/server/sync/SyncMetricsRegistry.kt:109` (insert after)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/SyncRoutes.kt` (imports, parameters, body)
- Modify: `server/src/main/kotlin/app/logdate/server/openapi/ApiTags.kt:17`, `:150` (insert after each)
- Modify: `server/src/main/kotlin/app/logdate/server/openapi/schemadocs/SyncSchemaDocs.kt:420` (insert before)
- Modify: `server/src/main/kotlin/app/logdate/server/di/ServerModule.kt` (imports; after the `RepoBackedLogDateCollectionsRepository` single)
- Modify: `server/src/main/kotlin/app/logdate/server/routes/ServerRouting.kt` (`contentApiRoutes`)
- Modify: `server/src/test/kotlin/app/logdate/server/SyncTestApp.kt:56-63`, `:83-92`
- Modify: `server/src/test/kotlin/app/logdate/server/ServerDescriptorConfigTest.kt` (append a test)
- Test: `server/src/test/kotlin/app/logdate/server/routes/sync/SyncMomentRoutesTest.kt`

**Interfaces:**
- Consumes: Task 11's `LogDateMomentsRepository`; Task 10's `isStrictCreate`, `isCreateOnly`, `respondConditionalCreate`; Task 5's `extractSyncUserId`; Task 6's test support (`assertSyncErrorCode`, `DeviceLinkTestKit`).
- Produces:
  - Wire types in `app.logdate.shared.model.sync`: `MomentUploadRequest(id, capturedAt, lastUpdated, deviceId = UNKNOWN, payload, schema = 1)`, `MomentUploadResponse(id, serverVersion, uploadedAt)`, `MomentUpdateRequest(payload, schema = 1, capturedAt: Long? = null, lastUpdated, deviceId = UNKNOWN, versionConstraint = None)`, `MomentUpdateResponse(id, serverVersion, updatedAt)`, `MomentChange(id, capturedAt, lastUpdated, deviceId, payload, schema, serverVersion)`, `MomentDeletion(id, deletedAt, serverVersion)`, `MomentChangesResponse(changes, deletions, lastTimestamp, hasMore = false)`.
  - Routes under `/api/v1/moments` (sync error envelope, full or linked-device tokens): `PUT /{momentId}` (201/200, `If-None-Match: *`, `?ifAbsent=true` with `412 MOMENT_EXISTS` / `410 MOMENT_DELETED`), `GET /` (`since`, `limit`), `GET /{momentId}`, `PATCH /{momentId}` (exact `versionConstraint`, `404` for an unknown ID), `DELETE /{momentId}` (204). Payloads must start with `LDSE2:` and be at most 64 KiB, `schema ≥ 1`, IDs valid record keys, else `400 INVALID_MOMENT`.
  - Like entries, moments pass no quota or entitlement check (`SyncCollectionRoutes` has none; quotas apply to media and backups through `EntitlementEnforcer`).
  - `ServerProtocolFeature.MOMENTS_V1 = "momentsV1"`; `syncRoutes(…, momentsRepository: LogDateMomentsRepository? = null)` mounts `/moments` only when given one; `configureSyncTestApp` always gives one.

- [ ] **Step 1: Write the failing tests**

Create `server/src/test/kotlin/app/logdate/server/routes/sync/SyncMomentRoutesTest.kt`:

```kotlin
package app.logdate.server.routes.sync

import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureSyncTestApp
import app.logdate.server.routes.support.DeviceLinkTestKit
import app.logdate.server.routes.support.assertSyncErrorCode
import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.support.contentUploadBody
import app.logdate.server.routes.support.jsonBody
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class SyncMomentRoutesTest {
    private val tokenService = JwtTokenService("moment-routes-secret")

    @Test
    fun `a moment round trips through put get patch and delete`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService)
            val auth = authHeader(tokenService)

            val created = client.putMoment(auth, "moment-1")
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals("/api/v1/moments/moment-1", created.headers[HttpHeaders.Location])
            val firstVersion = created.jsonBody().getValue("serverVersion").jsonPrimitive.long
            assertEquals(HttpStatusCode.OK, client.putMoment(auth, "moment-1").status)

            val read = client.getAs(auth, "/api/v1/moments/moment-1").jsonBody()
            assertEquals("LDSE2:moment-1", read.getValue("payload").jsonPrimitive.content)
            assertEquals(1, read.getValue("schema").jsonPrimitive.int)
            val current = read.getValue("serverVersion").jsonPrimitive.long

            assertSyncErrorCode(client.patchMoment(auth, "moment-1", knownVersion = firstVersion), HttpStatusCode.Conflict, "CONFLICT")
            assertEquals(HttpStatusCode.OK, client.patchMoment(auth, "moment-1", knownVersion = current).status)
            val patched = client.getAs(auth, "/api/v1/moments/moment-1").jsonBody()
            assertEquals("LDSE2:edited", patched.getValue("payload").jsonPrimitive.content)
            assertSyncErrorCode(client.patchMoment(auth, "never-written", knownVersion = null), HttpStatusCode.NotFound, "NOT_FOUND")

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/moments/moment-1").status)
            assertEquals(HttpStatusCode.NotFound, client.getAs(auth, "/api/v1/moments/moment-1").status)
            val feed = client.getAs(auth, "/api/v1/moments?since=0").jsonBody()
            assertTrue(feed.getValue("changes").jsonArray.isEmpty())
            assertEquals(listOf("moment-1"), feed.ids("deletions"))
        }

    @Test
    fun `moment payloads must be encrypted and ids must match`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService)
            val auth = authHeader(tokenService)

            assertSyncErrorCode(client.putMoment(auth, "m1", payload = "plain text"), HttpStatusCode.BadRequest, "INVALID_MOMENT")
            assertSyncErrorCode(client.putMoment(auth, "m1", schema = 0), HttpStatusCode.BadRequest, "INVALID_MOMENT")
            val oversized = "LDSE2:" + "x".repeat(MAX_MOMENT_PAYLOAD_LENGTH)
            assertSyncErrorCode(client.putMoment(auth, "m1", payload = oversized), HttpStatusCode.BadRequest, "INVALID_MOMENT")
            assertSyncErrorCode(client.putMoment(auth, "m1", bodyId = "m2"), HttpStatusCode.BadRequest, "VALIDATION_ERROR")
            assertEquals(HttpStatusCode.Unauthorized, client.putMoment(auth = null, id = "m1").status)
        }

    @Test
    fun `strict moment creation never brings back a deleted moment`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService)
            val auth = authHeader(tokenService)

            val created = client.putMoment(auth, "mark-1", strict = true)
            assertEquals(HttpStatusCode.Created, created.status)
            val version = created.jsonBody().getValue("serverVersion").jsonPrimitive.long
            val duplicate = client.putMoment(auth, "mark-1", strict = true)
            assertSyncErrorCode(duplicate, HttpStatusCode.PreconditionFailed, "MOMENT_EXISTS")
            val stored = duplicate.jsonBody().getValue("details").jsonObject.getValue("serverVersion").jsonPrimitive.content
            assertEquals(version.toString(), stored)

            assertEquals(HttpStatusCode.NoContent, client.deleteAs(auth, "/api/v1/moments/mark-1").status)
            val gone = client.putMoment(auth, "mark-1", strict = true)
            assertSyncErrorCode(gone, HttpStatusCode.Gone, "MOMENT_DELETED")
            assertNotNull(gone.jsonBody().getValue("details").jsonObject["deletedAt"])
            assertEquals(HttpStatusCode.Created, client.putMoment(auth, "mark-1", ifNoneMatch = true).status)
        }

    @Test
    fun `moments leave the entry and journal feeds and the status counts alone`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService)
            val auth = authHeader(tokenService)
            client.putMoment(auth, "mark-1")
            client.putMoment(auth, "mark-2")
            client.deleteAs(auth, "/api/v1/moments/mark-2")

            listOf("/api/v1/contents?since=0", "/api/v1/journals?since=0", "/api/v1/associations?since=0").forEach { path ->
                val feed = client.getAs(auth, path).jsonBody()
                assertTrue(feed.getValue("changes").jsonArray.isEmpty(), path)
                assertTrue(feed.getValue("deletions").jsonArray.isEmpty(), path)
            }
            val status = client.getAs(auth, "/api/v1/ops/sync/status").jsonBody().getValue("data").jsonObject
            assertEquals(0, status.getValue("contentCount").jsonPrimitive.int)
            assertEquals(0, status.getValue("journalCount").jsonPrimitive.int)
            assertEquals(0, status.getValue("associationCount").jsonPrimitive.int)

            client.put("/api/v1/contents/note-1") {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(contentUploadBody(id = "note-1"))
            }
            assertEquals(listOf("mark-1"), client.getAs(auth, "/api/v1/moments?since=0").jsonBody().ids("changes"))
        }

    @Test
    fun `a linked watch can write moments`() =
        testApplication {
            val kit = DeviceLinkTestKit(tokenService, InMemoryAccountRepository())
            configureSyncTestApp(tokenService = tokenService, accessTokenVerifier = kit.verifier)
            val watch = kit.linkWatch(kit.newAccount("moment_owner"))

            val marked = client.putMoment("Bearer ${watch.tokens.accessToken}", "watch-mark-1", strict = true)
            assertEquals(HttpStatusCode.Created, marked.status)
        }

    private suspend fun HttpClient.putMoment(
        auth: String?,
        id: String,
        payload: String = "LDSE2:$id",
        schema: Int = 1,
        bodyId: String = id,
        strict: Boolean = false,
        ifNoneMatch: Boolean = false,
    ): HttpResponse =
        put("/api/v1/moments/$id" + (if (strict) "?ifAbsent=true" else "")) {
            auth?.let { header(HttpHeaders.Authorization, it) }
            if (ifNoneMatch) header(HttpHeaders.IfNoneMatch, "*")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"$bodyId","capturedAt":1789221791412,"lastUpdated":1789221791412,""" +
                    """"deviceId":"pixel-watch-3","payload":"$payload","schema":$schema}""",
            )
        }

    private suspend fun HttpClient.patchMoment(
        auth: String,
        id: String,
        knownVersion: Long?,
    ): HttpResponse =
        patch("/api/v1/moments/$id") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            val constraint = knownVersion?.let { """{"type":"known","serverVersion":$it}""" } ?: """{"type":"none"}"""
            setBody(
                """{"payload":"LDSE2:edited","schema":1,"lastUpdated":1789221800000,"deviceId":"pixel-watch-3",""" +
                    """"versionConstraint":$constraint}""",
            )
        }

    private suspend fun HttpClient.getAs(
        auth: String,
        path: String,
    ): HttpResponse = get(path) { header(HttpHeaders.Authorization, auth) }

    private suspend fun HttpClient.deleteAs(
        auth: String,
        path: String,
    ): HttpResponse = delete(path) { header(HttpHeaders.Authorization, auth) }

    private fun JsonObject.ids(list: String): List<String> =
        getValue(list).jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
}
```

In `ServerDescriptorConfigTest.kt`, insert before the class's final closing brace:

```kotlin

    @Test
    fun `marked moments are advertised on every deployment`() {
        val identity = AtprotoIdentityConfig()
        listOf(DeploymentKind.FIRST_PARTY, DeploymentKind.SELF_HOSTED).forEach { kind ->
            val descriptor = ServerDescriptorConfig(deploymentKind = kind).toDescriptor(identity, "logdate.app", "LogDate")
            assertTrue(descriptor.hasProtocolFeature(ServerProtocolFeature.MOMENTS_V1), kind.name)
        }
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.routes.sync.SyncMomentRoutesTest' --tests 'app.logdate.server.ServerDescriptorConfigTest' --console=plain`
Expected: FAIL at compile time: `Unresolved reference 'MAX_MOMENT_PAYLOAD_LENGTH'` and `'MOMENTS_V1'`.

- [ ] **Step 3: Add the wire types and the descriptor flag**

Append to `SyncModels.kt`:

```kotlin

/**
 * A marked moment: a point in time someone flagged, usually with one tap on the watch, to fill in later.
 * Everything about it except the times is in [payload], encrypted on the device (`LDSE2:`); [schema]
 * versions that payload. All times are milliseconds since epoch (UTC).
 */
@Serializable
data class MomentUploadRequest(
    val id: String,
    val capturedAt: Long,
    val lastUpdated: Long,
    val deviceId: DeviceId = DeviceId.UNKNOWN,
    val payload: String,
    val schema: Int = 1,
)

@Serializable
data class MomentUploadResponse(
    val id: String,
    val serverVersion: Long,
    val uploadedAt: Long,
)

@Serializable
data class MomentUpdateRequest(
    val payload: String,
    val schema: Int = 1,
    val capturedAt: Long? = null,
    val lastUpdated: Long,
    val deviceId: DeviceId = DeviceId.UNKNOWN,
    val versionConstraint: VersionConstraint = VersionConstraint.None,
)

@Serializable
data class MomentUpdateResponse(
    val id: String,
    val serverVersion: Long,
    val updatedAt: Long,
)

@Serializable
data class MomentChange(
    val id: String,
    val capturedAt: Long,
    val lastUpdated: Long,
    val deviceId: DeviceId,
    val payload: String,
    val schema: Int,
    val serverVersion: Long,
)

@Serializable
data class MomentDeletion(
    val id: String,
    val deletedAt: Long,
    val serverVersion: Long,
)

@Serializable
data class MomentChangesResponse(
    val changes: List<MomentChange>,
    val deletions: List<MomentDeletion>,
    val lastTimestamp: Long,
    val hasMore: Boolean = false,
)
```

In `ServerDescriptor.kt`, insert after `const val CONDITIONAL_CREATE_V1 = "conditionalCreateV1"`:

```kotlin

    /** Marked moments sync through `/moments`, a collection of their own that the entry feeds never carry. */
    const val MOMENTS_V1 = "momentsV1"
```

In `ServerDescriptorConfig.kt`, insert after `add(ServerProtocolFeature.CONDITIONAL_CREATE_V1)`:

```kotlin
                    add(ServerProtocolFeature.MOMENTS_V1)
```

- [ ] **Step 4: Add the metric names**

In `SyncHelpers.kt`, insert after line 57 (`internal const val METRIC_ASSOCIATION_DELETE = "sync.association.delete"`):

```kotlin
internal const val METRIC_MOMENT_UPLOAD = "sync.moment.upload"
internal const val METRIC_MOMENT_CHANGES = "sync.moment.changes"
internal const val METRIC_MOMENT_UPDATE = "sync.moment.update"
internal const val METRIC_MOMENT_DELETE = "sync.moment.delete"
```

In `SyncMetricsRegistry.kt`, insert after line 109 (`"sync.association.delete",`):

```kotlin
                "sync.moment.upload",
                "sync.moment.changes",
                "sync.moment.update",
                "sync.moment.delete",
```

In `SyncCollectionRoutes.kt`, change line 605 from `private suspend fun parseSinceParameter(` to `internal suspend fun parseSinceParameter(`, and line 616 from `private fun io.ktor.server.application.ApplicationCall.resolvePageSize(): Int =` to `internal fun io.ktor.server.application.ApplicationCall.resolvePageSize(): Int =`.

- [ ] **Step 5: Create `SyncMomentRoutes.kt`**

`server/src/main/kotlin/app/logdate/server/routes/sync/SyncMomentRoutes.kt`:

```kotlin
package app.logdate.server.routes.sync

import app.logdate.server.auth.AccessTokenVerifier
import app.logdate.server.logdate.LogDateMoment
import app.logdate.server.logdate.LogDateMomentsRepository
import app.logdate.server.logdate.LogDateRecordState
import app.logdate.server.responses.error
import app.logdate.server.routes.docs.SyncMomentDocs
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.MomentChange
import app.logdate.shared.model.sync.MomentChangesResponse
import app.logdate.shared.model.sync.MomentDeletion
import app.logdate.shared.model.sync.MomentUpdateRequest
import app.logdate.shared.model.sync.MomentUpdateResponse
import app.logdate.shared.model.sync.MomentUploadRequest
import app.logdate.shared.model.sync.MomentUploadResponse
import app.logdate.shared.model.sync.VersionConstraint
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import studio.hypertext.atproto.syntax.RecordKey

private const val MOMENT_PAYLOAD_PREFIX = "LDSE2:"

/** A moment's encrypted payload holds a caption, a place and a heart rate; 64 KiB leaves ample room. */
internal const val MAX_MOMENT_PAYLOAD_LENGTH = 64 * 1024

/**
 * `/moments`: marked moments, in their own collection so the entry and journal feeds never carry them and
 * clients that do not know moments never download one. Auth, account scoping and tombstones work as they
 * do for entries, and like entries no quota applies.
 */
internal fun Route.syncMomentRoutes(
    accessTokenVerifier: AccessTokenVerifier?,
    metrics: SyncMetricsRegistry,
    momentsRepository: LogDateMomentsRepository,
) {
    route("/moments") {
        upsertMomentRoute(accessTokenVerifier, metrics, momentsRepository)
        listMomentChangesRoute(accessTokenVerifier, metrics, momentsRepository)
        getMomentRoute(accessTokenVerifier, metrics, momentsRepository)
        patchMomentRoute(accessTokenVerifier, metrics, momentsRepository)
        deleteMomentRoute(accessTokenVerifier, metrics, momentsRepository)
    }
}

private fun Route.upsertMomentRoute(
    verifier: AccessTokenVerifier?,
    metrics: SyncMetricsRegistry,
    repository: LogDateMomentsRepository,
) {
    put("/{momentId}", SyncMomentDocs.upsertMoment) {
        val start = System.currentTimeMillis()
        var success = false
        try {
            val userId = extractSyncUserId(call, verifier) ?: return@put
            val momentId = call.requiredPathParam("momentId")
            val req = call.receive<MomentUploadRequest>()
            if (req.id != momentId) {
                return@put call.respond(HttpStatusCode.BadRequest, error("VALIDATION_ERROR", "Request body id must match path momentId"))
            }
            if (call.rejectInvalidMoment(momentId, req.payload, req.schema)) return@put
            val moment = LogDateMoment(momentId, req.capturedAt, req.lastUpdated, 0L, req.deviceId, req.payload, req.schema)
            val strictCreate = call.isStrictCreate()
            if (strictCreate || call.isCreateOnly()) {
                call.respondConditionalCreate(
                    outcome = repository.createMomentIfAbsent(userId, moment, respectTombstones = strictCreate),
                    noun = "moment",
                    location = "/api/v1/moments/$momentId",
                ) { MomentUploadResponse(it.id, it.version, it.lastUpdated) }
                success = true
                return@put
            }
            val wasCreated = repository.momentState(userId, momentId) !is LogDateRecordState.Live
            val stored = repository.upsertMoment(userId, moment)
            val response = MomentUploadResponse(stored.id, stored.version, stored.lastUpdated)
            if (wasCreated) {
                call.response.headers.append(HttpHeaders.Location, "/api/v1/moments/$momentId")
                call.respond(HttpStatusCode.Created, response)
            } else {
                call.respond(HttpStatusCode.OK, response)
            }
            success = true
        } finally {
            metrics.recordOperation(METRIC_MOMENT_UPLOAD, System.currentTimeMillis() - start, success)
        }
    }
}

private fun Route.listMomentChangesRoute(
    verifier: AccessTokenVerifier?,
    metrics: SyncMetricsRegistry,
    repository: LogDateMomentsRepository,
) {
    get(SyncMomentDocs.listMomentChanges) {
        val start = System.currentTimeMillis()
        var success = false
        try {
            val userId = extractSyncUserId(call, verifier) ?: return@get
            val since = parseSinceParameter(call) ?: return@get
            val changeSet = repository.momentChanges(userId, since, call.resolvePageSize())
            call.respond(
                MomentChangesResponse(
                    changes = changeSet.changes.map(LogDateMoment::toMomentChange),
                    deletions = changeSet.deletions.map { MomentDeletion(it.id, it.deletedAt, it.serverVersion) },
                    lastTimestamp = changeSet.lastTimestamp,
                    hasMore = changeSet.hasMore,
                ),
            )
            success = true
        } finally {
            metrics.recordOperation(METRIC_MOMENT_CHANGES, System.currentTimeMillis() - start, success)
        }
    }
}

private fun Route.getMomentRoute(
    verifier: AccessTokenVerifier?,
    metrics: SyncMetricsRegistry,
    repository: LogDateMomentsRepository,
) {
    get("/{momentId}", SyncMomentDocs.getMoment) {
        val start = System.currentTimeMillis()
        var success = false
        try {
            val userId = extractSyncUserId(call, verifier) ?: return@get
            val moment =
                repository.getMoment(userId, call.requiredPathParam("momentId"))
                    ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Moment not found"))
            call.respond(moment.toMomentChange())
            success = true
        } finally {
            metrics.recordOperation(METRIC_MOMENT_CHANGES, System.currentTimeMillis() - start, success)
        }
    }
}

private fun Route.patchMomentRoute(
    verifier: AccessTokenVerifier?,
    metrics: SyncMetricsRegistry,
    repository: LogDateMomentsRepository,
) {
    patch("/{momentId}", SyncMomentDocs.patchMoment) {
        val start = System.currentTimeMillis()
        var success = false
        try {
            val userId = extractSyncUserId(call, verifier) ?: return@patch
            val momentId = call.requiredPathParam("momentId")
            val req = call.receive<MomentUpdateRequest>()
            if (call.rejectInvalidMoment(momentId, req.payload, req.schema)) return@patch
            val existing =
                repository.getMoment(userId, momentId)
                    ?: return@patch call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Moment not found"))
            val expected = (req.versionConstraint as? VersionConstraint.Known)?.serverVersion
            if (expected != null && expected != existing.version) {
                metrics.recordConflict()
                return@patch call.respond(HttpStatusCode.Conflict, error("CONFLICT", "Server has a newer version"))
            }
            val updated =
                repository.upsertMoment(
                    userId,
                    existing.copy(
                        capturedAt = req.capturedAt ?: existing.capturedAt,
                        lastUpdated = req.lastUpdated,
                        deviceId = req.deviceId,
                        payload = req.payload,
                        schema = req.schema,
                    ),
                )
            call.respond(MomentUpdateResponse(momentId, updated.version, updated.lastUpdated))
            success = true
        } finally {
            metrics.recordOperation(METRIC_MOMENT_UPDATE, System.currentTimeMillis() - start, success)
        }
    }
}

private fun Route.deleteMomentRoute(
    verifier: AccessTokenVerifier?,
    metrics: SyncMetricsRegistry,
    repository: LogDateMomentsRepository,
) {
    delete("/{momentId}", SyncMomentDocs.deleteMoment) {
        val start = System.currentTimeMillis()
        var success = false
        try {
            val userId = extractSyncUserId(call, verifier) ?: return@delete
            repository.deleteMoment(userId, call.requiredPathParam("momentId"), System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
            success = true
        } finally {
            metrics.recordOperation(METRIC_MOMENT_DELETE, System.currentTimeMillis() - start, success)
        }
    }
}

/** Answers 400 and returns true when a moment's ID, payload or schema cannot be stored. */
private suspend fun ApplicationCall.rejectInvalidMoment(
    id: String,
    payload: String,
    schema: Int,
): Boolean {
    val problem =
        when {
            !RecordKey.isValid(id) -> "Moment ID is not a valid record key"
            !payload.startsWith(MOMENT_PAYLOAD_PREFIX) -> "Moment payload must be one LDSE2 encrypted value"
            payload.length > MAX_MOMENT_PAYLOAD_LENGTH -> "Moment payload is larger than 64 KiB"
            schema < 1 -> "Moment schema must be 1 or higher"
            else -> return false
        }
    respond(HttpStatusCode.BadRequest, error("INVALID_MOMENT", problem))
    return true
}

private fun LogDateMoment.toMomentChange(): MomentChange =
    MomentChange(
        id = id,
        capturedAt = capturedAt,
        lastUpdated = lastUpdated,
        deviceId = deviceId,
        payload = payload,
        schema = schema,
        serverVersion = version,
    )
```

- [ ] **Step 6: Create `SyncMomentDocs.kt` and the tag**

`server/src/main/kotlin/app/logdate/server/routes/docs/SyncMomentDocs.kt`:

```kotlin
package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.created
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.noContent
import app.logdate.server.routes.ok
import app.logdate.server.routes.sinceAndLimit
import app.logdate.server.routes.sync.DEFAULT_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.MAX_SYNC_PAGE_SIZE
import app.logdate.server.routes.syncError
import app.logdate.shared.model.sync.MomentChange
import app.logdate.shared.model.sync.MomentChangesResponse
import app.logdate.shared.model.sync.MomentDeletion
import app.logdate.shared.model.sync.MomentUpdateRequest
import app.logdate.shared.model.sync.MomentUpdateResponse
import app.logdate.shared.model.sync.MomentUploadRequest
import app.logdate.shared.model.sync.MomentUploadResponse
import app.logdate.shared.model.sync.VersionConstraint
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

private const val MOMENT_ID = "6f0b2d4e-8a1c-4e3f-b5d7-9c2a4e6f8b10"
private const val MOMENT_PAYLOAD = "LDSE2:eyJlbnYiOnsiYWxnIjoiQUVTLUdDTSJ9fQ"

private val momentChange =
    MomentChange(
        id = MOMENT_ID,
        capturedAt = SyncExamples.CREATED_AT,
        lastUpdated = SyncExamples.CREATED_AT,
        deviceId = SyncExamples.deviceId,
        payload = MOMENT_PAYLOAD,
        schema = 1,
        serverVersion = SyncExamples.SERVER_VERSION,
    )

private val invalidMoment =
    ErrorCase(
        "INVALID_MOMENT",
        "The ID is not a valid record key, the payload is not one `LDSE2:` encrypted value of at most 64 KiB, or " +
            "`schema` is below 1. `message` says which.",
        "Moment payload must be one LDSE2 encrypted value",
    )

private fun RequestConfig.momentId(what: String) {
    pathParameter<String>("momentId") {
        description = "The moment's ID, chosen by the client (the apps use UUIDs). $what"
        example("Example") { value = MOMENT_ID }
    }
}

/** Documentation for `SyncMomentRoutes.kt`. */
internal object SyncMomentDocs {
    val upsertMoment: RouteConfig.() -> Unit = {
        bearerOperation(
            "upsertMoment",
            ApiTags.MOMENTS,
            "Create or replace a moment",
            """
            Saves a marked moment under the ID in the path, creating it if new (`201` with a `Location`) and
            replacing it if it exists (`200`). Everything but the times is in `payload`, encrypted on the device;
            the server never reads it. Linked devices may call this.

            Send `?ifAbsent=true` to create only: a live ID answers `412 MOMENT_EXISTS` and a deleted one
            `410 MOMENT_DELETED`, and nothing is written. `If-None-Match: *` also creates only, but may recreate
            a deleted moment.
            """,
        )
        request {
            momentId("Must match `id` in the body.")
            ifAbsentQuery("a live moment answers `412 MOMENT_EXISTS` and a deleted one `410 MOMENT_DELETED`; neither writes anything.")
            jsonBody(
                MomentUploadRequest(
                    id = MOMENT_ID,
                    capturedAt = SyncExamples.CREATED_AT,
                    lastUpdated = SyncExamples.CREATED_AT,
                    deviceId = SyncExamples.deviceId,
                    payload = MOMENT_PAYLOAD,
                ),
                "The whole moment.",
            )
        }
        response {
            val saved =
                MomentUploadResponse(id = MOMENT_ID, serverVersion = SyncExamples.SERVER_VERSION, uploadedAt = SyncExamples.SERVER_VERSION)
            created("The moment did not exist and has been created.", saved, "/api/v1/moments/$MOMENT_ID")
            ok("The moment existed and has been replaced.", saved)
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "VALIDATION_ERROR",
                    "`id` in the body differs from `momentId` in the path.",
                    "Request body id must match path momentId",
                ),
                invalidMoment,
            )
            syncError(
                HttpStatusCode.PreconditionFailed,
                ErrorCase(
                    "MOMENT_EXISTS",
                    "A moment already holds this ID and `If-None-Match: *` or `?ifAbsent=true` was sent. Nothing was " +
                        "written; `details.serverVersion` is the stored version.",
                    "Moment already exists",
                ),
            )
            syncError(
                HttpStatusCode.Gone,
                ErrorCase(
                    "MOMENT_DELETED",
                    "The moment was deleted and `?ifAbsent=true` was sent. Nothing was written; `details.deletedAt` says when.",
                    "Moment was deleted",
                ),
            )
            syncUnauthorized()
            syncServerError()
        }
    }

    val listMomentChanges: RouteConfig.() -> Unit = {
        bearerOperation(
            "listMomentChanges",
            ApiTags.MOMENTS,
            "Page moment changes",
            """
            Returns moments changed after the `since` cursor and tombstones for moments deleted after it. Works
            like **Page entry changes**, with the same cursor: moments share the account's version sequence.
            """,
        )
        request { sinceAndLimit(DEFAULT_SYNC_PAGE_SIZE, MAX_SYNC_PAGE_SIZE) }
        response {
            ok(
                "A page of changes. Send `lastTimestamp` back as the next `since`.",
                MomentChangesResponse(
                    changes = listOf(momentChange),
                    deletions =
                        listOf(
                            MomentDeletion(
                                id = "1c9e7a5b-3d2f-4b6a-8e0c-2f4a6c8e0b1d",
                                deletedAt = SyncExamples.CREATED_AT,
                                serverVersion = SyncExamples.SERVER_VERSION - 1,
                            ),
                        ),
                    lastTimestamp = SyncExamples.SERVER_VERSION,
                ),
            )
            syncError(HttpStatusCode.BadRequest, SyncExamples.invalidSince)
            syncUnauthorized()
            syncServerError()
        }
    }

    val getMoment: RouteConfig.() -> Unit = {
        bearerOperation(
            "getMoment",
            ApiTags.MOMENTS,
            "Read a moment",
            "Returns one live moment as the server holds it, with its `serverVersion`. A deleted moment answers `404`.",
        )
        request { momentId("") }
        response {
            ok("The moment.", momentChange)
            syncError(HttpStatusCode.NotFound, ErrorCase("NOT_FOUND", "No live moment has this ID.", "Moment not found"))
            syncUnauthorized()
            syncServerError()
        }
    }

    val patchMoment: RouteConfig.() -> Unit = {
        bearerOperation(
            "patchMoment",
            ApiTags.MOMENTS,
            "Update a moment",
            """
            Replaces a moment's payload and schema, and its capture time when sent. To ask for conflict detection,
            send `"versionConstraint": { "type": "known", "serverVersion": <the version you last saw> }`; if
            another device wrote in between, the response is `409 CONFLICT` and nothing changes. Unlike entries,
            patching an ID that does not exist answers `404`.
            """,
        )
        request {
            momentId("")
            jsonBody(
                MomentUpdateRequest(
                    payload = MOMENT_PAYLOAD,
                    lastUpdated = SyncExamples.CREATED_AT,
                    deviceId = SyncExamples.deviceId,
                    versionConstraint = VersionConstraint.Known(SyncExamples.SERVER_VERSION),
                ),
                "The new payload, plus the version you last saw.",
            )
        }
        response {
            ok("The moment was updated.", MomentUpdateResponse(MOMENT_ID, SyncExamples.SERVER_VERSION + 1, SyncExamples.SERVER_VERSION + 1))
            syncError(HttpStatusCode.BadRequest, invalidMoment)
            syncError(HttpStatusCode.NotFound, ErrorCase("NOT_FOUND", "No live moment has this ID.", "Moment not found"))
            syncError(HttpStatusCode.Conflict, SyncExamples.conflict)
            syncUnauthorized()
            syncServerError()
        }
    }

    val deleteMoment: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteMoment",
            ApiTags.MOMENTS,
            "Delete a moment",
            """
            Deletes a moment and leaves a tombstone, so other devices learn of the deletion from
            **Page moment changes**. Deleting a moment that does not exist also answers `204`.
            """,
        )
        request { momentId("") }
        response {
            noContent("The moment is deleted.")
            syncUnauthorized()
            syncServerError()
        }
    }
}
```

In `ApiTags.kt`, insert after line 17 (`const val DRAFTS = "Drafts"`):

```kotlin
    const val MOMENTS = "Moments"
```

and insert after line 150 (the `),` closing the `ApiTags.DRAFTS` entry):

```kotlin
                ApiTag(
                    ApiTags.MOMENTS,
                    """
                    A marked moment is a point in time someone flagged, usually with one tap on a watch, to fill in
                    later. Moments have a collection of their own: entry and journal feeds never include them, so
                    clients that do not know moments never see one. Everything but the times is encrypted on the
                    device. Use these endpoints only when the server descriptor lists `momentsV1`.
                    """.trimIndent(),
                ),
```

In `SyncSchemaDocs.kt`, insert before line 420 (`"BackupListResponse" to …`):

```kotlin
            "MomentUploadRequest" to
                SchemaDoc(
                    "A whole marked moment.",
                    mapOf(
                        "id" to "The moment's ID. Must match the path.",
                        "capturedAt" to "When the moment was marked. $EPOCH_MS",
                        "lastUpdated" to "When it was last edited on the device. $EPOCH_MS",
                        "deviceId" to DEVICE_ID,
                        "payload" to "Everything else about the moment as one `LDSE2:` encrypted value, at most 64 KiB. $ENCRYPTED_BY_APPS",
                        "schema" to "The version of the payload's format. Starts at `1`.",
                    ),
                ),
            "MomentUploadResponse" to
                SchemaDoc(
                    "Confirmation that a moment was saved.",
                    mapOf(
                        "id" to "The moment's ID.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "uploadedAt" to "When the server accepted the write. $EPOCH_MS",
                    ),
                ),
            "MomentUpdateRequest" to
                SchemaDoc(
                    "A new payload for an existing moment.",
                    mapOf(
                        "payload" to "The moment's new encrypted payload. $ENCRYPTED_BY_APPS",
                        "schema" to "The version of the payload's format.",
                        "capturedAt" to "A corrected capture time, or absent to keep the stored one. $EPOCH_MS",
                        "lastUpdated" to "When it was edited on the device. $EPOCH_MS",
                        "deviceId" to DEVICE_ID,
                        "versionConstraint" to "Send `known` with the version you last saw to get `409 CONFLICT` instead of overwriting.",
                    ),
                ),
            "MomentUpdateResponse" to
                SchemaDoc(
                    "Confirmation that a moment was updated.",
                    mapOf(
                        "id" to "The moment's ID.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "updatedAt" to "When the server accepted the write. $EPOCH_MS",
                    ),
                ),
            "MomentChange" to
                SchemaDoc(
                    "A marked moment as the server holds it.",
                    mapOf(
                        "id" to "The moment's ID.",
                        "capturedAt" to "When the moment was marked. $EPOCH_MS",
                        "lastUpdated" to "When it was last edited on the device. $EPOCH_MS",
                        "deviceId" to "Which device last saved it.",
                        "payload" to "The encrypted payload. $ENCRYPTED_BY_APPS",
                        "schema" to "The version of the payload's format.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                    ),
                ),
            "MomentDeletion" to
                SchemaDoc(
                    "A tombstone: a moment that was deleted.",
                    mapOf(
                        "id" to "The deleted moment's ID.",
                        "deletedAt" to "When it was deleted. $EPOCH_MS",
                        "serverVersion" to DELETION_VERSION,
                    ),
                ),
            "MomentChangesResponse" to
                SchemaDoc(
                    "Moments changed or deleted since a cursor.",
                    mapOf(
                        "changes" to "Moments created or updated after `since`.",
                        "deletions" to "Moments deleted after `since`.",
                        "lastTimestamp" to CURSOR_LAST_TIMESTAMP,
                        "hasMore" to HAS_MORE,
                    ),
                ),
```

- [ ] **Step 7: Mount the routes and wire production and tests**

In `SyncRoutes.kt`, add the imports `app.logdate.server.logdate.LogDateMomentsRepository` and `app.logdate.server.routes.sync.syncMomentRoutes`; add `- [syncMomentRoutes] — \`/moments\`.` to the KDoc list after the `syncCollectionRoutes` line; add a last parameter after `accessTokenVerifier`:

```kotlin
    momentsRepository: LogDateMomentsRepository? = null,
```

and after `syncCollectionRoutes(accessTokenVerifier, metrics, collectionsRepository)`:

```kotlin
    if (momentsRepository != null) syncMomentRoutes(accessTokenVerifier, metrics, momentsRepository)
```

In `ServerModule.kt`, add the imports `app.logdate.server.logdate.LogDateMomentsRepository` and `app.logdate.server.logdate.RepoBackedLogDateMomentsRepository`, and insert after the `single { RepoBackedLogDateCollectionsRepository(…) }` block (after line 491):

```kotlin
        single<LogDateMomentsRepository> {
            val collections = get<RepoBackedLogDateCollectionsRepository>()
            RepoBackedLogDateMomentsRepository(
                accountRepository = get(),
                signingKeyService = get(),
                blockStore = get(),
                metadataStore = get(),
                repoDidFor = collections::canonicalRepoDid,
            )
        }
```

In `ServerRouting.kt`, add the import `app.logdate.server.logdate.LogDateMomentsRepository`; in `contentApiRoutes` add `val momentsRepository by inject<LogDateMomentsRepository>()` after `val accessTokenVerifier by inject<AccessTokenVerifier>()`, and add `momentsRepository = momentsRepository,` after `accessTokenVerifier = accessTokenVerifier,` in the `syncRoutes(…)` call.

In `SyncTestApp.kt`, add the import `app.logdate.server.logdate.RepoBackedLogDateMomentsRepository`, replace lines 56-63:

```kotlin
    val collectionsRepository =
        RepoBackedLogDateCollectionsRepository(
            accountRepository = accountRepository,
            identityService = atprotoIdentityService,
            signingKeyService = signingKeyService,
            blockStore = InMemoryRepoBlockStore(),
            metadataStore = InMemoryLogDateCollectionsMetadataStore(),
        )
```

with:

```kotlin
    val blockStore = InMemoryRepoBlockStore()
    val metadataStore = InMemoryLogDateCollectionsMetadataStore()
    val collectionsRepository =
        RepoBackedLogDateCollectionsRepository(
            accountRepository = accountRepository,
            identityService = atprotoIdentityService,
            signingKeyService = signingKeyService,
            blockStore = blockStore,
            metadataStore = metadataStore,
        )
    val momentsRepository =
        RepoBackedLogDateMomentsRepository(
            accountRepository = accountRepository,
            signingKeyService = signingKeyService,
            blockStore = blockStore,
            metadataStore = metadataStore,
            repoDidFor = collectionsRepository::canonicalRepoDid,
        )
```

and add `momentsRepository = momentsRepository,` after the `accessTokenVerifier = …` argument Task 5 added to its `syncRoutes(…)` call.

- [ ] **Step 8: Run the tests and watch them pass**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :server:test --tests 'app.logdate.server.routes.sync.SyncMomentRoutesTest' --tests 'app.logdate.server.ServerDescriptorConfigTest' --console=plain`
Expected: PASS.

- [ ] **Step 9: Run the OpenAPI gates, iOS compile, the suite and the end-to-end suite**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :shared:model:compileKotlinIosSimulatorArm64 :shared:model:ktlintFormat :shared:model:ktlintCheck :server:ktlintFormat :server:ktlintCheck :server:detekt :server:test :integration:server-client-e2e:test --console=plain`
Expected: BUILD SUCCESSFUL, including `OpenApiContractTest`, `SchemaDocumentationTest`, and every end-to-end journey (the e2e server now wires `LogDateMomentsRepository` through Koin).

- [ ] **Step 10: Write the commit message file**

```bash
cat > /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-moment-routes.txt <<'MSG'
feat(server): sync marked moments in their own collection

/api/v1/moments stores marked moments: PUT creates or replaces one,
PATCH updates it with an exact version check, DELETE leaves a
tombstone, and GET pages changes with the same cursor as entries. A
moment's contents travel as one encrypted payload of at most 64 KiB
that the server never reads. ?ifAbsent=true creates only and never
brings back a deleted moment, and a linked watch may call every route.

Moments never appear in the entry, journal or link feeds or the sync
status counts, so apps that do not know them see no change. Servers
advertise this as momentsV1.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
MSG
```

- [ ] **Step 11: Commit**

```bash
git restore --staged . && git add shared/model/src/commonMain/kotlin/app/logdate/shared/model/sync/SyncModels.kt shared/model/src/commonMain/kotlin/app/logdate/shared/model/ServerDescriptor.kt server/src/main/kotlin/app/logdate/server/ServerDescriptorConfig.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncMomentRoutes.kt server/src/main/kotlin/app/logdate/server/routes/docs/SyncMomentDocs.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt server/src/main/kotlin/app/logdate/server/sync/SyncMetricsRegistry.kt server/src/main/kotlin/app/logdate/server/routes/SyncRoutes.kt server/src/main/kotlin/app/logdate/server/openapi/ApiTags.kt server/src/main/kotlin/app/logdate/server/openapi/schemadocs/SyncSchemaDocs.kt server/src/main/kotlin/app/logdate/server/di/ServerModule.kt server/src/main/kotlin/app/logdate/server/routes/ServerRouting.kt server/src/test/kotlin/app/logdate/server/SyncTestApp.kt server/src/test/kotlin/app/logdate/server/ServerDescriptorConfigTest.kt server/src/test/kotlin/app/logdate/server/routes/sync/SyncMomentRoutesTest.kt && ANDROID_HOME="$HOME/Library/Android/sdk" git commit -F /private/tmp/claude-501/-Users-williecubed-Projects-TheHypertextStudio-logdate-mobile--claude-worktrees-backup-sync-infinite-notification-dd4c3a/efb084d9-50a2-4c16-993c-f3ed8909e63e/scratchpad/commit-moment-routes.txt
```

Expected: hooks pass; sixteen files in the commit.

---

## Self-Review

**Spec and master-plan coverage**

| Requirement (master plan contract 3, 5, 9, 12; Track A-server; spec "Server changes") | Task |
|---|---|
| Every JWT gets a `jti`; decoding tolerates unknown claims; ships before any `sid` token | 1 (tolerance already on main in `c606d8ca4`, pinned by a test), Rollout Order, Task 6 Step 1 |
| `sid` and `scp=sync` claims | 2 |
| `device_sessions`, `device_link_codes` (spec said V30; V30-V33 are taken, so V34) | 3 |
| `DeviceSessionRepository` in-memory and PostgreSQL | 3 |
| `DeviceLinkService`: 256-bit code, 120 s, single use, ≤ 5 active devices, 10 mints an hour | 3 (store limits), 4 (service) |
| Redeem rate limit by IP | 6 (`DEVICE_LINK_REDEEM_RATE_LIMIT`) |
| `AccessTokenVerifier`, `DeviceSessionGuard` (30 s cache, 0 in tests) | 4, 5 (`DeviceLinkTestKit` uses `Duration.ZERO`) |
| `POST /auth/devices/link` (full scope only), `/link/redeem` (no auth), `GET /auth/devices`, `DELETE /auth/devices/{id}`, `DELETE /auth/devices/self` | 6 |
| Refresh with a `sid` checks the session (401 `DEVICE_SESSION_REVOKED`), slides expiry under 15 days, returns an optional `refreshToken` | 4 (logic), 6 (route) |
| `/auth/me` keeps `sid`/`scp`; a revoked device cannot get a full-access token | 6 (`issueAuthResponse(deviceSessionId)`, `DeviceSessionRevocationTest`) |
| `scp=sync` barred from profile PUT/DELETE, passkeys, email verification, link minting, revoking other devices, identity/PLC, billing | 2 (default deny), 5 (`extractUserId` 403), 6 (`resolveAuthenticatedAccount` 403; tests cover each family) |
| `deviceLinkingV1` | 6 |
| Shared `DeviceLinkModels.kt`; client `DeviceLinkApiClient` with MockEngine tests | 4, 7 |
| `WatchDeviceLinkE2ETest`: mint → redeem → upload a pre-link capture → revoke → 401 | 8 |
| Conditional create, delta only: `HEAD /contents/{id}`, `POST /associations?ifAbsent=true`, 410 `{deletedAt}`, `conditionalCreateV1`, atomic concurrent creates | 9, 10 |
| "Duplicate create makes zero commits, version unchanged, change feed empty; create after delete answers 410", against both harnesses | 9 (`ConditionalCreateProbeTest`: repo-backed store with commit counting, and the sync-backed fallback), 10 (`SyncConditionalCreateRoutesTest` over `configureInMemorySyncApp` and `configureSyncTestApp`) |
| `LogDateCollectionKind.MOMENT`, `/api/v1/moments` PUT/PATCH/DELETE/GET, body `{id, capturedAt, lastUpdated, deviceId, payload, schema}` | 11, 12 |
| Same entitlement checks and tombstone purge as content; isolation from content/journal feeds and status counts | 11 (purge and isolation at the store), 12 (isolation at the routes; content routes carry no quota, so moments carry none) |
| `MOMENTS_V1` | 12 |

**Placeholder scan.** Every step names exact files and carries complete code or an exact before/after edit; no "similar to Task N", no TODOs. The only commands that touch many lines at once are the two `perl` substitutions in Task 5 Step 5, each followed by a `grep` whose expected output is stated.

**Type and name consistency.** `AccessTokenClaims`, `TokenScope`, `AccessCheck`, `AccessTokenVerifier`, `bearerToken`, `DeviceSessionGuard.Disabled`, `DeviceLinkMint`, `DeviceLinkRedemption`, `DeviceSessionRefresh`, `LinkCodeCreation`, `LinkCodeRedemption`, `ConditionalCreate`, `LogDateRecordState`, `AssociationCreateResults`, `RepoConditionalWriter`, `LogDateMomentsRepository` and the error codes in `DeviceLinkErrorCodes` are spelled the same in every task that produces or consumes them. Wire names match between server and client: `code`, `expiresAt`, `deviceName`, `platform`, `accountId`, `sessionId`, `tokens`, `devices`, `current`, `refreshToken`, `existing`, `deleted`, `payload`, `schema`.

**Compatibility check, commit by commit.**
- Tasks 1-5, 9 and 11 change no response an existing client can observe: Task 2 only adds methods, Task 5 keeps every message and code for full tokens, Task 9 and Task 11 add storage nobody calls yet.
- Task 6 adds routes and an optional field that only device tokens ever receive. Mocked auth tests get explicit `decodeAccessToken`/`decodeRefreshToken` stubs so their relaxed mocks cannot fake device claims.
- Task 10 keeps `If-None-Match: *` semantics (pinned by `SyncContentAndJournalLifecycleTest` and `MacTextClientServerE2ETest`); its 412 body only gains `details`.
- Task 12 adds a collection older clients never request.

**Risks to watch while executing.**
- `SchemaDocumentationTest` keys schema prose by the generator's readable name. The plan assumes the class name (`DeviceLinkCodeResponse`, `MomentChange`, …), as for every existing DTO; if the test reports a different name, use the one it prints.
- The plain Ktor `head("/{contentId}")` route appears in `/openapi.json` with generated text. `OpenApiContractTest` checks only GET, POST, PUT, PATCH and DELETE, so it passes; document it later if the reference should list it.
- The two PostgreSQL tests (`DeviceLinkCodePostgresTest`, `ConditionalCreatePostgresTest`) use the local test database named by `DATABASE_URL`; run them with `./run dev` and `./run test:server` before deploying Task 10.
- The device-session guard caches per server instance. On a multi-instance deployment a removed device keeps sync access for up to 30 seconds on the other instances; refresh is checked against the database every time.
- `ktlintFormat` may rewrap some of the code in this plan (chains, long string templates); that is expected and part of each task's lint step.

**Decisions a reviewer may want to override**
1. Strict create lives behind `?ifAbsent=true`; `If-None-Match: *` keeps recreating deleted records because the Mac app's tests require it. The spec asked for 410 on `If-None-Match`.
2. No `reserveIfAbsent` with `SELECT … FOR UPDATE`: atomicity comes from the repo engine's existing `putRecordIfAbsent` and the repo head's compare-and-swap.
3. Single-use codes use a guarded `UPDATE` with a row-count check instead of `UPDATE … RETURNING`, so the H2 test database runs the same code.
4. Device tokens are refused by default everywhere; only the sync data routes, `GET /auth/me`, `GET /auth/devices` and `DELETE /auth/devices/self` accept them. Backups, location history, diagnostics, transcription, quota, device enrollment and key envelopes refuse a watch.
5. Errors keep their family's envelope: `412`/`410` details travel in `SimpleErrorResponse.details` as strings rather than a new body shape.
6. Moments get their own `LogDateMomentsRepository`, so the in-memory sync harness (`configureInMemorySyncApp`) does not serve `/moments`; moment tests run on the repo-backed harness.
7. `PATCH /moments/{id}` on an unknown ID answers `404`, unlike entries, whose PATCH creates.
8. Moment payloads are capped at 64 KiB and must start with `LDSE2:`; `schema` must be at least 1.
9. A lost redeem response spends the code, and refreshing does not revoke the previous device refresh token (so a lost refresh response cannot strand a watch).
10. `deviceLinkingV1`, `conditionalCreateV1` and `momentsV1` are advertised by every deployment, including in-memory development servers.
11. Commit trailers say `Claude Sonnet 5.5`, as this plan's brief requested; the session's default trailer was `Claude Opus 5.5`.
