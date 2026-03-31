# CI and Gate Policy

## Enforcement Levels

- Pull requests:
  - run `managedPhoneBenchmark`
  - run `managedWearBenchmark`
  - treat regressions in implemented Tier 1 scenarios as blocking once stable baselines are recorded
- Nightly:
  - rerun all managed macrobenchmarks
  - run microbenchmarks on a connected device lane
  - archive benchmark JSON outputs and generated profiles
- Release validation:
  - generate phone and Wear baseline profiles
  - rerun phone and Wear Tier 1 macrobenchmarks
  - compare against locked baselines before shipping

## Threshold Defaults

Use these until the first benchmark history is established:

- latency regressions: fail on repeated changes greater than 10%
- frame/jank regressions: fail on repeated changes greater than 15%
- allocation or peak memory regressions in microbenchmarks: flag for investigation at 10%+

Update the thresholds only after collecting at least 10 clean runs for the scenario and device pair.

## Stability Rules

- Do not promote a scenario to blocking if it is still flaky.
- If a scenario flakes, move it to nightly until the source of nondeterminism is fixed.
- Keep benchmark code release-like:
  - benchmark builds should avoid debug-only code paths
  - baseline profiles must be generated from realistic launch and navigation behavior
