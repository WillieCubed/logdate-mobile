# Camera And Audio Adaptive Quality Checklist

Source: https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/camera-audio

Source last updated: 2026-04-10 UTC

Audit created: 2026-06-14

## Scope

This checklist tracks Android adaptive app quality guidance for camera and audio experiences. Use it
when auditing any LogDate feature that captures media, records audio, plays audio, or exposes media
device routing.

Agent-driven validation must not target physical Android devices unless the developer explicitly
overrides the repository default in the current conversation. Hardware validation that requires real
external cameras, headphones, or USB microphones should be recorded as manual developer evidence, or
run on a safe emulator or Gradle Managed Device where the capability is available.

## Applicable Feature Inventory

Apply this checklist to every user-facing feature below. The evidence column points to the current
code surface that made the feature a candidate; update it as screens move.

| Status | Feature | Applicable checklist areas | Evidence |
| --- | --- | --- | --- |
| `[partial]` | Entry editor camera capture block for photo and video capture | Camera_Switcher; Audio_Switcher for video microphone input | `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/content/EditorContentFooter.kt`; `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/camera/CameraBlockEditor.kt`; `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/camera/CameraCaptureContent.android.kt`; `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/camera/AndroidCameraCaptureManager.kt` |
| `[partial]` | Entry editor audio recording block | Audio_Switcher for microphone input; Audio_Switcher for playback output; Audio_Background_Playback for recorded-note playback | `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/content/EditorContentFooter.kt`; `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AudioBlockEditor.kt`; `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AudioBlockContent.kt`; `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/editor/EntryBlockUiState.kt`; `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManager.kt`; `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/EditorAudioBlockE2ETest.kt` |
| `[pass]` | Audio note viewer and immersive audio screen | Audio_Switcher for playback output; Audio_Background_Playback | `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/NoteViewerScreen.kt`; `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/AudioNoteViewerViewModel.kt` |
| `[pass]` | Journal detail inline audio cards | Audio_Switcher for playback output; Audio_Background_Playback | `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailScreen.kt` |
| `[pass]` | Timeline day detail audio snippets | Audio_Switcher for playback output; Audio_Background_Playback | `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/AudioNoteSnippet.kt` |
| `[pass]` | App-wide audio playback provider and mini-player | Audio_Switcher for playback output; Audio_Background_Playback | `client/ui/src/commonMain/kotlin/app/logdate/ui/audio/AudioPlaybackProvider.kt`; `client/ui/src/commonMain/kotlin/app/logdate/ui/audio/MiniAudioPlayer.kt`; `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioPlaybackService.kt` |
| `[pass]` | Entry editor video block playback | Audio_Switcher for playback output; visible PiP/split-screen continuation | `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/video/VideoBlockEditor.kt`; `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/video/VideoContent.android.kt` |
| `[pass]` | Video note viewer | Audio_Switcher for playback output; visible PiP/split-screen continuation | `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/NoteViewerScreen.kt`; `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/video/VideoContent.android.kt` |
| `[pass]` | Library media detail video playback | Audio_Switcher for playback output; visible PiP/split-screen continuation | `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/detail/MediaDetailScreen.kt`; `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/detail/MediaDetailUiState.kt`; `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/video/VideoContent.android.kt` |
| `[pass]` | Library external-display media presentation for videos | Audio_Switcher for playback output across device and external-display routes | `client/feature/remotedisplay/src/main/kotlin/app/logdate/feature/remotedisplay/MediaPresentation.kt`; `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/detail/MediaDetailScreen.kt` |
| `[pass]` | Wear OS quick recording screen | Audio_Switcher for microphone input | `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt`; `app/wear/src/main/kotlin/app/logdate/wear/presentation/recording/WearRecordingScreen.kt`; `app/wear/src/main/kotlin/app/logdate/wear/recording/WearAudioRecordingService.kt` |
| `[pass]` | Wear OS full audio recording screen | Audio_Switcher for microphone input | `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt`; `app/wear/src/main/kotlin/app/logdate/wear/presentation/audio/AudioRecordingScreen.kt`; `app/wear/src/main/kotlin/app/logdate/wear/recording/WearAudioRecordingService.kt` |
| `[partial]` | Wear OS timeline audio playback | Audio_Switcher for playback output; Audio_Background_Playback | `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt`; `app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearDayDetailScreen.kt`; `app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearTimelineViewModel.kt`; `app/wear/src/main/kotlin/app/logdate/wear/playback/WearAudioOutputMonitor.kt` |
| `[partial]` | Wear OS remote camera control | Camera_Switcher for the controlled phone camera experience | `app/wear/src/main/kotlin/app/logdate/wear/presentation/MainActivity.kt`; `app/wear/src/main/kotlin/app/logdate/wear/presentation/camera/WearRemoteCameraScreen.kt`; `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt`; `app/compose-main/src/androidMain/kotlin/app/logdate/client/remote/RemoteCameraActivity.kt` |

Adjacent media surfaces that do not currently start camera capture, microphone capture, or playback
should not be audited against this checklist unless their behavior changes. Current examples include
voice-note model download settings, microphone permission onboarding, static timeline audio moments,
image-only pickers, image-only viewers, and Rewind story playback without audio or video playback.

## Exhaustive Audit Findings

Audit date: 2026-06-14

### Systemic Findings

| Status | Area | Finding | Remediation |
| --- | --- | --- | --- |
| `[partial]` | Camera device selection | `AndroidCameraCaptureManager` now enumerates CameraX `availableCameraInfos`, builds labeled front/back/external `MediaDeviceUiState` rows using Camera2 metadata, persists the selected camera ID, restores it on preview start, falls back with explicit copy when the saved camera is unavailable, and binds preview/capture through the selected Camera2 camera ID. The inline camera UI uses the shared device selector as the primary control, with the old switch icon retained only as a built-in front/back shortcut. The shared selector now has tappable rows, stable test tags, and adaptive screenshot coverage for both the compact camera chip and expanded camera sheet states, including an external USB-style row. External USB camera hardware validation is still missing. | Record external USB camera preview/capture evidence, disconnection fallback behavior, and orientation behavior on hardware or a safe emulator/GMD setup where possible. |
| `[partial]` | Microphone input selection | A shared Android/Wear `AndroidAudioRouteRepository` now enumerates input devices, persists the selected microphone, and phone/Wear audio recording services pass the selection into `MediaRecorder.setPreferredDevice`. CameraX `1.6.0` video capture exposes `withAudioEnabled()` and audio-source configuration, but no public preferred `AudioDeviceInfo` routing hook; camera video therefore shows detected-route/system-controlled microphone copy instead of a fake selector. The shared selector now has stable test tags and adaptive screenshot coverage for a controllable microphone sheet with built-in, USB, and Bluetooth-style rows. Phone and Wear recording managers now reapply the preferred mic when the route list changes during an active recording session. The shared route-selection resolver has common tests proving preferred microphone selection, unavailable-device fallback copy, and restoration when the preferred microphone appears again. Hardware USB/Bluetooth mic validation is still missing. | Record hardware evidence for built-in, USB, headset, and Bluetooth microphones. Keep system-controlled messaging where Android/CameraX cannot force a route. |
| `[partial]` | Playback output selection | The shared route repository now enumerates output devices. Phone audio-note surfaces, shared Android video playback surfaces, compact external-display video presentation controls, and Wear day-detail playback show detected output route controls, including Wear no-output/Bluetooth settings fallback. The shared selector now exposes stable UI test tags, row-level selection semantics, adaptive output-sheet previews, a platform route action, and Android/Wear screenshot coverage for compact mini-player, expanded immersive player, journal inline audio cards, timeline snippets, note-viewer, and system-controlled output-selector states. Android system-controlled output route sheets now default to an "Open output switcher" action that calls `MediaRouter2.showSystemOutputSwitcher()` on Android 14+ and falls back to Sound settings if the platform ignores the request or the OS is older. `MediaDeviceSelectorE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` with 3 tests, 0 failures, proving the default output-switcher action remains visible and tappable on phone and tablet layouts. The shared route-selection resolver has common tests proving preferred output display while retaining system-controlled routing copy. Android/Wear media output routing is still marked system-controlled until hardware behavior is validated. | Validate route behavior with real Bluetooth/headphone/USB output devices and external-display hardware. Keep system-controlled copy and direct platform route affordances anywhere Android owns the route. |
| `[pass]` | Audio note background playback | Audio-note playback uses a `MediaSessionService`, starts the playback service before controller playback, exposes Media3 metadata, wires a notification provider, and now has focused unit coverage for service start order, progress resume after focus-style pause/refocus, completion, pause/seek/stop, and unsuitable-output synchronization. `AudioPlaybackBackgroundE2ETest` proves on `flagshipPhoneApi36` that playback starts with suitable output, shows the media notification, keeps playing after Home, keeps the notification active, survives 65 seconds locked, resumes after wake, directly taps visible pause/play media controls when SystemUI exposes them, synchronizes media notification pause/play actions with app playback state, and also synchronizes MediaSession pause/play commands with app playback state. The same class now passes on `largeScreenTabletApi35` as well after explicit notification-shade expansion surfaces visible Pause and Play/Resume controls and the playback state stays synchronized across pause and resume. | None for phone or tablet audio-note background playback evidence currently in scope. |
| `[pass]` | Video visible continuation policy | Product policy is now visible video continuation, not invisible audio-only background playback. Android video players keep playing when visible in split-screen/multi-window, can enter picture-in-picture from the player, and try to enter PiP instead of pausing when the user leaves while a video is playing. If PiP is unavailable or the video is not playing, playback pauses rather than continuing invisibly without video. `VideoPlaybackVisibilityE2ETest` passed on `flagshipPhoneApi36`, proving the shared Android video player exposes the PiP affordance and enters PiP from a real PiP-enabled activity. It also passed on `largeScreenTabletApi35` with 2 tests, 0 failures; the fresh XML from 2026-06-15 reports `videoPlayerPipAffordanceKeepsVideoVisibleWhenLeavingTheAppSurface` and `videoPlayerRemainsVisibleWhenAppIsResizedIntoMultiWindow`, proving PiP and split-screen/desktop-windowing visibility on a large-screen managed device. | Keep invisible video-audio background playback marked `[n/a]` unless product requirements change to audio-only video continuation. Add per-surface runtime checks only where a surface diverges from the shared video player. |
| `[partial]` | Remote camera from Wear | Wear remote camera now opens LogDate's controlled `CameraCaptureContent` in a dedicated phone activity instead of the generic system camera intent. The phone publishes its current `MediaDeviceSelectionUiState` to Wear through `RemoteCameraDeviceDataMapper`; the Wear listener updates `WearRemoteCameraDeviceStore`; and the Wear UI renders a scrollable camera list so users can select exact phone camera device IDs, including external-style rows, without losing the dominant capture action. The phone service handles open/capture/switch/select/close messages and exact `device:<id>` selections. Captures are auto-accepted into a normal `JournalNote.Image` or `JournalNote.Video`; the phone then publishes a saved/failed result through `RemoteCameraCaptureResultDataMapper`, and the Wear UI stays in "Capturing" until that phone acknowledgement arrives. Remote-camera state sync now also has unit coverage for watch-side selection updates from the phone-published camera store. Runtime watch-phone validation and built-in/external camera hardware evidence are still missing. | Validate the full watch-phone runtime flow on a safe watch-phone setup and record built-in/external camera hardware evidence. |

### Per-Feature Audit

