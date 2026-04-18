# LogDate Agent Guidelines

> Principles, workflow, and non-negotiables for any AI agent working in this repository.

## Development Workflow

All changes land directly on `main`. There are no long-lived feature branches. This demands extreme
commit discipline — every commit must leave `main` in a shippable state. Incomplete features are
gated behind feature flags, not hidden in branches.

Every change follows this process. No exceptions.

### 1. Understand

Before writing any code, determine:

- **What type of change is this?** Feature (`feat`), bug fix (`fix`), refactor (`refactor`), or
  maintenance (`chore`)? This determines the commit type.
- **What user-facing behavior does this serve?** LogDate is a multiplatform product (app + server) —
  changes are always in service of the user. A feature may span `client/*`, `shared/*`, and
  `server/` modules, and that's expected. Think in terms of the feature, not the module boundary.
- **What already exists?** Read the affected code. Check for existing utilities and patterns before
  creating new ones.

### 2. Plan

Design the approach before writing code. For non-trivial changes:

- Brainstorm interactively with the developer to clarify requirements, trade-offs, and scope.
- Trace the feature across layers: what UI changes, what domain logic, what data/repository changes?
- If API contracts or sync behavior are involved, trace changes through `:server` and validate
  impact in `:integration:server-client-e2e`.
- Keep the scope tight — one logical feature or fix, even if it touches several modules.

### 3. Write Tests First

Tests define the contract for the change. Write them before the implementation.

- Write tests that describe the expected behavior.
- If the implementation doesn't exist yet, create stubs or interfaces so the tests compile. Tests
  should fail because the behavior isn't implemented, not because the code doesn't build.
- For features spanning multiple modules, write tests at the appropriate layer: unit tests for
  domain logic, integration tests for repository/data interactions.

```bash
# Run tests for affected modules
./gradlew :client:feature:timeline:test :client:repository:test
```

### 4. Implement

Build the implementation to satisfy the tests.

- Follow existing patterns. Consistency matters more than novelty.
- Keep it minimal — implement what the tests require, nothing more.
- Work across module boundaries as needed. A single feature touching `client/feature/`,
  `client/domain/`, `client/repository/`, `shared/*`, and `server/` can be normal.
- **Gate incomplete features behind feature flags.** Every commit lands on `main`, so
  partially-built features must be invisible to users. Structure code so flagged paths are easy to
  find and remove once the feature ships.

### 5. Iterate Until Green

Run tests and quality checks. Fix failures. Repeat.

```bash
# Run tests for all affected modules
./gradlew :client:feature:timeline:test :client:domain:test

# Kotlin lint
./gradlew ktlintCheck

# Full build
./gradlew :app:android-main:assembleDebug
```

All tests must pass and quality gates must succeed before committing.

### 6. Review

Get feedback from the developer before committing. Walk through the changes, confirm the approach is
correct, and adjust if needed.

### 7. Commit

Each commit should read like a changelog entry — a single, distinct change that a user of the app
would recognize. Scope to the primary feature module, even if the commit touches supporting modules.

Because every commit lands on `main`, every commit must leave the app in a shippable state. If the
feature isn't ready for users, it must be behind a feature flag. A commit that breaks `main` is
unacceptable regardless of how correct the code is.

For staging safety and commit mechanics, see [
`docs/reference/standards/git-guidelines.md`](./docs/reference/standards/git-guidelines.md).

## Non-Negotiable Principles

### You Touch It, You Fix It

If you modify ANY file, leave it better than you found it. Fix all lint violations, compile errors,
and broken tests in that file. No exceptions — "that was already there" is not acceptable.

### Honesty Over Completion

Never claim success with unresolved errors. Never lie by omission. Always check exit codes —
non-zero is a failure. If you cannot complete a task, say so clearly rather than producing broken
output.

### Commit Messages Describe User Impact

Commits are changelog entries. Think in terms of features, not layers. Scope to the feature the user
interacts with, not the internal module that happens to contain the code. Generic scopes like "
client" or "domain" are not valid — pick the specific feature scope.

