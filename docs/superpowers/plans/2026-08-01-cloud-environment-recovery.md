# Cloud Environment Recovery Implementation Plan

> **Database-provider amendment (2026-08-01):** The product owner selected
> Neon as the authoritative first-party database. Task 3 and every Cloud SQL
> provisioning, connector, proxy, or IAM requirement in this plan are
> superseded by
> [`2026-08-01-neon-first-party-database.md`](./2026-08-01-neon-first-party-database.md).
> Cloud Run and durable object storage remain in scope; first-party staging and
> production must not provision or migrate Cloud SQL.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to execute this plan task-by-task with specification review and code-quality review after every task.

**Goal:** Make first-party staging and production deployments reproducible, fail closed when durability or identity configuration is missing, prove a debug Android build targets staging and a release build targets production, and quarantine the legacy custom-server path that can currently surface multiple origin-scoped accounts.

**Architecture:** Committed environment-specific Terraform variables are the sole deployment authority. A tested renderer converts them into one immutable Cloud Run candidate contract, and a tested rollout script deploys that complete contract without traffic, proves it through the tagged revision URL, promotes the exact revision, verifies the canonical domain, and restores traffic only to an independently proven healthy allocation. The Android app receives a build-variant default endpoint through manifest metadata and classifies persisted configuration before the rest of the dependency graph can use it. A legacy custom origin is preserved only as migration-pending metadata while remote connectivity stays closed; corruption, I/O failure, or timeout also produces an offline-only retryable state rather than a network fallback. Runtime endpoint mutation is feature-gated until Slice 2 can prove the full same-identity binding and recovery-envelope protocol; it is never modeled as account switching.

**Tech Stack:** Kotlin Multiplatform, Android Gradle Plugin, Koin, Android DataStore, Ktor, PostgreSQL/HikariCP, Cloud SQL Java Connector, Terraform Google provider, Google Cloud Run, Secret Manager, GitHub Actions, Bash, `jq`, `gcloud`, Python passkey verifier.

## Global constraints

- Work directly on `main`; every commit must be shippable and leave relevant tests green.
- Write each contract test first and observe the expected failure before implementation.
- Never target a physical Android device. Use JVM/host tests, Gradle Managed Devices, or an emulator whose serial begins with `emulator-`.
- Never print, place on a command line, or commit secret values. Move secrets by stdin or direct secret-to-secret commands.
- Keep `LOGDATE_ENV=production` in both first-party Cloud environments. “Staging” is a deployment environment, not permission to enable development fallbacks.
- Never apply Terraform until the selected remote state reports the same environment and project as the tfvars, and a saved plan contains no unexpected destroy/replace actions.
- Never promote production unless the identical commit SHA has passed the full staging proof.
- Every migration that runs before traffic promotion must remain compatible with the prior serving revision. Use expand/contract migrations or split the rollout when rollback would otherwise be unsafe.
- Never upload to Google Play in this slice. A release build and bundle may be generated locally/CI, but store upload remains the final launch gate.
- A user has exactly one local LogDate identity. Selecting another server keeps the local library and owner identity, clears the active remote authorization, and requires explicit reauthentication. It must never reveal or activate a second stored account.
- Every server-dependent operation must remain queued or locally durable when offline. Endpoint validation may fail, but it may not delete, hide, or block access to local journal data.
- Configuration uncertainty is an offline condition, never permission to use a build default. A failed, corrupt, timed-out, or legacy-custom configuration read must open onboarding/library/editing locally, suspend remote calls and workers, preserve preferences, and remain retryable.
- Keep intermediate commits independently deployable with compatibility gates: first-party strict validation activates only when `LOGDATE_EXPECT_FIRST_PARTY=true`; the enhanced smoke script retains the legacy invocation until the rollout commit switches callers atomically; and the new rollout stays disabled until state, IAM, and secret preflight succeeds. Tasks 7–9 are one indivisible Android/account change: review each checkpoint, but do not commit or push the manifest endpoint injection, startup gate, or settings quarantine separately. Commit them together only after the combined gate passes, so no intermediate app can advertise one origin, activate another, or expose the old mutation UI. Rebase and push each other green commit when its guards preserve the current serving path, then monitor CI/staging before building on it.
- Use the repository staging rule for every commit:

```bash
git restore --staged . && \
  git update-index --add -- <exact-paths> && \
  git commit -F <commit-message-file>
```

---

## Task 0: Freeze Android signing inputs before deployment work

**Files:** local/remote credential state only; repository hardening and build wiring land atomically in Tasks 7–9.

- [ ] Tighten `~/.android/debug.keystore` from the observed mode `0644` to `0600`. It remains a local developer key, not staging authority.
- [ ] Create one dedicated non-production staging keystore in approved private storage, with freshly generated secrets passed through prompt/stdin/private files rather than argv. Back it up through Hypertext Studio's approved credential-recovery path and never commit it.
- [ ] Build a locally signed staging APK with that keystore, use `apksigner verify --print-certs` as the artifact authority, and derive both the colon-hex SHA-256 fingerprint and base64url `android:apk-key-hash:` origin from the same digest bytes. Do not reuse a per-machine default debug certificate.
- [ ] Upload the same keystore bytes/alias/passwords to the GitHub `staging` environment through stdin and retain only its public fingerprint/origin in tfvars and evidence.
- [ ] Recover the original Play upload keystore from Hypertext Studio's approved credential store; none was found in the repository or scoped project/user folders during read-only preflight. In the exact Play application, retrieve the upload and Play app-signing public certificates, cross-check the upload key, and freeze the complete production set before Task 1. If the original upload key cannot be recovered, stop and use Play's formal upload-key-reset process as a separately evidenced credential-recovery action; never substitute a new key silently.
- [ ] Standardize GitHub deployment/publishing on the existing `staging` and `production` environments. Before any upload, add required-reviewer protection to `production`; do not create or keep the workflow-only `android-production` name.
- [ ] Stop if any private key is unavailable or unbacked, if the release artifact does not match the Play upload certificate, if package/application identity is ambiguous, or if a workflow secret would need to be read back from GitHub (encrypted values are non-retrievable).

---

## Task 1: Freeze the complete first-party Cloud Run contract

**Files:**

- Create: `scripts/render-cloud-run-contract.sh`
- Create: `scripts/tests/deploy/test-render-cloud-run-contract.sh`
- Create: `scripts/validate-terraform-isolated.sh`
- Create: `scripts/tests/deploy/test-validate-terraform-isolated.sh`
- Modify: `infra/terraform/main.tf`
- Modify: `infra/terraform/versions.tf`
- Modify: `infra/terraform/variables.tf`
- Modify: `infra/terraform/staging.tfvars`
- Modify: `infra/terraform/staging.tfvars.example`
- Modify: `infra/terraform/production.tfvars`
- Modify: `infra/terraform/production.tfvars.example`

### 1.1 Write the failing renderer contract test

- [ ] Add a shell test that calls:

```bash
scripts/render-cloud-run-contract.sh \
  --environment staging \
  --release-sha 0123456789abcdef0123456789abcdef01234567
```

- [ ] Assert the JSON result has this stable top-level shape:

```json
{
  "environment": "staging",
  "release_sha": "0123456789abcdef0123456789abcdef01234567",
  "project_id": "logdate-dev",
  "region": "us-central1",
  "service_name": "logdate-server-staging",
  "canonical_origin": "https://cloud-staging.logdate.app",
  "runtime_service_account": "<staging-runtime-service-account>",
  "image": "<staging-image-uri>:0123456789abcdef0123456789abcdef01234567",
  "env_vars": {},
  "secret_env": {},
  "runtime": {}
}
```

- [ ] Assert `env_vars` contains the exact environment contract, including:

```text
HOST=0.0.0.0
GCS_PROJECT_ID=<project>
GCS_BUCKET_NAME=<environment bucket>
LOGDATE_ENV=production
LOGDATE_EXPECT_FIRST_PARTY=true
LOGDATE_DEPLOYMENT_KIND=first_party
LOGDATE_SERVER_DISPLAY_NAME=<environment display name>
LOGDATE_PUBLIC_ORIGIN=<canonical origin>
ATPROTO_PDS_SERVICE_URL=<canonical origin>
ATPROTO_HANDLE_DOMAIN=<staging host or production apex>
WEBAUTHN_RP_ID=<environment RP ID>
WEBAUTHN_ORIGIN=<canonical origin>
WEBAUTHN_ALLOWED_ORIGINS=<canonical HTTPS origin plus every approved Android apk-key-hash origin>
ANDROID_CERT_FINGERPRINTS=<matching colon-hex SHA-256 certificates>
BILLING_PROVIDER=play
SERVER_ENCRYPTION_ENABLED=true
SYNC_MEDIA_SIGNED_URLS=true
SYNC_MEDIA_SIGNED_URL_TTL_HOURS=1
AUTO_MIGRATE=false
RELEASE_VERSION=logdate-server@<full SHA>
```

- [ ] Assert `secret_env` requires enabled versions for the selected database mode and for all first-party security keys:

```text
DATABASE_USER
DATABASE_PASSWORD
JWT_SECRET
SERVER_ENCRYPTION_KEY
SERVER_ENCRYPTION_KEY_ID
HEALTH_INTERNAL_TOKEN
```

`DATABASE_URL` is additionally required when `INSTANCE_CONNECTION_NAME` is absent. A connector-backed Cloud SQL contract requires `INSTANCE_CONNECTION_NAME`, `DB_NAME`, `DATABASE_USER`, and `DATABASE_PASSWORD` instead. Every `secret_env` entry is an object containing a Secret Manager ID and an exact positive numeric version; `latest`, aliases, omitted versions, and disabled/destroyed versions are forbidden.

