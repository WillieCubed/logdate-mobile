# Server-Client E2E Test Module

This module validates real client-to-server interactions for authentication and sync APIs.
Tests run a real Ktor server instance and execute requests using `LogDateCloudApiClient`.
Repositories are in-memory by default; supplying `DATABASE_URL` requires a live database and runs
the production database initialization path.

## Scope

- Passkey sign-up and sign-in journeys
- Google/passkey implicit linking behavior covered through auth API flows
- Sync upload/download/update/delete journeys
- Error matrix validation for auth and sync endpoints
- End-to-end connectivity smoke checks

## Test Layout

- `smoke/`: server boot and client connectivity
- `journeys/`: complete user journeys across auth and sync
- `errors/`: API error contract assertions
- `harness/`: reusable server/client bootstrap
- `fixtures/`: synthetic credential and assertion helpers

## Run

```bash
./gradlew :integration:server-client-e2e:test --console=plain
```

## Notes

- Tests use generated values and synthetic WebAuthn credentials.
- No external cloud dependencies are required.

## Encrypted location history across Android installations

`AndroidHistoryHarness` provisions a temporary account through the real HTTP auth API and holds
one live server across two sequential managed-device test runs. Its fixture endpoint binds only
loopback port 18879 and contains a disposable recovery phrase and session; do not expose that port
or record its response. The harness exits after 30 minutes or when its control directory contains
`stop`. It never terminates other server processes.

1. Build `:integration:server-client-e2e:writeAndroidHistoryHarnessClasspath`.
2. Launch `app.logdate.integration.e2e.harness.AndroidHistoryHarness` with the classpath in
   `build/android-history-harness.classpath` and a private temporary directory as its sole argument.
   Wait for the `ready` file. For PostgreSQL validation, pass an isolated database's `DATABASE_URL`,
   `DATABASE_USER`, `DATABASE_PASSWORD`, and `AUTO_MIGRATE=true` to that process. Database setup fails
   closed; it cannot silently fall back when a database URL was supplied.
3. Run `:app:android-main:flagshipPhoneApi36DebugAndroidTest` with
   `-Plogdate.androidTestClass=app.logdate.client.e2e.EncryptedHistoryInstallationsE2ETest` and
   `-Pandroid.testInstrumentationRunnerArguments.historyPhase=source`.
4. Run `:app:android-main:largeScreenTabletApi35DebugAndroidTest` with the same test class and
   `-Pandroid.testInstrumentationRunnerArguments.historyPhase=restore`.
5. Require both `source.complete` and `restore.complete` files, then create `stop` and wait for the
   harness to exit. Shut down only the isolated database instance created for this run.

These are two separate Android installations with different Android IDs, enforced by the fixture.
The source queues 205 offline records, encrypts/uploads them, deletes one, and rejects a stale
resurrection. Restore recovers the identity through Android secure storage, interrupts pagination,
reopens its disk Room database, and resumes to 204 exact decrypted records plus one tombstone.
Both use the production Android cipher, sync engine and HTTP transport. Auth provisioning uses the
host test authenticator; this test does not exercise Android's passkey UI. Concurrent correction
merging is covered separately by `EncryptedLocationHistoryDevicesE2ETest` on the JVM.
