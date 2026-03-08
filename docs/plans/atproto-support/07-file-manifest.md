# File Manifest

This manifest reflects the current AT Protocol implementation shape in the repo. It replaces the older `shared/did`-based manifest.

## Shared Kotlin AT Protocol Library

### Syntax

- `shared/atproto-syntax/src/commonMain/kotlin/studio/hypertext/atproto/syntax/Did.kt`
- `shared/atproto-syntax/src/commonMain/kotlin/studio/hypertext/atproto/syntax/Handle.kt`
- `shared/atproto-syntax/src/commonMain/kotlin/studio/hypertext/atproto/syntax/Nsid.kt`
- `shared/atproto-syntax/src/commonMain/kotlin/studio/hypertext/atproto/syntax/RecordKey.kt`
- `shared/atproto-syntax/src/commonMain/kotlin/studio/hypertext/atproto/syntax/Tid.kt`
- `shared/atproto-syntax/src/commonMain/kotlin/studio/hypertext/atproto/syntax/AtUri.kt`

### Identity

- `shared/atproto-identity/src/commonMain/kotlin/studio/hypertext/atproto/identity/AtprotoDid.kt`
- `shared/atproto-identity/src/commonMain/kotlin/studio/hypertext/atproto/identity/DidDocument.kt`
- `shared/atproto-identity/src/commonMain/kotlin/studio/hypertext/atproto/identity/Resolvers.kt`
- `shared/atproto-identity/src/commonMain/kotlin/studio/hypertext/atproto/identity/ResolversImpl.kt`

### XRPC

- `shared/atproto-xrpc/src/commonMain/kotlin/studio/hypertext/atproto/xrpc/XrpcClient.kt`
- `shared/atproto-xrpc/src/commonMain/kotlin/studio/hypertext/atproto/xrpc/XrpcRequestBuilder.kt`
- `shared/atproto-xrpc/src/commonMain/kotlin/studio/hypertext/atproto/xrpc/XrpcAuth.kt`
- `shared/atproto-xrpc/src/commonMain/kotlin/studio/hypertext/atproto/xrpc/KtorXrpcClient.kt`

### Crypto

- `shared/atproto-crypto/src/commonMain/kotlin/studio/hypertext/atproto/crypto/Base58Btc.kt`
- `shared/atproto-crypto/src/commonMain/kotlin/studio/hypertext/atproto/crypto/Multikey.kt`

### PLC

- `shared/atproto-plc/src/commonMain/kotlin/studio/hypertext/atproto/plc/PlcModels.kt`
- `shared/atproto-plc/src/commonMain/kotlin/studio/hypertext/atproto/plc/PlcOperations.kt`
- `shared/atproto-plc/src/commonMain/kotlin/studio/hypertext/atproto/plc/PlcEncoding.kt`
- `shared/atproto-plc/src/commonMain/kotlin/studio/hypertext/atproto/plc/PlcDirectoryClient.kt`

### Repo

- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/RepoModels.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/RepoRecordStore.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/RepoException.kt`

## Server Identity and PLC Integration

- `server/src/main/kotlin/app/logdate/server/identity/AtprotoIdentityConfig.kt`
- `server/src/main/kotlin/app/logdate/server/identity/AtprotoIdentityService.kt`
- `server/src/main/kotlin/app/logdate/server/identity/SigningKeyRepository.kt`
- `server/src/main/kotlin/app/logdate/server/identity/SigningKeyService.kt`
- `server/src/main/kotlin/app/logdate/server/identity/PlcIdentityService.kt`
- `server/src/main/kotlin/app/logdate/server/database/PostgreSQLSigningKeyRepository.kt`
- `server/src/main/resources/db/migration/V6__Add_atproto_identity.sql`

## Server OAuth and PDS-Compatible Routes

- `server/src/main/kotlin/app/logdate/server/oauth/OAuthConfig.kt`
- `server/src/main/kotlin/app/logdate/server/oauth/OAuthErrors.kt`
- `server/src/main/kotlin/app/logdate/server/oauth/OAuthNonceService.kt`
- `server/src/main/kotlin/app/logdate/server/oauth/OAuthClientMetadataResolver.kt`
- `server/src/main/kotlin/app/logdate/server/oauth/OAuthDpopVerifier.kt`
- `server/src/main/kotlin/app/logdate/server/oauth/OAuthKeyService.kt`
- `server/src/main/kotlin/app/logdate/server/oauth/OAuthAccessTokenService.kt`
- `server/src/main/kotlin/app/logdate/server/oauth/OAuthAuthorizationService.kt`
- `server/src/main/kotlin/app/logdate/server/routes/IdentityRoutes.kt`
- `server/src/main/kotlin/app/logdate/server/routes/IdentityApiRoutes.kt`
- `server/src/main/kotlin/app/logdate/server/routes/OAuthRoutes.kt`
- `server/src/main/kotlin/app/logdate/server/routes/XrpcRoutes.kt`
- `server/src/main/kotlin/app/logdate/server/atproto/AtprotoContentRecordStore.kt`

## Shared and Client Model Integration

- `server/src/main/kotlin/app/logdate/server/auth/AccountModels.kt`
- `server/src/main/kotlin/app/logdate/server/database/Tables.kt`
- `shared/model/src/commonMain/kotlin/app/logdate/shared/model/CloudAccount.kt`
- `shared/model/src/commonMain/kotlin/app/logdate/shared/model/Account.kt`
- `client/domain/src/commonMain/kotlin/app/logdate/client/domain/account/model/UserIdentity.kt`
- `client/networking/src/commonMain/kotlin/app/logdate/client/networking/PasskeyApiClient.kt`
- `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepository.kt`

## Wiring and Build Integration

- `settings.gradle.kts`
- `server/build.gradle.kts`
- `shared/atproto-identity/build.gradle.kts`
- `shared/atproto-xrpc/build.gradle.kts`
- `shared/atproto-crypto/build.gradle.kts`
- `shared/atproto-plc/build.gradle.kts`
- `shared/atproto-repo/build.gradle.kts`

## Primary Test Coverage

### Shared library

- `shared/atproto-crypto/src/commonTest/...`
- `shared/atproto-identity/src/commonTest/...`
- `shared/atproto-plc/src/commonTest/...`
- `shared/atproto-repo/src/commonTest/...`
- `shared/atproto-syntax/src/commonTest/...`
- `shared/atproto-xrpc/src/commonTest/...`

### Server identity, OAuth, and XRPC

- `server/src/test/kotlin/app/logdate/server/identity/...`
- `server/src/test/kotlin/app/logdate/server/oauth/...`
- `server/src/test/kotlin/app/logdate/server/atproto/...`
- `server/src/test/kotlin/app/logdate/server/routes/Identity*.kt`
- `server/src/test/kotlin/app/logdate/server/routes/OAuthRoutesTest.kt`
- `server/src/test/kotlin/app/logdate/server/routes/Xrpc*.kt`
