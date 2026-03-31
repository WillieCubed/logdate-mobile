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
./gradlew :benchmark:micro:connectedCheck
./gradlew generatePhoneBaselineProfile
./gradlew generateWearBaselineProfile
```

## Device Guidance

- Phone macrobenchmarks: use the managed `phoneApi35` device or a connected physical Pixel device.
- Wear macrobenchmarks: use the managed `wearApi34` device or a connected physical Wear device.
- Baseline Profile generation:
  - managed devices are the default path
  - if you need a connected-device experiment, invoke the underlying connected Android test task directly from the benchmark module instead of the root helper

## How to Read Results

- Macrobenchmark results are emitted as instrumentation outputs and JSON summaries under each benchmark module.
- Baseline Profile generation copies the generated profile into the target app module through the AndroidX Baseline Profile plugin.
- Microbenchmark results include time and allocation statistics.

## Expansion Workflow

When adding a new benchmark:

1. Add the scenario to the [Benchmark Matrix](./benchmark-matrix.md).
2. Add or reuse a helper in the relevant benchmark module.
3. Prefer launch paths and deterministic gestures before adding heavier fixture seeding.
4. If the scenario needs seeded data or complex state, document the benchmark-only hook contract in the test class and the matrix before wiring it into production code.