- [ ] Assert optional secrets such as Sentry, Google OIDC, Redis, Stripe, and Play verification are rendered only when explicitly mounted in tfvars and are likewise pinned to positive numeric versions.
- [ ] Assert production fails closed if its upload-certificate and Play app-signing certificate origins/fingerprints are absent; never accept a placeholder.
- [ ] Assert `var.domains[0]` is the canonical domain when legacy `var.domain` is blank.
- [ ] Assert malformed SHA values, unknown environments, HTTP origins, empty required keys, mismatched origins, `BILLING_PROVIDER=disabled`, `AUTO_MIGRATE=true`, or any secret reference using `latest`/an alias/non-numeric version all exit non-zero without writing a contract.

Run and observe the intended RED result:

```bash
bash scripts/tests/deploy/test-render-cloud-run-contract.sh
```

Expected: failure because the renderer does not yet exist and the tfvars omit required keys.

### 1.2 Implement one deterministic renderer

- [ ] Make the script accept only `staging|production` and a 40-character lowercase hexadecimal SHA.
- [ ] Create an isolated `TF_DATA_DIR` with `mktemp -d`, clean it with `trap`, run `terraform -chdir=infra/terraform init -backend=false -input=false`, then use `terraform console -var-file=<environment>.tfvars` and `jsonencode(...)` to read variables and Terraform locals. Decode the console string with `jq -r` rather than parsing HCL with regex. Never reuse a directory initialized against production state.
- [ ] Produce sorted JSON with `jq -S`; this output is both the rollout input and the drift fingerprint.
- [ ] Remove Terraform's `version = "latest"` secret-mount default. Make `version` required in the input type and validate it as a positive decimal string before the renderer can emit `secret_env`.
- [ ] Compute the image URI from project, region, Artifact Registry repository, service, and full SHA. Do not trust a mutable `latest` image.
- [ ] Validate the contract before printing it. Error messages may name missing keys but must never include secret values.

The renderer's only output on stdout must be JSON. Human diagnostics go to stderr.

### 1.3 Correct first-party tfvars

- [ ] Add the exact plain variables above to staging and production.
- [ ] Set staging display name to `LogDate Cloud (Staging)`, public/PDS origin to `https://cloud-staging.logdate.app`, handle domain and RP ID to `cloud-staging.logdate.app`, and use only the public origin/fingerprint extracted from Task 0's deterministic staging-signed APK. Reject the previously observed per-machine debug certificate unless it is independently proven to be that designated key.

- [ ] Set production display name to `LogDate Cloud`, public/PDS origin to `https://cloud.logdate.app`, handle domain and RP ID to `logdate.app`, and add the verified upload and Play app-signing certificates obtained from the exact Play application. Do not merge this task while those values are unknown.
- [ ] Mount the six always-required secret variables plus the environment's selected database secret contract in both tfvars.
- [ ] Commit the exact enabled numeric version for every mounted secret in each environment's tfvars. A secret rotation becomes an explicit reviewed tfvars change and therefore a new commit/SHA; it cannot silently alter an already-rendered or serving revision.
- [ ] Add those secret IDs to `secret_ids` so Terraform owns the containers.
- [ ] Remove the misleading placeholder-image comments; the rollout always substitutes the immutable image before deployment.
- [ ] Mirror the safe shape in both example files without copying project-specific signing values.

### 1.4 Remove the obsolete Cloud SQL environment name

- [ ] Replace `CLOUD_SQL_INSTANCE_CONNECTION_NAME` in Terraform locals with the supported `INSTANCE_CONNECTION_NAME` expected by the server's connector path.
- [ ] Keep `DB_NAME`; never synthesize a localhost URL for a Cloud SQL deployment.

### 1.5 Make backend-free Terraform validation hermetic

- [ ] Write a fake-Terraform test proving the validation helper creates a new private temporary directory, exports it as `TF_DATA_DIR`, runs `init -backend=false -input=false` followed by `validate`, and removes the directory on success or failure.
- [ ] Prove the helper ignores any `.terraform` directory or backend metadata in the checkout and forwards no environment-specific backend config.
- [ ] Use this helper in every CI and local validation gate. The stateful environment wrapper in Task 4 must likewise allocate a fresh `TF_DATA_DIR` for each invocation before intentionally initializing the selected remote backend.

### 1.6 Verify and commit

```bash
terraform -chdir=infra/terraform fmt -recursive
terraform -chdir=infra/terraform fmt -check -recursive
bash scripts/tests/deploy/test-validate-terraform-isolated.sh
bash scripts/validate-terraform-isolated.sh
bash scripts/tests/deploy/test-render-cloud-run-contract.sh
```

Commit message:

```text
fix(infra): define complete first-party runtime contracts

Make staging and production tfvars the only source of Cloud Run runtime
configuration and reject deployments that could fall back to memory,
self-hosted identity, unlimited quota, or non-strict passkeys.
```

---

## Task 1A: Reconcile required first-party secrets without accidental rotation

**Files:**

- Create: `scripts/reconcile-first-party-secrets.sh`
- Create: `scripts/tests/deploy/test-reconcile-first-party-secrets.sh`
- Modify: `infra/terraform/main.tf`
- Modify: `docs/runbook/staging-production-configuration.md`

### 1A.1 Write the secret-safety tests first

- [ ] Use fake `gcloud` and `gh` binaries to prove `--mode inventory` lists only secret IDs, enabled-version counts, and IAM principals—never values.
- [ ] Prove inventory reports the exact latest enabled numeric version for each ID, while reconciliation emits a sorted non-secret version-lock proposal and never writes `latest` into tfvars or a Cloud Run contract.
- [ ] Prove existing enabled `JWT_SECRET`, `SERVER_ENCRYPTION_KEY`, and key ID versions are left byte-for-byte untouched. The script must not add a version or rotate a signing/encryption key merely because a deployment is being repaired.
- [ ] Prove a missing JWT or encryption key halts when the environment has any existing account/database record or media/backup object. Initialization is allowed only with an explicit `--initialize-empty-environment` flag after every emptiness probe passes; otherwise recover the authoritative existing value.
- [ ] Prove generated health/signing/encryption values flow only through stdin (`gcloud secrets versions add ... --data-file=-`) or permission-0600 temporary files removed by `trap`; values may not appear in argv, logs, shell traces, GitHub outputs, or generated follow-up commands.
- [ ] Prove database secrets are never invented for an existing environment. Missing values require permission-0600 input files or an already enabled Secret Manager version.
- [ ] Prove the exact health token is synchronized to GitHub's matching `staging` or `production` environment with `gh secret set HEALTH_INTERNAL_TOKEN --env <environment>` reading stdin.
- [ ] Prove runtime and deploy principals have only the required accessor bindings and that the deploy principal can read migration secrets before rollout is enabled.
- [ ] Prove the deploy service account can invoke private Cloud Run candidate URLs and mint an audience-bound ID token for itself. For fresh bootstrap this binding must exist at project/principal scope before the service exists; do not make the candidate public merely to smoke it.

Run and observe RED:

```bash
bash scripts/tests/deploy/test-reconcile-first-party-secrets.sh
```

### 1A.2 Implement inventory and reconciliation

- [ ] Accept only `--environment staging|production` and `--mode inventory|reconcile`.
- [ ] Resolve project and secret IDs from the rendered tfvars contract; do not accept repo-variable mirrors.
- [ ] Create missing secret containers, but add versions only under the rules above.
- [ ] Generate a high-entropy JWT signing key, a 32-byte server encryption key, and a non-secret random key ID only for a proven-empty environment. Store all three in Secret Manager because the runtime contract mounts them there.
- [ ] Generate a high-entropy health token when absent and synchronize it without exposing it.
- [ ] Emit only a sorted `SECRET_ENV=secret-id:numeric-version` lock proposal after reconciliation. Update and commit the corresponding tfvars numeric versions, re-render, and verify that every pinned version is enabled before rollout; the script must not mutate source files or deploy an uncommitted lock.
- [ ] Verify enabled versions for every mounted secret and least-privilege IAM before returning success.
- [ ] Grant the deploy service account the narrow Cloud Run invoker role needed for private candidate proof plus the self-scoped OpenID-token creation permission required by workload-identity impersonation; verify both without printing a token.

### 1A.3 Reconcile staging and production before enabling rollout v2

- [ ] Reauthenticate an authorized gcloud operator, inventory both projects, and record only redacted status evidence.
- [ ] Recover existing JWT signing and encryption material from its authoritative source if a project contains data but a mounted secret lacks a version. Never replace either with a new key.
- [ ] Supply missing database values through private files/stdin, validate connectivity, and erase the input files.
- [ ] Run the inventory again, apply the non-secret numeric lock proposal to tfvars as a reviewed commit, and require every mounted exact version to be enabled. Do not deploy a same-SHA candidate after changing a secret version.

### 1A.4 Verify and commit

```bash
bash scripts/tests/deploy/test-reconcile-first-party-secrets.sh
```

Commit message:

```text
fix(infra): reconcile first-party deployment secrets safely

Provision required health, database, signing, and encryption bindings without
exposing values or rotating keys that protect sessions, user media, and backups.
```

---

## Task 2: Make production-profile startup fail closed and expose release identity

**Files:**

- Modify: `server/src/main/kotlin/app/logdate/server/config/ProductionConfigValidator.kt`
- Modify: `server/src/test/kotlin/app/logdate/server/config/ProductionConfigValidatorTest.kt`
- Modify: `server/src/main/kotlin/app/logdate/server/Application.kt`
- Modify: `server/src/test/kotlin/app/logdate/server/ApplicationTest.kt`
- Create: `server/src/test/kotlin/app/logdate/server/ServerDescriptorConfigTest.kt`
- Modify: `server/docs/environment-variables.md`
- Modify: `docs/observability/health-endpoint.md`

