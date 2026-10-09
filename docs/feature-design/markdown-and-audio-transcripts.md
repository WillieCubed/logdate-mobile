# Markdown and durable audio transcripts

Markdown source remains the stored text. The shared JetBrains syntax-tree adapter drives source
styling, full reading, captions, and compact previews. Swift Markdown 0.9.0 drives the corresponding
native Apple adapter. Keep `test-fixtures/markdown-parity.json` byte-identical to the native Apple
repository's `LogDateTests/markdown-parity.json`. Its 17 cases check Unicode UTF-16 source ranges,
structure, formatting, line breaks, lists, tasks, tables, code indentation, and readable unsupported
syntax. Rendering changes must preserve the source and editor selection/undo behavior.
Text direction follows each paragraph's content in RTL layouts. The audio transport retains
elapsed-before-total ordering while its surrounding controls follow the layout direction.

## Audio record contract

Original media, authored captions, and structured transcripts are separate fields. A transcript
retains revision, document status, language, engine metadata, speakers, segments, word timing,
confidence, provenance, and finality where available. A valid FINAL document with no segments
represents completed no-speech recognition and suppresses automatic regeneration.

The optional wire field is an encrypted JSON string, bound to
`sync:note:<lowercase-note-id>:transcript`. Missing fields on older-client updates preserve the
existing transcript when the original media reference and duration are unchanged. Replacing the
audio reference or duration without a new transcript clears the old document so clients generate
one for the replacement.
Existing content version checks still apply. Server migration V35 adds storage;
deploy this backward-compatible support before enabling generation in newly released clients.

The local database is version 51. Migration 50 to 51 associates durable transcription state with
its source media URI and duration. A different URI or duration invalidates ownership and queues new
work; remote-to-local URI hydration atomically rebinds an existing transcript. This identity assumes
media references do not silently change their bytes in place. A future mutable-media API must supply
an explicit immutable revision or digest.

## Generation and persistence

Application startup scans saved audio newest first, and observed recording/import/sync changes
ensure missing transcripts. Newly saved and visible audio move ahead of queued recovery work;
active recognition retains its ownership. Android API 31 and later use same-identity WorkManager
expedited updates with ordinary-work fallback when quotas apply. API 30 keeps ordinary work
schedulable without introducing a foreground notification requirement. Interrupted jobs recover;
failed jobs can be retried. Jobs capture the
note, media identity, transcript revision, and work identity before recognition. Completion checks
those values again inside the persistence transaction. Deleted notes and superseded jobs cannot be
recreated or overwrite newer documents. Final empty documents are terminal.

Android uses its existing recognition engine. The shared file scheduler persists successful Desktop
and Compose iOS recognition results, with serialized recognition and cancellation ownership. Native
Apple uses an app-owned SpeechAnalyzer implementation behind a replaceable transcriber interface.

Transcript persistence and its sync outbox mutation share a Room transaction. The transcript path
does not acquire the metadata service mutex while holding the database writer. Every changed
transcript gets a fresh outbox operation identity, while an existing CREATE, queue age, and expected
server version are retained. An older upload acknowledgment cannot clear the newer transcript;
scoped and legacy DELETE operations are preserved. A transcript generated during the first upload
must progress from CREATE to a versioned UPDATE after successful or lost acknowledgment, without
settling the newer transcript mutation or erasing real caption/media conflicts.
When a transcript changes during an UPDATE, its pending server-version base advances atomically
after a confirmed upload to the same account and server, before the note's sync metadata is written.
The newer operation identity and deletion guards remain intact.

Search reads transcript text alongside captions. Backup/archive export and restore retain the full
document, including empty FINAL documents and speaker/engine metadata.

## Verification and rollout

Run affected database, data, media, sync, domain, UI, editor, server, and integration suites, touched
Kotlin lint/detekt checks, Android app/test APK builds, and affected iOS compilations. The native
Apple repository supplies `verify-server-client.py` using this repository's
`:integration:server-client-e2e:writeAndroidHistoryHarnessClasspath`. It starts a disposable loopback
server and verifies encrypted Kotlin-Swift documents and audio, omission preservation, offline
reopen, transcript search, reconnect uploads, and conflict resolution with synthetic data.

`MarkdownAudioParityScreenshotsTest` uses real synthetic spoken audio, decoded waveforms, timed
transcripts, playback and scrubbing. Use Android emulators or Gradle Managed Devices only. Inspect populated phone and
tablet captures in light, dark, RTL, and large text after geometry/control changes. Keep native
editor/recording presentation gated until final native window/capture/motion acceptance, matching
platform comparisons, and the server rollout are complete. Unit tests and screenshots alone do not
prove microphone ownership or interruption recovery in the running application.

The captured component matrix passes on Managed Device phone portrait and tablet landscape in
light, dark, RTL, and 200% text. Rendered tests cover mixed inline formatting without extra
paragraphs, authored line breaks, caption layout, transcript punctuation, and elapsed/total order.
Book/tabletop, dynamic colors, Reduced Motion, full application navigation, and live recognition
provider/visibility-priority acceptance remain pending alongside the native runtime checks.
