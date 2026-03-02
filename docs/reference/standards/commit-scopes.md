# Commit Scope Reference

> **Authoritative lookup table for all module commit scopes**

Zero ambiguity: Look up the exact scope for the module you're modifying. Just follow the tables below.

## Feature Scopes (Primary)

These are the main scopes for user-facing features. **Prefer these scopes** for most commits.

| Directory | Scope |
|-----------|-------|
| `client/feature/core/` | `core` |
| `client/feature/editor/` | `editor` |
| `client/feature/journal/` | `journal` |
| `client/feature/location-timeline/` | `location-timeline` |
| `client/feature/onboarding/` | `onboarding` |
| `client/feature/passkeys/` | `passkeys` |
| `client/feature/rewind/` | `rewind` |
| `client/feature/search/` | `search` |
| `client/feature/timeline/` | `timeline` |

## Apps Scopes

| Directory | Scope |
|-----------|-------|
| `app/wear/` | `wear` |
| `server/` | `server` |

## Client Library Scopes

| Directory | Scope |
|-----------|-------|
| `client/auth/` | `auth` |
| `client/billing/` | `billing` |
| `client/data/` | `data` |
| `client/database/` | `database` |
| `client/datastore/` | `datastore` |
| `client/device/` | `device` |
| `client/health-connect/` | `health-connect` |
| `client/intelligence/` | `intelligence` |
| `client/location/` | `location` |
| `client/media/` | `media` |
| `client/networking/` | `networking` |
| `client/permissions/` | `permissions` |
| `client/repository/` | `repository` |
| `client/sensor/` | `sensor` |
| `client/sharing/` | `sharing` |
| `client/sync/` | `sync` |
| `client/theme/` | `theme` |
| `client/ui/` | `ui` |
| `client/util/` | `util` |

## Shared Module Scopes

| Directory | Scope |
|-----------|-------|
| `shared/model/` | `shared-model` |
| `shared/config/` | `shared-config` |
| `shared/activitypub/` | `shared-activitypub` |

## Cross-Cutting Changes

| Scenario | Scope |
|----------|-------|
| Multiple modules in different categories | Omit scope or use `infra` |
| Changes to build/tooling | `build` or `infra` |
| Changes affecting entire platform | `platform` or omit |
| Root-level config/docs | omit scope (e.g., `docs: ...`) |
| iOS-specific changes across modules | `ios` |
| Android-specific changes across modules | `android` |
| Desktop-specific changes across modules | `desktop` |

## Examples

```bash
# Feature modules (preferred for user-facing changes)
git commit -m "feat(editor): add voice recording playback controls"
git commit -m "fix(timeline): correct date grouping for notes"
git commit -m "refactor(onboarding): simplify permission request flow"

# Client libraries (for infrastructure/data layer changes)
git commit -m "feat(sync): implement conflict resolution for notes"
git commit -m "fix(database): handle migration edge case"
git commit -m "refactor(data): extract validation use cases"

# Cross-cutting
git commit -m "chore(infra): update gradle wrapper version"
git commit -m "docs: update architecture guide"

# Platform-specific
git commit -m "fix(android): resolve permission handling on API 34"
git commit -m "feat(ios): add haptic feedback support"
```

## Related

- [Commit Message Standards](./commit-messages.md)
- [Git Guidelines](./git-guidelines.md)
