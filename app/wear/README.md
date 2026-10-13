# `:app:wear` — Wear OS App

LogDate captures thoughts, voice recordings, moods, and marked moments on your wrist. The phone
companion manages setup, the shared library, reminders, and device access.

**Min SDK**: 34 (Wear OS 5+) | **Target SDK**: 37 | **Compile SDK**: 37

## Release and compatibility

The phone and watch use the same application ID (`studio.hypertext.logdate`, or
`studio.hypertext.logdate.debug` for debug) and must have matching signing certificates for the Wear
Data Layer. Matching identity is necessary, but does not establish pairing or a verified LogDate link.
A Play phone build and a locally upload-key-signed watch build do not have matching app-signing keys.
Install both from the corresponding Play internal test tracks for hardware dogfood.

The redesign is gated by `FeatureFlag.WATCH_EXPERIENCE_V2`, default OFF. Protocol negotiation also
requires the peer's version and advertised feature; an incompatible or older peer retains the legacy
path. The flag is not evidence of runtime readiness. Enablement waits for the paired scenarios and
user-operated hardware pass in the [dogfood checklist](../../docs/testing/wear-dogfood-checklist.md).
The full design is [Wear OS and Phone Companion Redesign](../../docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md).

Mobile distribution is **internal only**: phone `internal`, watch `wear:internal`. Release identity,
versioning, and signing come from `app.logdate.android-release` in `build-logic`. See
[Google Play Publishing](../../docs/reference/google-play-publishing.md#wear-os). Batch a verified
implementation into one publication rather than creating a new Play version for each local commit.

## Companion behavior

With the redesign enabled, Today leads into Flow, retained memories, Archive, and moment details.
Capture supports tap-to-record, hold-to-talk, pause/resume, explicit discard, and Undo. Shared
preferences govern capture places, post-recording mood, reminders, and library sources. Permission
and preference checks still apply when a background operation executes.

The phone's Watch page uses shared settings components and includes a populated Today preview,
library/capture controls, Tiles and complications instructions, connection diagnostics, and removal.
Optional watch onboarding persists a per-device handled marker. Installation and repair actions
remain bound to the selected watch rather than silently targeting another node.

A linked watch can create encrypted captures through the phone or directly through its restricted
cloud session. The phone remains the editor: it resolves marked moments, captions, photo suggestions,
and attachments. Accepted captures survive offline retries; sync receipts distinguish local storage
from delivery and cloud backup. The watch's cloud profile cannot enroll another device.

Recording continuity exposes the live watch recording on the phone with Stop and Open actions.
Playback handoffs use acknowledged requests: an attempted command must not appear completed merely
because a button was tapped or the transport accepted bytes.

Removal drains pending captures first, revokes the linked cloud session, and clears watch data.
An offline watch retains a deferred erase request; a failed step remains actionable and must not be
shown as complete. Sign-out follows the same protection before clearing phone credentials.

These are implemented paths under acceptance, not a claim that the current published flag-off build
exposes them. See the checklist for the exact release candidate and unresolved runtime gates.

## Reminders

Reminders are explicitly optional. Notification permission alone does not enable them. Established
users with effective legacy nudges retain their choice through migration; a new installation stays
OFF until chosen. The phone publishes learned waking hours for offline watch scheduling, with safe
fallback when sleep access is unavailable. Device arbitration and the shared daily budget still apply.

Phone Health Connect workout collection is deliberately omitted from this internal release; no
exercise permission or Play health declaration is added. Compatible watches retain the separate
passive workout trigger subject to capability, consent, and permissions. See the
[recorded release decision](../../docs/testing/wear-workout-reminder-release-decision.md).

## Architecture

- `di/`: platform dependencies, protocol, window publication, reminders, continuity, and removal.
- `recording/`: shared recording-service controller, audio persistence/recovery, and Undo.
- `capture/`: shared preference checks and the capture/write guard used during secure erasure.
- `sync/v2/`: verified linking, receipts, library membership, media delivery, shared state, and removal.
- `v2/`: responsive watch destinations, moment actions, recorder, playback, and live continuity.
- `presentation/`, `playback/`: retained legacy presentation and playback compatibility.
- `tile/`, `complication/`: system entry points and compact summaries.

The watch uses the shared Room schema, migrations, SQLCipher storage, and note repositories. Cloud
credentials and identity/media keys stay in protected device storage. The bounded library window is
phone-owned; watch-owned captures are distinct from evictable phone library copies. Persisted window
reasons supply On this day, the previous completed week's Rewind, and actual starred sources without
requiring the watch to generate Rewinds.

## Build and emulator validation

```bash
# Build only; no connected-device selection
./gradlew :app:wear:assembleDebug

# Host behavior checks
./gradlew :app:wear:testDebugUnitTest :app:wear:ktlintCheck :app:wear:detekt

# Emulator-only instrumentation
./gradlew :app:wear:wearSmallRoundApi34DebugAndroidTest

# Compare existing visual references
./gradlew :app:wear:validateDebugScreenshotTest
```

For manual paired acceptance, first verify both explicit `adb` serials begin with `emulator-`.
Use Android Studio Device Manager's Pair Wearable flow and verify the actual peer in LogDate's
connection diagnostics. An AVD named “Paired Watch” or a saved Android Studio association does not
prove that the running phone and watch are paired. Preserve retained AVD data and credentials.

Repository policy forbids agent installs, instrumentation, and other device commands against
physical hardware. Hardware acceptance is performed by the user using Play internal builds. Do not
uninstall or clear LogDate data to troubleshoot setup. Controlled fixture instrumentation can replace
link/session state; run it on managed devices, not the retained pair used for end-to-end acceptance.

Visual tests cover small/large round screens; the consolidated phone/watch matrix also covers
populated states, large text, RTL, dark/dynamic colors, and reduced motion. Static rendering does not
prove rotary/gesture input, real audio, background behavior, Bluetooth, battery, or paired delivery.
See [Screenshot tests](../../docs/testing/screenshot-tests.md) and the
[cloud acceptance record](../../docs/testing/wear-cloud-companion-acceptance.md).

## Further reading

- [Audio recording feature](../../docs/feature-design/wear-audio-recording.md)
- [Testing strategy](../../docs/testing/introduction.md)
- [Wear OS and phone companion design](../../docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md)
- [Internal hardware dogfood and rollout](../../docs/testing/wear-dogfood-checklist.md)
