# Neon First-Party Database Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore LogDate Cloud on isolated Neon databases and remove Cloud SQL from the first-party deployment path without risking existing user data.

**Architecture:** First repair the existing staging workflow's temporary runtime-secret path so it migrates the same Neon database as the serving revision. Then make rendered environment contracts pin exact Neon URL/user/password secret versions for both runtime and migrations. Finally remove Cloud SQL dependencies/provisioning and perform read-only inventory before any resource deletion.

**Tech Stack:** Bash, Python standard library, Flyway 12.4.0, PostgreSQL 16 client, GitHub Actions, Terraform, Google Secret Manager, Cloud Run, Neon PostgreSQL

## Global Constraints

- The client remains offline-first; a Cloud outage may never block or erase local capture.
- One canonical LogDate identity is bound to remote authorization; database account IDs never replace it.
- Staging and production use separate Neon databases and separate credentials.
- Database URLs contain no embedded credentials and only support validated `sslmode` and `channelBinding` parameters.
- Flyway and PostgreSQL verification use the same URL, principal, password, TLS, and channel-binding contract.
- Invalid configuration fails before Docker, Flyway, `psql`, candidate deployment, or traffic mutation.
- Secret values and complete database URLs never appear in argv, logs, traces, generated commands, GitHub outputs, or committed files.
- Official contracts pin exact enabled numeric secret versions; `latest` is temporary recovery compatibility only.
- No physical Android device or `connected*AndroidTest` command may be used.
- No Cloud SQL resource may be deleted until read-only evidence proves it contains no unique user data and Neon is authoritative.

---

### Task 1: Repair staging migrations to target the serving Neon database

**Files:**

- Modify: `scripts/run-migrations.sh`
- Modify: `scripts/tests/deploy/test-run-migrations.sh`
- Verify: `.github/workflows/deploy-server-cloud-run.yml`

**Interfaces:**

- Consumes: temporary `--legacy-config`, project/region inputs, and Secret Manager IDs `logdate-db-url`, `logdate-db-user`, and `logdate-db-password`.
- Produces: validated permission-0600 Flyway and PostgreSQL environment files derived from one direct Neon connection contract.

- [ ] **Step 1: Complete the failing URL and pre-mutation test matrix**

  Add table-driven shell scenarios with literal inputs and expected failures for:

  - `jdbc:postgresql://[2001:db8::7]:6543/logdate?sslmode=verify-full&channelBinding=require` success;
  - `postgres://` and `postgresql://` normalization success;
  - userinfo credentials, `user=`, `username=`, and `password=` rejection;
  - missing host, multi-host authority, malformed port, unsafe database path,
    fragments, tabs/newlines, duplicate parameters, unsupported parameters,
    invalid `sslmode`, and invalid `channelBinding` rejection.

  Each rejection must assert that Docker, Flyway, `psql`, proxy download, and
  proxy startup did not occur and that neither the password nor complete URL is
  present in captured output.

- [ ] **Step 2: Run the focused test and observe RED**

  Run: `bash scripts/tests/deploy/test-run-migrations.sh`

  Expected: at least one new scenario fails because the current direct-URL
  parser or fixture does not yet satisfy the contract.

- [ ] **Step 3: Normalize and validate before either database client starts**

  Parse the URL once with Python's `urllib.parse`, validate the authority,
  database, and exact supported query parameters, and write both environment
  files only after all validation succeeds. Normalize Flyway to
  `jdbc:postgresql://...`; map host, port, database, `PGSSLMODE`, and
  `PGCHANNELBINDING` explicitly for `psql`. Reject credentials in both userinfo
  and query parameters so separate secret files are authoritative.

- [ ] **Step 4: Run focused verification**

  Run:

  ```bash
  bash scripts/tests/deploy/test-run-migrations.sh
  bash -n scripts/run-migrations.sh scripts/tests/deploy/test-run-migrations.sh
  shellcheck -x scripts/run-migrations.sh scripts/tests/deploy/test-run-migrations.sh
  bash scripts/tests/deploy/test-github-actions-metadata.sh
  git diff --check -- scripts/run-migrations.sh scripts/tests/deploy/test-run-migrations.sh
  ```

  Expected: every command exits 0, URL/credential sentinels remain absent from
  output, and the direct Neon path never invokes the Cloud SQL proxy.

- [ ] **Step 5: Obtain independent review and commit**

  Require spec compliance and code-quality approval. Commit only the two script
  files and this plan/spec if not already committed, using:

  ```text
  fix(infra): keep staging migrations on Neon

  Validate the serving runtime database contract before migration and use the
  same Neon target and credentials for Flyway and PostgreSQL verification.
  ```

