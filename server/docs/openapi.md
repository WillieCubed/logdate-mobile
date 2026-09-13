# API reference

The API reference is generated from the running server and published at `/docs`. This page is
for people who change it: where the words live, how to document a route, and the tests that keep
the reference honest.

## What the server publishes

| Path | What it is |
|---|---|
| `GET /docs` | The interactive reference (Scalar, self-hosted; no third-party assets, no telemetry). |
| `GET /openapi.json` | The OpenAPI 3.1 document, generated from the route declarations. |
| `GET /openapi.yaml` | The same document as YAML. |
| `GET /` | A landing page that links to the three above. |

Both spec routes are always on. Metrics and maintenance routes are marked `hidden = true` at the
route and never appear.

## Where the words live

Nothing reader-facing is written inline in a route handler. The pieces:

| Piece | File |
|---|---|
| The overview that opens the reference (guide, concepts, errors, limits, sync model) | `server/src/main/resources/openapi/overview.md` |
| Tag names, tag descriptions and sidebar groups | `server/src/main/kotlin/app/logdate/server/openapi/ApiTags.kt` |
| Per-endpoint documentation (summary, description, parameters, examples, every response) | `server/src/main/kotlin/app/logdate/server/routes/docs/*Docs.kt` |
| Schema and field descriptions | `server/src/main/kotlin/app/logdate/server/openapi/schemadocs/*SchemaDocs.kt` |
| Plugin configuration: info, servers, security schemes, schema generator, post-build passes | `server/src/main/kotlin/app/logdate/server/openapi/OpenApiSpec.kt` |
| The helper DSL the docs objects use | `server/src/main/kotlin/app/logdate/server/routes/OpenApiDocumentation.kt` |
| Scalar page settings and favicon | `server/src/main/kotlin/app/logdate/server/ScalarApiReferenceRoutes.kt` |

Numbers in prose (rate limits, page sizes) are never typed by hand. Write `{{auth.signup}}` or
`{{sync.limit.max}}` and `ApiLimits` fills it in from the constant the handler enforces, so a limit
cannot change in code without the docs following.

## How to document a route

1. Write the operation in the `*Docs.kt` object for its family, as a `val` named after its
   `operationId`:

   ```kotlin
   val deleteDraft: RouteConfig.() -> Unit = {
       bearerOperation("deleteDraft", ApiTags.DRAFTS, "Delete a draft", """
           Soft-deletes a draft. Other devices are not told about the deletion by **Page draft
           changes**, so delete it on each device once the finished entry is saved. Repeating the
           call answers `204` again.
       """)
       request { pathParameter<String>("draftId") { description = "The draft's ID."; example("Example") { value = SyncExamples.DRAFT_ID } } }
       response {
           noContent("The draft is deleted (or already was).")
           bearerUnauthorized(ErrorEnvelope.SYNC)
           syncError(HttpStatusCode.InternalServerError, SyncExamples.serverMisconfigured)
       }
   }
   ```

   Use `publicOperation`, `bearerOperation`, `dpopOperation` or `bearerOrDpopOperation` for the
   header; `ok`, `created`, `noContent` for success; `apiError`, `syncError`, `pdsError`,
   `oauthError`, `messageError` for failures in the family's envelope; `bearerUnauthorized`,
   `rateLimited` and `quotaExceeded` for the cross-cutting ones. Every error case says what went
   wrong in the reader's terms and what to do next.

2. Reference it from the route: `delete("/{draftId}", SyncCollectionDocs.deleteDraft) { … }`.

3. Describe any new schema and each of its fields in the matching `*SchemaDocs.kt`, keyed by the
   readable schema name (`ContentChange`, `VersionConstraint.Known`,
   `SimpleSuccessResponse_SyncStatusSnapshot`). Enum values go in `enumValues`.

4. Reuse the shared example values in `DocExamples` and `SyncExamples` so the reference reads as one
   story. Examples are typed Kotlin instances, so a renamed field is a compile error, not a stale
   doc.

Write for a junior or enthusiast developer: explain every concept where it is first met, say
which call comes next, and keep sentences short. The overview's Concepts section is the place for
anything that needs more than a sentence.

## The gates

`./gradlew :server:test --tests 'app.logdate.server.openapi.*'` runs them in a few seconds.

| Test | Guards |
|---|---|
| `OpenApiContractTest` | Every operation has a real summary and description, one declared and grouped tag, a camelCase unique operation ID, path parameters that match the template, a success response with a body and example, a `401` in its family's envelope when protected, `429`/`402` where the handler rate-limits or checks quota, `501` on XRPC methods that can be switched off, and no inline documentation left in route files. |
| `SchemaDocumentationTest` | Every published schema and field is described, and the registry has no entries for schemas that no longer exist. |
| `OpenApiSchemaTest` | Schemas match the wire: sealed-class discriminators, value classes as strings, snake_case renames. |
| `OpenApiOverviewTest` | The overview has every section, every placeholder resolved, and quotes the limits the server enforces. |
| `ApplicationTest` | `/docs`, `/openapi.json`, `/openapi.yaml` and the favicon are served; Scalar telemetry stays off. |

`./gradlew :server:generateOpenApi` exports the spec from a live in-process server to
`server/build/openapi/openapi.json` and `openapi.yaml`, for client generators or a diff.
`./gradlew :server:validateOpenApi` (part of `:server:check`) runs it and checks the
launch-critical paths and tag groups are present.

## Previewing locally

```bash
LOGDATE_ALLOW_INMEMORY_FALLBACK=true ./run server
```

Then open <http://localhost:8765/docs>. The Try-it panel sends requests to the same server; sign
in with **Sign up with Google** or use an existing token.

## Behaviour the docs describe as-is

These are documented truthfully rather than papered over. Fixing them is code work, not docs work:

- `MEDIA_STORAGE_UNAVAILABLE` is `500` on media download but `503` on media delete.
- The drafts feed defaults `limit` to 100 without clamping, treats a non-numeric `since` as `0`,
  and `PUT /drafts/{id}` never answers `201` or sends `Location`.
- `GOOGLE_AUTH_NOT_CONFIGURED` is `503` while `EMAIL_VERIFICATION_UNAVAILABLE` is `501`.
- Quota and transcription answer errors as `{"error": "..."}` rather than the standard envelope.
- Auth sign-up and sign-in `429`s carry no `Retry-After` header.
- `syncVersion` on upload requests is never read; `isDeleted` on change items is always `false`.
- `POST /auth/me/email/verify/complete` answers `400` in two different shapes.
