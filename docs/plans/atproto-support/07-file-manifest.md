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

### Lexicon

- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/LexiconModels.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/LexiconParser.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/LexiconValidator.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/LexiconRegistry.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/LexiconCodegen.kt`
- `shared/atproto-lexicon/src/jvmMain/kotlin/studio/hypertext/atproto/lexicon/GenerateLexicons.kt`
- `shared/atproto-lexicon/src/commonMain/resources/studio/hypertext/logdate/content.json`
- `shared/atproto-lexicon/src/commonMain/resources/studio/hypertext/logdate/journal.json`
- `shared/atproto-lexicon/src/commonMain/resources/studio/hypertext/logdate/association.json`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/logdate/ContentLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/logdate/JournalLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/logdate/AssociationLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/identity/resolveHandle.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/server/describeServer.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/repo/createRecord.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/repo/defs.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/repo/deleteRecord.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/repo/describeRepo.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/repo/getRecord.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/repo/listRecords.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/repo/putRecord.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/repo/uploadBlob.json`
- `shared/atproto-lexicon/src/commonMain/resources/com/atproto/sync/getBlob.json`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/identity/ResolveHandleLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/server/DescribeServerLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo/CreateRecordLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo/DefsLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo/DeleteRecordLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo/DescribeRepoLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo/GetRecordLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo/ListRecordsLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo/PutRecordLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/repo/UploadBlobLexicon.kt`
- `shared/atproto-lexicon/src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/com/atproto/sync/GetBlobLexicon.kt`

### PDS Contracts

- `shared/atproto-pds/src/commonMain/kotlin/studio/hypertext/atproto/pds/DiscoveryModels.kt`
- `shared/atproto-pds/src/commonMain/kotlin/studio/hypertext/atproto/pds/BlobModels.kt`
- `shared/atproto-pds/src/commonMain/kotlin/studio/hypertext/atproto/pds/OAuthModels.kt`
- `shared/atproto-pds/src/commonMain/kotlin/studio/hypertext/atproto/pds/RepoModels.kt`
- `shared/atproto-pds/src/commonMain/kotlin/studio/hypertext/atproto/pds/Services.kt`
- `shared/atproto-pds/src/commonMain/kotlin/studio/hypertext/atproto/pds/PdsException.kt`

### PDS Runtime

- `shared/atproto-pds-runtime/src/commonMain/kotlin/studio/hypertext/atproto/pds/runtime/StaticPdsDiscoveryService.kt`
- `shared/atproto-pds-runtime/src/commonMain/kotlin/studio/hypertext/atproto/pds/runtime/DefaultPdsRepoService.kt`
- `shared/atproto-pds-runtime/src/commonMain/kotlin/studio/hypertext/atproto/pds/runtime/PdsBlobStore.kt`
- `shared/atproto-pds-runtime/src/commonMain/kotlin/studio/hypertext/atproto/pds/runtime/DefaultPdsBlobService.kt`

### PLC

- `shared/atproto-plc/src/commonMain/kotlin/studio/hypertext/atproto/plc/PlcModels.kt`
- `shared/atproto-plc/src/commonMain/kotlin/studio/hypertext/atproto/plc/PlcOperations.kt`
- `shared/atproto-plc/src/commonMain/kotlin/studio/hypertext/atproto/plc/PlcEncoding.kt`
- `shared/atproto-plc/src/commonMain/kotlin/studio/hypertext/atproto/plc/PlcDirectoryClient.kt`

### Repo

- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/Cid.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/DagCborCodec.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/MerkleSearchTree.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/RepoBlockStore.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/RepoEngine.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/RepoModels.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/RepoRecordStore.kt`
- `shared/atproto-repo/src/commonMain/kotlin/studio/hypertext/atproto/repo/RepoException.kt`

## Server Identity and PLC Integration

- `server/src/main/kotlin/app/logdate/server/identity/AtprotoIdentityConfig.kt`
- `server/src/main/kotlin/app/logdate/server/identity/AtprotoIdentityService.kt`
- `server/src/main/kotlin/app/logdate/server/identity/IdentityLifecycleExceptions.kt`
- `server/src/main/kotlin/app/logdate/server/identity/HostedPlcOperationRepository.kt`
- `server/src/main/kotlin/app/logdate/server/identity/SigningKeyRepository.kt`
- `server/src/main/kotlin/app/logdate/server/identity/SigningKeyService.kt`
- `server/src/main/kotlin/app/logdate/server/identity/PlcIdentityService.kt`
- `server/src/main/kotlin/app/logdate/server/database/PostgreSQLHostedPlcOperationRepository.kt`
- `server/src/main/kotlin/app/logdate/server/database/PostgreSQLSigningKeyRepository.kt`
- `server/src/main/resources/db/migration/V6__Add_atproto_identity.sql`
- `server/src/main/resources/db/migration/V11__Add_hosted_plc_operations_table.sql`
- `server/src/main/resources/db/migration/V12__Add_accounts_plc_recovery_did_key.sql`

## Server LogDate Boundaries