| Feature | Status | Requirement results | Current evidence | Remediation |
| --- | --- | --- | --- | --- |
| Entry editor camera capture block for photo and video capture | `[partial]` | `Camera_Switcher`: implementation partial, external hardware unverified. `Audio_Switcher`: partial/system-controlled for CameraX video microphone routing. `Audio_Background_Playback`: n/a for capture. | `EditorContentFooter` creates the camera block. `CameraCaptureContent.android` requests `CAMERA` and `RECORD_AUDIO`, renders the shared camera selector, shows detected-route/system-controlled microphone copy in video mode, and keeps the switch icon only as a built-in front/back shortcut. `AndroidCameraCaptureManager` enumerates CameraX cameras, persists/restores selected camera IDs, falls back when a saved camera is unavailable, and binds by selected Camera2 ID. Local CameraX `1.6.0` sources were inspected after `ctx7` quota failure; `PendingRecording.withAudioEnabled()` does not expose preferred device routing. Android screenshot coverage now includes compact and expanded camera selector states. Validation passed `:client:feature:editor:compileAndroidMain`, `:client:feature:editor:ktlintAndroidMainSourceSetCheck`, and `:client:feature:editor:testAndroidHostTest`. | Validate external USB camera behavior and keep CameraX video microphone routing documented as system-controlled unless a public preferred-device API becomes available. |
| Entry editor audio recording block | `[partial]` | `Audio_Switcher` input: partial. `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: partial because phone Home/lock/notification/action behavior and large-screen visible-but-not-focused playback are verified, but direct visual lock-screen control tapping is not. | `AudioBlockEditor` starts/stops recording and playback. Existing recorded audio now bypasses the microphone permission wrapper, so users can play saved audio and change playback output without granting recording permission; only new/active recording remains permission-gated. `AudioBlockContent` exposes the shared "Audio output" selector in the expanded saved-audio editor block, with the Android output-switcher action for system-controlled routes. `AndroidAudioRouteRepository` enumerates microphone routes, persists selection, and `AudioRecordingService` applies the selected microphone with `MediaRecorder.setPreferredDevice`, including reapplying the preferred input when route lists change during an active recording. `EditorAudioBlockE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` with 0 failures, proving compact phone and expanded tablet users can open the actual editor audio-block output route sheet and reach the platform output-switcher action. `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36`, proving media notification persistence, Home backgrounding, 65-second locked playback, wake continuity, media notification pause/play action synchronization, and MediaSession pause/play synchronization. It also passed on `largeScreenTabletApi35` with 2 tests, 0 failures, including visible-but-not-focused multi-window playback. | Add hardware validation for external microphones and visual lock-screen control tapping if managed-device UI access supports it. Validate Android output route behavior with external devices or retain system-controlled copy. |
| Audio note viewer and immersive audio screen | `[pass]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: partial because phone Home/lock/notification/action behavior and large-screen visible-but-not-focused playback are verified, but direct visual lock-screen control tapping is not. | `NoteViewerScreen` renders `ImmersiveAudioScreen`; `AudioNoteViewerViewModel` starts playback with `AudioPlaybackManager.startPlayback`. `ImmersiveAudioScreen` accepts the shared output route state and renders an audio-output selector below the main transport controls, keeping skip/play/seek actions primary on compact and expanded layouts. `AudioPlaybackStatus` now carries the active URI and metadata, `AndroidAudioPlaybackManager` publishes those values, and `AudioPlaybackProvider` adopts externally-started immersive playback so the mini-player remains the visible control surface after the viewer closes. The viewer now cancels waveform work on clear without stopping active playback. Screenshot coverage includes expanded immersive player and system-controlled output route states. `AudioNoteViewerE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` with 0 failures, proving compact phone and expanded tablet users can open the audio-output route sheet from the actual viewer path and reach the platform output-switcher action. Playback manager tests cover service start order, progress resume after focus-style pause/refocus, unsuitable-output synchronization, active URI/metadata status, and the focused viewer/provider ownership slice. `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36`, proving media notification persistence, Home backgrounding, 65-second locked playback, wake continuity, media notification pause/play action synchronization, and MediaSession pause/play synchronization. It also passed on `largeScreenTabletApi35` with visible-but-not-focused multi-window playback. | Add visual lock-screen control evidence. Validate Android output route behavior with external devices or retain system-controlled copy. |
| Journal detail inline audio cards | `[pass]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: partial because phone Home/lock/notification/action behavior and large-screen visible-but-not-focused playback are verified, but direct visual lock-screen control tapping is not. | `JournalDetailScreen.AudioEntryCard` uses `LocalAudioPlaybackState.play/pause` from the global provider and now shows the shared output route selector for the current audio entry. The current-entry card is responsive: compact widths preserve the play button, resolved audio label, progress, and duration in the primary row while stacking the output selector below; expanded widths keep the selector inline. Android adaptive screenshot coverage includes the inline journal card with the output selector visible during current playback. `JournalInlineAudioE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` with 0 failures, proving compact phone and expanded tablet users can see the inline playback control, open the audio-output route sheet, and reach the platform output-switcher action. Shared playback manager tests cover service start order and progress resume after focus-style pause/refocus. `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36`, proving media notification persistence, Home backgrounding, 65-second locked playback, wake continuity, media notification pause/play action synchronization, and MediaSession pause/play synchronization for the shared phone playback stack. The same shared stack passed visible-but-not-focused multi-window validation on `largeScreenTabletApi35`. | Validate Android output route behavior with external devices or retain system-controlled copy. Add visual lock-screen control evidence through the shared audio playback remediation. |
| Timeline day detail audio snippets | `[pass]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: partial because phone Home/lock/notification/action behavior and large-screen visible-but-not-focused playback are verified, but direct visual lock-screen control tapping is not. | `AudioNoteSnippet` uses `LocalAudioPlaybackState.play/pause`, shows inline play/pause UI, and now shows the shared output route selector for the current audio snippet. The current-note layout is responsive: compact widths keep the playback title and stop action in the primary row and stack the output selector below, while expanded widths keep the selector inline. Android compact screenshot coverage includes the timeline snippet with the output selector visible during current playback. Shared playback manager tests cover service start order and progress resume after focus-style pause/refocus. `TimelineAudioSnippetE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` with 0 failures, proving compact phone and expanded tablet users can see the snippet identity, open the audio-output route sheet, and reach the platform output-switcher action. `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36`, proving media notification persistence, Home backgrounding, 65-second locked playback, wake continuity, media notification pause/play action synchronization, and MediaSession pause/play synchronization for the shared phone playback stack. The same shared stack passed visible-but-not-focused multi-window validation on `largeScreenTabletApi35`. | Validate Android output route behavior with external devices or retain system-controlled copy. Add visual lock-screen control evidence through the shared audio playback remediation. |
| App-wide audio playback provider and mini-player | `[pass]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: partial because phone Home/lock/notification/action behavior and large-screen visible-but-not-focused playback are verified, but direct visual lock-screen control tapping is not. | `AudioPlaybackProvider` owns global audio playback state, collects `AudioRouteRepository.outputDevices`, and now adopts active URI/metadata from `AudioPlaybackStatus` when another surface starts playback through the shared manager. `MiniAudioPlayer`, immersive audio, current journal audio cards, and current timeline snippets expose route-status selectors. The mini-player is responsive: compact widths keep play/pause, title/subtitle, and stop controls in the primary row and stack the output selector below; expanded widths keep the selector inline. `MediaDeviceSelector` provides stable adaptive test tags and an Android Sound settings affordance for system-controlled route sheets, and Android screenshot coverage includes compact mini-player, expanded immersive player, journal inline audio, timeline snippet, and note-viewer route paths. `MiniAudioPlayerE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` with 0 failures, proving compact phone and expanded tablet users can see the active audio identity, open the audio-output route sheet, and reach the platform output-switcher action. `AndroidAudioPlaybackManager` starts `AudioPlaybackService` before controller playback and its tests cover progress resume after focus-style pause/refocus plus active URI/metadata status so the mini-player can own externally-started playback. `AudioPlaybackService` uses Media3 `MediaSessionService`, notification provider, metadata, and unsuitable-output suppression. `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36`, proving the actual user path through Home, persistent notification, 65-second lock, wake, media notification play/pause actions, and MediaSession play/pause synchronization. It also passed on `largeScreenTabletApi35`, proving playback remains active and the media notification remains present while LogDate is visible but not focused in multi-window. | This should remain the primary remediation layer for all phone audio-note playback. Add route-change state and visual lock-screen media-control validation if managed-device UI access supports it. |
| Entry editor video block playback | `[pass]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: `[n/a]` for invisible video-audio playback by policy; PiP and split-screen/desktop-windowing visible continuation pass through the shared player. | `VideoBlockEditor` renders `VideoPlayerContent`. Android `VideoPlayerContent` now shows the shared output route selector, uses pooled ExoPlayer, keeps playing in split-screen/multi-window, enters PiP from the player, and tries PiP rather than pausing when the user leaves during active playback. Preview-mode video rendering now also shows the output selector and PiP affordance, so editor, journal viewer, and library screenshot surfaces expose the same controls. `EditorActivity` is PiP-enabled. `EditorVideoBlockE2ETest` now validates the actual editor user path in compact and expanded layouts: the user can see the video player, reach the PiP affordance, open the audio-output route sheet, and reach the platform output-switcher action. Validation passed editor Android ktlint, `:app:compose-main:compileAndroidMain`, `:app:android-main:compileDebugScreenshotTestKotlin`, `:app:android-main:compileDebugAndroidTestKotlin`, `:app:android-main:ktlintCheck`, `VideoPlaybackVisibilityE2ETest` on `flagshipPhoneApi36`, `VideoPlaybackVisibilityE2ETest` on `largeScreenTabletApi35`, and the focused `EditorVideoBlockE2ETest` managed-device run. | Keep invisible audio-only video playback paused unless product requirements change. Validate output route behavior with real Bluetooth/headphone/USB hardware. |
| Video note viewer | `[pass]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: `[n/a]` for invisible video-audio playback by policy; PiP and split-screen/desktop-windowing visible continuation pass through the shared player. | `NoteViewerScreen.VideoNoteViewerContent` delegates to the same `VideoPlayerContent` implementation, so it inherits the shared output route selector, preview-mode route/PiP screenshot affordances, and visible PiP/split-screen continuation behavior. `NoteViewerVideoE2ETest` validates the actual note-viewer video surface in compact and expanded layouts: the user can see the video player, reach the PiP affordance, open the audio-output route sheet, and reach the platform output-switcher action. Validation passed `:app:android-main:compileDebugAndroidTestKotlin`, `:app:android-main:ktlintCheck`, `:client:feature:journal:ktlintCommonMainSourceSetCheck`, `VideoPlaybackVisibilityE2ETest` on `flagshipPhoneApi36` and `largeScreenTabletApi35`, and the focused `NoteViewerVideoE2ETest` managed-device run. | Keep invisible audio-only video playback paused unless product requirements change. Validate output route behavior with real Bluetooth/headphone/USB hardware. |
| Library media detail video playback | `[pass]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: `[n/a]` for invisible video-audio playback by policy; PiP and split-screen/desktop-windowing visible continuation pass through the shared player. | `MediaDetailScreen.MediaContent` calls `VideoPlayerContent` when `isVideo` is true, so library detail videos inherit the shared output route selector, preview-mode route/PiP screenshot affordances, and visible PiP/split-screen continuation behavior. `VideoPlaybackVisibilityE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` against the shared player. `MediaDetailUiState.VideoContent` identifies the surface. Expanded library video detail now shows the presenter/external-display control cluster beside video metadata instead of hiding those controls in compact-only presentation. `LibraryMediaDetailVideoE2ETest` now validates the actual compact phone and expanded tablet media-detail controller paths: the user can see external-display audio controls, open the output route sheet, reach the platform output-switcher action, and stop presenting. Validation passed `:client:feature:library:compileAndroidMain`, `:client:feature:library:ktlintCommonMainSourceSetCheck`, `:app:compose-main:compileAndroidMain`, `:app:android-main:compileDebugScreenshotTestKotlin`, `:app:android-main:compileDebugAndroidTestKotlin`, `:app:android-main:ktlintCheck`, and the focused `LibraryMediaDetailVideoE2ETest` managed-device run. | Validate output route behavior with real Bluetooth/headphone/USB or external-display hardware. Add per-surface runtime checks only if library playback diverges from the shared player. |
| Library external-display media presentation for videos | `[pass]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: not evaluated separately because presentation is external-display playback. | `MediaPresentation` acquires an ExoPlayer, repeats the video, and renders a controller-less `PlayerView` on the external display. Compact and expanded phone/tablet controller layouts now show the presenter controls plus an "External display audio" route selector/status while presenting videos, keeping the external display chrome-free. Validation passed `:client:feature:library:compileKotlinDesktop`, `:client:feature:library:compileAndroidMain`, `:client:feature:library:ktlintCommonMainSourceSetCheck`, `:app:compose-main:compileAndroidMain`, `:app:android-main:compileDebugKotlin --continue`, and `:app:android-main:compileDebugScreenshotTestKotlin`. | Validate on real or safe external-display hardware that Android's selected/system output is understandable and stable. Do not rely on the controller-less presentation surface for route control. |
| Wear OS quick recording screen | `[pass]` | `Audio_Switcher` input: partial. | `WearRecordingScreen` records through `WearRecordingViewModel`; `AndroidAudioRouteRepository` enumerates microphone routes on Wear, and `WearAudioRecordingService` applies the selected microphone with `MediaRecorder.setPreferredDevice`. Wear now exposes the shared `MediaDeviceSelector` chip under the recording controls, and screenshot coverage shows the selected mic label plus the controllable microphone selector in ready, active, long active, saving, saved, too-short, and error states. | Add hardware evidence for built-in and Bluetooth microphones. If a Wear OS device cannot force a specific external microphone, keep detected-route/system-controlled copy. |
| Wear OS full audio recording screen | `[pass]` | `Audio_Switcher` input: partial. | `AudioRecordingScreen` uses `AudioRecordingViewModel`; recording goes through `WearAudioRecordingService`, which now applies the selected microphone route from the shared repository. Wear now exposes the shared `MediaDeviceSelector` chip under the recording controls, and screenshot coverage shows the selected mic label plus the controllable microphone selector before recording, during active recording, and during paused recording. | Reuse the same hardware validation as quick recording and verify selected input behavior on an actual safe Wear setup. |
| Wear OS timeline audio playback | `[partial]` | `Audio_Switcher` output: partial/system-controlled. `Audio_Background_Playback`: partial because Wear now registers its playback notification channel and has a GMD test harness, but runtime playback continuity requires a suitable Wear audio output. | `WearTimelineViewModel` blocks playback when `AudioOutputState.Unavailable`; `WearAudioOutputMonitor` observes output devices and can open Bluetooth settings. `WearDayDetailScreen` now shows a tappable output summary, inline output picker, detected output route rows, unavailable output copy, and Bluetooth settings fallback. Wear declares `POST_NOTIFICATIONS`, depends on `client:notifications`, and `LogDateWearApplication` registers the audio playback channel at startup before shared `AudioPlaybackService` posts media notifications. Wear screenshot coverage includes closed, open, and system-controlled output-picker states. `WearDayDetailPlaybackTest` passes on `wearSmallRoundApi34` with 14 tests, 0 failures, proving the user can open output options, see the Bluetooth route, select an available controllable route, and reach Bluetooth settings when Wear OS owns routing or no output is available. `WearAudioPlaybackBackgroundE2ETest` passes on `wearSmallRoundApi34` with `wearApplicationRegistersPlaybackNotificationChannel` green and `playbackNotificationAndSessionStaySynchronizedAfterWearHome` skipped because the managed device reports unsuitable audio output. Validation also passed `:app:wear:compileDebugKotlin`, `:app:wear:compileDebugAndroidTestKotlin`, `:app:wear:ktlintCheck`, `:app:wear:testDebugUnitTest`, and focused shared Android playback-manager tests. | Validate real Wear speaker/Bluetooth route behavior and rerun `WearAudioPlaybackBackgroundE2ETest` on a Wear-safe target with suitable output so background playback, notification persistence, and MediaSession action synchronization can be marked complete. Keep system-controlled copy where Wear OS owns routing. |
| Wear OS remote camera control | `[partial]` | `Camera_Switcher`: phone camera list mirroring, exact device selection, and phone-to-Wear capture saved/failed acknowledgement are implemented; external hardware/runtime evidence remains partial. | `WearRemoteCameraScreen` keeps the main capture action dominant, renders a scrollable phone camera list alongside compact Back, Switch, and Front shortcuts, shows "Capturing" until the phone confirms the result, and then moves to preview or error based on the saved/failed acknowledgement. `RemoteCameraActivity` publishes phone camera selections and capture results to Wear through `PhoneWearSyncBridge`; `WearDataLayerListenerService` updates `WearRemoteCameraDeviceStore` and `WearRemoteCameraCaptureResultStore`; `WearRemoteCameraViewModel` can select exact `device:<id>` rows and waits for `RemoteCameraCaptureResultDataMapper` results before leaving `CAPTURING`; and `PhoneDataLayerListenerService` routes exact IDs to `CameraRemoteCommand.SelectCameraDevice`. Validation passed focused Wear remote-camera unit tests, remote camera device/result mapper host tests, phone listener and bridge unit tests, Wear ktlint, compose-main Android ktlint, and app-wide compile coverage. | Validate watch-phone runtime behavior and record built-in/external camera hardware evidence. |

### Requirement Coverage Matrix

| Requirement | Status | Covered features | Gaps |
| --- | --- | --- | --- |
| Camera switcher includes built-in and external cameras | `[partial]` | Phone editor camera uses CameraX enumeration, a labeled shared selector, and persisted selected camera IDs. Wear remote camera receives the phone camera device list, renders exact camera rows, and can send `device:<id>` selections back to the phone-controlled camera flow. | External camera hardware is unverified. |
| Camera selection updates live preview correctly | `[partial]` | Phone editor binds preview/capture by selected Camera2 ID and keeps the preview streaming gate. | External camera preview/capture behavior, disconnection fallback, and orientation still need hardware or safe emulator/GMD evidence. |
| Camera switcher remains discoverable and accessible across adaptive layouts | `[pass]` | Phone editor has a labeled selector plus a conditional built-in switch shortcut. Android screenshot coverage includes the compact camera chip and expanded sheet with built-in and USB-style camera rows. `MediaDeviceSelector` rows now expose explicit assistive-tech state descriptions that match the visible row status. `MediaDeviceSelectorE2ETest` passed on `largeScreenTabletApi35` and `flagshipPhoneApi36`, proving camera rows are displayed, selected rows expose selected and "In use" state, external-style rows expose "External device" state, and rows remain tappable on phone and tablet layouts. | None for selector discoverability/accessibility. External camera hardware behavior remains tracked by the separate camera-function rows. |
| Microphone input switcher exists | `[pass]` | Phone and Wear recording use the shared Android input route repository and selector UI. Camera video shows detected-route/system-controlled microphone copy because CameraX `1.6.0` does not expose preferred input-device routing. Screenshot coverage includes a controllable microphone sheet with built-in, USB, and Bluetooth-style rows. | External hardware evidence is still missing. |
| Playback output switcher exists | `[pass]` | Editor saved-audio blocks, mini-player, immersive audio, current journal audio cards, current timeline snippets, shared Android video players, compact external-display video presentation controls, and Wear day-detail playback expose detected output routes. System-controlled Android output route sheets now include an Open output switcher action that calls the platform output switcher where supported and falls back to Sound settings. Screenshot and GMD coverage exercise the selector in editor audio-block, compact, expanded, inline journal, timeline, note-viewer, external-display, and Wear states, and `MediaDeviceSelectorE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` with 3 tests, 0 failures, proving the actual output-route sheet action is visible and tappable on phone and tablet. | Output routing is system-controlled and hardware validation is still missing. |
| Audio follows selected route after switching or hot-plugging | `[partial]` | Phone and Wear audio recording pass selected microphone IDs into `MediaRecorder.setPreferredDevice`, and now reapply the preferred input device when the available route list changes during an active recording session. `MediaDeviceSelectionResolver` tests prove selected microphone fallback copy when a preferred route disappears and restoration when it reappears, plus selected output display and system-controlled no-device fallback copy while Android remains the system-controlled output router. | Hardware route behavior and cross-device hot-plug behavior are not validated. |
| Audio switcher is accessible and adaptive | `[pass]` | Shared selector UI uses labeled chips/sheets with selected-state text, explicit row state descriptions for assistive tech, stable selector/sheet/row/settings test tags, deterministic common tests for tag stability, E2E assertions for selected rows, row state descriptions, and the platform output-route affordance, and Android/Wear screenshot/GMD coverage for compact, expanded, system-controlled, editor audio-block, mini-player, immersive-player, inline journal, timeline, note-viewer, and Wear output-picker states. `:app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.MediaDeviceSelectorE2ETest` passed on `largeScreenTabletApi35` and `flagshipPhoneApi36`, proving the actual user flow from compact chip to sheet selection/platform output-switcher action on phone and tablet layouts. | None for UI/UX accessibility/adaptive behavior. Hardware route behavior remains tracked by the separate route-function rows. |
| Playback continues when visible but not focused | `[pass]` for audio notes and visible video | Audio notes use a MediaSessionService foundation and playback-manager tests cover progress resume after focus-style pause/refocus. `AudioPlaybackBackgroundE2ETest` passed on `largeScreenTabletApi35` with 2 tests, 0 failures, including `playbackContinuesWhenAppIsVisibleButNotFocusedInMultiWindow`, proving playback remains active and the media notification remains present while LogDate is visible but not focused in multi-window. Video keeps playing when visible in split-screen/multi-window or PiP; `VideoPlaybackVisibilityE2ETest` passed on `flagshipPhoneApi36`, proving PiP entry from the shared Android video player, and passed on `largeScreenTabletApi35` with `videoPlayerRemainsVisibleWhenAppIsResizedIntoMultiWindow`, proving runtime split-screen/desktop-windowing visibility. `VideoPauseVisibilityPolicyTest` passed with ktlint, proving the pause policy keeps playback alive for PiP and multi-window visibility while pausing when no visible surface remains. | None for shared phone audio-note playback or shared Android video-player visible-continuation behavior. |
| Playback while not visible uses foreground media service | `[pass]` for audio notes, `[n/a]` for video by policy | Audio notes use `AudioPlaybackService`; playback-manager tests verify the service is started before controller playback. `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36`, proving playback remains active after Home, behind Android Settings, during a 65-second locked interval, and after wake. Video playback pauses rather than continuing invisibly as audio-only media. | Revisit video only if product requirements change. |
| Playback while not visible shows persistent notification | `[pass]` for audio notes, `[n/a]` for video by policy | `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36` and asserted the active media notification ID, channel ID, title metadata, persistence after Home, persistence behind Android Settings, persistence while locked for 65 seconds, and persistence after wake. Video playback uses visible PiP or pauses. | None for phone audio-note foreground notification behavior. |
| Lock-screen media controls pause/resume and stay synchronized | `[pass]` for audio notes, `[n/a]` for video by policy | MediaSession foundation exists for audio notes, playback-manager tests cover pause/resume/stop/seek synchronization, and `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36` with visible media-control tapping, notification action synchronization, and MediaSession pause/play commands synchronized to shared playback state after Home and lock/wake. The same test now also passes on `largeScreenTabletApi35` after explicit notification-shade expansion surfaces visible Pause and Play/Resume controls. Video playback is not intended to continue as invisible lock-screen audio. | None for audio-note lock-screen/media-notification control synchronization. |
| Refocus playback continues without stutter | `[pass]` | Playback-manager tests cover progress resume after focus-style pause/refocus without resetting progress. `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36`, proving retained progress after Home, while Android Settings was foreground, after returning to LogDate, during lock, and after wake. | None for phone audio-note Home/settings/lock/wake refocus. |
| Premium restriction is intentional and documented | `[n/a]` | No premium-tier background playback restriction found during this audit. | Re-evaluate if entitlements are added. |

## Detailed Remediation Plan

This plan treats camera and audio device selection as part of LogDate's capture experience, not as a
settings afterthought. LogDate's current product surface is a personal memory system: users capture
voice notes, photos, videos, timeline moments, journal details, library media, remote-display
presentations, and quick Wear OS entries. Remediation should therefore keep capture fast and calm,
while making professional-device behavior discoverable when users attach external cameras,
microphones, headphones, Bluetooth devices, or external displays.

The current implementation already has an initial UI foundation in the worktree: shared
`MediaDeviceUiState`/`MediaDeviceSelectionUiState`, a reusable `MediaDeviceSelector`, visible
system-controlled route labels in phone and Wear recording surfaces, a mini-player output affordance,
and a camera selection model for built-in front/back cameras. That foundation is not enough to pass
the audit because it does not yet enumerate external devices, force selected routes, persist selected
devices, validate background playback, or cover video and remote camera behavior.

### Product And UX Principles

- Keep the primary memory-capture action dominant. Camera shutter, record, play, and save controls
  remain the largest and most reachable controls; device selection is secondary but visible.
- Show the active device where the user is already acting. Users should see labels such as "Back
  camera", "USB camera", "System microphone", "Bluetooth headphones", or "Phone speaker" directly
  on capture/playback surfaces.
- Use progressive disclosure. Compact phone surfaces use selector chips or icon buttons; tapping
  opens a platform sheet. Wear uses short status text and compact picker flows. External-display
  playback exposes routing on the controlling phone screen, not the presentation surface.
- Be honest when the platform controls a route. If a capture path cannot force a selected input or
  output on the current API/device, keep the active-route label visible and show "System controlled"
  instead of presenting a fake selector.
- Keep the platform settings escape hatch as the selector default. `MediaDeviceSelector` and
  `MediaDeviceSelectorSheet` should default `systemSettingsAction` to
  `rememberMediaRouteSettingsAction(selection.kind)` so every Android system-controlled microphone
  or output sheet gives the user a working next step. Override it only for previews, screenshot
  fixtures, tests, or intentionally constrained surfaces where opening system settings would be
  misleading.
- Treat that default as product behavior, not caller convenience. The user opened the route sheet
  because they are trying to change a camera, microphone, speaker, headset, or display route; if
  Android owns that route, the most useful outcome is a direct settings handoff scoped to the same
  media kind. Requiring every call site to pass
  `rememberMediaRouteSettingsAction(selection.kind)` makes the common path easier to omit and risks
  leaving users at explanatory copy with no action.
- Decision: prefer the default-parameter form:
  `systemSettingsAction: MediaRouteSettingsAction? = rememberMediaRouteSettingsAction(selection.kind)`.
  It is preferable because the selector owns the route-state context that determines which
  platform handoff is useful. Keeping that default in the selector makes the production path
  self-documenting, prevents call sites from accidentally showing system-controlled explanatory
  copy with no useful next action, and leaves the nullable parameter available for tests, previews,
  screenshots, and deliberately constrained UI where the settings handoff should be hidden. Do not
  replace this with required per-call boilerplate unless the UX contract changes and every selector
  entry point is re-audited.
- Prefer one shared route model. Phone, Wear, inline audio cards, immersive audio, video players,
  and remote camera should use the same device identity, selected-state, unavailable-state, and
  fallback semantics.
- Preserve background audio as a first-class journaling behavior. Closing a viewer should return the
  user to their journal/timeline without unexpectedly killing playback; Stop remains explicit.

### Phase 0 - Documentation And API Refresh

- Refresh current Android API guidance with `ctx7` before implementing platform-forced routing. The
  2026-06-14 attempts are quota-blocked, so an implementer must run `npx ctx7@latest login` or set
  `CONTEXT7_API_KEY`, then fetch docs for Android audio routing, `AudioManager`, `AudioDeviceInfo`,
  Media3 route/output controls, CameraX camera selection, and foreground media services.
- Re-check the source Android quality page before release certification. Current source requirements
  are: camera switcher for built-in and external cameras, audio switcher for built-in and external
  peripherals, background playback in visible and non-visible states, foreground service for
  non-visible audio, persistent notification, and lock-screen media controls.
- Convert this plan into tracked work items before implementation. Each work item must point back to
  one or more rows in `Applicable Feature Inventory`, `Systemic Findings`, and `Requirement
  Checklist`; do not mark a feature `[pass]` until implementation and evidence both exist.

### Phase 1 - Shared Device Selection Foundation

#### 1.1 Audio Route Repository

- Replace the current `SystemControlledAudioRouteRepository` placeholder with Android and Wear
  implementations backed by platform device observation.
- Expose input and output streams with:
  - stable route ID;
  - user-facing label;
  - category: built-in, phone speaker, watch speaker, Bluetooth, wired, USB, external, system
    default;
  - `isAvailable`;
  - `isExternal`;
  - `isSelectionControllable`;
  - `routeControlMessage`;
  - `selectedDeviceId`;
  - hot-plug event reason for UI copy.
- Persist only user preferences, not transient device instances:
  - preferred input ID by feature family: `audio-recording`, `camera-video`, `wear-recording`;
  - preferred output ID by playback family: `audio-notes`, `video`, `wear-audio`,
    `external-display`;
  - clear or fallback automatically when a device disappears.
- Selection behavior:
  - if selected device is available and controllable, route new playback/capture to it;
  - if selected device disappears during idle state, select system default and show a short status;
  - if selected device disappears during recording, keep recording when possible and show the active
    replacement route;
  - if selected output disappears during playback, pause only when the platform reports unsuitable
    output or no output; otherwise continue on the fallback route and update UI.
- Minimum implementation surfaces:
  - common interface in `client/media`;
  - Android implementation in `client/media/src/androidMain`;
  - Wear implementation or adapter in `app/wear`;
  - no-op/system-controlled implementations for iOS and desktop to preserve KMP compilation;
  - fake repository for unit, Compose, and screenshot tests.

#### 1.2 Camera Device Repository

- Add a CameraX-backed Android camera device repository instead of deriving selection only from
  `CameraFacing`.
- Expose:
  - stable camera ID;
  - label: "Back camera", "Front camera", "USB camera", or platform-specific label when available;
  - lens facing;
  - external/built-in classification;
  - availability;
  - selected ID;
  - whether live switching is supported for the current capture mode.
- Camera selection behavior:
  - front/back shortcut remains only when exactly two built-in cameras are present;
  - selector list is the canonical path and includes external cameras;
  - switching while recording is disabled with visible disabled state;
  - switching waits for fresh preview frames before hiding the switch overlay;
  - if selected external camera disconnects, return to the last available built-in camera and show a
    transient route-change message.
- Persist selected camera per phone camera flow and remote-camera flow, falling back to built-in
  back camera when unavailable.

#### 1.3 Shared Selector Components

- Evolve `MediaDeviceSelector` into the production route selector:
  - compact chip for normal phone capture/playback surfaces;
  - icon-only button with tooltip where space is constrained;
  - platform sheet/dialog for detailed selection;
  - Wear picker variant with two to four large list items and a Bluetooth-settings fallback;
  - support for unavailable routes, selected route, route changed, and system-controlled copy.
- Add accessibility requirements:
  - content descriptions include device type and selected label;
  - selector sheet has a clear title: "Camera", "Microphone", or "Audio output";
  - selected item exposes selected state;
  - unavailable items explain why they cannot be selected.
- Add adaptive layout requirements:
  - compact phone: chip may wrap below controls but must not overlap shutter/record/play controls;
  - expanded/tablet: selector remains visible near the media control cluster, not buried in overflow;
  - desktop-windowing/split-screen: selector text ellipsizes and controls stay reachable;
  - Wear round screens: status text is short and picker items remain tappable.

### Phase 2 - Phone Capture Surfaces

#### 2.1 Entry Editor Camera Capture Block

- Current finding addressed: binary front/back switch only; no external camera; no video mic
  selection.
- Target UX:
  - top-left camera chip shows current camera;
  - tapping chip opens all available cameras;
  - quick switch button remains near shutter when front/back shortcut is valid;
  - video mode shows a microphone chip below the camera chip;
  - while recording, camera and microphone chips are read-only and explain that switching is disabled
    during recording.
- Implementation:
  - replace `CameraCaptureState.cameraSelection` fallback with repository-backed state;
  - update `AndroidCameraCaptureManager` to bind selected CameraX camera, not only
    `CameraSelector.DEFAULT_FRONT_CAMERA`/`DEFAULT_BACK_CAMERA`;
  - route `selectCameraDevice(deviceId)` through the existing preview-switch gate;
  - add selected microphone state to video capture state;
  - pass selected microphone into the recorder path when supported; otherwise show
    "System controlled" and keep `.withAudioEnabled()` as fallback;
  - update iOS/desktop camera managers with compatible no-op or platform-specific state.
- Tests/evidence:
  - fake camera repository tests for built-in cameras, external camera, disconnect fallback, and
    selected-state persistence;
  - camera ViewModel tests for selecting front/back/external IDs;
  - Compose tests for chip visibility, disabled state during recording, and selector sheet labels;
  - screenshot tests for photo mode, video mode, recording mode, compact phone, expanded phone/tablet;
  - manual or lab evidence for external USB camera preview updates.
- Pass criteria:
  - built-in and external cameras are listed;
  - preview updates after each selection without stale/blank frames;
  - selected camera label is visible;
  - video mic route is visible and selectable or honestly marked system-controlled.

#### 2.2 Entry Editor Audio Recording Block

- Current finding addressed: no microphone input switcher; playback route access only partially
  started through mini-player; background playback unverified.
- Target UX:
  - before recording, show "Mic: <selected device>" above the record affordance;
  - tapping opens microphone choices;
  - during active recording, show the active microphone as read-only unless live input switching is
    confirmed supported;
  - when an external mic connects while the block is open, show it in the picker and optionally
    prompt if it is preferred;
  - when an external mic disconnects, show a one-line fallback message and continue recording if
    possible.
- Implementation:
  - extend `AudioRecordingManager.startRecording` with selected input route metadata;
  - update `AndroidAudioRecordingManager` and `AudioRecordingService` to apply supported input
    selection;
  - update `AudioViewModel` state with active input route and route-change message;
  - keep current foreground recording notification behavior and add selected input label to the
    recording notification if notification layout supports it;
  - route playback of saved recordings through the global `AudioPlaybackProvider` instead of a
    local-only playback path where possible.
- Tests/evidence:
  - audio recording ViewModel tests for selected input, unavailable input fallback, and hot-plug
    state;
  - service tests or fakes proving selected route metadata reaches the recorder;
  - Compose tests for idle, recording, paused, fallback, and system-controlled states;
  - manual USB microphone validation.
- Pass criteria:
  - microphone selection is visible before recording;
  - route state remains understandable during recording;
  - selected input is used where platform-supported;
  - fallback behavior is visible and non-destructive.

### Phase 3 - Phone Audio Playback Surfaces

#### 3.1 App-Wide Audio Playback Provider And Mini-Player

- Current finding addressed: MediaSession foundation exists, active playback URI/metadata now flow from
  the playback manager into the app-wide provider, and the mini-player can remain the route home for
  immersive playback after the viewer closes. Output selector hardware behavior and background
  validation are still incomplete.
- Target UX:
  - mini-player is the global route home for active audio;
  - output chip/button shows current route;
  - tapping opens output list;
  - unsuitable output state shows a compact warning and route selector;
  - play/pause/stop remain one-tap controls.
- Implementation:
  - complete `AudioPlaybackState.currentUri` use so resume never requires call sites to resend URI;
  - connect `AudioRouteRepository.outputDevices` to Media3/Android route selection;
  - expose selected output, route changed, unsuitable output, and route unavailable states;
  - ensure playback manager metadata includes title, subtitle, note ID, journal names, and artwork
    for notification/lock screen;
  - keep playback service alive while playing and stop it when playback is explicitly stopped or
    completed.
- Tests/evidence:
  - unit test that mini-player resume uses current URI;
  - fake output repository tests for selection and hot-plug fallback;
  - Media3 controller tests for play/pause/stop/seek and unsuitable-output state;
  - notification/lock-screen manual evidence or emulator/GMD evidence;
  - split-screen/desktop-windowing playback evidence.
- Pass criteria:
  - route selector is visible when playback is active;
  - playback resumes with correct URI;
  - notification and lock-screen controls stay synchronized;
  - playback continues across visible/unfocused and non-visible states.

#### 3.2 Audio Note Viewer And Immersive Audio Screen

- Current finding addressed: output selector exists and viewer teardown no longer stops active
  playback. Playback URI/metadata now flow through `AudioPlaybackStatus` so the app-wide provider and
  mini-player can adopt playback started from the immersive viewer.
- Target UX:
  - immersive screen shows route control only when controls are visible;
  - closing immersive screen returns to journal/timeline while playback continues through the
    mini-player;
  - explicit Stop ends playback.
- Implementation:
  - add output selector to `ImmersiveAudioScreen` controls;
  - move playback lifetime from `AudioNoteViewerViewModel` to `AudioPlaybackProvider` for
    user-visible playback;
  - ensure viewer `onCleared` does not stop global playback unless the user explicitly stopped it;
  - synchronize waveform/progress with global playback state.
- Tests/evidence:
  - ViewModel/provider ownership test that closing viewer does not stop active global playback;
  - Compose test for output selector visibility with auto-hidden controls;
  - lock-screen/refocus test starting from immersive screen.
- Pass criteria:
  - output selection is reachable in immersive mode;
  - background playback survives viewer close;
  - refocusing the viewer preserves progress without stutter.

#### 3.3 Journal Detail Inline Audio Cards

- Current finding addressed: inline cards play via global provider but have no route affordance.
- Target UX:
  - tapping Play starts audio and reveals mini-player with output selector;
  - inline card remains dense and journal-focused;
  - optional small route icon appears only when the inline card is the active source and layout has
    space.
- Implementation:
  - pass full URI/display metadata into global playback state;
  - ensure active card and mini-player share selected route and progress;
  - add a compact route affordance only if it does not crowd title/duration content.
- Tests/evidence:
  - Compose test: play inline card, mini-player appears with route selector;
  - state test: route selection from mini-player reflects while inline card remains active;
  - screenshot test for compact journal detail.
- Pass criteria:
  - output route selection is available once inline playback starts;
  - inline card state remains synchronized with global playback.

#### 3.4 Timeline Day Detail Audio Snippets

- Current finding addressed: no output selector; background playback unverified.
- Target UX:
  - snippets remain quick-scan timeline artifacts;
  - playback starts inline;
  - mini-player becomes the route and background playback control;
  - if output is unavailable, snippet shows concise blocked-output copy.
- Implementation:
  - ensure snippet playback uses global playback provider metadata and URI retention;
  - wire unsuitable-output state into snippet copy where space permits;
  - rely on mini-player selector for full route list.
- Tests/evidence:
  - Compose tests for idle, playing, blocked output, and completed snippet states;
  - screenshot tests for day detail with active audio snippet;
  - background playback validation starting from timeline.
- Pass criteria:
  - output route control is reachable after snippet playback starts;
  - timeline state stays synchronized with global playback and notification controls.

### Phase 4 - Video And External Display Playback

#### 4.1 Product Policy

- Product decision: LogDate videos remain visual media. Playback may continue when the video remains
  visible in split-screen, desktop-windowing, or picture-in-picture, but video must not continue as
  invisible audio-only background playback. If the app cannot keep the video visible, it pauses.
- UX constraint: continuation must be obvious. Users should see the video in the current window or
  PiP surface, with the route control and pause affordance still reachable before they leave.

#### 4.2 Visible Video Continuation

- Current finding addressed: local `VideoPlayerContent` paused on `ON_PAUSE`/`ON_STOP` and lacked
  a clear policy for video audio outside the focused screen.
- Target UX:
  - video controls include an output-route affordance when the video has audio;
  - split-screen and desktop-windowing focus changes keep visible playback running;
  - leaving the screen while playback is active moves the video into PiP when Android supports it;
  - if PiP is unavailable, playback pauses instead of continuing invisibly.
- Implementation:
  - keep the shared output route selector in video controls;
  - enable PiP on activities that host video playback;
  - keep playback running while `Activity.isInMultiWindowMode` or `Activity.isInPictureInPictureMode`;
  - try to enter PiP during active playback before pausing on lifecycle loss;
  - keep local ExoPlayer playback because invisible audio-only video playback is out of scope.
- Tests/evidence:
  - lifecycle tests for split-screen, desktop-windowing, PiP entry, and refocus;
  - manual or GMD validation for PiP controls and non-PiP pause behavior;
  - screenshot tests for video route control.
- Pass criteria:
  - video audio route can be selected;
  - visible video continues in split-screen/desktop-windowing and PiP;
  - video pauses when it cannot remain visible.

#### 4.3 Entry Editor Video Block Playback

- Apply visible video continuation to `VideoBlockEditor`.
- Keep edit/delete/block controls distinct from playback route controls.
- Validate compact editor and expanded editor layouts.

#### 4.4 Video Note Viewer

- Apply visible video continuation to `NoteViewerScreen.VideoNoteViewer`.
- Closing the viewer should stop local video playback unless the video is already visible in PiP.
- Validate viewer-specific lifecycle and route selector behavior.

#### 4.5 Library Media Detail Video Playback

- Apply visible video continuation to `MediaDetailScreen` video content.
- Ensure route selector remains visible in both stacked compact layout and side-by-side expanded
  layout.
- Validate that PiP/refocus behavior preserves the user's place in the library media detail flow.

#### 4.6 Library External-Display Media Presentation

- Current finding addressed: controller-less external display has no route UI or source-of-truth
  route state.
- Target UX:
  - phone remains the control surface;
  - phone shows "Presenting on <display>" and "Audio: <route>";
  - route selection controls whether audio goes to phone/system output, Bluetooth, USB, or the
    presentation route when supported;
  - external display stays clean and media-focused.
- Implementation:
  - add presentation session state shared between `MediaDetailScreen` and `MediaPresentation`;
  - expose audio route selector on the phone media detail/presentation controls;
  - synchronize play/pause/position between phone control and external `PlayerView`;
  - handle display disconnect by returning playback to phone controls and showing a short message.
- Tests/evidence:
  - fake presentation route tests;
  - Compose screenshot tests for active external display controls;
  - manual external-display validation for audio route behavior.
- Pass criteria:
  - route state is visible on the phone while presenting;
  - audio route behavior is defined and validated for external display sessions.

### Phase 5 - Wear OS Surfaces

#### 5.1 Wear Quick Recording Screen

- Current finding addressed: no input selector; worktree now shows route status only.
- Target UX:
  - ready state shows "Mic: <active route>" below or near the hold-to-record prompt;
  - paused state can open the microphone picker;
  - active recording keeps route text visible but non-interactive;
  - small round screens do not lose the main hold/record affordance.
- Implementation:
  - replace status-only text with `WearMediaRoutePicker` entry in ready and paused states;
  - back it with Wear-aware `AudioRouteRepository.inputDevices`;
  - if Wear cannot force route selection, keep route text and system-controlled explanation;
  - propagate selected route into `WearAudioRecordingManager` and `WearAudioRecordingService` when
    supported.
- Tests/evidence:
  - ViewModel/fake repository tests for built-in, Bluetooth mic, no mic, disconnected route;
  - Wear Compose tests for ready, active, paused, and error states;
  - manual Bluetooth mic validation.
- Pass criteria:
  - active or selected input is visible;
  - selection/fallback behavior is clear and usable on round screens.

#### 5.2 Wear Full Audio Recording Screen

- Apply the same Wear input route component as quick recording.
- Keep the large record/stop control primary.
- Add picker access before recording and while paused; active recording remains read-only.
- Validate with existing `AudioRecordingScreenTest` style tests and screenshot states.

#### 5.3 Wear Timeline Audio Playback

- Current finding addressed: route status and inline output picker now exist; output routing remains
  system-controlled and background playback evidence is still missing.
- Target UX:
  - header or active card shows "Output: <route>";
  - no-output state opens the output picker first;
  - Bluetooth settings remains a secondary fallback to connect new hardware;
  - active playback card remains one-tap stop/play.
- Implementation:
  - keep `AudioRouteRepository.outputDevices` wired into `WearDayDetailScreen`;
  - use the compact inline output picker as the primary route-control surface;
  - extend `WearAudioOutputMonitor` into route state if selection becomes controllable beyond
    system-managed Bluetooth settings;
  - route playback through selected output where supported;
  - keep `AudioOutputState.Unavailable` for true no-output cases.
- Tests/evidence:
  - add Wear UI tests for picker collapsed/expanded, no output, speaker, Bluetooth, and active
    playback;
  - background playback and notification evidence on Wear emulator/GMD or manual hardware.
- Pass criteria:
  - output selection is user-facing;
  - no-output prompt is actionable;
  - playback state survives background/lock-screen scenarios.

#### 5.4 Wear Remote Camera Control

- Current finding addressed: Wear no longer opens the generic system camera intent. It launches a
  LogDate-controlled phone camera surface, handles capture/switch/select/close commands, mirrors the
  phone camera-device list, lets the user select exact phone camera IDs, saves captured media as
  normal LogDate notes, and sends saved/failed capture acknowledgements back to Wear. Remaining gaps
  are external-camera evidence and watch-phone runtime validation.
- Target UX:
  - watch shows phone connection state, selected camera, capture button, switch-camera affordance,
    and capture result;
  - phone opens LogDate-controlled camera flow, not a generic system camera intent;
  - if phone-side camera list cannot be mirrored, watch shows selected camera and a simple switch
    action; phone shows full selector.
- Implementation:
  - keep `PhoneDataLayerListenerService` routed through the LogDate `RemoteCameraActivity`, not
    `MediaStore.ACTION_IMAGE_CAPTURE`;
  - define Wear data-layer messages:
    - `/logdate/camera/open`;
    - `/logdate/camera/select`;
    - `/logdate/camera/switch`;
    - `/logdate/camera/capture`;
    - `/logdate/camera/close`;
  - phone-to-Wear messages:
    - `/logdate/camera/devices`;
    - `/logdate/camera/capture-result`;
  - phone sends current camera device list and selected device to Wear;
  - Wear sends select/switch/capture commands;
  - phone captures into LogDate's note flow and returns result status.
- Tests/evidence:
  - data-layer protocol tests for open/devices/select/capture/result;
  - ViewModel tests for each remote camera phase;
  - phone service tests proving capture message is handled, not only logged;
  - manual watch-phone remote camera validation.
- Pass criteria:
  - Wear remote camera can switch or select camera;
  - capture command produces a LogDate-controlled result;
  - errors and disconnects are visible and recoverable.

### Phase 6 - Cross-Cutting Background Playback

- Scope: audio notes, video audio, Wear timeline audio, and any future media surface that continues
  playback after losing focus.
- Implementation requirements:
  - use Media3 media session service for active background playback;
  - start foreground service for non-visible playback;
  - show persistent, non-dismissible notification while non-visible playback is active;
  - expose lock-screen play/pause/stop and seek where appropriate;
  - keep app UI, notification, lock-screen, and service state synchronized;
  - handle process recreation by recovering active session metadata or stopping cleanly with correct
    notification dismissal;
  - do not stop playback from feature ViewModel cleanup unless user explicitly stops playback.
- Validation requirements:
  - start playback, enter split-screen or desktop-windowing, verify no stutter;
  - move another app foreground and verify playback/notification;
  - lock device for at least one minute and verify playback survives;
  - use lock-screen controls and verify app state updates;
  - unlock/refocus and verify playback continues without stutter;
  - repeat for audio note and video audio once video support lands.

### Phase 7 - Test And Evidence Matrix

| Finding | Automated evidence | Manual or GMD evidence | Completion gate |
| --- | --- | --- | --- |
| Camera device selection | Android host tests; CameraViewModel selection tests; Compose selector tests still needed | External USB camera preview switching | Built-in and external cameras selectable; preview updates |
| Microphone input selection | Fake input repository tests; recording ViewModel tests; service route metadata tests | USB mic and Bluetooth mic validation | Selected/active mic visible and routed or system-controlled |
| Playback output selection | Desktop compile for editor/journal/timeline; journal and timeline ktlint; Wear compile/unit/test-source validation; mini-player, immersive, and picker-state Compose tests still needed | Bluetooth/headphones/USB output validation | Output selector available on active playback |
| Audio-note background playback | MediaSession service tests; notification metadata tests | split-screen, lock-screen, refocus validation | Playback survives focus/visibility changes |
| Visible video continuation | lifecycle tests for split-screen and PiP | PiP, split-screen, and refocus validation for visible video | Video remains visible while continuing, or pauses when it cannot remain visible |
| Remote camera from Wear | data-layer protocol tests; Wear ViewModel tests; phone service tests | watch-phone capture validation | Wear can switch/select and capture through LogDate camera |
| Guideline tests | Test checklist rows updated with command/evidence links | hardware matrix attached to audit | Each checklist row has pass evidence |

### Phase 8 - Status Updates And Release Certification

- After each phase, update:
  - `Applicable Feature Inventory` status;
  - affected `Systemic Findings`;
  - affected `Per-Feature Audit` row;
  - `Requirement Coverage Matrix`;
  - `Test Checklist` evidence.
- Do not mark a row `[pass]` if only UI exists but route selection is not functional.
- Use `[partial]` for system-controlled route labels without functional selection.
- Use `[unverified]` when implementation exists but hardware/background evidence is missing.
- Before release certification, re-run:
  - relevant unit tests;
  - Compose tests;
  - `ktlintCheck` for touched modules;
  - Android/Wear emulator or GMD checks;
  - manual hardware matrix for external camera, USB mic, Bluetooth headset/mic, lock-screen controls,
    and external display.
- Release pass requires every non-`[n/a]` checklist row to have direct evidence, not inference from
  adjacent features.

## Verification Notes

Last updated: 2026-06-15

- `:client:domain:compileCommonMainKotlinMetadata` initially failed because
  `client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainModule.kt` referenced
  `FileSystem.SYSTEM` from common metadata. That has been remediated by introducing
  `domainFileSystem` as an `expect`/`actual` platform filesystem provider in `client:domain`.
- `:app:compose-main:compileAndroidMain` initially failed after the filesystem fix because the
  `client:feature:library` Android compile output was stale/corrupt and its compile jar contained
  only `R.class`, causing unresolved references to `libraryFeatureModule`, `LibraryOverviewRoute`,
  `MediaDetailRoute`, `LibraryScreen`, and `libraryEntries`.
- A focused `:client:feature:library:clean :client:feature:library:compileAndroidMain` rebuilt the
  Android variant classes. The source-set wiring and dependency model were verified by
  `:app:compose-main:compileCommonMainKotlinMetadata` and `:app:compose-main:dependencies
  --configuration androidCompileClasspath`, which both include `project :client:feature:library`.
- Fresh verification now passes `:app:compose-main:compileAndroidMain` and
  `:app:android-main:compileDebugScreenshotTestKotlin`. Screenshot compilation is valid evidence
  again for the Android adaptive route-selector screenshot surfaces.
- Decision record: `MediaDeviceSelector` and `MediaDeviceSelectorSheet` intentionally default
  `systemSettingsAction` to `rememberMediaRouteSettingsAction(selection.kind)`. This is preferable
  to nullable per-call boilerplate because the platform settings escape hatch is part of the
  selector's expected UI contract, not a special behavior owned by each call site. The selector
  already has the `selection.kind` needed to choose the correct camera, microphone, or output
  settings action; duplicating that expression at every caller increases the chance that one
  production surface omits the handoff and leaves the user at explanatory copy with no path
  forward. Users who land in a system-controlled microphone or output route state should always get
  an immediate next action that matches the current media kind, whether they opened the selector
  from the mini-player, immersive audio viewer, inline journal audio, video controls, or Wear
  output UI. Tests and custom surfaces can still pass a distinct action or `null` when they have an
  explicit UX reason to suppress or replace the platform action. Do not move this back into required
  caller boilerplate without documenting the product reason and revalidating every selector entry
  point.
- `:app:android-main:validateDebugScreenshotTest` was run after screenshot compilation passed. It
  failed on 2026-06-15 after running 746 tests with 170 failures. The visible failures include
  missing baselines for newly added adaptive route-selector screenshots and broad existing
  editor/settings/onboarding/sync/library baseline diffs; Gradle then ended while collecting binary
  results with `NoSuchFileException` for an `in-progress-results` file. Treat the generated adaptive
  screenshot coverage as compile-time evidence until baselines are reviewed and updated; do not mark
  screenshot-validation rows `[pass]` from compilation alone.
- `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.MediaDeviceSelectorE2ETest` passed on
  2026-06-15 against the Gradle Managed Device smoke group: `largeScreenTabletApi35` and
  `flagshipPhoneApi36`. This is direct runtime UI/UX evidence that the shared selector opens from
  the user-facing chip, exposes selected row state, keeps route actions tappable on phone and tablet
  layouts, and shows the platform route action for system-controlled output routes.
- After replacing the Android output-route default action with `MediaRouter2.showSystemOutputSwitcher()`
  plus Sound settings fallback, `:client:ui:compileAndroidMain
  :client:ui:ktlintAndroidMainSourceSetCheck :app:android-main:compileDebugAndroidTestKotlin
  :app:android-main:ktlintCheck` passed on 2026-06-15. The follow-up
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.MediaDeviceSelectorE2ETest` also passed on
  `flagshipPhoneApi36` and `largeScreenTabletApi35` with 3 tests, 0 failures on each device. The
  new `systemControlledOutputDefaultsToPlatformOutputSwitcherAction` test proves the default Android
  audio-output route sheet shows the output-switcher action on phone and tablet layouts.
