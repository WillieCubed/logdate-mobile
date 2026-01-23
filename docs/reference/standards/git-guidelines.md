# Git Workflow Guidelines

**CRITICAL: Follow these guidelines to prevent repository damage and maintain clean history.**

This document covers **git workflow, commit sequencing, and change organization**. For commit message composition, see **[Commit Message Standards](./commit-messages.md)**.

---

## Commit Sequencing Principles

### One Logical Change Per Commit

Each commit should represent **one complete, coherent change**. A reader should understand the commit's purpose from its diff alone.

**What constitutes "one logical change":**

| Good (Single Commit) | Bad (Should Be Split) |
|---------------------|----------------------|
| Add user authentication endpoint + its tests | Add auth endpoint + refactor unrelated database code |
| Fix bug in payment processing | Fix payment bug + add new reporting feature |
| Refactor Button component to use new design tokens | Refactor Button + fix unrelated form validation |
| Update all lint configs for new rule | Update lint + fix lint errors in unrelated files |

**The test**: If you removed this commit, would exactly one feature/fix disappear? If removing it would break multiple unrelated things, it's too big.

### Commit Ordering Within a Feature

When implementing a feature that requires multiple commits, order them by **dependency**:

```
1. Infrastructure/types first     → Types, interfaces, shared utilities
2. Core implementation second     → Main feature logic
3. Integration third              → Wiring into existing code
4. Tests alongside or after       → Can be with implementation or separate
5. Documentation last             → README updates, API docs
```

**Example sequence for "Add workspace sharing":**

```bash
# Commit 1: Types and interfaces
feat(shared-model): add WorkspaceShare types and permissions model

# Commit 2: Core logic
feat(domain): implement workspace sharing use cases

# Commit 3: API integration
feat(server): add workspace sharing endpoints

# Commit 4: UI (if applicable)
feat(feature-core): add workspace sharing UI components

# Commit 5: Documentation
docs: add workspace sharing API documentation
```

> **Note**: These examples show only the title line. For message format (type, scope, body structure), see **[Commit Message Standards](./commit-messages.md)**. For valid scopes, see **[Commit Scopes Reference](./commit-scopes.md)**.

### When to Split vs. Combine Changes

**Split into separate commits when:**

- Changes serve different purposes (feature vs. refactor vs. fix)
- Changes affect different subsystems with no shared context
- You want the ability to revert one change without the other
- Changes have different reviewers or approval requirements

**Combine into one commit when:**

- Changes are meaningless without each other (API + its only consumer)
- Test files that only test the code in this commit
- Type definitions used only by this commit's implementation
- Config changes required by this commit's code