- [ ] **Step 6: Push and observe staging without manual traffic changes**

  Push `main`, require CI success, and inspect the staging workflow. Evidence
  must show migration via the direct runtime URL path, candidate smoke, and
  promotion; if migration or smoke fails, no candidate receives traffic.

---

### Task 2: Make exact Neon secret versions authoritative on every revision

**Files:**

- Modify: `infra/terraform/main.tf`
- Modify: `infra/terraform/variables.tf`
- Modify: `infra/terraform/staging.tfvars.example`
- Modify: `infra/terraform/production.tfvars.example`
- Modify: `scripts/render-cloud-run-contract.sh`
- Modify: `scripts/tests/deploy/test-render-cloud-run-contract.sh`
- Modify: `scripts/run-migrations.sh`
- Modify: `scripts/tests/deploy/test-run-migrations.sh`
- Modify: `.github/workflows/deploy-server-cloud-run.yml`
- Modify: `docs/runbook/staging-production-configuration.md`

**Interfaces:**

- Consumes: environment tfvars with exact numeric versions for
  `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` Secret Manager IDs.
- Produces: one rendered immutable contract consumed by Terraform runtime
  configuration and `run-migrations.sh --contract-file`.

- [ ] **Step 1: Write renderer and migration contract tests**

  Prove staging and production render different project/database secret
  bindings, reject `latest`, reject missing/disabled version metadata, reject
  Cloud SQL instance fields, and mount the same three exact versions into the
  Cloud Run candidate and migration process. Prove official mode rejects
  project, database, or credential overrides.

- [ ] **Step 2: Run tests and observe RED**

  Run:

  ```bash
  bash scripts/tests/deploy/test-render-cloud-run-contract.sh
  bash scripts/tests/deploy/test-run-migrations.sh
  ```

  Expected: failures show that the current contract still models Cloud SQL and
  the workflow still uses temporary recovery mode.

- [ ] **Step 3: Implement the immutable Neon contract**

  Replace instance/database connector fields with the three pinned standard
  PostgreSQL secret bindings. Render one private contract artifact, use it for
  migrations, and apply the same contract when deploying the candidate instead
  of setting only `RELEASE_VERSION`. Remove `--legacy-config` from the official
  workflow once the exact-version contract is active.

- [ ] **Step 4: Verify hermetic infrastructure behavior**

  Run:

  ```bash
  terraform -chdir=infra/terraform fmt -recursive
  terraform -chdir=infra/terraform fmt -check -recursive
  bash scripts/tests/deploy/test-validate-terraform-isolated.sh
  bash scripts/validate-terraform-isolated.sh
  bash scripts/tests/deploy/test-render-cloud-run-contract.sh
  bash scripts/tests/deploy/test-run-migrations.sh
  bash scripts/tests/deploy/test-github-actions-metadata.sh
  git diff --check
  ```

- [ ] **Step 5: Reconcile exact staging secret versions and deploy**

  Inventory only secret IDs, enabled numeric versions, and IAM principals.
  Commit the selected numeric versions without reading values into output.
  Deploy staging and prove its health/descriptor identifies
  `cloud-staging.logdate.app`, the staging RP ID, first-party deployment, the
  exact release SHA, and a connected database.

- [ ] **Step 6: Commit and push**

  Use:

  ```text
  fix(infra): make Neon the first-party database contract

  Pin isolated staging and production PostgreSQL secrets and apply the same
  immutable runtime contract to migrations and every Cloud Run candidate.
  ```

---

### Task 3: Remove Cloud SQL from first-party code and provisioning

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `server/build.gradle.kts`
- Modify: `server/src/main/kotlin/app/logdate/server/database/DatabaseConfig.kt`
- Modify: `server/src/test/kotlin/app/logdate/server/database/DatabaseConfigTest.kt`
- Modify: `infra/terraform/main.tf`
- Modify: `infra/terraform/variables.tf`
- Delete: `infra/terraform/tests/cloud_sql_migration_iam.tftest.hcl`
- Modify: `scripts/bootstrap-gcp-fresh.sh`
- Modify: `scripts/tests/deploy/test-bootstrap-gcp-fresh.sh`
- Modify: `scripts/run-migrations.sh`
- Modify: `scripts/tests/deploy/test-run-migrations.sh`
- Modify: `server/docs/environment-variables.md`
- Modify: `infra/terraform/README.md`

**Interfaces:**

- Consumes: the immutable standard PostgreSQL contract from Task 2.
- Produces: one provider-neutral server/runtime path with no Cloud SQL socket
  factory, Auth Proxy, SQL Admin API, instance resource, or Cloud SQL IAM role.

- [ ] **Step 1: Write removal-boundary behavior tests**

  Update database tests so production accepts only a valid standard URL plus
  credentials and fails closed when it is absent. Update Terraform/bootstrap
  tests to prove a fresh first-party project creates Secret Manager containers
  and Cloud Run/GCS infrastructure but no SQL API, SQL instance, SQL Admin call,
  proxy download, connector environment, or `roles/cloudsql.client` grant.

