# Android Benchmarking

This directory is the operating manual for Android performance work in LogDate.

It covers both Android surfaces in this repository:

- `app:android-main` / `app:compose-main`
- `app:wear`

The benchmark program now has executable Gradle scaffolding:

- `:benchmark:phone-macro` for phone startup, frame timing, and phone baseline profile generation
- `:benchmark:wear-macro` for Wear OS startup, frame timing, and Wear baseline profile generation
- `:benchmark:micro` for deterministic Android microbenchmarks

Start here:

- [Benchmark Plan](./benchmark-plan.md)
- [Benchmark Matrix](./benchmark-matrix.md)
- [Running Benchmarks](./running-benchmarks.md)
- [CI and Gate Policy](./ci-policy.md)
- [Regression Triage Runbook](./regression-triage.md)

Current implementation status:

- Starter phone macrobenchmarks are in place for launcher startup, deep-link startup, and frame timing smoke.
- Starter Wear macrobenchmarks are in place for launcher startup, quick-capture startup path, and frame timing smoke.
- Starter microbenchmark coverage is in place for transcript accumulation and timeline page shaping, covering two of the highest-value shared hot paths.
- The full scenario catalog is documented below and should be expanded into benchmark code incrementally.
