# Running Benchmarks

## Quick Commands

Use the repo entrypoint:

```bash
./run test:bench:phone
./run test:bench:wear
./run test:bench:micro
./run perf:baseline:phone
./run perf:baseline:wear
```

Direct Gradle equivalents:

```bash
./gradlew managedPhoneBenchmark
./gradlew managedWearBenchmark
./gradlew managedMicroBenchmark
./gradlew generatePhoneBaselineProfile
./gradlew generateWearBaselineProfile
```

Use deterministic reruns for trend runs:

```bash
./gradlew managedPhoneBenchmark managedWearBenchmark managedMicroBenchmark --no-daemon --rerun-tasks
./gradlew generatePhoneBaselineProfile generateWearBaselineProfile --no-daemon --rerun-tasks
```

## Device Guidance

- Phone macrobenchmarks: use the managed `flagshipPhoneApi36` device (Pixel 9 Pro, API 36).
- Wear macrobenchmarks: use the managed `wearApi35` device (API 35).
- Microbenchmarks: always run on the managed `flagshipPhoneApi36` device (Pixel 9 Pro, API 36).
- Baseline Profile generation:
  - managed devices are the default path
  - all baseline profile generation uses managed devices only

## Safety Rules

- Never run benchmark commands against connected physical devices.
- `managed*Benchmark` tasks are mapped to managed-device group tasks and should not use connected devices by default.
- If a connected-device run is explicitly requested, it must be approved in advance for this repository context and documented in the run ticket.

This repository's Android agent workflow must not target connected non-emulator devices for benchmark runs unless an explicit override is approved by the repo owner.

## How to Read Results

- Macrobenchmark results are emitted as instrumentation outputs and JSON summaries under each benchmark module.
- Baseline Profile generation copies the generated profile into the target app module through the AndroidX Baseline Profile plugin.
- Microbenchmark results include time and allocation statistics for transcript accumulation and timeline page shaping hot paths.

## Expansion Workflow

When adding a new benchmark:

1. Add the scenario to the [Benchmark Matrix](./benchmark-matrix.md).
2. Add or reuse a helper in the relevant benchmark module.
3. Prefer launch paths and deterministic gestures before adding heavier fixture seeding.
4. If the scenario needs seeded data or complex state, document the benchmark-only hook contract in the test class and the matrix before wiring it into production code.
