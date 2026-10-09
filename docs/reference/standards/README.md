# Development Standards

This directory contains authoritative standards for LogDate development.

## Contents

- **[Commit Message Standards](./commit-messages.md)** — How to write clear, maintainable commit messages
- **[Commit Scopes Reference](./commit-scopes.md)** — Lookup table for module scope names
- **[Git Guidelines](./git-guidelines.md)** — Staging safety, atomic workflow, branch management
- **[Entrypoint Structure](./entrypoint-structure.md)** — Keeping module entrypoints small, and the detekt size limits that enforce it
- **[Media Quality Checklist](./media-quality-checklist.md)** — Standing rules for every image and video pipeline

## Quick Reference

### Commit Format

```
type(scope): Brief description (under 72 chars)

Body explaining WHY, not just WHAT. Use full clauses.

Co-Authored-By: Claude <noreply@anthropic.com>
```

### Types

| Type | Purpose |
|------|---------|
| `feat` | New functionality |
| `fix` | Bug fix |
| `refactor` | Code restructure without behavior change |
| `docs` | Documentation only |
| `test` | Test changes |
| `chore` | Build/tooling/maintenance |
| `perf` | Performance improvements |

### Common Scopes

**Feature scopes (primary):**
| Scope | Module |
|-------|--------|
| `editor` | Editor feature |
| `timeline` | Timeline feature |
| `onboarding` | Onboarding feature |
| `rewind` | Rewind feature |
| `search` | Search feature |
| `settings` | App settings screens |
| `android-widget` | Home screen widgets and launcher shortcuts |

**Other scopes:**
| Scope | Module |
|-------|--------|
| `wear` | Wear OS app |
| `server` | Backend server |
| `sync` | Sync client library |
| `database` | Database client library

See [commit-scopes.md](./commit-scopes.md) for the full list.

### Atomic Workflow

```bash
git restore --staged . && git add file1.kt file2.kt && git commit -F /path/to/message.txt
```

Write the message to a file first, in its own step, and commit with `-F`. Scopes must come from
[`allowed-scopes.txt`](../../../allowed-scopes.txt); pick the feature the user sees, not the module.

### Forbidden

- `git add -A` / `git add .`
- `git reset --hard`
- `git push --force`
- `git commit --no-verify`
- Phase numbers in commit messages