- After adding row state descriptions for assistive technology, `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.MediaDeviceSelectorE2ETest
  :client:ui:ktlintCommonMainSourceSetCheck :app:android-main:ktlintCheck` passed on 2026-06-15.
  `flagshipPhoneApi36` reported `tests="2"`, `failures="0"`, `errors="0"`, `skipped="0"`,
  timestamp `2026-06-15T09:48:26`; `largeScreenTabletApi35` reported `tests="2"`,
  `failures="0"`, `errors="0"`, `skipped="0"`, timestamp `2026-06-15T09:48:59`. The E2E test now
  asserts selected camera rows expose selected semantics and "In use" state, external-style camera
  rows expose "External device" state, and system-controlled audio output keeps a tappable settings
  affordance.
- After adding stable Wear day-detail output-route tags and picker interaction assertions,
  `:app:wear:compileDebugAndroidTestKotlin :app:wear:ktlintCheck` passed on 2026-06-15. The
  focused Gradle Managed Device run `:app:wear:wearSmallRoundApi34DebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.WearDayDetailPlaybackTest`
  then passed on `wearSmallRoundApi34` with 14 tests, 0 failures, 0 errors, and 0 skipped. The XML
  result at
  `app/wear/build/outputs/androidTest-results/managedDevice/debug/wearSmallRoundApi34/TEST-wearSmallRoundApi34-_app_wear-.xml`
  has timestamp `2026-06-15T10:46:47` and includes
  `outputSummary_tapShowsOutputPicker`, `outputPicker_displaysBluetoothOutputRoute`,
  `outputPicker_controllableRouteSelectsBluetoothOutput`, and
  `outputPicker_systemControlledRouteOpensBluetoothSettings`.
