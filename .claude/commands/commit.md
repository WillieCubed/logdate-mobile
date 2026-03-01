# /commit - Atomic Commit Workflow

Create an atomic, dependency-complete commit following the mandatory git workflow.

## Workflow

### Step 1: Analyze Changes

Run these commands to understand the current state:
- `git status` to see all modified/untracked files
- `git diff` to see unstaged changes
- `git diff --cached` to see already-staged changes

If no changes exist, inform the user and stop.

### Step 2: Identify Files and Scope

1. List all files that should be part of this commit
2. Map each file to its module scope using the directory-to-scope table in `docs/reference/standards/commit-scopes.md`
3. Check for dependency completeness:
   - If you modified a function, did you update its callers?
   - If you changed an interface, did you update implementations?
   - If you changed a data class, did you update tests?
   - If you modified a module's public API, did you update docs?

### Step 3: Determine Scope and Type

**Scope inference:**
- All files in one module → use that module's scope (e.g., `timeline`)
- Multiple modules in same category → use the shared scope (e.g., `client`)
- Cross-cutting changes → omit scope or use `infra`
- Validate scope against `allowed-scopes.txt`

**Type inference:**
- New functionality → `feat`
- Bug fix → `fix`
- Code restructuring (no behavior change) → `refactor`
- Documentation only → `docs`
- Code style (no behavior change) → `style`
- Test additions/modifications → `test`
- Maintenance (deps, config) → `chore`
- Performance improvement → `perf`

### Step 4: Draft Commit Message

Follow the format in `docs/reference/standards/commit-messages.md`:

```
type(scope): Brief description (imperative, ≤72 chars)

Body explaining WHY the change was made, not just what changed.
Use full clauses, not imperative fragments.
Lead with impact — what can users/developers now do?

Co-Authored-By: Claude <noreply@anthropic.com>
```

**Rules:**
- Title MUST be under 72 characters
- Use imperative mood ("add", not "added" or "adds")
- Body REQUIRED for meaningful changes (not needed for typo fixes)
- NEVER include phase numbers (Phase 1, Phase 5, etc.)
- Capitalize proper nouns: API, CLI, SDK, JWT, OAuth, WebSocket, PostgreSQL, Kotlin, Android, Compose

### Step 5: Present Plan to User

Before executing, show the user:
1. Files to be committed (with their module scopes)
2. The proposed commit message
3. Ask for approval before proceeding

### Step 6: Execute Atomic Commit

CRITICAL: Always combine all git commands in a single `&&`-chained command:

```bash
git restore --staged . && git add <file1> <file2> ... && git commit -m "$(cat <<'EOF'
type(scope): Brief description

Body explaining the change.

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

**Why this pattern:**
- `git restore --staged .` clears stale staging from other processes
- `git add <files>` stages ONLY the intended files
- All steps succeed or fail together via `&&`
- Prevents race conditions with concurrent agents

### Step 7: Verify

Run `git log -1 --format='%s'` to confirm the commit was created successfully.

## Rules (NON-NEGOTIABLE)

- **NEVER** use `git add -A`, `git add .`, or `git add *`
- **NEVER** use `git commit --no-verify` or `git commit -n`
- **NEVER** use `git commit -a` or `git commit --all`
- **ALWAYS** use the atomic pattern (restore → add specific files → commit)
- **ALWAYS** include `Co-Authored-By` footer for AI-assisted commits
- **ALWAYS** validate scope against `allowed-scopes.txt` before committing
- **ALWAYS** present the plan to the user before executing