- `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsRepository.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionRecords.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/LogDateCollectionsMetadataStore.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/RepoBackedLogDateCollectionsRepository.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/LogDateBlobStorage.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/InMemoryLogDateBlobStorage.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/LogDateAtprotoBlobRepository.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/LogDateMediaRepository.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/LogDateBackupRepository.kt`
- `server/src/main/kotlin/app/logdate/server/logdate/LogDateSyncAdapters.kt`
- `server/src/main/kotlin/app/logdate/server/database/PostgreSQLLogDateCollectionsMetadataStore.kt`
- `server/src/main/kotlin/app/logdate/server/database/PostgreSQLLogDateAtprotoBlobRepository.kt`
- `server/src/main/kotlin/app/logdate/server/database/PostgreSQLLogDateMediaRepository.kt`
- `server/src/main/kotlin/app/logdate/server/database/PostgreSQLLogDateBackupRepository.kt`
- `server/src/main/kotlin/app/logdate/server/sync/GcsMediaStorage.kt`
- `server/src/main/resources/db/migration/V8__Add_logdate_collection_repo_index.sql`
- `server/src/main/resources/db/migration/V9__Add_logdate_media_and_backup_tables.sql`
- `server/src/main/resources/db/migration/V10__Add_logdate_atproto_blob_table.sql`

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
- `server/src/main/kotlin/app/logdate/server/atproto/LogDatePdsBlobStore.kt`
- `server/src/main/kotlin/app/logdate/server/atproto/LogDateRepoStore.kt`
- `server/src/main/kotlin/app/logdate/server/atproto/SyntheticCid.kt`

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
- `gradle.properties`
- `build-logic/src/main/kotlin/app/logdate/AtprotoPublishedModulePlugin.kt`
- `build.gradle.kts`
- `server/build.gradle.kts`
- `shared/atproto-syntax/build.gradle.kts`
- `shared/atproto-identity/build.gradle.kts`
- `shared/atproto-crypto/build.gradle.kts`
- `shared/atproto-xrpc/build.gradle.kts`
- `shared/atproto-plc/build.gradle.kts`
- `shared/atproto-repo/build.gradle.kts`
- `shared/atproto-lexicon/build.gradle.kts`
- `shared/atproto-pds/build.gradle.kts`
- `shared/atproto-pds-runtime/build.gradle.kts`

## Library Documentation and Samples

- `docs/reference/atproto-library.md`
- `docs/reference/atproto-publishing.md`
- `.run/Generate ATProto Dokka.run.xml`
- `.github/workflows/publish-atproto.yml`
- `samples/atproto-consumer/build.gradle.kts`
- `samples/atproto-consumer/settings.gradle.kts`
- `samples/atproto-consumer/README.md`
- `samples/atproto-consumer/src/main/kotlin/studio/hypertext/atproto/sample/Main.kt`

## Primary Test Coverage

### Shared library

- `shared/atproto-crypto/src/commonTest/...`
- `shared/atproto-identity/src/commonTest/...`
- `shared/atproto-lexicon/src/commonTest/...`
- `shared/atproto-lexicon/src/jvmTest/...`
- `shared/atproto-pds/src/commonTest/...`
- `shared/atproto-pds-runtime/src/commonTest/...`
- `shared/atproto-plc/src/commonTest/...`
- `shared/atproto-repo/src/commonTest/...`
- `shared/atproto-syntax/src/commonTest/...`
- `shared/atproto-xrpc/src/commonTest/...`

### Server identity, OAuth, and XRPC

- `server/src/test/kotlin/app/logdate/server/identity/...`
- `server/src/test/kotlin/app/logdate/server/oauth/...`
- `server/src/test/kotlin/app/logdate/server/atproto/...`
- `server/src/test/kotlin/app/logdate/server/logdate/RepoBackedLogDateCollectionsRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/logdate/InMemoryLogDateMediaRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/logdate/InMemoryLogDateBackupRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/logdate/InMemoryLogDateAtprotoBlobRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/logdate/SyncBackedLogDateCollectionsRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/logdate/SyncBackedLogDateMediaRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/logdate/SyncBackedLogDateBackupRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/database/PostgreSQLLogDateCollectionsMetadataStoreTest.kt`
- `server/src/test/kotlin/app/logdate/server/database/PostgreSQLLogDateAtprotoBlobRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/database/PostgreSQLHostedPlcOperationRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/database/PostgreSQLLogDateMediaRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/database/PostgreSQLLogDateBackupRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/atproto/LogDatePdsBlobStoreTest.kt`
- `server/src/test/kotlin/app/logdate/server/sync/GcsMediaStorageTest.kt`
- `server/src/test/kotlin/app/logdate/server/identity/InMemoryHostedPlcOperationRepositoryTest.kt`
- `server/src/test/kotlin/app/logdate/server/routes/Identity*.kt`
- `server/src/test/kotlin/app/logdate/server/routes/OAuthRoutesTest.kt`
- `server/src/test/kotlin/app/logdate/server/routes/Xrpc*.kt`