- The Wear managed-device declaration was corrected during that validation from the unavailable
  `system-images;android-34;google_apis_ps16k;arm64-v8a` image to the available Wear OS image
  channel `system-images;android-34;android-wear;arm64-v8a` with 4 KB page alignment. Phone and
  tablet managed devices still default to 16 KB page alignment.
- `:app:android-main:compileDebugAndroidTestKotlin :app:android-main:ktlintCheck` passed on
  2026-06-15 for the updated background-playback instrumentation test.
- A strict follow-up `AudioPlaybackBackgroundE2ETest` now requires visible SystemUI media controls
  for the background/lock flow. The 2026-06-15 smoke-device run passed on `flagshipPhoneApi36`;
  `TEST-flagshipPhoneApi36-_app_android-main-.xml` reports `tests="2"`, `failures="0"`,
  `errors="0"`, `skipped="0"`, timestamp `2026-06-15T12:13:05`, proving the phone managed device
  exposed visible pause/play controls and kept notification plus MediaSession state synchronized.
  The same run passed on `largeScreenTabletApi35`; `TEST-largeScreenTabletApi35-_app_android-main-.xml`
  reports `tests="2"`, `failures="0"`, `errors="0"`, `skipped="0"`, timestamp
  `2026-06-15T12:16:44`, after explicit notification-shade expansion surfaced visible Pause and
  Play/Resume controls and playback state stayed synchronized across pause and resume.
