# OpenAPI

Machine-readable API contracts are always available from the running server.

## Runtime Endpoints
- `GET /openapi.json` (OpenAPI 3.1 JSON)
- `GET /openapi.yaml` (OpenAPI 3.1 YAML)
- `GET /swagger` (Swagger UI)

## Generate Artifacts
Generate OpenAPI files to `server/build/openapi`:

```bash
./gradlew :server:generateOpenApi
```

Generated files:
- `server/build/openapi/openapi.json`
- `server/build/openapi/openapi.yaml`

## Validate Contract Coverage
Validate generated artifacts and required route coverage:

```bash
./gradlew :server:validateOpenApi
```

`validateOpenApi` is wired into `:server:check`.

## Notes
- OpenAPI data is generated from live Ktor routing metadata using official Ktor OpenAPI libraries.
- Human-readable launch docs remain in:
  - `server/docs/auth-v1-api.md`
  - `server/docs/sync-v1-api.md`