Use [`docs/reference/standards/commit-messages.md`](./docs/reference/standards/commit-messages.md)
as the source of truth for commit message structure and detail. Follow that guide when writing
commit messages.

- `feat` and `fix` are reserved for changes that affect end-user behavior. If the user wouldn't
  notice the difference, use `refactor`, `chore`, or `docs` instead.
- Write `feat` commits as if consumed by the general public. The test: "Can I do something different
  because of this?"
- Focus on behavior, not implementation. Capitalize proper nouns (API, Kotlin, Android, Compose,
  Gradle, etc.).

### Minimal, Focused Changes

Keep modifications focused on the task. Don't add comments that restate code. Don't refactor
surrounding code unless asked. Don't over-engineer.

### Android Device Safety

Physical Android devices are forbidden test and deployment targets for agent-driven work in this
repository.

- Never run `./gradlew :app:android-main:connectedDebugAndroidTest`. This task is forbidden in this
  repository, regardless of target selection, and must not be used as a shortcut for emulator or
  device validation.
- Never run `connected*AndroidTest`, `install*`, `adb install`, `adb shell am instrument`,
  `adb uninstall`, `adb shell pm clear`, or any other `adb` command against a physical device.
- Never suggest or run app-data-destructive commands for `co.reasonabletech.logdate` (for example
  `adb uninstall`, `adb shell pm uninstall`, `adb shell pm clear`,
  `adb shell cmd package uninstall`, `adb shell rm -rf /data/data/co.reasonabletech.logdate`, or
  Gradle `uninstall*` tasks).
- Only use Android emulators or Gradle Managed Devices for Android app installs, instrumentation
  tests, UI tests, screenshots, benchmarks, and manual validation.
- If Android instrumentation coverage is needed, use an emulator-only or Gradle Managed Device task
  such as `:app:android-main:smokeDevicesGroupDebugAndroidTest` instead of
  `:app:android-main:connectedDebugAndroidTest`.
- Before any Android command that could talk to a device, verify the target is safe. A safe target
  is:
    - an emulator with an `adb` serial that starts with `emulator-`, or
    - a Gradle Managed Device started by Gradle for the current task.
- If any connected target is a physical device, do not use it. Stop immediately and either:
    - run the task on an emulator,
    - run the task on a Gradle Managed Device, or
    - ask the developer to disconnect the physical device before proceeding.
- Convenience is irrelevant here. If there is any uncertainty about the target device type, treat it
  as unsafe and do not run the command.
- A user must explicitly override this in the current conversation before any physical-device
  interaction is allowed. Repository defaults forbid it.

## Key References

| Topic                     | Location                                                                                                                                    |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Build commands            | [`./run help`](./run)                                                                                                                       |
| Architecture & module map | [`README.md`](./README.md) and module `README.md` files                                                                                     |
| Commit message format     | [`docs/reference/standards/commit-messages.md`](./docs/reference/standards/commit-messages.md)                                              |
| Valid commit scopes       | [`allowed-scopes.txt`](./allowed-scopes.txt) and [`docs/reference/standards/commit-scopes.md`](./docs/reference/standards/commit-scopes.md) |
| Git workflow & safety     | [`docs/reference/standards/git-guidelines.md`](./docs/reference/standards/git-guidelines.md)                                                |

## Code Conventions

- **Logging**: Napier only. Never `System.out`, `println`, or `Log.d`.
- **UUIDs**: `kotlin.uuid.Uuid` with `Uuid.random()`, not Java's UUID.
- **Dates**: `kotlinx.datetime.Instant`.
- **Serialization**: `kotlinx.serialization`.
- **DI**: Constructor injection with Koin.
- **State**: Sealed classes/interfaces for UI state.
- **Error handling**: try-catch with Napier. Prefer nullable returns over exceptions.
- **Imports**: `kotlin.*` > `androidx.*`/`kotlinx.*` > `app.logdate.*`. No wildcards.