### 2.1 Write fail-closed tests first

- [ ] Extend the validator fixture's valid environment to include every required production key so existing focused tests remain meaningful.
- [ ] Add one parameterized case for each missing or unsafe condition:

```text
neither DATABASE_URL nor a complete INSTANCE_CONNECTION_NAME/DB_NAME connector contract is present
neither GCS_BUCKET_NAME nor a durable self-hosted LOGDATE_BLOB_STORAGE_DIR is present
LOGDATE_EXPECT_FIRST_PARTY=true while LOGDATE_DEPLOYMENT_KIND is not first_party
LOGDATE_PUBLIC_ORIGIN absent or not HTTPS
ATPROTO_PDS_SERVICE_URL different from LOGDATE_PUBLIC_ORIGIN
BILLING_PROVIDER disabled while LOGDATE_EXPECT_FIRST_PARTY=true
HEALTH_INTERNAL_TOKEN absent
RELEASE_VERSION absent or not logdate-server@<40-hex-sha>
SERVER_ENCRYPTION_ENABLED not true
SERVER_ENCRYPTION_KEY absent or invalid base64/AES length
SERVER_ENCRYPTION_KEY_ID absent
SYNC_MEDIA_SIGNED_URLS not true
WEBAUTHN_ALLOWED_ORIGINS lacks an Android origin while LOGDATE_EXPECT_FIRST_PARTY=true
ANDROID_CERT_FINGERPRINTS absent or malformed while LOGDATE_EXPECT_FIRST_PARTY=true
```

- [ ] Keep development and test profiles permissive.
- [ ] Keep secure production self-hosting valid: `LOGDATE_EXPECT_FIRST_PARTY` is the fail-closed assertion used by managed staging/production, while a self-hosted operator may choose a durable filesystem blob store and disabled billing without being mislabeled as LogDate Cloud.
- [ ] Add an `ApplicationTest` proving public health includes an exact immutable `release` field while still hiding `db_connected`.
- [ ] Add an internal-health test proving the correct token returns `db_connected=true` and the same `release`.
- [ ] Keep API contract version (`version`) separate from deployment release (`release`).

Run and observe RED:

```bash
./gradlew :server:test --tests '*ProductionConfigValidatorTest' --tests '*ApplicationTest'
```

### 2.2 Implement validation and health release

- [ ] Add small named validation helpers rather than one monolithic method.
- [ ] Decode the encryption key only to check length; never include its raw or decoded value in an exception.
- [ ] Add `releaseVersion: String = System.getenv("RELEASE_VERSION").orEmpty()` to `Application.module(...)` so tests do not mutate process environment.
- [ ] Emit:

```json
{
  "status": "healthy",
  "timestamp": "...",
  "version": "1.0.0",
  "release": "logdate-server@<full-sha>"
}
```

- [ ] Keep `db_connected` token-gated.

### 2.3 Verify and commit

```bash
./gradlew :server:test
./gradlew ktlintCheck
```

Commit message:

```text
fix(server): refuse non-durable first-party startup

Fail production-profile boot when identity, database, durable media,
encryption, quota, passkey, health, or release configuration is incomplete,
and expose the immutable deployment release in health responses.
```

---

## Task 3: Add a supported, turnkey Cloud SQL connector path

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `server/build.gradle.kts`
- Modify: `server/src/main/kotlin/app/logdate/server/database/DatabaseConfig.kt`
- Modify: `server/src/test/kotlin/app/logdate/server/database/DatabaseConfigTest.kt`
- Modify: `infra/terraform/main.tf`
- Modify: `scripts/bootstrap-gcp-fresh.sh`
- Modify: `scripts/tests/deploy/test-bootstrap-gcp-fresh.sh`
- Modify: `scripts/run-migrations.sh`
- Modify: `scripts/tests/deploy/test-run-migrations.sh`

### 3.1 Write connector tests first

- [ ] Add tests proving `INSTANCE_CONNECTION_NAME` selects this Hikari configuration:

```kotlin
jdbcUrl = "jdbc:postgresql:///logdate"
username = configuredDatabaseUser
password = configuredDatabasePassword
dataSourceProperties["socketFactory"] = "com.google.cloud.sql.postgres.SocketFactory"
dataSourceProperties["cloudSqlInstance"] = instanceConnectionName
dataSourceProperties["ipTypes"] = "PUBLIC,PRIVATE"
dataSourceProperties["cloudSqlRefreshStrategy"] = "lazy"
```

- [ ] Prove a normal `DATABASE_URL` still wins for Neon/self-hosted deployments and local host/port remains development-only.
- [ ] Update the bootstrap fake-CLI test to assert it writes an `INSTANCE_CONNECTION_NAME`, creates/versions required secrets, and never emits the removed variable.
- [ ] Prove the bootstrap owns SQL-user creation through the Cloud SQL Admin API while Terraform owns only the instance, database, IAM, and Secret Manager containers. Assert the database password never appears in argv, stdout/stderr, a generated command, Terraform input/state, or shell trace.
- [ ] Add a migration-script test proving `INSTANCE_CONNECTION_NAME` starts a pinned Cloud SQL Auth Proxy, waits for its local readiness, and runs the stock Flyway image against that localhost tunnel. The Java socket factory is an application-runtime dependency and must not be assumed present in the Flyway image.
- [ ] Prove Flyway receives its user/password through a permission-0600 `--env-file` removed by `trap`, never as Docker/Flyway command-line values or logs.
- [ ] Require `run-migrations.sh --contract-file <path>` and prove it derives the exact project, connection name, database, and pinned `DATABASE_USER`/`DATABASE_PASSWORD` secret ID/version mappings from that rendered contract. Official mode rejects independent database/instance/secret overrides, missing numeric versions, or an environment mismatch.
- [ ] Exercise the optional `--validate-passkey-fk` path under `set -x` and prove its stock PostgreSQL container receives `PGUSER`/`PGPASSWORD`/database through a permission-0600 env file, never `docker -e NAME=<secret>`, argv, stdout/stderr, or a generated command.

Run and observe RED:

```bash
./gradlew :server:test --tests '*DatabaseConfigTest'
bash scripts/tests/deploy/test-bootstrap-gcp-fresh.sh
bash scripts/tests/deploy/test-run-migrations.sh
```

### 3.2 Implement the official connector pattern

- [ ] Add `com.google.cloud.sql:postgres-socket-factory:1.29.0` to the version catalog and server runtime dependencies (the current upstream release verified on 2026-08-01).
- [ ] When `INSTANCE_CONNECTION_NAME` is set, configure the Hikari properties above. The Cloud Run runtime service account supplies Application Default Credentials; no service-account key file is allowed.
- [ ] Keep the pool serverless-friendly (`minimumIdle=0`, lazy connector refresh, bounded maximum pool size).
- [ ] Have the bootstrap write `DATABASE_USER` and `DATABASE_PASSWORD` as secrets and the non-secret database name/connection name as plain config.
- [ ] Provision or update the database user with the Cloud SQL Admin REST API. Read the password from a permission-0600 file/stdin, build the JSON request body by reading that file (never an argument), write the access-token header and request body to private temporary files, call `curl` with only those file paths in argv, and erase them with `trap`. Tests must run under `set -x` and still find no secret.
- [ ] Keep migrations on a pinned Cloud SQL Auth Proxy plus the stock Flyway image. Put Flyway credentials in a private env file and pass only its path; stop the proxy and remove all temporary files on every exit.
- [ ] Make both Flyway and the optional `psql` foreign-key validation consume the same contract-derived proxy/database and pinned Secret Manager versions. Put all database credentials in private env files and pass only file paths to Docker.
- [ ] Do not model the Cloud SQL user or its password in Terraform. The bootstrap/reconciliation path owns the user through the Cloud SQL Admin API and Secret Manager so credentials never enter Terraform state.
- [ ] Grant the runtime account `roles/cloudsql.client`.
- [ ] Grant the GitHub deploy account least-privilege access to only the database secrets needed for migrations; do not grant project-wide Secret Manager Admin.

### 3.3 Verify and commit

```bash
./gradlew :server:test
bash scripts/tests/deploy/test-bootstrap-gcp-fresh.sh
bash scripts/tests/deploy/test-run-migrations.sh
```

Commit message:

```text
fix(server): connect fresh deployments to Cloud SQL

Use the authenticated Cloud SQL Java connector in Cloud Run and provision the
database contract and least-privilege migration access required for persistent
accounts in a freshly bootstrapped project.
```

---

## Task 4: Isolate Terraform state and reject destructive environment drift

**Files:**

- Create: `infra/terraform/backend.tf`
- Create: `infra/terraform/backend-staging.hcl`
- Create: `infra/terraform/backend-production.hcl`
- Create: `scripts/terraform-environment.sh`
- Create: `scripts/tests/deploy/test-terraform-environment.sh`
- Modify: `infra/terraform/main.tf`
- Modify: `infra/terraform/outputs.tf`
- Modify: `infra/terraform/README.md`
- Modify: `scripts/deploy-production.sh`
- Create: `scripts/tests/deploy/test-deploy-production.sh`

### 4.1 Write the guard test first