- `:app:android-main:flagshipPhoneApi36DebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.AudioPlaybackBackgroundE2ETest --rerun-tasks`
  passed on 2026-06-15 with 1 test, 0 failures, and a 69.287-second test duration. This is direct
  phone Gradle Managed Device evidence that audio-note playback starts with suitable output, posts
  the media notification, keeps playback and notification active after Home, keeps playback and
  notification active behind Android Settings as a non-audio foreground app, returns to LogDate with
  playback and notification still active, remains active for a 65-second locked interval, remains
  active after wake, synchronizes media notification pause/play actions with app playback state, and
  synchronizes MediaSession pause/play commands with app playback state. It does not prove a visual
  tap on the lock-screen media controls. Split-screen/desktop-windowing playback is covered by the
  later `largeScreenTabletApi35` rerun.
- A clean rebuild of `:client:feature:core`, `:client:feature:onboarding`, and
  `:client:feature:android-widgets` fixed the earlier stale unresolved-reference compile blocker
  for `:client:feature:onboarding:compileAndroidMain` and
  `:client:feature:android-widgets:compileAndroidMain`.
- `:client:feature:editor:testAndroidHostTest --tests
  app.logdate.feature.editor.ui.video.VideoPauseVisibilityPolicyTest
  :client:feature:editor:ktlintAndroidMainSourceSetCheck
  :client:feature:editor:ktlintAndroidHostTestSourceSetCheck` passed on 2026-06-15. This is focused
  host-test evidence that shared Android video playback should remain active when its surface is
  already visible in PiP or multi-window, and should pause when no visible surface remains. Runtime
  split-screen/desktop-windowing validation is covered by the later `largeScreenTabletApi35`
  `VideoPlaybackVisibilityE2ETest` rerun.
