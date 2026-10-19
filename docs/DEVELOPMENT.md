# Development

This guide takes you from a fresh clone to a running LogDate app and server, and lists the
commands that build and test them. Every command goes through `./run`, which wraps Gradle,
Docker, and the helper scripts; `./run help` lists all of them. Deeper references are linked from
each section.

## Toolchain

LogDate is a Kotlin Multiplatform project built with Gradle. Install these before setting up:

- **JDK 17 or newer** to launch the Gradle wrapper. The wrapper pins Gradle 9.4.1, and the Gradle
  daemon runs on JDK 21, which Gradle downloads by itself from the daemon JVM criteria in
  `gradle/gradle-daemon-jvm.properties`.
- **Android Studio** (latest stable) with the Android SDK, emulator, and platform tools (`adb`).
  On macOS, `brew install --cask android-platform-tools` installs `adb` on its own.
- **Docker and Docker Compose** for the local PostgreSQL and Redis the server uses.
- **Xcode**, only for iOS work.
- **Node.js and pnpm**, only for `./run lint:lexicons`, `./run lint:conformance` and
  `./run conformance:update`.

## Set up

One command prepares a fresh clone or worktree: tools, Git hooks, `local.properties`, Firebase
configuration, and test dependencies. It is safe to re-run at any time.

```bash
./run setup            # set up this machine
./run setup --check    # report what is missing without changing anything
```

[Project setup](reference/project-setup.md) explains every step, including the environment
setup (`./run setup staging` and `./run setup production`) that only release maintainers need.

### Configuration

No production credentials are needed for ordinary development. Configuration lives in files Git
ignores:

- `local.properties`: `./run setup` creates it with the Android SDK location. Add
  `GOOGLE_MAPS_API_KEY` and `OPENAI_API_KEY` yourself when you work on maps or the intelligence
  features.
- `.env` (copied from [`docs/environment/.env.example`](environment/.env.example)): `DATABASE_URL`,
  `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_URL`, and `OPENAI_API_KEY` for a server run outside
  Docker, and `POSTGRES_*` or `REDIS_PORT` overrides for Docker Compose.

[Environment setup](environment/setup.md) describes each name and its default.

## Run

Start the local services first, then run the part of LogDate you are working on.

### Start the development services

```bash
./run start     # PostgreSQL, Redis, and dev tools in Docker
./run status    # show what is running
./run stop      # stop them; data is kept
```

### Run the server

```bash
./run server    # Ktor server on the local database
```

### Run the Android app

Run the Android app on an **emulator**. `./run android` installs on whatever device `adb` selects,
so name the emulator explicitly with `ANDROID_SERIAL`. Agents must never install on, test on, or
run `adb` against a physical Android device; see "Android Device Safety" in [AGENTS.md](../AGENTS.md).

```bash
adb devices                                  # find the emulator serial, such as emulator-5554
ANDROID_SERIAL=emulator-5554 ./run android   # install and open the debug build
ANDROID_SERIAL=emulator-5556 ./run run:wear  # the Wear OS app, on a Wear OS emulator
```

### Run the desktop app

```bash
./run desktop
```

## Build

Build only the targets you changed. The pre-push hook already compiles the Android app, so a full
build of every module is rarely needed.

```bash
./run build:android          # Android debug APK
./run build:wear             # Wear OS debug APK
./run build:desktop          # desktop package for this OS
./run build:server           # server JAR
./run build:server:docker    # server Docker image
```

Release builds (`./run build:android:release`, `./run build:wear:release`) need the signing setup
from `./run setup production`; see [Release and recovery](RELEASE.md).

## Test

Run the tests for the modules you changed, plus any suite whose contract your change touches. The
pre-push hook re-runs the tests for affected modules before code leaves your machine.

```bash
./gradlew :client:feature:timeline:test :client:repository:test   # tests for the modules you changed
./run test:server                                                 # server tests
./gradlew :integration:server-client-e2e:test                     # API and sync contract tests
./run test:screenshots                                            # Android, desktop, and Wear screenshot references
./run lint:check                                                  # ktlint
./run lint:complexity                                             # Detekt size limits
```

Instrumented Android tests run only on Gradle Managed Devices, for example
`./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest`. Never run
`connectedDebugAndroidTest`. `./run test` and `./run check` run every test and check in the
project; they take a long time and a lot of disk, so save them for changes to shared tooling or
configuration. The [testing guides](testing/introduction.md) explain each kind of test.

## Inspect the first workflow

Passing tests do not show that the app works. After a change, check the first thing a new user
does:

1. Start an emulator and install the debug build with `ANDROID_SERIAL=<emulator> ./run android`.
2. Open LogDate, finishing onboarding if it appears.
3. Write an entry with text, then add a photo or a voice recording, and save it.
4. Confirm the entry is on Home, then restart the app and confirm it is still there.

No account or network connection is needed for this workflow. UI changes have a stricter
acceptance bar, described in [AGENTS.md](../AGENTS.md): populated screenshots at phone and tablet
sizes, dark theme, right-to-left layout, and large text.

## Land a change

Every change reaches `main` through a pull request merged with a rebase. The
[Git guidelines](reference/standards/git-guidelines.md#branch-and-pull-request-workflow) describe
the branch, pull request, and merge steps.
