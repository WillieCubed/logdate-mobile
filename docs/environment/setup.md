# Environment Setup

Centralized guide for configuring local and CI environments for LogDate.

## Required secrets and configuration

- `OPENAI_API_KEY`: Used by the intelligence module (Koin property). Supply via environment variable or Gradle/JVM property.
- `DATABASE_URL`: JDBC URL for the server (e.g., `jdbc:postgresql://localhost:15432/logdate`), only needed when running the server locally without Docker defaults.
- `REDIS_URL` (if overriding): Redis connection string; defaults come from Docker Compose.
- `GOOGLE_MAPS_API_KEY`, `metaAppId` (if applicable to platform builds): Place in `local.properties` for mobile builds. Keep out of VCS.

## Supplying secrets locally

### Environment variables
```bash
export OPENAI_API_KEY="your-openai-key"
export DATABASE_URL="jdbc:postgresql://localhost:15432/logdate" # if not using defaults
```
You can also pass JVM props: `./gradlew -DOPENAI_API_KEY="your-openai-key" ...`

### local.properties (never commit)
Create `local.properties` in the repo root:
```properties
OPENAI_API_KEY=your-openai-key
DATABASE_URL=jdbc:postgresql://localhost:15432/logdate
GOOGLE_MAPS_API_KEY=your-google-maps-key
metaAppId=your-meta-app-id
```

### Docker Compose
The supplied `docker-compose.yml` and scripts (`./scripts/dev-start.sh`) start Postgres/Redis with defaults. Override with a `.env` file if needed:
```env
POSTGRES_PASSWORD=logdate
POSTGRES_USER=logdate
POSTGRES_DB=logdate
REDIS_PORT=16379
```

## CI configuration

- Store secrets in the CI secret manager (never in the repo).
- Set `OPENAI_API_KEY` and any server DB/Redis overrides as environment variables in the CI job.
- Mobile builds: inject `OPENAI_API_KEY` (and maps/meta keys if required) via CI-provided env vars or Gradle properties.

## Per-platform notes

- **Server**: Picks up `DATABASE_URL`, optional `REDIS_URL`, and uses the same `OPENAI_API_KEY` if intelligence features are exercised server-side.
- **Client/Intelligence**: Requires `OPENAI_API_KEY` (Koin property). Fails fast if missing.
- **Android/iOS/Desktop**: API keys can come from `local.properties` during build; keep that file untracked.

## Safety

- Do not commit `.env`, `local.properties`, or any secrets.
- Rotate keys if a leak is suspected; history has been rewritten to remove the prior hardcoded key.
