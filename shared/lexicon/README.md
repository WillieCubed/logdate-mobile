# shared/lexicon

LogDate's data models, generated from its lexicon schemas.

Every kind of record LogDate stores, such as a journal entry, a place, a person or a share, is
defined once as a [Lexicon](https://atproto.com/specs/lexicon) schema in
[`lexicons/`](../../lexicons). This module turns those schemas into Kotlin classes that build for
Android, iOS and the desktop JVM, so the apps and the server can work with one definition of the
data instead of several hand-written copies.

## Nothing to run by hand

The models aren't checked in. Every build generates them from `lexicons/` into
`build/generated/lexicon/kotlin` before compiling, so a schema change shows up in the code the
next time anything builds. An invalid schema fails the build with a message naming the schema
and the problem.

The `app.logdate.lexicon-models` plugin from `build-logic` does this. Any Kotlin Multiplatform
module can generate models the same way by applying it and naming its schemas in a
`lexiconModels { }` block; see `LexiconModelsPlugin` for the options.

To look at the generated code, build the module and open that folder, or jump to a class from
your IDE once Gradle has synced:

```bash
./gradlew :shared:lexicon:generateLexiconModels
```

## How the schemas map to Kotlin

| Schema | Kotlin |
|---|---|
| `app.logdate.journal.entry` | package `app.logdate.lexicon.app.logdate.journal`, class `Entry` |
| a definition `#textBlock` in that document | class `EntryTextBlock` |
| `community.lexicon.location.geo` | package `app.logdate.lexicon.community.lexicon.location`, class `Geo` |
| a `record` | a data class with `TYPE` (its collection) and `KEY` (its record-key rule) |
| a `union` | a sealed interface with one wrapper per allowed type, plus `Unknown` if the union is open |
| `blob`, `cid-link`, `bytes` | `Blob`, `CidLink`, `Bytes` |
| an optional field | a nullable property that defaults to `null` |

`LogDateRecords.records` maps every record type's collection NSID to its serializer, for code that
reads records whose type it only knows at runtime, such as sync.

Encode and decode with `LexiconData`: its `json` configuration for JSON, and
`encodeToDagCbor` / `decodeFromDagCbor` for the AT Protocol's binary encoding.

## Tests

- Every example record in `tools/lexicon-check/examples/` decodes into its model and encodes back
  unchanged, through JSON and DAG-CBOR.
- `LogDateRecords` lists every record schema.
- A journal entry round-trips on every platform.

The generator itself lives in the AT Protocol library, `shared/atproto-lexicon`.