- [ ] Use fake `terraform` and `gcloud` binaries to prove the wrapper:
  - initializes the live-verified staging state bucket with prefix `logdate/staging`;
  - initializes the live-verified production state bucket with prefix `logdate/production`;
  - compares state output `environment` and `project_id` to tfvars;
  - rejects absent/mismatched state unless `--reconcile-existing` is explicitly passed;
  - writes a binary saved plan and inspects `terraform show -json`;
  - rejects every delete or replace unless the exact resource address is in a reviewed allow-list file;
  - accepts the one reviewed `removed { lifecycle { destroy = false } }` forget action for the Cloud Run service, proves no delete API call occurs, and requires a clean second plan;
  - proves Terraform owns no Cloud Run revision template or traffic field and a post-rollout plan is empty;
  - never runs `terraform apply` from the validation subcommand.

Run and observe RED:

```bash
bash scripts/tests/deploy/test-terraform-environment.sh
```

### 4.2 Implement environment-specific remote state

- [ ] Commit an empty `backend "gcs" {}` block and two non-secret backend config files only after confirming each bucket belongs to the matching GCP project. The expected bootstrap defaults are `logdate-dev-tfstate` and `logdate-tfstate`, but live ownership—not the name guess—is authoritative.
- [ ] Raise Terraform's required version to at least 1.7 so the audited `removed` block is supported, and pin CI/operator setup to a currently verified compatible release rather than whatever happens to be newest.
- [ ] Add Terraform outputs for `environment` and `project_id`; introduce an explicit `environment` variable constrained to `staging|production|self_hosted`.
- [ ] Make `scripts/rollout-cloud-run.sh`/`gcloud` the sole owner of the Cloud Run service, revision template, and traffic. For each environment, first take a versioned state backup and prove the current configuration is zero-drift. Replace the service resource with an auditable Terraform `removed` block whose lifecycle has `destroy = false`, require the saved plan to show only forgetting that exact address and no remote deletion, apply that state-only transition, then require a second plan with no service destroy/recreate. Do not use an unrecorded `state rm` shortcut. Terraform continues to own prerequisite infrastructure plus invoker IAM and domain mappings by explicit service-name string.
- [ ] Make the wrapper require `--environment`, allocate a fresh `TF_DATA_DIR`, select the matching backend/tfvars, and refuse a state project mismatch before plan/import/apply. Remove the private data directory on every exit.
- [ ] Add `--reconcile-existing` that inventories and imports all live resources managed by Terraform: APIs, Artifact Registry, runtime/deploy service accounts, IAM bindings, GCS bucket/IAM, Cloud SQL instance/database, Secret Manager containers/IAM, WIF pool/provider, Cloud Run invoker IAM, and domain mappings. Explicitly exclude the Cloud Run service/revisions/traffic, Cloud SQL user, and all secret versions; the rollout and bootstrap/reconciliation scripts respectively own those surfaces.
- [ ] For a fresh environment, make the turnkey wrapper apply prerequisite infrastructure first, have rollout bootstrap the private service, then apply service-dependent invoker/domain resources and run canonical proof. At no point may Terraform synthesize or later revert a serving revision template.
- [ ] Make reconciliation idempotent and print resource addresses only, never secret values.
- [ ] Retire `deploy-production.sh`'s implicit local-state behavior by delegating to this wrapper.

### 4.3 Reconcile live environments before applying

- [ ] Reauthenticate an authorized gcloud operator interactively.
- [ ] Create or verify the two state buckets with versioning and uniform bucket-level access.
- [ ] Reconcile staging into its state, then production into its state.
- [ ] Save both plan JSON files as launch evidence and require zero unexpected deletes/replacements.
- [ ] After a tested staging rollout, run a new saved Terraform plan and require no drift. Repeat after production recovery. Any attempt to recreate/import the Cloud Run service or change its template/traffic is fatal.
- [ ] Do not proceed if either state cannot be reconciled safely.

### 4.4 Verify and commit

```bash
terraform -chdir=infra/terraform fmt -check -recursive
bash scripts/tests/deploy/test-terraform-environment.sh
bash scripts/tests/deploy/test-deploy-production.sh
```

Commit message:

```text
fix(infra): isolate deployment state by environment

Separate staging and production Terraform state, reject cross-project plans,
and require explicit reconciliation before any existing resource can change.
```

---

## Task 5: Turn the revision smoke test into a real durability proof

**Files:**

- Modify: `scripts/smoke-test-revision.sh`
- Modify: `scripts/passkey-verify/sim.py`
- Modify: `scripts/tests/deploy/test-smoke-test-revision.sh`
- Modify: `scripts/passkey-verify/README.md`

### 5.1 Write failing smoke-script tests

- [ ] Replace substring-only assertions with a fake HTTP fixture covering each endpoint and cleanup request.
- [ ] Require explicit flags:

```text
--service-url
--contract-file <sorted JSON emitted by render-cloud-run-contract.sh>
--expected-release
--invoker-token-file <permission-0600 Cloud Run ID-token file for private candidate URLs>
--phase prepare|verify-and-cleanup|health-only
--state-file <permission-0600 temporary file>
```

- [ ] Read the canonical origin, RP ID, handle domain, package name, complete Android apk-key-hash origin set, and complete colon-hex certificate set from the immutable contract file. Sort and compare full sets exactly; checking that one fingerprint is present is insufficient.
- [ ] Introduce the contract-file form without breaking the currently deployed caller. Keep the old invocation as a tested, warning-emitting compatibility path only until Task 6 switches every official caller in the same commit, then remove it.
- [ ] Read `HEALTH_INTERNAL_TOKEN` from the environment only.
- [ ] Require candidate/private-service phases to read a Cloud Run identity token from the private file and send it as `X-Serverless-Authorization: Bearer ...` on every request. Preserve `Authorization` for LogDate bearer tokens after signin. Reject an absent, empty, over-permissioned, or logged token file; canonical public verification does not depend on it.
- [ ] Add negative cases for:
  - public health wrong release;
  - internal health absent/false `db_connected`;
  - descriptor not `FIRST_PARTY`;
  - wrong `serverOrigin`, `apiBaseUrl`, handle domain, or RP ID;
  - missing first-party capabilities;
  - asset links with the wrong package, relations, an omitted expected certificate, or any unapproved extra certificate;
  - unauthenticated protected route returning anything except 401;
  - authenticated quota unlimited or not equal to the seeded free tier;
  - media bytes/hash changing between candidate upload and canonical-domain download;
  - media still readable after deletion;
  - failed test-account cleanup.

Run and observe RED:

```bash
bash scripts/tests/deploy/test-smoke-test-revision.sh
```

### 5.2 Implement authenticated proof and cleanup

- [ ] Extend the verifier to return the disposable account ID, bearer/refresh tokens, credential file, media ID, and media SHA-256 through a permission-0600 state file, never stdout.
- [ ] In `prepare`, complete passkey signup and signin against the candidate URL while using the canonical origin for `clientDataJSON`, assert finite quota, upload a cryptographically random media fixture, and retain the state for the post-promotion phase.
- [ ] Thread the private-service header through the Python verifier and every shell HTTP probe without ever copying the token into argv/errors. Tests must prove authenticated requests carry both Cloud Run's `X-Serverless-Authorization` and LogDate's `Authorization` headers to their respective layers.
- [ ] In `verify-and-cleanup`, sign in through the canonical domain with the same passkey, download the candidate-created media, SHA-256 compare it, verify finite quota/usage, delete the media, require a not-found response, delete the disposable account, revoke tokens, and erase the state file.
- [ ] Add a cleanup subcommand/trap that attempts remote account/media cleanup after any failure and always removes the local temporary directory. A pre-promotion failure cleans through the candidate; a post-promotion failure cleans through the promoted revision before rollback when possible.
- [ ] Redact authorization headers, tokens, challenge payloads, and credential material from errors.

### 5.3 Verify and commit

```bash
bash scripts/tests/deploy/test-smoke-test-revision.sh
python -m compileall -q scripts/passkey-verify/sim.py
```

Commit message:

```text
test(server): prove deployed identity quota and media durability

Gate traffic on exact release and descriptor identity, authenticated database
health, strict passkeys, finite quota, durable media round trips, and complete
disposable-account cleanup.
```

---

## Task 6: Make no-traffic rollout complete, atomic, and reversible

**Files:**

- Create: `scripts/rollout-cloud-run.sh`
- Create: `scripts/tests/deploy/test-cloud-run-rollout.sh`
- Modify: `.github/workflows/deploy-server-cloud-run.yml`
- Modify: `.github/workflows/deploy-server-staging.yml`
- Modify: `.github/workflows/deploy-server-production.yml`
- Modify: `.github/workflows/ci.yml`
- Modify: `scripts/deploy-cloud-run.sh`
- Modify: `scripts/tests/deploy/test-github-actions-metadata.sh`

### 6.1 Write the fake-gcloud rollout test first

- [ ] Prove the script performs this exact order:

```text
render complete contract
resolve the SHA-tagged image to an immutable digest and finalize the effective contract
validate every pinned numeric Secret Manager version
capture the complete serving traffic allocation, probe it, and classify whether a safe rollback baseline exists
run migrations from that same contract
deploy the immutable image digest as a tagged no-traffic candidate with the full contract
resolve the exact candidate revision and tag URL
describe and normalize the observed candidate revision, then require exact contract equality
mint an audience-bound deploy-identity token into a private file
smoke candidate URL in prepare phase and retain the disposable proof state
promote exact candidate revision to 100%
in bootstrap mode, finalize Terraform-owned invoker/domain bindings and wait for the canonical mapping
smoke canonical origin in verify-and-cleanup phase using the candidate-created account/media
on canonical failure restore the complete captured traffic allocation only when it was independently healthy
smoke the restored canonical origin when rollback is available; otherwise stop with explicit recovery diagnostics
```

