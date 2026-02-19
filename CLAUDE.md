# LogDate Development Guide

## Build Commands
- Build: `./gradlew :app:compose-main:assembleDebug`
- Run tests: `./gradlew test`
- Run single test: `./gradlew :module:test --tests "package.TestClass.testMethod"`
- Lint: `./gradlew lint`
- Documentation: `./gradlew dokkaHtmlMultiModule`
- Run Android app: `./gradlew :app:compose-main:installDebug`
- Run Desktop app: `./gradlew :app:compose-main:run`

## Test Coverage Commands  
- Generate coverage report: `./gradlew koverHtmlReport`
- Verify coverage meets threshold: `./gradlew koverVerify`
- Run tests with coverage: `./gradlew test koverHtmlReport`
- XML coverage report: `./gradlew koverXmlReport`

## Code Style Guidelines
- **Imports**: Order by kotlin.* > androidx.*/kotlinx.* > app.logdate.*. No wildcards. Avoid fully qualified package names unless there's a name conflict. Use the simple class name whenever possible.
- **Class Members**: Constants/companions > properties > init blocks > methods
- **Naming**: Classes/Interfaces: PascalCase, Functions/Properties: camelCase, Constants: UPPER_SNAKE_CASE
- **Error Handling**: Use try-catch with Napier logging, prefer nullable returns over exceptions
- **Documentation**: Use KDoc style (/** */) for public APIs with @param and @return tags
- **Architecture**: Follow clean architecture with UI, domain, and data layers
- **Data representation**: Use sealed result classes for fetching data/performing in the domain layer, prefer data classes for data models
- **State Management**: Use sealed classes/interfaces for UI state
- **Immutability**: Prefer data classes and immutable properties
- **Asynchronous**: Use coroutines and Flow for reactive programming
- **DI**: Use constructor-based dependency injection with Koin
- **Logging**: Use Napier for all logging - our multiplatform logging solution

## Logging Guidelines
- Use Napier for all logging across all platforms (Android, iOS, Desktop)
- Log levels: `Napier.v()` (verbose), `Napier.d()` (debug), `Napier.i()` (info), `Napier.w()` (warning), `Napier.e()` (error)
- Include meaningful context in log messages
- Use structured logging with exception details when available
- Example: `Napier.e("Failed to save data", exception)`

## Development Notes
- You can use grep and gradlew without asking for permission.
- Avoid redundant comments when making code changes. Don't add comments that simply restate what the code does.
- Keep modifications minimal and focused on addressing the issue at hand.

## Git Commit Standards

Follow the commit message standards in `docs/reference/standards/commit-messages.md`.

### Quick Reference

**Format**: `type(scope): Brief description`

**Types**: feat, fix, refactor, docs, style, test, chore, perf

**Scopes**: See `docs/reference/standards/commit-scopes.md` for the authoritative list.

**Common scopes** (feature scopes are primary):
- `editor`, `timeline`, `onboarding`, `rewind`, `search`, `core` - Feature modules (preferred)
- `app` - Main compose app
- `wear` - Wear OS app
- `server` - Backend server
- `database`, `sync`, `auth`, `domain` - Client libraries

**Rules**:
- Title under 72 characters, imperative mood
- Body required for meaningful changes (explains WHY, not just WHAT)
- NEVER include phase numbers in commit messages
- Use full clauses in body, not imperative fragments

### Atomic Workflow (Required)

**CRITICAL**: Always combine `git restore --staged .`, `git add`, and `git commit` in a single command using `&&` to avoid race conditions with other agents.

```bash
git restore --staged . && git add file1.kt file2.kt && git commit -m "$(cat <<'EOF'
type(scope): Brief description

Body explaining what changed and why. Use full clauses, not fragments.
Lead with impact—what can users now do? Explain the problem solved,
not just the implementation details.

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

**Why this pattern:**
- Files may be modified by other processes between separate commands
- `git restore --staged .` clears stale staging from other processes
- All steps succeed or fail together using `&&`
- Prevents race conditions with concurrent agents

**Required steps in order:**
1. `git restore --staged .` - Unstage all files to ensure clean slate
2. `git add <files>` - Stage only the intended files (NEVER use `git add .` or `git add -A`)
3. `git commit` - Commit with properly formatted message
- **Never** run these commands separately in different tool calls

### Forbidden Practices

🚨 **These are fireable offenses:**
- `git add -A` / `git add .` / `git add *` — Stages everything indiscriminately
- `git reset --hard` — Permanently destroys uncommitted work
- `git push --force` — Destroys commit history on remote
- `git commit --no-verify` — Bypasses critical quality enforcement
- Phase numbers in commit messages (Phase 1, Phase 5, etc.)

### Safe Alternatives

| Goal | Safe Command |
|------|--------------|
| Undo last commit, keep changes | `git reset --soft HEAD~1` |
| Unstage everything | `git reset HEAD` |
| Discard changes in specific file | `git restore --source=HEAD -- path/to/file` |

See `docs/reference/standards/git-guidelines.md` for full documentation

## KMP Guidelines
- For UUIDs, use kotlin.uuid.Uuid with Uuid.random() for generating new UUIDs (NOT Java's UUID class)
- For cross-platform serialization, use kotlinx.serialization
- For dates and times, use kotlinx.datetime.Instant