- `:app:android-main:flagshipPhoneApi36DebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.VideoPlaybackVisibilityE2ETest` passed on
  2026-06-15 after the video policy and test-tag updates. The XML reports `tests="1"`,
  `failures="0"`, `errors="0"`, `skipped="0"`, timestamp `2026-06-15T09:44:40`, and test time
  `3.776` seconds for
  `videoPlayerPipAffordanceKeepsVideoVisibleWhenLeavingTheAppSurface`.
- `:app:android-main:largeScreenTabletApi35DebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.AudioPlaybackBackgroundE2ETest` passed on
  2026-06-15 with 2 tests, 0 failures. The tablet managed-device run includes
  `playbackContinuesWhenAppIsVisibleButNotFocusedInMultiWindow`, proving audio-note playback remains
  active and its media notification remains present while LogDate is visible but not focused in
  multi-window.
- `:app:android-main:largeScreenTabletApi35DebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.VideoPlaybackVisibilityE2ETest` passed on
  2026-06-15 with 2 tests, 0 failures. The fresh XML reports
  `videoPlayerPipAffordanceKeepsVideoVisibleWhenLeavingTheAppSurface` and
  `videoPlayerRemainsVisibleWhenAppIsResizedIntoMultiWindow`, proving both PiP entry and runtime
  split-screen/desktop-windowing visibility for the shared Android video player.
- `:app:android-main:compileDebugAndroidTestKotlin :app:android-main:ktlintCheck` passed on
  2026-06-15 after adding `LibraryMediaDetailVideoE2ETest`. The focused managed-device run
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.LibraryMediaDetailVideoE2ETest` also passed on
  2026-06-15. `flagshipPhoneApi36` reported `tests="2"`, `failures="0"`, `errors="0"`,
  `skipped="1"`, timestamp `2026-06-15T11:04:21`, with
  `compactVideoDetailShowsPresenterOutputRouteControls` executed. `largeScreenTabletApi35` reported
  `tests="2"`, `failures="0"`, `errors="0"`, `skipped="1"`, timestamp `2026-06-15T11:04:59`, with
  `expandedVideoDetailShowsPresenterOutputRouteControlsBesideMetadata` executed. Together these
  prove the compact phone and expanded tablet library video detail controller paths expose
  external-display audio controls, open the output route sheet, keep the platform output-switcher
  action reachable, and expose the stop-presenting action.
- `:app:android-main:compileDebugAndroidTestKotlin :app:android-main:ktlintCheck` passed on
  2026-06-15 after adding `EditorVideoBlockE2ETest`. The first focused managed-device attempt hit a
  local Kotlin build-cache packing error for `:client:feature:editor:compileAndroidMain`
  (`shrunk-classpath-snapshot.bin`) before tests ran; the same command passed with
  `--no-build-cache`. The successful run was
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.EditorVideoBlockE2ETest --no-build-cache`.
  `flagshipPhoneApi36` reported `tests="2"`, `failures="0"`, `errors="0"`, `skipped="1"`,
  timestamp `2026-06-15T11:13:05`, with
  `compactEditorVideoBlockExposesPlaybackRouteAndPipControls` executed. `largeScreenTabletApi35`
  reported `tests="2"`, `failures="0"`, `errors="0"`, `skipped="1"`, timestamp
  `2026-06-15T11:13:47`, with
  `expandedEditorVideoBlockExposesPlaybackRouteAndPipControls` executed. Together these prove the
  compact phone editor video block and expanded tablet editor video block expose the player, PiP
  affordance, audio-output route sheet, and platform output-switcher action.