- [ ] Add `--mode normal|recovery|bootstrap` fixtures:
  - `normal` requires the captured allocation to pass baseline health and enables automatic rollback;
  - `recovery` permits an unhealthy existing allocation (the current production condition), leaves it untouched until a fully proven no-traffic candidate is ready, and does not claim it as a rollback target;
  - `bootstrap` permits no prior revision for a genuinely new service.
- [ ] Derive the only legal mode from observed state and reject inconsistent operator input: `bootstrap` only when the service is proven absent, `recovery` only when the service exists but the complete baseline is unhealthy/unknown, and `normal` only when it exists and the complete baseline is healthy. Never let a manually selected mode weaken the observed precondition.
- [ ] Snapshot revision percentages before any mutation and probe the canonical service plus every allocated revision through an unambiguous revision/tag URL. Mark the allocation rollback-safe only when the complete serving baseline passes release and database-health checks independently of the new candidate. A proven-absent service requires `bootstrap`; an existing service with a 503, unknown revision, or partial/split-allocation failure requires `recovery`.
- [ ] In recovery/bootstrap mode, a candidate failure leaves traffic unchanged. After promotion, a canonical failure must stop and retain complete diagnostics; automatic rollback is allowed only when the independently probed baseline was healthy. Never describe an unhealthy restored revision as recovery.
- [ ] Prove a pre-promotion failure never invokes traffic update.
- [ ] For the normal single-revision baseline, prove a post-promotion failure invokes exactly:

```bash
gcloud run services update-traffic "$SERVICE_NAME" \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --to-revisions "$PRIOR_REVISION=100"
```

- [ ] Prove the candidate command uses the immutable image digest and complete env/pinned-secret/runtime/scaling/resources/probe contract and never `latest`, a secret alias, or the destructive one-key form `--set-env-vars RELEASE_VERSION=...`.
- [ ] Normalize a fake `gcloud run revisions describe` result and reject any observed difference in image digest, runtime service account, literal environment set, secret ID/numeric-version set, Cloud SQL attachment, scaling, CPU/memory, timeout, concurrency, startup probe, or liveness probe. Ignore only documented server-generated fields.
- [ ] In normal, recovery, and bootstrap fixtures, prove candidate HTTP/passkey/media requests work while the traffic-tag URL is private: resolve the untagged Cloud Run service URL and mint the deploy-identity token with that service URL as `aud`, while sending HTTP to the tagged candidate URL. Reject the tag URL as audience unless a separately tested custom audience is explicitly configured. Capture the token directly into a mode-0600 file, pass only that path to smoke, and remove it on every exit. A fake trace/log must contain neither token nor an `Authorization` collision.
- [ ] Prove production rejects a SHA without a successful full staging evidence record.
- [ ] Add a split-traffic fixture and prove rollback reconstructs the exact captured revision percentages instead of collapsing them to `latest`.

Run and observe RED:

```bash
bash scripts/tests/deploy/test-cloud-run-rollout.sh
```

### 6.2 Implement the reusable rollout

- [ ] Keep complex behavior in the tested script; GitHub Actions should authenticate/build and call it.
- [ ] Build and push only immutable SHA tags. `latest` may be updated for operator convenience but must never be deployed.
- [ ] Resolve the pushed SHA tag to its registry digest, add that digest to a sorted effective-contract artifact, fingerprint it, and deploy by digest. Pass multiline complete plain env and exact `secret-id:numeric-version` bindings with overwrite semantics so runtime drift is removed intentionally.
- [ ] Resolve candidate revision by candidate tag and promote by revision name, not by `latest` or an ambiguous tag.
- [ ] After deployment and before smoke/promotion, describe the exact candidate revision, normalize its rollout-owned spec, compare it byte-for-byte with the effective contract, and record both desired and observed fingerprints. A mismatch is a pre-promotion failure.
- [ ] Keep every candidate tag private. Resolve the untagged service URL from Cloud Run, mint a token for that audience through service-account impersonation, then call the tagged URL. Store the token in a permission-0600 file under `umask 077`; the smoke client reads the file and uses `X-Serverless-Authorization`, leaving LogDate's `Authorization` header available. Never place the token in argv, workflow outputs, shell traces, or evidence.
- [ ] Make canonical-domain failure fatal. Automatic rollback is mandatory only when the captured baseline independently passed health; recovery/bootstrap mode must stop with explicit diagnostics when no safe rollback exists.
- [ ] Carry the permission-0600 smoke state from candidate preparation into canonical verification so the same database account and media object prove persistence across the traffic transition.
- [ ] Pass the same effective contract file to `run-migrations.sh`, candidate deployment, observed-spec verification, and smoke proof. Official rollout paths expose no independent database or secret selectors.
- [ ] Switch every workflow/wrapper to the new smoke `--contract-file` interface in this commit, then remove the temporary legacy invocation retained by Task 5. No official caller may supply identity/certificate expectations independently of the rendered contract.
- [ ] In bootstrap mode only, after candidate proof/promotion, call the guarded Terraform finalization that adds invoker/domain bindings without owning the service template, wait for the canonical mapping, and then run canonical verification. Normal/recovery modes require those bindings to pre-exist.
- [ ] Emit a machine-readable staging evidence artifact containing SHA, image digest, desired and observed contract fingerprints, candidate revision, smoke result, and canonical result. It contains no credentials.
- [ ] Production wrapper downloads and validates that evidence for the same SHA.
- [ ] Make manual production dispatch require an explicit SHA plus confirmation instead of silently skipping because no `server-v*` ref exists.

### 6.3 Add permanent CI validation

- [ ] Add a deploy-contract CI job running:

```bash
terraform -chdir=infra/terraform fmt -check -recursive
bash scripts/validate-terraform-isolated.sh
shellcheck scripts/render-cloud-run-contract.sh scripts/rollout-cloud-run.sh scripts/smoke-test-revision.sh
actionlint
bash scripts/tests/deploy/test-render-cloud-run-contract.sh
bash scripts/tests/deploy/test-cloud-run-rollout.sh
bash scripts/tests/deploy/test-smoke-test-revision.sh
bash scripts/tests/deploy/test-github-actions-metadata.sh
```

- [ ] Make `scripts/deploy-cloud-run.sh` a compatibility wrapper around the same renderer/rollout script. Remove official `repo_vars` and divergent image-only first-party paths.

### 6.4 Verify and commit

```bash
bash scripts/tests/deploy/test-cloud-run-rollout.sh
bash scripts/tests/deploy/test-github-actions-metadata.sh
actionlint
shellcheck scripts/render-cloud-run-contract.sh scripts/rollout-cloud-run.sh scripts/smoke-test-revision.sh
```

Commit message:

```text
fix(infra): make server rollouts atomic and reversible

Deploy the full tested runtime contract to an immutable no-traffic candidate,
promote only after durability proof, and restore a proven healthy prior traffic
allocation when canonical verification fails.
```

---

## Task 6A: Publish exact Android Digital Asset Links at each RP domain

**Files:**

- Modify in the `logdate-web` repository: `apps/web/public/.well-known/assetlinks.json`
- Modify in the `logdate-web` repository: `tests/e2e/dal.spec.ts`
- Modify if necessary: `apps/web/scripts/extract-signing-fingerprint.mjs`
- Modify in this repository: `scripts/tests/deploy/test-render-cloud-run-contract.sh`

### 6A.1 Write exact-certificate tests first

- [ ] In the web test, reject debug-only and placeholder production files. Require package `co.reasonabletech.logdate`, both relations, and the exact verified Play upload plus Play app-signing SHA-256 fingerprints.
- [ ] In the Cloud contract test, require staging's server-served asset links to contain only approved staging/debug certificates and production's server-served copy to match the same exact production certificate set as the apex website.
- [ ] Add a live probe target derived from the RP ID—not merely the API origin—because production Credential Manager verifies `https://logdate.app/.well-known/assetlinks.json` while the API runs at `cloud.logdate.app`.

### 6A.2 Update and deploy the web contract

- [ ] Fetch and reconcile the web repository's current integration branch according to its own `AGENTS.md`; preserve unrelated dirty work.
- [ ] Use Play Console's exact Hypertext Studio LogDate application to retrieve the two public certificate fingerprints. Cross-check the upload certificate against the signed release candidate locally.
- [ ] Update the committed DAL file and deploy the web change through its normal preview/production path.
- [ ] Require HTTP 200, `application/json`, no redirects, the exact package, relations, and certificate set from:

```text
https://logdate.app/.well-known/assetlinks.json
https://cloud-staging.logdate.app/.well-known/assetlinks.json
https://cloud.logdate.app/.well-known/assetlinks.json
```

- [ ] Record the web commit/deployment URL in the launch evidence. Do not continue to Android passkey proof while the RP-domain file is stale.

### 6A.3 Verify and commit in each repository

```bash
# logdate-web
pnpm test:e2e -- tests/e2e/dal.spec.ts
pnpm lint

# logdate-android
bash scripts/tests/deploy/test-render-cloud-run-contract.sh
```

Android-repository commit message:

```text
test(infra): require exact RP-domain app links

Keep staging and production server contracts aligned with the Android signing
certificates that their WebAuthn relying-party domains publish.
```

---

## Task 7: Give Android build variants deterministic Cloud endpoints

**Files:**

