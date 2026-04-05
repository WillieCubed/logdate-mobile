# Benchmark Matrix

This matrix is the source of truth for which Android surfaces are measured and how.

## Measurement Types

- `Macrobenchmark`: startup, scroll, animation, and user-visible interaction latency
- `Baseline Profile`: release optimization coverage for critical user journeys
- `Microbenchmark`: deterministic hot-path CPU and allocation measurements
- `Trace/Profiler Runbook`: Perfetto or Studio profiling path for deeper investigation
- `Production telemetry`: Android vitals and Firebase Performance backstop

## Phone App

| Surface | Primary metric | Tooling | Status |
| --- | --- | --- | --- |
| Launcher cold/warm startup | startup time | Macrobenchmark + Baseline Profile | Implemented |
| Deep-link startup | startup time | Macrobenchmark + Baseline Profile | Implemented |
| Timeline first render and scroll | frame timing / jank | Macrobenchmark | Starter smoke implemented |
| Onboarding | startup-to-interaction | Macrobenchmark | Partial |
| New editor open / existing editor open | interaction latency | Macrobenchmark | Partial |
| Text save / autosave recovery | interaction latency | Macrobenchmark | Planned |
| Audio record / stop / save / playback / transcript | interaction latency / frame timing | Macrobenchmark + Microbenchmark | Partial |
| Search open / query / results | interaction latency | Macrobenchmark | Implemented |
| Journals overview / detail | frame timing / interaction latency | Macrobenchmark | Planned |
| Rewind open / playback path | frame timing | Macrobenchmark | Planned |
| Share-in startup | startup time | Macrobenchmark | Planned |
| Export / import | wall-clock latency | Trace/Profiler Runbook | Planned |
| Phone/watch sync ingest | latency / allocations | Macrobenchmark + Trace | Planned |
| Widgets refresh | worker latency | Macrobenchmark + Trace | Planned |
| Dynamic feature install | install + first-use latency | Macrobenchmark | Planned |
| Billing / passkey / Health Connect flows | interaction latency | Macrobenchmark + Trace | Planned |

## Wear App

| Surface | Primary metric | Tooling | Status |
| --- | --- | --- | --- |
| Launcher cold/warm startup | startup time | Macrobenchmark + Baseline Profile | Implemented |
| Quick capture path | startup time | Baseline Profile starter | Starter implemented |
| Home frame pacing | frame timing / jank | Macrobenchmark | Starter smoke implemented |
| Timeline open / scroll / day detail | frame timing / interaction latency | Macrobenchmark | Planned |
| Recording start / stop / save | interaction latency | Macrobenchmark | Planned |
| Playback start | interaction latency | Macrobenchmark | Planned |
| Phone/watch sync render after ingest | latency | Macrobenchmark + Trace | Planned |
| Tile open / complication open | startup-to-interaction | Macrobenchmark | Planned |
| Mood check-in / settings / rewind | interaction latency | Macrobenchmark | Planned |

## Shared Hot Paths

| Hot path | Primary metric | Tooling | Status |
| --- | --- | --- | --- |
| Transcript accumulation | wall-clock time / allocations | Microbenchmark | Implemented |
| Timeline grouping and large-list shaping | wall-clock time / allocations | Microbenchmark | Implemented |
| Sync diffing and payload serialization | wall-clock time / allocations | Microbenchmark | Planned |
| Search indexing and ranking | wall-clock time / allocations | Microbenchmark | Planned |
| Database read/write hot paths | wall-clock time / allocations | Microbenchmark | Planned |

## Production Backstops

- Android vitals:
  - startup time
  - slow rendering
  - ANRs
  - crashes
  - LMKs
  - excessive battery and background network usage where applicable
- Firebase Performance:
  - supplement local traces for startup and network paths
  - do not use as the sole release gate because sampling and field variance are higher than lab benchmarks