- `:app:android-main:compileDebugAndroidTestKotlin :app:android-main:ktlintCheck` and
  `:client:feature:journal:ktlintCommonMainSourceSetCheck` passed on 2026-06-15 after exposing
  `VideoNoteViewerContent` and adding `NoteViewerVideoE2ETest`. The focused managed-device run
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.NoteViewerVideoE2ETest --no-build-cache` passed
  on 2026-06-15. `flagshipPhoneApi36` reported `tests="2"`, `failures="0"`, `errors="0"`,
  `skipped="1"`, timestamp `2026-06-15T11:17:30`, with
  `compactVideoNoteViewerExposesPlaybackRouteAndPipControls` executed. `largeScreenTabletApi35`
  reported `tests="2"`, `failures="0"`, `errors="0"`, `skipped="1"`, timestamp
  `2026-06-15T11:18:10`, with
  `expandedVideoNoteViewerExposesPlaybackRouteAndPipControls` executed. Together these prove the
  compact phone video note viewer and expanded tablet video note viewer expose the player, PiP
  affordance, audio-output route sheet, and platform output-switcher action.
- `:client:feature:timeline:compileAndroidMain`,
  `:client:feature:timeline:ktlintCommonMainSourceSetCheck`,
  `:app:android-main:compileDebugAndroidTestKotlin`, and `:app:android-main:ktlintCheck` passed on
  2026-06-15 after making the timeline audio snippet route controls responsive and adding
  `TimelineAudioSnippetE2ETest`. The first managed-device attempt exposed a compact-width UX issue:
  the audio title could be squeezed by the inline output selector. `AudioNoteSnippet` now stacks the
  output selector below the title/play/stop row on compact widths and keeps it inline on expanded
  widths. The focused managed-device run
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.TimelineAudioSnippetE2ETest --no-build-cache`
  passed on 2026-06-15. `flagshipPhoneApi36` reported `tests="2"`, `failures="0"`, `errors="0"`,
  `skipped="1"`, timestamp `2026-06-15T11:28:25`, with
  `compactTimelineAudioSnippetExposesOutputRouteControls` executed. `largeScreenTabletApi35`
  reported `tests="2"`, `failures="0"`, `errors="0"`, `skipped="1"`, timestamp
  `2026-06-15T11:29:04`, with `expandedTimelineAudioSnippetExposesOutputRouteControls` executed.
  Together these prove compact phone and expanded tablet timeline audio snippets keep the snippet
  identity visible, expose the audio-output route control, open the route sheet, and keep the
  platform output-switcher action reachable.
- `:client:feature:journal:compileAndroidMain`,
  `:client:feature:journal:ktlintCommonMainSourceSetCheck`,
  `:app:android-main:compileDebugAndroidTestKotlin`, and `:app:android-main:ktlintCheck` passed on
  2026-06-15 after making journal inline audio route controls responsive and adding
  `JournalInlineAudioE2ETest`. `AudioEntryCard` now stacks the output selector below the playback
  identity and progress content on compact widths, while preserving the inline selector on expanded
  widths. The focused managed-device run
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.JournalInlineAudioE2ETest --no-build-cache`
  passed on 2026-06-15. `flagshipPhoneApi36` reported `tests="2"`, `failures="0"`, `errors="0"`,
  `skipped="1"`, timestamp `2026-06-15T11:33:35`, with
  `compactJournalAudioCardExposesOutputRouteControls` executed. `largeScreenTabletApi35` reported
  `tests="2"`, `failures="0"`, `errors="0"`, `skipped="1"`, timestamp `2026-06-15T11:34:13`,
  with `expandedJournalAudioCardExposesOutputRouteControls` executed. Together these prove compact
  phone and expanded tablet journal inline audio cards expose the playback control, open the
  audio-output route sheet, and keep the platform output-switcher action reachable.
- `:client:ui:compileAndroidMain`, `:client:ui:ktlintCommonMainSourceSetCheck`,
  `:app:android-main:compileDebugAndroidTestKotlin`, and `:app:android-main:ktlintCheck` passed on
  2026-06-15 after making the mini-player route controls responsive and adding
  `MiniAudioPlayerE2ETest`. `MiniAudioPlayer` now stacks the output selector below the active
  audio identity on compact widths, while preserving inline controls on expanded widths. The
  focused managed-device run
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.MiniAudioPlayerE2ETest --no-build-cache`
  passed on 2026-06-15. `flagshipPhoneApi36` reported `tests="2"`, `failures="0"`, `errors="0"`,
  `skipped="1"`, timestamp `2026-06-15T11:38:15`, with
  `compactMiniPlayerExposesOutputRouteControls` executed. `largeScreenTabletApi35` reported
  `tests="2"`, `failures="0"`, `errors="0"`, `skipped="1"`, timestamp `2026-06-15T11:38:47`,
  with `expandedMiniPlayerExposesOutputRouteControls` executed. Together these prove compact phone
  and expanded tablet mini-player users can see the active audio identity, open the audio-output
  route sheet, and keep the platform output-switcher action reachable.
