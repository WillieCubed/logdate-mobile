# Development Standards

This directory contains authoritative standards for LogDate development.

## Contents

- **[Commit Message Standards](./commit-messages.md)** — How to write clear, maintainable commit messages
- **[Commit Scopes Reference](./commit-scopes.md)** — Lookup table for module scope names
- **[Git Guidelines](./git-guidelines.md)** — Staging safety, atomic workflow, branch management

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
| `core` | Core feature (settings, home) |

**Other scopes:**
| Scope | Module |
|-------|--------|
| `app` | Main compose app |
| `wear` | Wear OS app |
| `server` | Backend server |
| `sync` | Sync client library |
| `database` | Database client library

See [commit-scopes.md](./commit-scopes.md) for the full list.

### Atomic Workflow

```bash
git restore --staged . && git add file1.kt file2.kt && git commit -m "type(scope): title"
```

### Forbidden

- `git add -A` / `git add .`
- `git reset --hard`
- `git push --force`
- `git commit --no-verify`
- Phase numbers in commit messages