- Modify: `gradle.properties`
- Modify: `.gitignore`
- Modify: `app/android-main/build.gradle.kts`
- Modify: `app/android-main/src/main/AndroidManifest.xml`
- Create: `client/data/src/androidMain/kotlin/app/logdate/client/data/config/AndroidBackendUrlResolver.kt`
- Create: `client/data/src/androidHostTest/kotlin/app/logdate/client/data/config/AndroidBackendUrlResolverTest.kt`
- Modify: `client/data/src/androidMain/kotlin/app/logdate/client/data/di/DataModule.android.kt`
- Modify: `shared/config/src/commonMain/kotlin/app/logdate/shared/config/LogDateConfigRepository.kt`
- Create: `shared/config/src/commonTest/kotlin/app/logdate/shared/config/LogDateConfigRepositoryTest.kt`
- Modify: `shared/config/build.gradle.kts`
- Create: `scripts/verify-android-signing-contract.sh`
- Create: `scripts/tests/deploy/test-verify-android-signing-contract.sh`
- Modify: `scripts/setup-play-publishing-secrets.sh`
- Modify: `scripts/prepare-android-play-publishing.sh`
- Create: `scripts/tests/deploy/test-play-publishing-signing.sh`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/publish-android-play.yml`

### 7.1 Write resolver and build-contract tests first

- [ ] Add host tests proving the Android resolver:
  - reads `app.logdate.backendUrl` manifest metadata;
  - normalizes an absent path or exactly `/` to a pathless origin;
  - accepts HTTPS staging/production/custom origins only when they are an origin, not a base URL with a subpath;
  - rejects missing, blank, credential-bearing, non-root-path, query/fragment-bearing, or non-HTTPS values.
- [ ] Add a Gradle verification task test/proof that:
  - debug merged manifest contains `https://cloud-staging.logdate.app` by default;
  - release merged manifest contains `https://cloud.logdate.app` by default;
  - `-Plogdate.backendUrl=https://example.test` overrides a local build;
  - `verifyPlayBackendUrl` fails for every release URL except exactly `https://cloud.logdate.app`.
- [ ] Add repository tests proving `buildDefaultBackendUrl` remains the injected build value after loading a custom endpoint and that `resetToDefaults()` returns to that injected value, not the global production constant.
- [ ] Add fake-`apksigner`/`jarsigner`/`keytool`/HTTP tests for the signing verifier. Require it to extract the exact signer SHA-256 digest from an APK, strictly verify an AAB and extract the same signer, derive the corresponding base64url `android:apk-key-hash:` origin from those digest bytes, and compare the complete sorted certificate/origin sets in the rendered contract with the live RP-domain Digital Asset Links document. Reject redirects, wrong content type, missing/extra certificates, APK/AAB signer disagreement, and an artifact signer that is not the environment's expected build signer.
- [ ] Prove the CI staging journey fails before passkey signup when no deterministic staging keystore is supplied or when its APK signer, rendered contract, and live `cloud-staging.logdate.app` DAL do not match exactly.
- [ ] Prove every release/bundle/publish task fails closed when any upload-signing input is missing or the keystore signer differs from Task 0's upload certificate. The release variant may never silently fall back to the debug key; benchmark/test-only variants may keep their explicit debug signing.
- [ ] Prove the Play setup helper sends keystore bytes and passwords to `gh secret set --env production` through stdin, never `--body`, argv, logs, or generated commands. Prove both internal and production publishing jobs select the protected `production` environment and remain disabled until the explicit launch variable is enabled.

Run and observe RED:

```bash
./gradlew :shared:config:allTests :client:data:testAndroidHostTest
./gradlew :app:android-main:processDebugMainManifest :app:android-main:processReleaseMainManifest
./gradlew :app:android-main:verifyPlayBackendUrl -Plogdate.backendUrl=https://cloud-staging.logdate.app
bash scripts/tests/deploy/test-verify-android-signing-contract.sh
bash scripts/tests/deploy/test-play-publishing-signing.sh
```

The `verifyPlayBackendUrl ... staging` Gradle command is the intentional post-implementation failure; before implementation that task is absent. Both signing shell tests transition from RED (missing safe behavior) to GREEN only with exact fixture agreement.

### 7.2 Implement build-variant defaults

- [ ] Remove the global `logdate.backendUrl` value from checked-in `gradle.properties`; it currently forces every build to production.
- [ ] Define lazy Providers in the Android app build:

```kotlin
val backendOverride = providers.gradleProperty("logdate.backendUrl")
val debugBackendUrl = backendOverride.orElse("https://cloud-staging.logdate.app")
val releaseBackendUrl = backendOverride.orElse("https://cloud.logdate.app")
```

- [ ] Put `${logdateBackendUrl}` into application metadata and set the placeholder per build type. Benchmark inherits the release value.
- [ ] Resolve that metadata once when Android's data module constructs the singleton `LogDateConfigRepository`; remove Android's inclusion of the common production-default `configModule`. Desktop/iOS/Wear keep their existing production default.
- [ ] Expose the immutable constructor value as `buildDefaultBackendUrl` and use it for `resetToDefaults()`. Give the interface a production-default getter so existing lightweight test fakes remain source-compatible, and override it in the real repository. This is the source of truth for “LogDate Cloud” in settings, so a debug build never silently jumps from staging to production.
- [ ] Add `verifyPlayBackendUrl` as an input-tracked verification task and make Play publish tasks depend on it.
- [ ] Add `*.jks` and `*.keystore` defense-in-depth ignores (the pre-commit secret hook remains mandatory). Remove the release build's debug-signing fallback and make requested release/bundle/publish tasks fail with names of missing inputs but no values.
- [ ] Use Task 0's designated non-production staging key and the matching GitHub `staging` secrets. Local staging proof and CI must sign with that same key; a per-machine Android debug key is never accepted as staging passkey evidence.
- [ ] Reconstruct the staging keystore only into a permission-0600 temporary file in CI, sign the debug candidate through an explicit staging signing configuration, and erase it on every exit. Ordinary offline developer debug builds may still use their local debug key, but Cloud signup/passkey verification must remain disabled until `verify-android-signing-contract.sh` passes.
- [ ] Make CI assemble a debug APK without a backend override, inspect the merged manifest for staging, and run the signing verifier against the rendered staging contract and live RP-domain DAL before any passkey journey.
- [ ] Make the Play workflow pass `-Plogdate.backendUrl=https://cloud.logdate.app` explicitly and verify the release merged manifest before bundle publication.
- [ ] Change both Play jobs to the protected `production` GitHub environment and provision `ANDROID_PUBLISHER_CREDENTIALS`, `LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_RELEASE_BASE64`, plus all four upload-signing secrets there via stdin. Keep both publish-enable variables false/absent throughout this slice; no upload is authorized until the final launch gate.
- [ ] Change `scripts/setup-play-publishing-secrets.sh` and every touched bootstrap helper from `gh secret set --body <value>`/generated value-bearing commands to stdin or private files, with fake-CLI tests that run under shell tracing and detect no leaked sentinel.
- [ ] For production, require the local release artifact signer to equal the verified Play upload certificate while the rendered contract and RP-domain DAL contain the complete exact upload plus Play app-signing certificate set. A locally built APK cannot stand in for the Play-re-signed artifact.

### 7.3 Verify the checkpoint; do not commit or push

```bash
./gradlew :shared:config:allTests :client:data:testAndroidHostTest
./gradlew :app:android-main:processDebugMainManifest :app:android-main:processReleaseMainManifest
./gradlew :app:android-main:verifyPlayBackendUrl
./gradlew :app:android-main:assembleDebug
bash scripts/tests/deploy/test-verify-android-signing-contract.sh
bash scripts/tests/deploy/test-play-publishing-signing.sh
```

Keep these reviewed changes in the working tree and continue directly through Tasks 8 and 9. Their safety properties depend on one another and land in the single Task 9 commit.

---

## Task 8: Persist the selected endpoint before networking can start

**Files:**

