# Lexicon code generation: records, unions and media

Part of the Lexicon toolchain project in the LogDate on the AT Protocol initiative. This design
covers the first toolchain task: teaching the AT Protocol library's schema tools to read, check
and generate Kotlin for records, unions, blobs, content links and bytes.

## Why this comes first

LogDate's schema family (`lexicons/app/logdate/`) is drafted and passes the reference
implementation's lint. But the library's schema tools only understand plain objects, queries and
procedures. Every LogDate record type is a `record`, the journal entry's blocks are a `union`, and
photos, video and audio are `blob`s. Until the tools understand those, none of the family can
generate code.

## What a Lexicon schema can say, and what we support after this change

| Kind | Before | After |
|---|---|---|
| `object`, `params`, `query`, `procedure` | Parsed and generated | Unchanged |
| `string`, `boolean`, `integer`, `array`, `token`, `ref` | Parsed and generated | Unchanged |
| `record` | Treated as unknown | Parsed, checked and generated, with its record-key rule |
| `union` | Treated as unknown | Parsed, checked and generated as a sealed type |
| `blob` | Generated as raw JSON | Generated as a typed `Blob` |
| `cid-link` | Treated as unknown | Generated as `CidLink` |
| `bytes` | Treated as unknown | Generated as `Bytes` |
| `unknown` | Generated as raw JSON | Unchanged |
| `subscription`, `permission-set` | Treated as unknown | Still unsupported; a later task |

## Decisions

**The data-model types live in the schema library.** `Blob`, `CidLink` and `Bytes` are small
serializable classes in `atproto-lexicon`, built on the `Cid` type and DAG-CBOR codec in
`atproto-repo`. Generated models depend only on the schema library, so the apps never need the
repository engine to read a record.

**A union becomes a sealed interface with one wrapper per member.** For a field like the entry's
`blocks`, the generator writes a sealed interface (`EntryBlocksItem`) with one small data class
per allowed type (`EntryBlocksItem.TextBlock(val value: EntryTextBlock)`), plus `Unknown` for
open unions. The wrapper approach was chosen over making each member class implement the
interface directly, because a type such as a location can belong to unions in many documents,
and an interface list on its class would make every document's code depend on every other's.

**Unknown union members are kept, not dropped.** An open union's `Unknown` member keeps the
member's `$type` and its full JSON, and writes them back unchanged. A device running an older
app can read and re-save an entry that contains a block type it doesn't know without losing it.
A closed union rejects a `$type` outside its list.

**Each union gets a generated serializer that reads and writes `$type`.** That's the AT Protocol
rule: a union member always carries `$type`, while an object reached through a plain `ref` does
not. Records carry `$type` too, so a generated record class has a `$type` property with its
collection's NSID as the default.

**Optional fields default to null, and stay absent.** A field the schema doesn't require is
nullable with a `null` default, so a record that omits it decodes, and re-encodes without it. A
field that's required but `nullable` has no default and is always written, as `null` when it is.

**Record keys are part of the schema model.** `record.key` is parsed into `tid`, `nsid`,
`literal:<value>` or `any`, and the generated record class exposes it, so storage code can check
a key before writing.

**Bytes use standard base64.** The data model encodes `bytes` in JSON as `{"$bytes": "..."}` with
the standard base64 alphabet and optional padding. The repository's DAG-CBOR codec wrote and read
the URL-safe alphabet; that's fixed here, because records with bytes would otherwise round-trip
through JSON into a different value than other AT Protocol implementations produce.

**Legacy blobs wait.** The data model still allows reading an old blob form with no size,
`{"cid": "...", "mimeType": "..."}`, but only very old Bluesky records use it. LogDate's records
and the community records it reuses never do, so `Blob` decoding rejects that form with a clear
error for now. Reading other apps' records, which may contain it, is a later project.

## Checking schemas

The schema checker additionally reports:

- a record without a `key`, with a malformed key rule, or whose `record` isn't an object;
- a union with a ref that doesn't resolve, or that points at anything other than an object or a
  record;
- an empty closed union;
- a blob `accept` entry that isn't a MIME type, `type/*` or `*/*`;
- `bytes` or `blob` size limits that are negative, or a minimum above its maximum.

## Proving it

- Parser and checker tests use the official `com.atproto` schemas already in the library, the
  community location and calendar schemas, and LogDate's own family.
- A test schema set exercising every new kind is generated into the library's common test
  sources and checked in. Its tests run on the JVM, Android and iOS, and check that each model
  round-trips unchanged through JSON and through DAG-CBOR bytes.
- The existing check that checked-in generated code matches its schemas covers the new output.

## Out of scope

- Generating the whole LogDate family into a shared module: the next toolchain task.
- Checking record data against its schema (string formats, lengths, blob size and type limits):
  the "Check incoming records against their schemas" task.
- `subscription` and `permission-set`: the permission sets and Spaces task.
- Reading legacy blobs: needed once LogDate reads other apps' records.
