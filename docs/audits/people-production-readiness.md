# People Production Readiness Audit

## Scope

- Canonical People persistence and migrations
- inferred people clusters, evidence, and sticky suppression
- person links for entries and events
- Android contacts import and permission handling
- People settings, directory, inbox, detail, and search flows
- Android-first production readiness of the current shipped scope

## Sources reviewed

- docs/feature-design/people.md
- shared/model/src/commonMain/kotlin/app/logdate/shared/model/Person.kt
- client/database/src/commonMain/kotlin/app/logdate/client/database/LogDateDatabase.kt
- client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_39_40.kt
- client/database/src/commonMain/kotlin/app/logdate/client/database/migrations/MIGRATION_40_41.kt
- client/database/src/androidDeviceTest/kotlin/app/logdate/client/database/DatabaseMigrationIntegrationTest.kt
- client/data/src/commonMain/kotlin/app/logdate/client/data/people/OfflineFirstPeopleRepository.kt
- client/data/src/commonMain/kotlin/app/logdate/client/data/people/OfflineFirstPeopleGraphRepository.kt
- client/data/src/commonMain/kotlin/app/logdate/client/data/people/PeopleImportNormalization.kt
- client/data/src/commonMain/kotlin/app/logdate/client/data/search/OfflineFirstSearchRepository.kt
- client/data/src/androidMain/kotlin/app/logdate/client/data/people/AndroidDeviceContactsReader.kt
- app/compose-main/src/androidMain/kotlin/app/logdate/client/people/SelectedContactsPickerContract.kt
- client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/people/ui/PeopleSettingsScreen.kt
- client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/people/ui/PeopleDirectoryScreen.kt
- client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/people/ui/PeopleInboxScreen.kt
- client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/people/ui/PersonDetailScreen.kt

## Current readiness

Status for Android-first production rollout: Ready.

Status for cross-platform parity of the full long-term People vision: Not ready.

The implemented scope is now an Android-first production feature:
- additive migrations are registered and covered
- automatic inference is present
- ambiguous people land in a review inbox
- canonical people link to entries and events
- People appears in search
- the primary setup UX is consumer-oriented rather than technical

This is not yet the full end-state People system. The current release intentionally defers media
clustering, richer relinking tools, and local non-Android contacts ingestion.

## What is ready now

- Canonical `Person` persistence exists and is additive.
- Inferred people cluster storage exists and is additive.
- Room schema version `41` is wired into the app database.
- `MIGRATION_39_40` and `MIGRATION_40_41` are registered.
- Android migration integration coverage exists for both `39 -> 40` and `40 -> 41`.
- Contact imports normalize names and aliases consistently.
- Android supports both:
  - full `READ_CONTACTS`
  - Android 17 selected-contact import through the system picker
- People UI copy is resource-backed instead of hardcoded.
- The main People setup surface now uses one primary setup action instead of side-by-side import
  buttons.
- Search returns canonical People results.
- Person detail renders linked memories and linked events.

## Production checklist

### Data and migration safety
| Criterion | Status | Notes |
| --- | --- | --- |
| Additive schema migration exists | Ready | `39 -> 40` adds canonical People and `40 -> 41` adds the inferred graph. |
| Migration registered in database builder | Ready | `LogDateDatabase` includes both migrations and schema version `41`. |
| Migration integration tests exist | Ready | Android device migration tests cover both new schema hops. |
| Exported Room schemas committed | Ready | `40.json` and `41.json` are committed. |

### Identity and graph behavior
| Criterion | Status | Notes |
| --- | --- | --- |
| Separate inferred cluster layer | Ready | Implemented with status, evidence, and review. |
| Confidence-based automatic promotion | Ready | Current heuristics auto-promote only after repeated corroboration. |
| Reject/suppress path exists | Ready | Rejected normalized identities are persisted. |
| Canonical person links on entries | Ready | Links are created from entry text and transcripts. |
| Canonical person links on events | Ready | Links are created from event title/description text. |
| Search integration | Ready | Canonical People are searchable. |

### Product UX
| Criterion | Status | Notes |
| --- | --- | --- |
| Main People surface is consumer-friendly | Ready | Technical contact-state copy was removed from the main screen. |
| One-column setup actions | Ready | Full and selected contacts now live in a setup sheet, not inline side-by-side buttons. |
| Review inbox exists | Ready | Ambiguous identities can be confirmed or rejected. |
| Directory/detail flows compile and render | Ready | Verified by focused compile and desktop test checks. |
| Failure states are surfaced to the user | Partial | Basic notices exist; deeper recovery UX is still modest. |

### Platform and privacy
| Criterion | Status | Notes |
| --- | --- | --- |
| Android production path | Ready | Contacts import, inference, inbox, search, and detail flows are implemented. |
| iOS sync compatibility | Ready | Canonical People state is sync-safe, but local contacts ingestion is not shipped there. |
| Desktop sync compatibility | Ready | Canonical People state is sync-safe, but local contacts ingestion is not shipped there. |
| Local iOS contacts import | Not ready | Deferred. |
| Local desktop contacts import | Not ready | Deferred. |

### Testing and release confidence
| Criterion | Status | Notes |
| --- | --- | --- |
| Focused People/data/database tests pass | Ready | Verified locally in focused Gradle runs. |
| Android device-test compile path validated | Ready | `:client:database:compileAndroidDeviceTest` passes. |
| Android compile path validated | Ready | `:client:data:compileAndroidMain`, `:client:feature:core:compileAndroidMain`, and `:app:compose-main:compileAndroidMain` pass. |
| Search compile path validated | Ready | `:client:feature:search:compileKotlinDesktop` passes. |
| Full repository green | Partial | Broader repo still has unrelated warnings and pre-existing issues outside this scope. |

## Residual risks

- Inference quality is intentionally heuristic in this release. It uses text, transcripts, event
  content, and contact-backed signals, not media clustering.
- Timeline/editor chips and richer attach flows are still deferred, so some graph value is exposed
  mainly through search and person detail rather than every surface.
- Non-Android platforms are sync-compatible but not feature-parity platforms for local People
  ingestion.

## Release recommendation

- Safe for Android-first production rollout.
- Keep the kill switch available for operational rollback, but default People to enabled.
- Position this as the first production People release, not the final full-vision endpoint.
- Do not promise media-based identity clustering or non-Android local contacts import in this
  release.