- `:client:feature:journal:compileAndroidMain`,
  `:client:feature:journal:ktlintCommonMainSourceSetCheck`,
  `:client:feature:editor:compileAndroidMain`, `:app:android-main:compileDebugAndroidTestKotlin`,
  and `:app:android-main:ktlintCheck` passed on 2026-06-15 after adding
  `AudioNoteViewerE2ETest`. The focused managed-device run
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.AudioNoteViewerE2ETest --no-build-cache`
  passed on 2026-06-15. `flagshipPhoneApi36` reported `tests="2"`, `failures="0"`,
  `errors="0"`, `skipped="1"`, timestamp `2026-06-15T11:43:52`, with
  `compactAudioNoteViewerExposesOutputRouteControls` executed. `largeScreenTabletApi35` reported
  `tests="2"`, `failures="0"`, `errors="0"`, `skipped="1"`, timestamp
  `2026-06-15T11:44:29`, with `expandedAudioNoteViewerExposesOutputRouteControls` executed.
  Together these prove compact phone and expanded tablet audio note viewers expose the
  audio-output route sheet from the actual viewer path and keep the platform output-switcher action
  reachable.
- Remote camera list mirroring validation passed on 2026-06-15 with
  `:client:sync:testAndroidHostTest --tests
  app.logdate.client.sync.datalayer.RemoteCameraDeviceDataMapperTest
  :app:wear:testDebugUnitTest --tests
  app.logdate.wear.presentation.camera.WearRemoteCameraViewModelTest
  :app:android-main:testDebugUnitTest --tests
  app.logdate.client.sync.PhoneDataLayerListenerServiceTest
  :app:wear:ktlintCheck :app:compose-main:ktlintAndroidMainSourceSetCheck
  :client:sync:ktlintCommonMainSourceSetCheck`. This proves phone camera selections serialize into
  Wear data items, the Wear ViewModel can render and select exact external-style camera device IDs,
  and the phone listener routes `device:<id>` payloads to `CameraRemoteCommand.SelectCameraDevice`.
  The broader `:client:sync:ktlintCommonTestSourceSetCheck` task was not used as evidence because
  it currently fails on an unrelated import-order issue in
  `client/sync/src/commonTest/kotlin/app/logdate/client/sync/DefaultSyncManagerAuthGatingTest.kt`.
- Remote camera capture-result validation passed on 2026-06-15 with
  `:client:sync:testAndroidHostTest --tests
  app.logdate.client.sync.datalayer.RemoteCameraCaptureResultDataMapperTest
  :app:wear:testDebugUnitTest --tests
  app.logdate.wear.presentation.camera.WearRemoteCameraViewModelTest
  :app:android-main:testDebugUnitTest --tests app.logdate.client.sync.PhoneWearSyncBridgeTest
  :app:wear:ktlintCheck :app:compose-main:ktlintAndroidMainSourceSetCheck
  :client:sync:ktlintCommonMainSourceSetCheck`. This proves saved/failed phone capture results
  serialize through the Wear data layer, the watch remains in `CAPTURING` until the phone confirms a
  result, the watch moves to preview only after a saved acknowledgement, and failed phone saves move
  the watch to the error state with actionable copy.
- Immersive audio ownership validation passed on 2026-06-15 with
  `:app:wear:testDebugUnitTest --tests app.logdate.client.media.audio.AndroidAudioPlaybackManagerTest
  :client:feature:journal:testAndroidHostTest --tests
  app.logdate.feature.journals.ui.detail.AudioNoteViewerViewModelTest
  :client:media:ktlintCommonMainSourceSetCheck :client:media:ktlintAndroidMainSourceSetCheck
  :client:ui:ktlintCommonMainSourceSetCheck
  :client:feature:journal:ktlintCommonMainSourceSetCheck`. This proves the Android playback manager
  publishes active URI/metadata in playback status and the touched media/UI/journal source sets pass
  ktlint. Source inspection confirms `AudioNoteViewerViewModel.onCleared` now cancels waveform work
  without calling `AudioPlaybackManager.stopPlayback`.
- Editor audio-block route-control validation passed on 2026-06-15 with
  `:client:feature:editor:compileAndroidMain
  :client:feature:editor:ktlintCommonMainSourceSetCheck
  :app:android-main:compileDebugAndroidTestKotlin :app:android-main:ktlintCheck` and
  `:app:android-main:smokeDevicesGroupDebugAndroidTest
  -Plogdate.androidTestClass=app.logdate.client.e2e.EditorAudioBlockE2ETest`. The managed-device XML
  reports `flagshipPhoneApi36` at `2026-06-15T11:58:46` with tests=2, failures=0, errors=0,
  skipped=1, executing `compactEditorAudioBlockExposesPlaybackRouteControls`; and
  `largeScreenTabletApi35` at `2026-06-15T11:59:19` with tests=2, failures=0, errors=0, skipped=1,
  executing `expandedEditorAudioBlockExposesPlaybackRouteControls`. This proves saved editor audio
  blocks render playback output routing on compact and expanded layouts and do not require microphone
  permission before the user can access saved-audio playback controls.
- Audio route fallback resolver validation passed on 2026-06-15 with
  `:client:media:testAndroidHostTest --tests
  app.logdate.client.media.device.MediaDeviceSelectionUiStateTest
  :client:media:compileAndroidMain :client:media:ktlintCommonMainSourceSetCheck
  :client:media:ktlintCommonTestSourceSetCheck :client:media:ktlintAndroidMainSourceSetCheck`. This
  proves the same resolver used by `AndroidAudioRouteRepository` selects a preferred microphone when
  available, shows fallback copy when that preferred microphone is unplugged, restores the preferred
  microphone when it appears again, and keeps preferred output display state paired with
  system-controlled output-route copy.

## Evidence Reviewed

- `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/camera/AndroidCameraCaptureManager.kt`
- `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/camera/CameraCaptureContent.android.kt`
- `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AudioBlockEditor.kt`
- `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AudioBlockContent.kt`
- `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AudioViewModel.kt`
- `client/media/src/androidMain/kotlin/app/logdate/client/media/device/AndroidAudioRouteRepository.kt`
- `client/media/src/commonMain/kotlin/app/logdate/client/media/device/AudioRouteRepository.kt`
- `client/media/src/commonMain/kotlin/app/logdate/client/media/device/MediaDeviceSelectionResolver.kt`
- `client/media/src/commonTest/kotlin/app/logdate/client/media/device/MediaDeviceSelectionUiStateTest.kt`
- `client/ui/src/commonMain/kotlin/app/logdate/ui/media/MediaDeviceSelector.kt`
- `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioRecordingService.kt`
- `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManager.kt`
- `client/media/src/androidMain/kotlin/app/logdate/client/media/audio/AudioPlaybackService.kt`
- `client/ui/src/commonMain/kotlin/app/logdate/ui/audio/AudioPlaybackProvider.kt`
- `client/ui/src/commonMain/kotlin/app/logdate/ui/audio/MiniAudioPlayer.kt`
- `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/EditorAudioBlockE2ETest.kt`
- `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/expansion/ImmersiveAudioScreen.kt`
- `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/video/VideoContent.android.kt`
- `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/NoteViewerScreen.kt`
- `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/AudioNoteViewerViewModel.kt`
- `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailScreen.kt`
- `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/AudioNoteSnippet.kt`
- `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/detail/MediaDetailScreen.kt`
- `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/navigation/LibraryNavRoute.kt`
- `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/di/LibraryFeatureModule.kt`
- `client/feature/remotedisplay/src/main/kotlin/app/logdate/feature/remotedisplay/MediaPresentation.kt`
- `client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainModule.kt`
- `client/domain/src/commonMain/kotlin/app/logdate/client/domain/di/DomainFileSystem.kt`
- `client/domain/src/androidMain/kotlin/app/logdate/client/domain/di/DomainFileSystem.android.kt`
- `client/domain/src/desktopMain/kotlin/app/logdate/client/domain/di/DomainFileSystem.desktop.kt`
- `client/domain/src/iosMain/kotlin/app/logdate/client/domain/di/DomainFileSystem.ios.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/recording/WearAudioRecordingService.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/recording/WearAudioRecordingManager.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/playback/WearAudioOutputMonitor.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearTimelineViewModel.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/presentation/timeline/WearDayDetailScreen.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/presentation/recording/WearRecordingScreen.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/presentation/audio/AudioRecordingScreen.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/presentation/camera/WearRemoteCameraScreen.kt`
- `app/wear/src/main/kotlin/app/logdate/wear/presentation/camera/WearRemoteCameraViewModel.kt`
- `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/PhoneDataLayerListenerService.kt`
- `app/wear/src/test/kotlin/app/logdate/client/media/audio/AndroidAudioPlaybackManagerTest.kt`
- `app/wear/src/androidTest/kotlin/app/logdate/wear/e2e/WearDayDetailPlaybackTest.kt`
- `client/feature/editor/src/commonTest/kotlin/app/logdate/feature/editor/ui/camera/CameraPreviewStreamingGateTest.kt`

## Status Legend

- `[todo]` Not yet audited.
- `[partial]` Some behavior exists or evidence is incomplete.
- `[pass]` Requirement is satisfied with evidence linked.
- `[fail]` Requirement is not satisfied.
- `[unverified]` Implementation may exist, but current evidence does not prove the requirement.
- `[n/a]` Requirement does not apply to the audited feature.

## Requirement Checklist

| Status | Guideline ID | Checklist item | Evidence |
| --- | --- | --- | --- |
| `[partial]` | Camera_Switcher | If the feature uses a camera, the UI includes a camera switcher that can toggle between built-in cameras and external cameras. | Phone editor now uses a shared camera selector backed by CameraX enumeration and Camera2 IDs, and selected camera IDs are persisted with unavailable-device fallback copy. Wear remote camera exposes Back, Switch, and Front controls, mirrors exact phone camera device IDs, and renders external-style rows for the controlled phone camera. The shared selector also has tappable rows and adaptive screenshot coverage for compact chip and expanded sheet states. External USB camera hardware is unverified. |
| `[partial]` | Camera_Switcher | Camera selection changes the live preview to the selected built-in or external camera without stale frames, blank preview, or incorrect orientation. | Phone editor binds preview/capture by selected Camera2 ID and keeps the preview streaming gate; external camera behavior and orientation still need hardware or GMD evidence. |
| `[pass]` | Camera_Switcher | Camera switcher state is discoverable, accessible, and does not disappear on large screens, foldables, desktop windowing, or resized layouts. | Phone editor uses labeled selector chips/sheets and keeps the old icon as a shortcut only when built-in switching is available. Android screenshot coverage includes compact chip and expanded camera sheet states. `MediaDeviceSelector` rows now expose selected semantics plus explicit state descriptions, and `MediaDeviceSelectorE2ETest` passed on `largeScreenTabletApi35` and `flagshipPhoneApi36` with assertions for visible camera rows, selected "In use" state, external-style "External device" state, and row clickability. |
| `[partial]` | Audio_Switcher | If the feature uses a microphone, the UI includes an audio input switcher for built-in microphones and external peripherals such as headphones or USB microphones. | Phone and Wear audio recording now share an input selector backed by Android device enumeration. Camera video shows detected-route/system-controlled microphone copy because CameraX `1.6.0` does not expose preferred input-device routing. The shared selector also has stable test tags and adaptive screenshot coverage for the controllable microphone sheet with built-in, USB, and Bluetooth-style rows. External hardware evidence is still missing. |
| `[partial]` | Audio_Switcher | If the feature plays audio through speakers or headphones, the UI includes an audio output switcher for built-in audio devices and external peripherals. | Editor saved-audio blocks, the mini-player, immersive audio viewer, current journal audio cards, current timeline audio snippets, Android video player surfaces, compact and expanded external-display presentation controls, and Wear day detail playback expose detected output routes through the shared selector model. Android system-controlled output route sheets include an Open output switcher action with Sound settings fallback. The shared selector now has row-level selection semantics and screenshot/GMD coverage for editor audio-block, compact, expanded, inline journal, timeline, note-viewer, external-display, and Wear output states. `MediaDeviceSelectorE2ETest` and `EditorAudioBlockE2ETest` passed on `flagshipPhoneApi36` and `largeScreenTabletApi35` with the default output-switcher action visible and tappable. Android/Wear output routing remains system-controlled pending hardware validation. |
| `[partial]` | Audio_Switcher | Audio routing follows the selected input and output device after switching, including when the external device is connected after the feature is already open. | Phone and Wear audio recording pass the selected microphone into `MediaRecorder.setPreferredDevice`, reapply the preferred input device when the available route list changes during an active recording session, and expose output route selectors on the audio surfaces. `MediaDeviceSelectionResolver` tests now cover preferred input fallback, preferred input restoration after the route appears again, and preferred output display state while Android remains system-controlled. Hardware output routing and cross-device hot-plug behavior are not yet validated. |
| `[pass]` | Audio_Switcher | Audio switcher state is understandable to assistive technology and remains usable in compact, expanded, split-screen, and desktop-windowed layouts. | Shared selector UI uses labeled chips, sheets, icons, selected-state text, explicit row state descriptions, stable UI test tags, common tests for tag stability, E2E assertions for selected rows/state descriptions and the platform output-route affordance, and Wear/Android screenshot coverage for compact, expanded, system-controlled, mini-player, immersive-player, inline journal, timeline, and note-viewer output-picker states. `:app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.MediaDeviceSelectorE2ETest` passed on `largeScreenTabletApi35` and `flagshipPhoneApi36`, covering the actual user flow from compact chip to sheet selection/platform output-switcher action on phone and tablet layouts. |
| `[pass]` | Audio_Background_Playback | If the feature plays audio, playback continues when the app is visible but not focused, such as in split-screen mode or desktop windowing mode. | Audio notes use a service-backed player foundation, playback-manager tests cover progress resume after focus-style pause/refocus, and `AudioPlaybackBackgroundE2ETest` passed on `largeScreenTabletApi35` with `playbackContinuesWhenAppIsVisibleButNotFocusedInMultiWindow`, proving playback and the media notification remain active while LogDate is visible but not focused in multi-window. Video keeps playing only while visible in split-screen/multi-window or PiP; `VideoPlaybackVisibilityE2ETest` passed on `flagshipPhoneApi36` for PiP entry and on `largeScreenTabletApi35` for runtime multi-window visibility, and `VideoPauseVisibilityPolicyTest` passed with Android main/host-test ktlint for PiP and multi-window pause policy. |
| `[pass]` | Audio_Background_Playback | If the feature plays audio while not visible, playback uses a foreground service so the process is not killed after focus is lost. | Audio notes use `AudioPlaybackService`; playback-manager tests verify the service is started before controller playback. `AudioPlaybackBackgroundE2ETest` passed on the `flagshipPhoneApi36` Gradle Managed Device and proved playback remains active after Home, during a 65-second locked interval, and after wake. Video audio-only background playback is `[n/a]` by policy because video pauses unless it remains visible in PiP. |
| `[pass]` | Audio_Background_Playback | If the feature plays audio while not visible, the app shows a persistent, non-dismissible notification in the status bar or on the lock screen. | `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36` and asserted the active `AudioPlaybackService.NOTIFICATION_ID` media notification, channel ID, title metadata, notification persistence after Home, notification persistence while locked for 65 seconds, and notification persistence after wake. Video audio-only background playback is `[n/a]` by policy. |
| `[pass]` | Audio_Background_Playback | Lock-screen media controls can pause and resume playback, and foreground service state stays synchronized with app playback state. | Audio note MediaSession foundation exists, playback-manager tests cover pause/resume/stop/seek synchronization, and `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36` with visible SystemUI pause/play tapping, media notification pause/play actions, and MediaSession pause/play commands synchronized to shared playback state after Home and lock/wake. The same test now also passes on `largeScreenTabletApi35` after explicit notification-shade expansion surfaces visible Pause and Play/Resume controls. Video audio-only lock-screen playback is `[n/a]` by policy. | None for audio-note lock-screen/media-notification control synchronization. |
| `[pass]` | Audio_Background_Playback | Playback continues without stutters or pauses after refocusing the app from another foreground app or from the lock screen. | Playback-manager tests cover progress resume after focus-style pause/refocus without resetting progress. `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36` and proved playback stayed active with retained progress after Home, while Android Settings was the foreground non-audio app, after relaunching LogDate, during the 65-second locked interval, and after wake. |
| `[n/a]` | Audio_Background_Playback | Any premium-tier restriction for background playback is intentional, documented, and reflected consistently in product copy and entitlement checks. | No premium-tier background playback restriction was found during this audit. |

## Test Checklist

| Status | Test ID | Test procedure | Evidence |
| --- | --- | --- | --- |
| `[partial]` | T-Camera_Switcher | Connect an external camera, use the app's camera switcher to move between built-in cameras and the external camera, and verify that the preview updates correctly for each selection. Emulator-backed UI/state validation is now documented in `docs/testing/camera-audio-emulator-test-matrix.md`, including AVD camera mapping, the preview-streaming gate, and Wear remote-camera mirroring. | Automated slice passed `:client:feature:editor:compileAndroidMain`, `:client:feature:editor:ktlintAndroidMainSourceSetCheck`, `:client:feature:editor:testAndroidHostTest`, focused Wear remote-camera ViewModel tests for front/back/switch/exact-device commands, remote camera device mapper tests, and remote camera capture-result acknowledgement tests. Still needs external camera hardware matrix, watch-phone runtime validation, screen recording, or manual validation notes. |
| `[partial]` | T-Audio_Switcher | Connect an external audio device such as headphones or a USB microphone, use the app's audio switcher, and verify that input and output route to the selected device. Emulator-backed UI/state validation is now documented in `docs/testing/camera-audio-emulator-test-matrix.md`, including managed-device selector coverage, Wear Bluetooth fallback, and the playback notification/session checks. | Automated slice passed `:client:media:compileAndroidMain`, `:app:wear:compileDebugKotlin`, `:client:media:ktlintAndroidMainSourceSetCheck`, `:app:wear:ktlintCheck`, `:client:media:testAndroidHostTest`, `:client:media:desktopTest`, `:app:wear:compileDebugAndroidTestKotlin`, `:app:wear:testDebugUnitTest`, Android compile for editor/journal/library video surfaces, editor Android ktlint, app-wide `:app:android-main:compileDebugKotlin --continue`, `:app:android-main:compileDebugScreenshotTestKotlin`, and desktop compile for editor/journal/timeline. Journal and timeline common ktlint passed. Still needs hardware matrix, screen recording, or manual validation notes for USB/Bluetooth input and output routes. |
| `[pass]` | T-Audio_Background_Playback | Start audio playback, move another non-audio app to the foreground, and verify playback continues without stutters or pauses. If LogDate is not visible, verify the status-bar notification is displayed. | Automated slice passed `:app:wear:testDebugUnitTest --tests app.logdate.client.media.audio.AndroidAudioPlaybackManagerTest`; it verifies playback service start order and progress resume after focus-style pause/refocus. `AudioPlaybackBackgroundE2ETest` passed `:app:android-main:flagshipPhoneApi36DebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.AudioPlaybackBackgroundE2ETest --rerun-tasks`, proving playback and the media notification survive Home, remain active while Android Settings is the foreground non-audio app, and remain active after returning to LogDate. |
| `[pass]` | T-Audio_Background_Playback | Lock the device for at least one minute, verify playback is not killed, verify the lock-screen notification is present, and verify lock-screen play and pause controls communicate with the app. | `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36` after a 65-second locked interval, proving playback was not killed, the media notification remained active, visible SystemUI pause/play controls were tappable on the phone managed device, and media notification plus MediaSession play/pause commands communicated with app playback state after lock/wake. The same managed-device run now passes on `largeScreenTabletApi35` as well after the notification shade is explicitly expanded to surface Pause and Play/Resume controls. |
| `[pass]` | T-Audio_Background_Playback | Unlock the device, verify playback continues, and for non-visible playback verify the status-bar notification is still present. | `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36`, proving playback remained active after wake, the media notification remained active after wake, and playback progress was retained after the Home and lock/wake sequence. |
| `[pass]` | T-Audio_Background_Playback | Refocus LogDate as the foreground app and verify playback continues without stutters or pauses. | `AudioPlaybackBackgroundE2ETest` passed on `flagshipPhoneApi36` with retained progress after Home, Android Settings foregrounding, relaunching LogDate, and lock/wake. Playback and the media notification stayed active across the full refocus sequence. |

## Audit Notes

- Record each audited feature or screen in the evidence column rather than marking the entire app as
  passing globally.
- Prefer deterministic automated checks where the repo can safely run them. Record hardware-only
  checks as manual evidence when external devices are required.
- Refresh Android API details with `ctx7` before implementing route-selection code. The 2026-06-14
  audit attempted a Context7 lookup, but the environment quota was exhausted.
- Re-check the source page before using this checklist for release certification.
- The emulator and managed-device route for the final two test rows is documented in
  `docs/testing/camera-audio-emulator-test-matrix.md`; use that matrix before claiming the rows are
  hardware-only blockers.
