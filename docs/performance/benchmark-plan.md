# Benchmark Plan

## Goal
Treat the Android product as a performance platform: measure every major user journey, validate key APIs, and feed those results into CI/persistent telemetry. We start with the most impactful experiences, then layer on tooling, data, and observability so no part of the app remains unmeasured.

## Key Experiences to Benchmark
1. **Launch & Deep Link Paths** – Macrobenchmarks for cold/warm startup, and launcher-to-deep-link flow. Measure `Activity`/`ComponentActivity` cold starts, Compose snapshot/slot table hydration, and Play Asset Delivery if it ever fires.
2. **Timeline Rendering & Scroll** – Frame-timing macro benchmarks for Compose `LazyColumn`/`LazyListState`, `ComposeView` interactions, large list diffing, and `LazyLayout` prefetch. Correlate with `FrameTimingMetric` and Perfetto traces to expose GC pauses and input latency.
3. **Recording & Transcription** – Macro + micro coverage for audio capture, `MediaRecorder`/`AudioRecord`, `AudioTrack`, and `TranscriptAccumulator`. Validate `Manifest.permission.RECORD_AUDIO` path, `Kotlinx.coroutines` dispatchers, `Media3` path, and concurrency with `WorkManager`/`ForegroundService` as needed.
4. **Saving & Syncing** – Macro benchmarks wrapping Room/SQLDelight reads+writes, `DataStore`, `ContentProvider` exports, and network sync. Micro benchmarks should exercise `SyncWorker` diffing, serialization layers, and `Atproto` collision logic.
5. **Wear & Widgets** – Macro benchmarks for Wear startup, quick capture route, and complication refresh. Baseline profile instrumentation for tiles/widgets ensures optimized frame rates under Compose constraints and `HotReload` flows.
6. **Settings & Billing Flows** – Macro playback for settings navigation, billing/passkey inflight, Health Connect, and other feature toggles. Exercise `BiometricPrompt`, `BillingClient`, `Play Billing Library`, and UI transitions.

## Measurement Plan
| Level | Tool | Target | Metrics |
| --- | --- | --- | --- |
| Macro | `androidx.benchmark.macro` | Shared managed devices (phone & Wear) | Startup time, frame timing, jank, CPU time, UI latency, `FrameTimingMetric`, `StartupTimingMetric`, baseline-profile generation |
| Micro | `androidx.benchmark.junit4` + `BenchmarkRule` | Single managed phone device | Wall-clock per operation, allocations, GC counts, `TimedUtterance`/`TranscriptAccumulator`, `Room` query hotpaths, serialization, diffing, `WorkManager` job latency |
| Baseline Profiles | `androidx.benchmark.macro.junit4.BaselineProfileRule` | Release builds for phone & Wear | packaged baseline profiles; hits recorded via `CodegenStats`, `lineNumberTable` coverage |
| Instrumentation | `androidx.test.runner.AndroidJUnitRunner`, `Perfetto` capture | Gradle Managed Device trace outputs | `catlog`/`perfetto` log streaming, `dumpsys gfxinfo`, `trace` events for new APIs (Jetpack Compose, `android.media`) |

## Platform & API Coverage
- **Jetpack Compose** – benchmark `Compose` recomposition, `LazyColumn`, `ConstraintLayout`/`Layout` custom measuring, and cross-module navigation transitions.
- **Kotlin Coroutines + Flows** – Micro instruments `Dispatchers`, `CoroutineScope`, `StateFlow`, `SharedFlow`, verifying concurrency bottlenecks for transcription, syncing, and media playback.
- **Room/SQLDelight + Paging** – Bench snippet ingestion, large query handling, transaction boundaries, and multi-threaded `RoomDatabase` fetch/save operations.
- **Media/Audio/Video** – Exercise `AudioRecord`, `Media3`, `MediaStore`, `ContentResolver`, `CameraX` if used, `Transcription` pipeline, `MediaSession` updates, and `ForegroundService` interactions.
- **WorkManager/Foreground Services** – Micro ensures `WorkManager` scheduling, `ForegroundService` latencies, and `Notification` display stay within budget.
- **Play Core / Billing / Health Connect / Biometrics** – Macro flows hitting `BillingClient`, `PlayBilling`, `Health Connect`, and `BiometricPrompt` to measure onboarding latencies.
- **Wear OS & Tiles** – Macro traces identify `Complication` updates, `TileService` frame pacing, `WearableActivity` transitions, and `Tile` data loads.
- **Baseline Profiles & Startup Optimizations** – Baseline profile generators ensure startup-critical code is captured and verified for API 34+ devices.

## Developer Tooling & Reporting
- **Gradle Tasks** – `managedPhoneBenchmark`, `managedWearBenchmark`, `managedMicroBenchmark`, and device-matrix tasks run on managed devices only; `managedMicroBenchmark` is pinned to the `flagshipPhoneApi36` (Pixel 10 Pro, API 36) device so no connected physical handset is ever targeted and `run test:bench:micro` launches that task.
- **Perfetto & Trace Scripts** – Document `Perfetto` capture recipes (CPU slices, `TraceEvent` categories) with sample query instructions stored alongside reports. Team-run instructions include hooking trace config at the end of instrumentation runs.
- **Benchmark Matrix & Docs** – Maintain `docs/performance/benchmark-matrix.md`, `README.md`, `running-benchmarks.md`, and this plan. Each new scenario must list the instrumentation contract and required data inputs before code lands.
- **Result Collation** – Gather JSON outputs from `androidx.benchmark` to `build/benchmarks` plus CI artifact archiving; parse via `tools/benchmark-analyzer` (if available) and log into `docs/performance/benchmarks/YYYY-MM-DD.md` for manual triage.

## Automation & CI Integration
1. **Lint & Tests** – Bench tasks run only on managed devices but are scheduled manually; gating ensures no Gradle target defaults to `connectedCheck` without explicit review.
2. **CI Jobs** – Daily/weekly jobs rerun `managedBenchmark` (macro) and `managedMicroBenchmark` on Gradle Managed Devices. Baseline profile generation tasks refresh stored profiles when shipping new features.
3. **Regression Alerts** – Configure alerting for >10% regressions (CPU, frame time, allocations). Use `androidx.benchmark` JSON diff tooling and Perfetto trace comparisons for verification.

## Documentation & Team Ramp-Up
- Document how to interpret each benchmark artifact, locate `build/outputs/microbenchmarks`, and match results to features (e.g., transcript build, timeline scroll, Wear quick capture).
- Capture runbooks for `Perfetto` captures, `TraceProcessor`, and `TraceEvent` categories to share with non-Android teammates.
- Keep this plan in sync with `docs/performance/README.md` via cross-links and a changelog entry under `docs/performance/benchmark-matrix.md` when new scenarios are added.
