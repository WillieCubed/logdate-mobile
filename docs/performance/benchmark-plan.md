# Performance Benchmarking Plan

## Objective

Use benchmark data to optimize *perceived* performance across every major user journey, not just happy-path timing.

Primary goal:
- keep launch and interaction latency low enough that users feel immediate responsiveness on first use.

What this means in practice:
- prioritize "felt" metrics first (startup, input-to-paint, scroll smoothness, time-to-action)
- use deterministic tests for repeatable micro-level improvements
- use instrumentation traces for API-level regressions in media, sync, and platform integrations.

## Device & Tool Stack (by execution lane)

- Phone Macrobenchmarks: managed `flagshipPhoneApi36` (Pixel 9 Pro, API 36)
- Wear Macrobenchmarks: managed `wearApi35` (Wear OS Small Round, API 35)
- Microbenchmarks: managed `flagshipPhoneApi36`
- Baseline profiles: phone and wear release build generators on managed devices
- Tools:
  - `androidx.benchmark.macro` for startup/interaction frame timing
  - `androidx.benchmark.junit4` for deterministic CPU + allocation
  - Perfetto/AppJankStats/App Startup traces for root-cause
  - CI artifact diffs for baseline comparisons

## What to measure (priority order)

### Tier 1: Must have baseline from day one

These represent the first impression and most common user actions.

| Journey | Surface | Scenario | Tool | Primary metric | Baseline target |
| --- | --- | --- | --- | --- | --- |
| Cold start | Phone | launcher cold startup | macro | startup: initial display + full compose render | improve > first-run median baseline |
| Warm start | Phone | warm startup after home | macro | startup + process resume latency | no 95th percentile drift |
| Deep link start | Phone | `logdate://rewind` path | macro | deep-link time to first meaningful screen | stable P95 |
| Home render readiness | Phone | first timeline frame | macro | frame pacing + dropped frames | no regression in jank % |
| Search response | Phone | open search + first result | macro | input-to-content and frame timing | lower is better |
| Onboarding start | Phone | fresh onboarding landing | macro | time to first interactive control | track every release |
| Wear cold start | Wear | launch | macro | startup time + time to home | keep under existing baseline |
| Wear interaction | Wear | home scroll/poke loop | macro | frame timing + input to action | no visible stutter spike |

### Tier 2: High value paths

| Journey | Surface | Scenario | Tool | Primary metric |
| --- | --- | --- | --- | --- |
| Edit/create | Phone | open editor, type, save draft | macro | input-to-save latency |
| Recording loop | Phone | start-stop-save-transcript | macro + micro | end-to-end latency + allocations |
| Playback | Wear | playback start and stop | macro | start-to-play and first audio frame |
| Timeline aggregation | Phone | large-page shaping | micro | wall-clock + allocations |
| Search indexing | Phone | local note ranking path | micro | wall-clock + allocations |
| Sync diffing | Shared | sync diff build + payload serialize | micro | operation duration + allocation spikes |
| Widget / complications | Wear | tile/complication rendering | macro | render budget adherence |
| Data export/import | Phone | large dataset throughput | micro | elapsed + peak memory proxy |

### Tier 3: Edge and integration flows

| Journey | Surface | Scenario | Tool | Primary metric |
| --- | --- | --- | --- | --- |
| Billing / health / passkey | Phone | login + prompt + confirm | macro | dialog and transition latency |
| Dynamic module / feature install | Phone | first use cold path | macro | install + first interaction |
| Notification-to-interaction | Shared | deep open from notification | macro | open latency + first paint |
| Background sync scheduling | Phone/Wear | WorkManager + foreground service kickoff | macro + micro | scheduling delay + worker run time |

## Modern platform and API coverage

The plan explicitly covers APIs that most often cause perceived latency:
- Jetpack Compose composition, lazy lists, transitions
- App startup APIs and SplashScreen behavior
- Room / SQLDelight query composition and large list paging
- Kotlin coroutines dispatchers, `Flow` backpressure, and state fanout
- WorkManager and foreground service dispatch timing
- Media3, `AudioRecord`, `MediaRecorder`, `MediaStore` export path
- Health Connect read paths and permission transitions
- Wear OS tile, complication, and service lifecycle behavior
- BillingClient and Play services handshake
- Notification/shortcut routing latency

## Execution rules

1. No user-facing feature should ship without a matching entry in this plan and matrix.
2. For each new feature, add one macro or micro benchmark before feature completion:
   - one baseline test
   - one high-confidence "happy path" path
   - one failure-path guard if applicable
3. Use fixture-backed startup state so benchmarks are deterministic.
4. Avoid manual app setup during benchmark runs; seed state through supported fixture hooks.
5. Treat microbenchmarks as deterministic lab guards; don't optimize test-only behavior.

## Reporting and triage

- Keep results in Gradle benchmark JSON outputs under each module’s output directories.
- Archive JSON and Perfetto traces from each managed run.
- For each release candidate:
  - compare against prior baselines
  - escalate changes outside thresholds
  - annotate intentional changes with PR-level rationale and updated baseline notes.

## Regression gates

- Latency: flag at >10% repeated drift on implemented Tier 1 scenarios.
- Frame/jank: flag at >15% repeated drift.
- Allocations: flag at >10% drift on implemented microbenchmarks.
- Any new flaky scenario is moved to non-blocking until stabilized.

## Safety and isolation

- Benchmark runs are managed-device lanes only by default.
- Any connected-device benchmarking command must be explicit and approved by repo owner.
- No benchmarks should be wired to automatic PR/CI gates until scenario history is stable across at least 3 clean runs.