When combining multiple related changes, write a commit body that explains how they fit together. See **[Writing Effective Bodies](./commit-messages.md#writing-effective-bodies)** for guidance.

### Refactoring Commits

**CRITICAL: Never mix refactoring with feature changes.**

Refactoring commits should:
- Change code structure without changing behavior
- Be independently reviewable and revertible
- Come **before** the feature that benefits from them

```bash
# ✅ CORRECT: Refactor first, then feature
refactor(auth): extract token validation to shared utility
feat(auth): add refresh token rotation using shared validator

# ❌ WRONG: Mixed refactor and feature
feat(auth): add refresh tokens and refactor validation
```

**Why this matters:**
- If the feature has bugs, you can revert it without losing the refactor
- Reviewers can verify the refactor doesn't change behavior
- Git bisect can identify exactly which commit introduced issues

> **Commit type guidance**: Use `refactor` for structural changes without behavior change, `feat` for new functionality. See **[Commit Types](./commit-messages.md#commit-types)** for the complete list.

---

## Staging Safety Rules

- **NEVER use `git add -A` or `git add .`** unless 100% certain of what you're staging
- **PREFER specific file paths**: `git add path/to/specific/file.kt`
- **ALWAYS run `git status` before committing** to review staged files
- **Use `git add -p` for interactive staging** of partial file changes

### Safe Staging Examples

```bash
# ✅ Good - specific files
git add client/sync/src/commonMain/kotlin/SyncManager.kt server/src/main/kotlin/SyncRoutes.kt

# ✅ Good - interactive staging (choose hunks)
git add -p client/feature/editor/src/commonMain/kotlin/EditorViewModel.kt

# ✅ Good - stage entire directory you've reviewed
git add client/database/src/

# ❌ Bad - stages everything indiscriminately
git add -A
git add .
git add *
```

---

## Atomic Workflow Pattern

**For multi-agent safety and clean commits, use this atomic pattern:**

```bash
git restore --staged . && git add file1.kt file2.kt && git commit -m "type(scope): title

Body explaining what changed and why.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

> **Message format**: The example above shows the basic structure. For complete formatting rules (72-char limit, body style, footer annotations), see **[Message Anatomy](./commit-messages.md#message-anatomy)**.

**Why this pattern:**

| Benefit | Explanation |
|---------|-------------|
| **Multi-agent safety** | Prevents Agent A's files from being included in Agent B's commit |
| **Atomic operation** | All steps succeed or fail together using `&&` |
| **Clean slate** | `git restore --staged .` clears stale staging from other processes |
| **Explicit staging** | No surprises from lingering staged files |
| **Race condition prevention** | Prevents interleaving from concurrent agents |

### Workflow Checklist

1. **Clear staging**: `git restore --staged .`
2. **Stage exact files**: `git add path/to/file.kt` (or `git add -p`)
3. **Review staging**: `git status`
4. **Commit using the atomic pattern** — write message following **[Commit Message Standards](./commit-messages.md)**
5. **Verify commit**: `git log -1` and `git show --stat`

---

## Branch Workflow

### Feature Branch Strategy

```
main (protected)
  └── feature/TICKET-123-add-workspace-sharing
        ├── commit 1: types
        ├── commit 2: implementation
        ├── commit 3: tests
        └── commit 4: docs
```

**Branch naming conventions:**
- `feature/TICKET-description` — New functionality
- `fix/TICKET-description` — Bug fixes
- `refactor/description` — Code restructuring
- `chore/description` — Maintenance tasks

### Keeping Branches Updated

**Prefer rebase for feature branches** (cleaner history):

```bash
# Update feature branch with latest main
git fetch origin
git rebase origin/main

# If conflicts, resolve then continue
git add resolved-file.kt
git rebase --continue
```

**Use merge only when:**
- Branch has been shared/pushed and others are working on it
- You need to preserve the exact commit history for audit purposes

### Before Creating a PR

1. **Rebase on latest main**: `git rebase origin/main`
2. **Verify all commits are logical**: `git log --oneline origin/main..HEAD`
3. **Squash fixup commits**: `git rebase -i origin/main` — combine "fix typo" or "WIP" commits into their parent
4. **Review commit messages**: Ensure each follows **[Commit Message Standards](./commit-messages.md)** (clear title, body explains why)
5. **Run full validation**: `./gradlew test lint`
6. **Push with lease**: `git push --force-with-lease` (safer than `--force`)

---

## Workflow Rules and Exceptions

- **Trust the tooling** — pre-commit hooks are authoritative
- **Fix, don't bypass** — resolve hook failures instead of skipping
- **No proactive repo-wide validation** — avoid `./gradlew test` for whole repo unless:
  - You changed tooling/configuration that affects the whole repo
- **Local builds are OK** — run `./gradlew :module:test` for the modules you touched if useful
- **Single atomic commit** — don't split commits because hooks failed; fix the failures
- **Re-run the full atomic command** after fixes (unstage → stage → commit)

---

## Absolutely Forbidden Practices

**🚨 THESE ACTIONS ARE FIREABLE OFFENSES:**

| Forbidden | Why |
|-----------|-----|
| `git add -A` / `git add .` / `git add *` | Stages everything indiscriminately |
| `git reset --hard` | Permanently destroys uncommitted work |
| `git push --force` | Destroys commit history on remote |
| `git commit -a` | Commits without proper review |
| `git commit --no-verify` | Bypasses critical quality enforcement |
| Dropping failing files after hooks fail | Hides problems instead of fixing them |
| Commit titles mentioning phases | See **[Forbidden Patterns](./commit-messages.md#forbidden-patterns)** |

---

## Damage Prevention

❌ **NEVER USE `git reset --hard` UNDER ANY CIRCUMSTANCES**

- This command **permanently destroys** all uncommitted work
- There is **no legitimate reason** to use this command
- If the user asks for it, explain the dangers and offer safer alternatives

### Safe Alternatives

| Goal | Safe Command |
|------|--------------|
| Undo last commit, keep changes staged | `git reset --soft HEAD~1` |
| Unstage everything | `git reset HEAD` or `git reset --mixed HEAD` |
| Discard changes in specific file | `git restore --source=HEAD -- path/to/file` |
| Create backup before risky operation | `git branch backup-branch` |
| Undo a pushed commit | `git revert <commit-hash>` (creates new commit) |

**Never force push** without explicit user permission, and **never to main/master**.

---

## Related Documentation

- **[Commit Message Standards](./commit-messages.md)** — Format, structure, body writing style, examples
- **[Commit Scopes Reference](./commit-scopes.md)** — Valid scope names by module
- **[Project Conventions](../../../CLAUDE.md)** — Full project conventions and standards
