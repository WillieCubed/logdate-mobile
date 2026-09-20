# LogDate Data Export

An export is a single `.zip` file that contains a copy of what LogDate stores for a person. This document
explains what is in it, how to read it, and how the app builds it. It describes two layouts:

- **Format 2.0**, the current format written by Android, desktop, iOS and LogDate Cloud backups.
- **Format 1.x**, the legacy import-only layout. Restore continues to read versions 1.0 through 1.2.

If you only want to open an export, read [Reading an archive](#reading-an-archive). If you are
changing what gets exported, read [Adding a new kind of data](#adding-a-new-kind-of-data).

## What an export has to be

An export is the person's data leaving the app, so it has to work without the app. Format 2.0 is
designed around these promises:

| Promise | What it means in practice |
|---|---|
| **Self-contained** | Nothing needs the app, an account, the network, or another file. The documentation, the schemas and the checksums are inside the zip, and every link inside it points at a file that is also inside it. |
| **Honest about completeness** | If something is not in the export, the export says so and says why. It never looks complete when it is not. |
| **Readable by a person** | A README explains the archive in plain language. File names describe themselves. |
| **Readable by a program** | Every data file is JSON with a published schema. Times are RFC 3339 in UTC, ids are UUIDs, text is Markdown. |
| **Free of device internals** | No content URI, file path, original file name, sync counter or device id is ever written. A file is only ever referred to by its place inside the archive. |
| **Verifiable** | `SHA256SUMS` lets anyone confirm that no file was damaged. |

## Layout of a 2.0 archive

All files sit at the top level of the zip. Readers should also accept an archive that was unzipped and
zipped again inside one folder, as Finder and Explorer do; restore already does this for 1.x.

```
README.txt                  Plain-language guide.
manifest.json               What this archive is and contains. Programs start here.
schema/                     JSON Schemas (draft 2020-12), one per data file.
data/
  journals.json             The journals.
  notes.json                Every entry: text, photos, videos and voice notes.
  drafts.json               Unfinished drafts from the older draft store (see Completeness).
  places.json               Saved places (only when there are any).
  profile.json              The profile (only when one was filled in).
  location-history.jsonl    One location sample per line (only when there are any).
  media.json                Every media file with its type, size and SHA-256.
media/
  photos/2026/2026-09-17_21-30-05.jpg
  videos/…    audio/…       Named for when they were captured, in local time.
SHA256SUMS                  Last file. A checksum for every other file.
```

`README.txt`, `manifest.json` and `SHA256SUMS` are the only fixed names outside `data/`, `schema/` and
`media/`.
Readable copies of the journal (Markdown and HTML pages) are planned as a separate layer under
`journal/`; the manifest already has a role for them, and the README describes them only when they
are present.

### Why the files are in this order

The media is written first. The README, the manifest, the data files and the media inventory come
after it, and `SHA256SUMS` is written last. Nothing depends on the order to be read, because zip
readers use the central directory at the end of the file, and Finder and Explorer list the top-level
files alphabetically.

The order exists because the README, the manifest and the notes must state exactly which media files
the archive contains. A media file can become unreadable between the moment the export plans it and the
moment it is copied, for example when the person deletes a photo or a cloud provider removes its local
copy. Because the files that describe the archive are written after the copy, that file is reported as
omitted and the export still finishes. A file that fails halfway through being read cannot be taken
back out of the zip, so that case ends the export with an error.

## Reading an archive

### As a person

Open `README.txt`. It says what each folder is, what is missing and why, how times are shown, and how
to check the files. Photos, videos and voice recordings are in `media/`, named for when they were
captured. Everything in `data/` is plain text that any editor opens, but it is written for programs.

### As a program

1. Read `manifest.json`. Check `format` is `logdate-export` and `schemaVersion` starts with `2.`
   (see [Versioning](#versioning)).
2. Use `contents` to find each file. Each entry gives the file's `role`, its `path`, its
   `mediaType`, and the `schema` that describes it. Never guess a name.
3. Validate a file against its schema if you want to. The schemas are permissive about extra keys,
   because a minor version only adds fields.
4. Read `scope` before trusting the data to be everything. See [Completeness](#completeness).

LogDate restore follows the same rule: it detects 2.0 through `manifest.json` and resolves every data
file by its manifest role, so valid custom paths are supported. If there is no manifest, restore falls
back to the 1.x `metadata.json` layout. Both formats may be at the ZIP root or inside one wrapper
directory. Every manifest-listed data file must exist before restore starts writing to repositories.

Restore currently applies journals, notes and their memberships, drafts, profile, saved places,
location history and portable media references. Included media is copied through the platform media
importer. A media record that was omitted or is unavailable is reported as a warning and its note or
draft attachment is skipped; a source-device path is never retained as a fallback.

### Conventions in every data file

- **Keys** are `camelCase`. A key that is not in a record means the value is not known; `null` is never
  written.
- **Times** are RFC 3339 in UTC with a trailing `Z`, such as `2026-09-18T03:30:05.123Z`.
- **Ids** are lowercase hyphenated UUIDs.
- **Text** is stored exactly as the person wrote it. `textFormat` says how to read it; today it is
  always `markdown`.
- **Files** are referred to by an archive path: relative, forward slashes, no drive letter or URI
  scheme, no `..`. A path that breaks these rules makes the file unreadable, since an archive is
  untrusted input.
- **Coordinates** are decimal degrees with named `latitude` and `longitude` keys, never a pair whose
  order has to be remembered.

### Notes

`data/notes.json` contains `{"notes": [...]}`. Each note has an `id`, a `type` (`text`, `image`, `video` or
`audio`), `createdAt` and `updatedAt`, and:

- `text` and `textFormat` for text notes, `caption` for photos and videos, `durationMs` for audio.
- `location`, when one was recorded.
- `journalIds`: the journals the note appears in. A note can be in several journals or in none, so
  membership lives on the note.
- `media`: where the file is. See below.
- `timeZone` and `createdAtLocal`: see below.

**Media.** `media.status` is `included` or `omitted`. An included file has a `path` and a `mediaType`.
An omitted file has an `omittedReason` and no path, so nothing ever points at a file that is not
there. `data/media.json` lists every included file with its size and SHA-256.

**Time zones.** A note stores an instant in UTC. When the device recorded the zone the note was written
in, the note also has `timeZone` (an IANA name) and `createdAtLocal`, the same moment as local time
with its offset. A note that was written before zones were recorded has neither; the export does not
guess. The readable README and any readable copy show such notes in `exportTimeZone`, the zone the
export was made in, which is what the app's timeline shows too.

## Completeness

`manifest.scope` answers whether the archive is everything:

- `complete` is true only when nothing the app stores is left out and no date range was applied.
- `dateRange` is present when the export was limited by date.
- `omitted` lists each missing category with a reason: `notRequested` (the person left it out),
  `outsideDateRange`, `unreadable` (it could not be read while exporting) or `notYetSupported` (this
  version of the app does not export it yet).

The README shows the same list in words. While a category is `notYetSupported`, `complete` is false,
so an export never claims more than it contains. The kinds of data not yet exported are transcripts,
audio sound labels, people, events, rewinds, postcards, stickers, health readings, favorites, journal
cover photos, the profile photo, app settings and drafts from the entry editor. The last one matters:
`drafts.json` comes from the older draft store, so drafts a person has open in the editor are not in
the archive yet.

`ExportCoverageTest` in `client/database` enforces this from the other side: every Room table must be
classified as exported, planned, derived or bookkeeping, so a new kind of data cannot be added to the
app without deciding what happens to it in the export.

## Versioning

`schemaVersion` is `major.minor`.

- A **minor** change only adds optional fields or new enum values. Readers ignore keys they do not
  know, so an older reader still reads a newer minor version.
- A **major** change renames, removes or retypes something, or changes the layout. A reader that
  supports major `N` refuses major `N + 1` instead of guessing.

Format 2.0 is a major change from 1.x: the files, the field names, the media paths and the way media is
linked all differ. Restore can still read 1.x archives.

## Verifying files

`SHA256SUMS` has one line per file, `<sha256>  <path>`, sorted by path, in the format that
`shasum -a 256 -c` and `sha256sum -c` read:

```bash
shasum -a 256 -c SHA256SUMS
```

It lists every file except itself. It shows that files are unchanged since the export, not who made
them.

## How the app builds an archive

The code is in `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/`.

`ExportArchiveUseCase` works in two passes.

1. **Read and resolve.** `ArchiveSnapshotReader` reads the app's data once, so the archive describes one
   moment. `MediaResolver` then opens every media file the entries refer to, works out what it is from
   its first bytes, gives it a path, and records whether it is included or why it is not. Because this
   finishes before anything is written, the manifest, the README and every note can state exactly what
   is and is not in the archive.
2. **Write.** `ArchiveWriter` copies the media into an `ArchiveContainer`, then writes the README, the
   manifest, the schemas and the data files. A media file that cannot be opened at this point is dropped
   from the plan and reported as omitted. Every byte passes through a hashing sink into a `HashLedger`,
   so no whole file is loaded into memory, and `SHA256SUMS` and `data/media.json` are both drawn from the
   same ledger and cannot disagree. The work runs on the IO dispatcher because zip writes, hashing and
   media reads block.

The pieces that stop device internals from entering the archive:

- `ArchivePath` is the only way a file is referred to. It cannot contain a URI, an absolute path or `..`.
- The archive's records (`ArchiveNote`, `ArchiveJournal` and so on) are their own types, never the
  app's domain classes, so a change inside the app cannot leak into the archive.
- Media is named by `MediaFileNamer` from its capture time, in the zone it was captured in, filed by
  kind and year. Repeats get `_2`, `_3`. The type comes from `MediaTypeSniffer`, which reads magic
  bytes, because a content URI has no extension and an extension can be wrong.

Each platform supplies two things the archive cannot know: a `MediaSourceOpener`, which opens whatever
reference that platform stored, and an `ArchiveContainer`, which puts files in a zip. The Android worker
and the desktop launcher use `ZipStreamArchiveContainer`. If an export fails or is cancelled, the
launcher deletes the partly written file.

### Current writing behavior

Format 2.0 is the only format newly written. Android and desktop stream it into a ZIP. iOS stages
entries in temporary files before assembling and sharing the ZIP, and removes staging data after
success, cancellation or failure. LogDate Cloud uploads the same v2 ZIP bytes and stores the exact
`manifest.json` extracted from that artifact. Cloud restore remains format-neutral, so downloaded
1.x and 2.0 backups use the shared reader.

## Adding a new kind of data

1. Add a read API the export use case can call. Domain code cannot reach DAOs, so data that only a DAO
   exposes needs a repository interface, an implementation in `client/data`, and DI on each platform.
2. Add a record type in `ArchiveData.kt` and a schema for it in `ArchiveSchemas`. The tests validate a
   maximal sample against the schema and against a strict copy, so a field the schema forgets fails.
3. Map it in `ArchiveRecords.kt`, write it in `ExportArchiveUseCase`, and add it to the manifest's
   `contents`.
4. Remove its category from `ArchiveCoverage.notYetSupported` and move its table from planned to
   exported in `ExportCoverageTest`.
5. Add its label to the README template.
6. If media is involved, ask the `MediaResolver` for it instead of writing a path.

## The 1.x layout

Format 1.x is retained for import compatibility only; the app never creates a new 1.x export. Its
files are at the top level:

`metadata.json`, `journals.json`, `notes.json`, `journal_notes.json`, `drafts.json`, and when present
`profile.json`, `places.json`, `location_history.json`, `media_manifest.json` and `export_issues.txt`,
plus a flat `media/` folder named by note id. Its main problems are the reason for 2.0: media is
linked by the raw path the device stored, there is no README or checksum, and the current editor's
drafts are not included.

## Checking the implementation

```bash
./gradlew :client:domain:jvmTest --tests 'app.logdate.client.domain.export.archive.*'
./gradlew :client:database:jvmTest --tests 'app.logdate.client.database.ExportCoverageTest'
./gradlew :client:domain:iosSimulatorArm64Test --tests 'app.logdate.client.domain.export.archive.*'
```

`ExportArchiveUseCaseTest` builds an archive from fixtures that contain planted device references and
checks that none reach the output, that every link resolves, that the checksums match the written
bytes, and that cancellation stops the export. `ExportArchiveConformanceTest` validates every file in a
real archive against the schema stored in that same archive.
