# Server-Client E2E Test Module

This module validates real client-to-server interactions for authentication and sync APIs.
Tests run a real Ktor server instance with in-memory repositories and execute requests using
`LogDateCloudApiClient`.

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