- Modify: `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/LogDateConfigDataSource.kt`
- Create: `client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/LogDateConfigDataSourceTest.kt`
- Modify: `client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/SessionStorage.kt`
- Create: `client/datastore/src/commonTest/kotlin/app/logdate/client/datastore/DataStoreSessionStorageTest.kt`
- Modify: `client/datastore/src/commonMain/kotlin/app/logdate/client/di/DatastoreModule.kt`
- Create: `shared/config/src/commonMain/kotlin/app/logdate/shared/config/RemoteEndpointStartupState.kt`
- Create: `shared/config/src/commonTest/kotlin/app/logdate/shared/config/RemoteEndpointStartupStateTest.kt`
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/LogDateApplication.kt`
- Create: `app/compose-main/src/androidHostTest/kotlin/app/logdate/client/LogDateApplicationStartupOrderTest.kt`
- Modify: `client/device/src/commonMain/kotlin/app/logdate/client/device/storage/SecureSessionStorage.kt`
- Create: `client/device/src/commonTest/kotlin/app/logdate/client/device/storage/SecureSessionStorageTest.kt`
- Modify: `client/device/src/androidMain/kotlin/app/logdate/client/device/AndroidAccountManager.kt`
- Create: `client/device/src/androidHostTest/kotlin/app/logdate/client/device/AndroidAccountManagerTest.kt`
- Create: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/account/LegacyRemoteAuthorizationQuarantine.kt`
- Create: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/account/LegacyRemoteAuthorizationQuarantineTest.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/LogDateCloudApiClient.kt`
- Modify: `client/data/src/commonMain/kotlin/app/logdate/client/data/account/PasskeyAccountRepository.kt`
- Modify: `client/sync/src/androidMain/kotlin/app/logdate/client/sync/cloud/di/CloudAccountModule.android.kt`
- Modify: `client/sync/src/desktopMain/kotlin/app/logdate/client/sync/cloud/di/CloudAccountModule.desktop.kt`
- Modify: `client/sync/src/iosMain/kotlin/app/logdate/client/sync/cloud/di/CloudAccountModule.ios.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepository.kt`
- Modify: `client/sync/src/commonTest/kotlin/app/logdate/client/sync/cloud/account/DefaultCloudAccountRepositoryTest.kt`

### 8.1 Write persistence, migration, and failure-policy tests first

- [ ] Prove a persisted endpoint equal to the injected build default reaches `Ready(origin)` before initialization returns.
- [ ] Prove a persisted endpoint different from the build default never becomes active. Preserve its normalized origin in `LegacyMigrationPending(preservedOrigin)`, leave the remote-connectivity gate closed, and keep the build default from being used as a fallback.
- [ ] Prove an empty DataStore keeps the injected build default (staging in debug), not the shared production constant.
- [ ] Prove startup does not write defaults before reading existing preferences.
- [ ] Prove DataStore corruption, read exception, and a deterministic two-second timeout each produce `LocalOnly(retryableFailure)` without writing or replacing any preference. The local owner, onboarding, library, editor, entries, media, and outbox remain readable/writable while every remote call is rejected as retryable offline work.
- [ ] Prove a later successful retry re-runs the same classification before opening the gate; it may reach `Ready` or `LegacyMigrationPending`, but may never silently choose a default.
- [ ] Prove backend URL, API version, and descriptor are persisted in one DataStore edit from a single combined collector only after startup reaches `Ready`.
- [ ] Prove initialization is idempotent and starts only one persistence collector.
- [ ] Prove the Android startup sequence completes configuration initialization before resolving or starting any scheduler, account repository, network client, or server-facing worker dependency.
- [ ] Prove the platform Cloud account module supplies the same `LogDateConfigRepository` instance as the API clients.
- [ ] Seed two origin-scoped legacy sessions/accounts and prove startup never loads either as another identity. For `LegacyMigrationPending`, clear every active access/refresh token from DataStore, secure storage, and `DefaultCloudAccountRepository`, then remove every legacy `username@backendUrl` Android system-account record before any account repository is resolved; preserve only non-secret migration metadata and the local canonical identity/library. For `LocalOnly`, leave credential/account storage byte-for-byte untouched and merely make it inaccessible behind the closed gate until configuration can be classified safely.
- [ ] Prove Android `AccountManager` exposes at most one LogDate account after successful authentication. Its stable system-account name is derived from the one local canonical identity, while remote username/origin are metadata/authorization attributes; adding an authorization replaces that metadata and tokens rather than adding a second system account.
- [ ] Prove `SessionStorage`, `SecureSessionStorage`, `DefaultCloudAccountRepository`, and `PasskeyAccountRepository` no longer reactively load credentials merely because `backendUrl` emits a different origin.
- [ ] Prove Cloud API calls and passkey/account operations require `RemoteEndpointStartupState.Ready`; pending/failed state returns a typed retryable offline result without constructing or executing an HTTP request.

Run and observe RED:

```bash
./gradlew \
  :shared:config:allTests \
  :client:logdate-datastore:allTests \
  :client:device:allTests \
  :client:data:allTests \
  :client:sync:allTests \
  :app:compose-main:testAndroidHostTest
```

### 8.2 Make configuration bootstrap explicit

- [ ] Remove all asynchronous work from `LogDateConfigDataSource.init`.
- [ ] Add a shared fail-closed state machine:

```kotlin
sealed interface RemoteEndpointStartupState {
    data object Initializing : RemoteEndpointStartupState
    data class Ready(val origin: String) : RemoteEndpointStartupState
    data class LegacyMigrationPending(val preservedOrigin: String) : RemoteEndpointStartupState
    data class LocalOnly(val retryableFailure: ConfigurationReadFailure) : RemoteEndpointStartupState
}
```

`RemoteConnectivityGate` exposes the state and returns the ready origin or a typed retryable-offline failure. No server-facing component reads `getCurrentBackendUrl()` directly without passing this gate.

- [ ] Add an idempotent `suspend fun initialize()` guarded by a `Mutex` and a two-second timeout:
  1. read exactly one Preferences snapshot without starting a writer;
  2. if no endpoint exists, apply the injected build default and enter `Ready`;
  3. if the stored endpoint equals the normalized build default, restore its API version/descriptor and enter `Ready`;
  4. if it differs, copy only the normalized origin to a `pending_legacy_server_origin` key, remove it from active configuration, quarantine active remote authorization, and enter `LegacyMigrationPending` with the gate closed;
  5. on corruption, exception, or timeout, make no DataStore mutation, enter `LocalOnly`, schedule a bounded background retry, and return so local UI can open;
  6. only in `Ready`, start one combined persistence collector and return when the origin is authoritative.
- [ ] Resolve the data source and run `initialize()` on `Dispatchers.IO` immediately after Koin starts and before notification, location, rewind, event, calendar, shortcut, WorkManager-dependent, account, or networking work starts.
- [ ] Keep local repositories, onboarding, library, capture/editing, and sync-outbox writes outside this gate. The bounded read protects remote identity but may not turn configuration failure into an app-start failure.
- [ ] Extract the ordered startup calls behind a small testable coordinator. Every pre-existing WorkManager or early-initializer path that can reach the server must pass the shared readiness gate; pending/failed workers return `Result.retry()` without executing HTTP, while purely local workers continue.
- [ ] Implement `LegacyRemoteAuthorizationQuarantine` as an idempotent, crash-safe migration that clears only remote session/account/token keys (including all known origin-scoped legacy prefixes), invalidates Android tokens, and removes legacy backend-keyed system accounts. It must not clear device identity keys, the local owner, encryption keys, entries, media, or outbox records. Mark completion only after every remote store succeeds; retry with the gate closed after partial failure.
- [ ] Stop encoding `username@backendUrl` as an Android identity. Migrate the platform account manager to one stable local-identity account record with remote origin/username as replaceable authorization metadata and make `getStoredAccounts()` return zero or one. If ambiguous legacy records exist, quarantine/remove them and require reauthentication rather than selecting one. Slice 2 adds verified origin-specific authorization bindings beneath this one identity; it never reintroduces multiple user accounts.
- [ ] Inject the shared config into every `DefaultCloudAccountRepository` binding; remove the private production-default split brain.
- [ ] Remove backend-flow collectors that auto-load a scoped session/account. A later endpoint mutation is rejected by Task 9; defensive handling closes the gate and quarantines authorization rather than loading credentials for the new origin.

### 8.3 Verify the checkpoint; do not commit or push

```bash
./gradlew \
  :shared:config:allTests \
  :client:logdate-datastore:allTests \
  :client:device:allTests \
  :client:data:allTests \
  :client:sync:allTests \
  :app:compose-main:testAndroidHostTest
./gradlew :app:android-main:assembleDebug
./gradlew ktlintCheck
```

Keep this checkpoint uncommitted and continue directly to Task 9 so the legacy settings action cannot mutate the newly guarded configuration in an intermediate build.

---

## Task 9: Quarantine legacy origin-as-account switching

**Files:**

- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/ServerConfigurationCoordinator.kt`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/AdvancedSettingsViewModel.kt`
- Modify: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/AdvancedSettingsViewModelTest.kt`
- Modify: `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/AdvancedSettingsScreen.kt`
- Create: `client/feature/core/src/commonTest/kotlin/app/logdate/feature/core/settings/ui/ServerConfigurationCoordinatorTest.kt`

The approved architecture requires a portable canonical identity, descriptor signing-key pinning, an origin-bound challenge, a server-signed same-identity binding receipt, and a recovery-envelope write/read proof before changing the active origin. Those foundations belong to Slice 2. A “sign out, change URL, load whatever credentials exist there” shortcut would preserve the current multi-account bug, so Slice 1 must make that path unreachable while retaining the build-time override needed for staging and operator testing.

### 9.1 Write the quarantine tests first

- [ ] Rename the first-party preset to `LOGDATE_CLOUD` and prove it resolves `configRepository.buildDefaultBackendUrl`; a debug build's Cloud preset must remain staging instead of jumping to the global production constant.
- [ ] Prove saving the already-active LogDate Cloud origin is idempotent.
- [ ] Prove a release build cannot persist a custom origin through the legacy coordinator while the compatible-server feature flag is off.
- [ ] Prove the settings UI does not render an account list, switch-account action, or enabled custom-server save action.
- [ ] Prove the disabled custom-server explanation says that same-identity server migration is being prepared and that local/offline data remains available; it must not imply users should create another account.
- [ ] Prove a developer build can still target a conformant server with the documented Gradle property without mutating runtime account state.
- [ ] Prove no blocked action changes the config repository, session stores, Android AccountManager, local owner, journal data, or sync outbox.

Run and observe RED:

```bash
./gradlew :client:feature:core:allTests
```

### 9.2 Gate the unsafe path

- [ ] Add one centralized `compatibleServerSelectionEnabled` flag, defaulting false in every distributable variant until Slice 2's full identity-transition suite passes.
- [ ] Keep server discovery/normalization helpers available for tests and the next slice, but make the legacy save methods return a typed `IdentityTransitionRequired` failure without mutating configuration.
- [ ] Remove any account-switching wording. Keep one LogDate Cloud choice and a disabled compatible-server row with the explanation above.
- [ ] Keep the documented `-Plogdate.backendUrl=...` build override for developers and separately built self-host clients; this chooses the build default before identity/session creation and is not a runtime account switch.
- [ ] Immediately follow this slice with a Slice 2 implementation plan for the complete identity state machine and compatible-server protocol from the approved launch design. Do not declare the launch requirement complete from this quarantine.

### 9.3 Verify and commit