- [ ] **Step 2: Run tests and observe RED**

  Run:

  ```bash
  ./gradlew :server:test --tests '*DatabaseConfigTest'
  bash scripts/tests/deploy/test-bootstrap-gcp-fresh.sh
  bash scripts/tests/deploy/test-run-migrations.sh
  ```

- [ ] **Step 3: Remove provider-specific implementation**

  Remove the socket-factory dependency and configuration, Terraform SQL
  resources/variables/IAM, bootstrap SQL Admin behavior, proxy code, and stale
  documentation. Preserve the standard TLS PostgreSQL path and fail-closed
  production validation.

- [ ] **Step 4: Verify server and infrastructure gates**

  Run:

  ```bash
  ./gradlew :server:test --tests '*DatabaseConfigTest' --tests '*ProductionConfigValidatorTest'
  bash scripts/tests/deploy/test-bootstrap-gcp-fresh.sh
  bash scripts/tests/deploy/test-run-migrations.sh
  bash scripts/tests/deploy/test-render-cloud-run-contract.sh
  bash scripts/tests/deploy/test-validate-terraform-isolated.sh
  ./gradlew ktlintCheck
  git diff --check
  ```

- [ ] **Step 5: Independently review, commit, push, and observe staging**

  Use:

  ```text
  refactor(infra): remove first-party Cloud SQL support

  Keep LogDate Cloud on its provider-neutral Neon PostgreSQL contract and remove
  the unused connector, proxy, provisioning, and IAM surface.
  ```

---

### Task 4: Prove staging, restore production, and retire unused resources

**Files:**

- Modify: `scripts/smoke-test-revision.sh`
- Modify: `scripts/tests/deploy/test-smoke-test-revision.sh`
- Modify: `scripts/passkey-verify/sim.py`
- Modify: `scripts/tests/deploy/test-passkey-verify.py`
- Modify: `docs/runbook/staging-production-configuration.md`
- Create: `docs/audits/neon-staging-production-acceptance.md`

**Interfaces:**

- Consumes: immutable Neon contracts, reviewed passkey/durability smoke tooling,
  GitHub environment protection, and no-traffic Cloud Run candidates.
- Produces: redacted staging/production acceptance evidence and a Cloud SQL
  keep/delete decision based on read-only inventory.

- [ ] **Step 1: Finish smoke-gate tests before live use**

  Require real `/auth/me` account binding, ES256 registration options, expected
  allow-credential binding, redirect-origin safety, idempotent phased cleanup,
  token revocation proof, account deletion last, and crash recovery across two
  smoke invocations.

- [ ] **Step 2: Run local smoke verification**

  Run:

  ```bash
  python3 scripts/tests/deploy/test-passkey-verify.py
  bash scripts/tests/deploy/test-smoke-test-revision.sh
  shellcheck -x scripts/smoke-test-revision.sh
  ruff check scripts/passkey-verify/sim.py scripts/tests/deploy/test-passkey-verify.py
  git diff --check
  ```

- [ ] **Step 3: Prove staging durability**

  Deploy a no-traffic staging candidate. Require database-backed health and the
  correct release/origin/RP contract, create a unique passkey user, write media
  and backup bytes, read identical bytes, sign out, reauthenticate, verify token
  revocation, delete media/backup/account, rerun cleanup, then promote only on
  success. Record request IDs, hashes, release SHA, and HTTP outcomes without
  recording credentials or user content.

- [ ] **Step 4: Restore and prove production**

  Render and review the production contract, deploy without traffic, run the
  same smoke journey against the candidate, obtain the configured environment
  approval, promote, and verify public and internal health. Confirm existing
  accounts and encrypted objects remain accessible before declaring recovery.

- [ ] **Step 5: Perform database restore drill**

  Restore a Neon snapshot/branch into an isolated verification database, run
  Flyway validation without mutation, and verify redacted row counts plus hashes
  for representative account, sync, media metadata, and backup records. Destroy
  only the temporary verification branch after evidence is captured.

- [ ] **Step 6: Inventory and decide Cloud SQL retirement**

  Record instance state, database names, row counts, backups, consumers, and
  recent connections. If every result proves no unique data and no runtime
  reference, delete the instance through the reviewed infrastructure path and
  document recovery implications. Otherwise keep it read-only and migrate or
  reconcile the unique data before any deletion.

- [ ] **Step 7: Commit the redacted acceptance record**

  Use:

  ```text
  docs(infra): record Neon recovery acceptance

  Capture staging and production database, durability, restore, and retirement
  evidence without exposing credentials or user content.
  ```
