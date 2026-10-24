# Native restore test driver

This unshipped app keeps Gradle Managed Devices alive while Android restarts LogDate
during native backup and restore. Its instrumentation runs in a separate package.
The transport waiting driver never acquires `UiAutomation`; nested LogDate probes own that connection.

The host entry point is `tests/e2e/managed-native-restore-acceptance.py`. Assemble the
LogDate app and instrumentation APKs before invoking it:

```sh
./gradlew :app:android-main:assembleDebug :app:android-main:assembleDebugAndroidTest
python3 tests/e2e/managed-native-restore-acceptance.py \
  --output /tmp/logdate-managed-native-transfer \
  --after-transfer missing-media --after-transfer storage --after-transfer ui \
  -- --exercise-duplicate-restore
```

Use a new private output directory for each run. APKs, journal fixtures, and transport
payloads in that directory can contain private data. The host records its active
Gradle handle there; inspect that process before starting a replacement after an
observation failure. A timeout does not establish that Gradle or its devices stopped.

Underlying native harnesses require the controller's custody environment. Their command builders
always use the same managed-target proxy, including streaming instrumentation. The proxy validates
the current Gradle, QEMU, and driver identities before replacing itself with the real ADB process;
host timeouts and cancellation remain attached to that ADB process. Failed probes separately stop
remote instrumentation through the owned endpoint cleanup.
Do not invoke a native harness against an independently selected emulator.

One managed-device task requests two concurrent shards. Each driver method occupies a
different AndroidJUnitRunner shard and publishes its source or destination role.
Two named tasks in the same project cannot replace this arrangement: the reviewed
UTP task retains the project lock while waiting for instrumentation. The driver
rejects reduced shard allocation before publishing readiness. The controller pins
the reviewed UTP backend, requires both roles, and checks both final test methods.

The controller cold boots each read-only test instance through a private SDK facade, preserving
Gradle's device ownership and the installed SDK. It requires current launch receipts and distinct
actual Wi-Fi addresses before rooted app probes start. The focused network profile then checks
both devices after an offline/online radio cycle. See [the native backup reference](../../docs/reference/native-android-backup.md)
for launch guards, evidence, and cleanup rules.

Only a live QEMU process with this generation's launch receipt and matching PID is eligible for
discovery; Gradle's snapshot-setup process is excluded. Each run uses a single-use daemon with the
configuration cache disabled because its SDK facade is temporary. The shared device limit leaves
bounded headroom for other jobs while this task still requests exactly two shards. A reduced pair
remains a failure.

Before readiness, each driver verifies the installed app and probe APK hashes,
matching signing certificates, and instrumentation target. The controller copies both
APKs into its private run directory before starting Gradle. It passes their absolute
paths through `logdate.nativeAcceptanceAppApk` and `logdate.nativeAcceptanceProbeApk`,
so Gradle installs that pair without rebuilding LogDate. Both properties are required
together and must name existing files. A different version-code environment or a later
build cannot change the selected bytes. Native harness reinstalls use the same copies.
Direct Gradle runs without the properties retain the ordinary app assembly dependencies.
The host binds every ADB operation to its
live Gradle process, newly started managed QEMU processes, and nonce-bearing driver
receipts with process birth identities. Protected transport access uses `su` without
restarting ADB. Normal instrumentation retains its shell identity.

The host marks the drivers accepted only after all selected native content and
transport oracles pass. Terminal completion closes transport commands and validates
each remaining endpoint independently, because its peer may already have shut down.
Both driver results must pass without skips. Driver success alone does not prove
native restore, Google-provider restore, or setup-wizard transfer.

`tests/e2e/managed-modern-callback-acceptance.sh` runs the separate Android 16 QPR2
callback contract on `restoreCallbackApi36_1`. A separate driver case launches the
LogDate suite with `am instrument --no-hidden-api-checks`, preserving access to the
framework's test-fixture constructor. This exemption belongs only to the nested test
process. The driver checks every named callback result and the exact full SDK value;
the host requires the outer managed case to pass too. Its device configuration uses the minor-version
input of the pinned AGP implementation until the public managed-device interface
exposes that setting; an incompatible plugin upgrade fails configuration. Passing
the test on Android 36.0 would not exercise `FullRestoreDataInput`.

`--mode publication` adds data-only/session-exclusion, replacement peer trust,
restart replay, and encrypted archive capture. The synthetic source account is
bound through the normal owner provider, draft adoption finishes before the backup
baseline, and its source remains offline until session cleanup. This prevents
unrelated cloud work from racing the strict durable-file comparison.

`--mode work-restart` retains the cloud-work process-death and account-transition
oracles. Pair it with `--after-transfer permissions` to test permissions and widget
ID remapping on the untouched managed peer. `--mode d2d` selects the managed Google
Play image and the guide's single-device transport simulation. `--mode enrollment`
requires `--classpath` and a private `--fixture-environment` for the isolated server.
Neither mode establishes Google credential delivery or setup-wizard acceptance.

`--mode late-media` uses the same owned managed pair for a focused completed-transfer
callback test. On the destination, the seed probe leaves an original image in replay
custody without a database source. The controller stops the app, then the verify probe
starts a new process, waits for the persisted reconciliation job, decodes the recovered
image offline, and checks a second pass leaves the note unchanged. Both selected probes
must report one passed test; an assumption skip or a fresh device between phases fails
acceptance. This synthetic callback test does not claim a Google transport or setup flow.

The `contracts` follow-up keeps the retained-key failure, two-process key persistence,
entry editing, journal lock, and legacy callback checks on a populated restored app.
CI runs independent scenarios on fresh managed generations and retains the existing
aggregate required check. Gradle alone starts and stops every emulator.
