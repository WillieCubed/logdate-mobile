# Regression Triage Runbook

## 1. Confirm the Regression

- rerun the failing benchmark locally or on the same managed device
- compare the current run against the last green artifact
- identify whether the regression is:
  - startup
  - frame timing / jank
  - interaction latency
  - allocation / memory

## 2. Choose the Right Tool

- Startup regression:
  - Macrobenchmark output first
  - Perfetto trace next
  - check Baseline Profile generation and installation status
- Frame timing or jank regression:
  - Macrobenchmark `FrameTimingMetric`
  - Perfetto frame timeline
  - API-36+ `AppJankStats` when reproducing on latest Android
- Memory regression:
  - Studio Memory Profiler
  - allocation-heavy microbenchmarks
- Sync or media regression:
  - Perfetto for threading and I/O
  - Studio Network and CPU profilers

## 3. Common Checks

- Did startup work move earlier in `MainActivity` or app initialization?
- Did a new provider, initializer, or worker run at process start?
- Did list or timeline code start more work on first composition?
- Did a media or sync path start touching disk or network on the main thread?
- Did a new feature bypass the generated baseline profile?

## 4. Resolution Standard

- land a code fix and re-run the failing scenario
- if the regression is intentional, update the baseline and document why
- never accept a changed number without either a fix or an explicit baseline update record