```bash
./gradlew \
  :shared:config:allTests \
  :client:logdate-datastore:allTests \
  :client:device:allTests \
  :client:data:allTests \
  :client:sync:allTests \
  :client:feature:core:allTests \
  :app:compose-main:testAndroidHostTest
./gradlew :app:android-main:processDebugMainManifest :app:android-main:processReleaseMainManifest
./gradlew :app:android-main:verifyPlayBackendUrl
./gradlew :app:android-main:assembleDebug
./gradlew :app:android-main:assembleRelease :app:android-main:bundleRelease
bash scripts/tests/deploy/test-verify-android-signing-contract.sh
bash scripts/tests/deploy/test-play-publishing-signing.sh
bash scripts/verify-android-signing-contract.sh \
  --apk app/android-main/build/outputs/apk/release/android-main-release.apk \
  --bundle app/android-main/build/outputs/bundle/release/android-main-release.aab \
  --environment production
./gradlew ktlintCheck
```

The release commands run with Task 0's private upload-signing inputs already materialized outside Git. Missing credentials must fail before artifact creation. The verifier derives the production contract/RP URL, requires strict APK/AAB signer equality with the frozen upload certificate, and requires the full live DAL set before the atomic commit.

Commit message:

```text
fix(account): enforce one offline-first LogDate identity

Bind each build to its intended Cloud origin, classify configuration before
remote startup, keep the local journal usable on failure, and block legacy
origin-scoped account switching until verified server migration is complete.
```

---

## Task 10: Unify operator paths and document the exact contract

**Files:**

- Modify: `scripts/bootstrap-gcp-fresh.sh`
- Modify: `scripts/deploy-production.sh`
- Modify: `scripts/setup-gcp-deploy.sh`
- Modify: their tests under `scripts/tests/deploy/`
- Modify: `docs/runbook/staging-production-configuration.md`
- Modify: `infra/terraform/README.md`
- Modify: `server/docs/google-cloud-production.md`
- Modify: `server/docs/environment-variables.md`
- Modify: `docs/observability/health-endpoint.md`

### 10.1 Write documentation/metadata assertions first

- [ ] Extend deployment metadata tests to reject:
  - `LOGDATE_DEPLOY_SOURCE=repo_vars` as a first-party authority;
  - `logdate.hypertext.studio` as the Cloud Run domain;
  - direct-to-traffic deploy commands;
  - optional Secret Manager mounts without enabled-version checks;
  - `gh secret set --body <value>`, `docker -e NAME=<secret>`, or generated manual commands containing secret values;
  - GitHub environment names other than `staging` and `production`;
  - any official path that skips migrations or the full smoke proof.

### 10.2 Delegate all operator paths to the same primitives

- [ ] Make bootstrap generate a self-hosted tfvars file, isolated backend config, and complete secret containers/versions, then call the shared Terraform and rollout scripts.
- [ ] Make production helper scripts call the same state guard, renderer, migrations, candidate smoke, promotion, canonical verification, and rollback flow as CI.
- [ ] Remove generated repo-variable mirrors as deployment inputs. Non-secret mirrors may remain informational only.
- [ ] Make all bootstrap/setup secret writes consume stdin/private files, including GitHub secrets. Failure output may name the destination key and a safe rerun script path, but may never embed a value-bearing command.
- [ ] Document the exact one-property Android override:

```bash
./gradlew :app:android-main:assembleDebug \
  -Plogdate.backendUrl=https://your-logdate-server.example
```

- [ ] Document that runtime server selection is temporarily gated and will require the complete same-identity descriptor/challenge/binding/recovery-envelope transaction while preserving the offline library.
- [ ] Document the actual GitHub environment names and required secrets/protection rules.

### 10.3 Verify and commit

```bash
for test_script in scripts/tests/deploy/test-*.sh; do
  bash "$test_script"
done
./gradlew :server:test :integration:server-client-e2e:test
```

Commit message:

```text
docs: make deployment and server selection turnkey

Route every supported operator path through the same safe rollout contract and
document staging, production, custom-server, secrets, and one-identity behavior
without obsolete configuration paths.
```

---

## Task 11: Integrate continuously and prove staging, then production

**Files:**

- Create: `docs/launch/evidence/cloud-environment-recovery-2026-08-01.md`
- Modify only if verification finds a defect: files from Tasks 1–10, with a new focused test and commit for each fix.

### 11.1 Run the local release gate

- [ ] Fetch origin and rebase `main` before the gate; preserve unrelated work and use no merge commits.
- [ ] Run:

```bash
terraform -chdir=infra/terraform fmt -check -recursive
bash scripts/validate-terraform-isolated.sh
for test_script in scripts/tests/deploy/test-*.sh; do bash "$test_script"; done
./gradlew \
  :server:test \
  :integration:server-client-e2e:test \
  :client:logdate-datastore:allTests \
  :client:sync:allTests \
  :client:data:allTests \
  :client:feature:core:allTests \
  :client:device:testAndroidHostTest \
  :app:compose-main:testAndroidHostTest \
  :app:android-main:assembleDebug \
  :app:android-main:bundleRelease \
  ktlintCheck
```

- [ ] Inspect merged manifests and record exact debug/release endpoints.
- [ ] Request a final subagent specification review and code-quality review. Fix every P0/P1 before pushing.

### 11.2 Push and prove staging

- [ ] Rebase on the newest `origin/main`, rerun the smallest affected gate, and push `main`.
- [ ] Configure the GitHub `production` environment with required-reviewer protection and prevent administrators from bypassing the same-SHA/evidence gate. Keep `staging` automatic from green `main`.
- [ ] Watch CI and staging deployment to completion; do not poll more often than useful workflow transitions.
- [ ] Download and retain the staging evidence artifact.
- [ ] Download the exact deterministically staging-signed debug APK produced by that SHA, record its checksum, run the signing-contract verifier again, and install that same artifact only on an emulator/Gradle Managed Device. A separately rebuilt or default-debug-key APK is not evidence for this journey.
- [ ] Prove from a clean app state:
  - manifest-selected endpoint is staging;
  - local onboarding works offline and local entry creation remains available;
  - Cloud offer appears when online;
  - passkey signup/signin works against staging;
  - `/server/info` is exact first-party staging identity;
  - quota is finite and real;
  - media upload/download survives a new server revision/instance;
  - app process recreation retains selected endpoint/session without exposing another account;
  - corrupt, throwing, and timed-out configuration starts leave onboarding/library/capture usable locally, make zero remote requests, retain queued data, and recover after a successful retry;
  - the unsafe legacy custom-server control is gated and cannot activate origin-scoped credentials as another account.

### 11.3 Prove production from the identical SHA

- [ ] Verification only: re-read the Play upload and app-signing certificate fingerprints from the exact Hypertext Studio Play application and require them to equal the already-tested, deployed contract and RP-domain DAL. If either differs, stop. Correcting any certificate input creates a new commit/SHA and requires the complete local, staging, signing, DAL, passkey, and same-SHA evidence sequence again; never patch this immediately before production.
- [ ] Require the same SHA's staging evidence.
- [ ] Create and push an annotated `server-vYYYY.MM.DD.N` tag pointing to that SHA.
- [ ] Watch production rollout to completion.
- [ ] Repeat release, descriptor, internal DB health, passkey simulator, finite quota, durable media, cleanup, asset links, and canonical-domain checks.
- [ ] Build the release Android candidate with explicit production backend and verify its manifest, signature certificate, version code/name, and bundle integrity. Do not upload it to Play yet.

### 11.4 Record reproducible evidence

- [ ] The evidence document must record:
  - commit SHA and server tag;
  - CI/staging/production run URLs;
  - image digests and revision names;
  - contract fingerprints;
  - canonical endpoint and descriptor results;
  - redacted DB/quota/media/passkey proof;
  - Android artifact checksums and merged-manifest endpoints;
  - emulator/GMD model and Android API;
  - any remaining launch gates, stated without euphemism.
- [ ] Do not include tokens, account credentials, recovery material, secret values, or passkey private keys.

Commit message:

```text
test(infra): record staging and production recovery proof

Capture the exact server revisions, runtime contracts, durability checks, and
Android endpoint artifacts that demonstrate the launch candidate uses working
staging and production LogDate Cloud environments.
```

## Slice completion definition

This slice is complete only when all of the following are true:

- staging and production canonical endpoints return healthy responses from the same tested SHA;
- internal health proves a real database connection;
- descriptor identity, RP IDs, origins, asset links, and Android certificates match each environment;
- the installed staging artifact is signed by the deterministic staging key, the release artifact by the verified upload key, and each immutable contract exactly matches the complete certificate set published by its live RP domain;
- passkey signup and signin succeed against a no-traffic candidate and canonical domain;
- quota is finite and database-backed;
- media bytes survive revision/instance replacement and match by SHA-256;
- candidate failure leaves traffic untouched; normal-mode post-promotion failure restores the exact independently healthy allocation, while recovery/bootstrap mode never claims an unhealthy or absent allocation as rollback;
- Terraform state is isolated and both environment plans contain no unexpected destroys;
- debug Android defaults to staging and release Android defaults to production;
- a persisted first-party endpoint is classified before networking; a legacy custom endpoint is preserved as migration-pending with remote access closed and no origin-scoped account activated;
- corrupt, failed, or timed-out configuration reads open the local journal/capture experience without a remote request or preference overwrite, and remain retryable;
- the unsafe legacy runtime endpoint switch is gated, and the documented build override can target a conformant development server without exposing multiple accounts;
- the full user-level compatible-server requirement remains an explicit Slice 2 gate and is not falsely claimed complete here;
- no Play upload has occurred.
