# Client conformance suite

LogDate has three clients: this repository's Kotlin Multiplatform apps (Android, iOS, desktop and
Wear OS), the native Mac app (Swift) and the native Linux app (Rust). They have to read and write
the same records and open each other's encrypted data. This folder holds the test cases that prove
it. Every client runs the same files, so a client that passes can read what another one wrote.

The files are plain JSON. Nothing here depends on Kotlin, and no client has to share code with
another.

## What's in here

| Path | What it checks |
|---|---|
| `records/<nsid>.json` | One file for each `app.logdate.*` record type. Valid records, each with the DAG-CBOR bytes and CID it must encode to, and invalid records, each labeled with the failure a validator must report. |
| `sealed/key-schedule.json` | How the personal space's content key, index key, key ID and opaque record keys derive from an identity key. |
| `sealed/padding.json` | How a payload is padded to its size bucket before encryption. |
| `sealed/envelopes.json` | Whole `app.logdate.crypto.sealed` envelopes: the plaintext payload, the nonce, the DAG-CBOR bytes, the ciphertext, and changes that must make an envelope fail to open. |
| `sealed/reference/verify.py` | A second implementation of sealing in Python, which reproduces every sealed fixture from scratch. |
| `manifest.json` | The suite's version, the failure kinds, the record types and every fixture file. Use it to notice when a file is added. |

Don't edit the `.json` files by hand. They are generated from the lexicons in `lexicons/` and from
the sealed-record format, by `tools/lexicon-check/conformance.mjs` (see [Changing the
suite](#changing-the-suite)).

## Running the record cases

Every `records/<nsid>.json` file looks like this:

```json
{
  "collection": "app.logdate.journal.entry",
  "recordKey": "any",
  "valid":   [{ "name": "minimal", "record": { }, "dagCbor": "a365…", "cid": "bafyrei…" }],
  "invalid": [{ "name": "missing-createdAt", "record": { }, "failure": { "kind": "missing-required", "path": "/createdAt" } }]
}
```

Records are written in the JSON form of the AT Protocol data model: bytes are
`{"$bytes": "<base64>"}`, CID links are `{"$link": "<cid>"}`, and a blob is an object with
`"$type": "blob"`, a `ref` link, a `mimeType` and a `size`.

For each **valid** case, a client must:

1. Validate `record` against the lexicon for `collection` and find no problems.
2. Encode `record` as DAG-CBOR and get exactly the bytes in `dagCbor` (lowercase hex).
3. Compute the CID of those bytes (CIDv1, `dag-cbor` codec `0x71`, SHA-256) and get `cid`.
4. Decode `dagCbor` and get `record` back.
5. If the client has typed models and the case doesn't say `"modelRoundTrip": false`, decode
   `record` into the model for `collection`, encode it again, and get the same JSON and the same
   bytes. A case with `"modelRoundTrip": false` holds a field from a newer version of the schema. It
   must still validate, but a model that doesn't know the field is allowed to drop it.

For each **invalid** case, a client's validator must refuse `record` and report `failure.kind` at
`failure.path`. The path is a [JSON Pointer](https://www.rfc-editor.org/rfc/rfc6901) into the
record, such as `/blocks/0/text`. The validator may report more than one problem and may word its
messages however it likes. It only has to include this one.

| Kind | The record has |
|---|---|
| `missing-required` | no value for a required field |
| `wrong-type` | a value of the wrong type, such as a number where a string belongs |
| `too-long` | a string, byte string or array over its limit |
| `too-short` | a string, byte string or array under its limit |
| `out-of-range` | an integer outside its minimum or maximum |
| `not-allowed-value` | a value that isn't one of a field's allowed values |
| `invalid-format` | a string that doesn't follow its declared format, such as `datetime` or `did` |
| `wrong-record-type` | a `$type` that isn't the record's collection |
| `union-member-not-allowed` | a `$type` that a closed union doesn't list |
| `union-member-missing-type` | a union value with no `$type` |

LogDate's own schemas don't yet use every constraint, so some kinds appear in no case today. They
start appearing when a schema first uses the constraint, and `tools/lexicon-check/conformance.test.mjs`
keeps the generator able to produce them.

Records always carry `$type`. The reference implementation refuses a record without it, so the
suite has no case for one.

## Running the sealed-record cases

Personal-space records are stored as `app.logdate.crypto.sealed` envelopes, so every client must
seal and open them in exactly the same way. Each file in `sealed/` states its algorithm in an
`algorithm` or `description` field. All byte strings are lowercase hex unless a field says it is
base64.

**`key-schedule.json`.** For each vector, derive the content key and the index key from
`identityKey` with HKDF-SHA256, derive the `keyId`, and derive the `sealedRkey` for each
collection and record key listed. Every value must match. A key of any length in
`refusedIdentityKeyLengths` must be refused.

**`padding.json`.** For each entry, a payload of `payloadLength` bytes pads to `paddedLength` bytes:
a 4-byte big-endian length, the payload, then zeros. A payload of `smallestRefusedPayload` bytes or
more can't be sealed.

**`envelopes.json`.** For each envelope:

1. Build the payload `{ collection, rkey, record }` from `payload`. If `expand` has entries, first
   replace the string at each `path` (a JSON Pointer into the payload) with `repeat` written `times`
   times. The fixtures use this to avoid a 70 KB line.
2. Encode it as DAG-CBOR. It must match `dagCbor`. `length` and `sha256` are always there, and
   `hex` is there when the bytes are short.
3. Pad it. The padded size must be `paddedLength`.
4. Derive the keys from `identityKey` and the sealed record key. It must be `sealedRkey`.
5. Encrypt with AES-256-GCM under the content key and `nonce`, using `additionalData` as the
   associated data. The result, tag included, must match `ciphertext`.
6. If `envelope` is present, it is the stored record. Open it with the same keys and the same
   `space`, `author` and `sealedRkey`, and get the payload back.
7. For each entry of `refusedEnvelopes`, replace the inputs it names (`author`, `space`,
   `sealedRkey`, or fields of `envelope`) and try to open the envelope. Opening must fail.

Never reuse a nonce with the same key outside a test. The fixed nonces are there only so the
output is repeatable.

## Using the suite from another repository

Copy this folder into the client's repository and run its cases from that client's test suite. Keep
the copy byte-identical to this one, as the Markdown parity fixtures are, and record which
`logdate-mobile` commit it came from. The suite's `version` in `manifest.json` goes up whenever the
shape of a file changes, so a client can refuse a copy it doesn't understand.

A client needs, at the least:

- a loader for `manifest.json` that fails when `version` isn't one it understands;
- a record test per `records/*.json` file, written as the steps above;
- a sealed test per file in `sealed/`;
- a check that every file named in the manifest is read, so a file added later isn't skipped.

## Where this repository runs them

- `shared/lexicon/src/jvmTest/.../RecordConformanceJvmTest.kt` runs the record cases against the
  Kotlin validator, DAG-CBOR codec and generated models.
- `shared/space/src/commonTest/.../SealingKeysTest.kt`, `SealedPaddingTest.kt` and
  `SealedEnvelopeConformanceTest.kt` run the sealed cases on every target, iOS included. The build
  embeds `sealed/*.json` into the test sources, so there is one copy of the fixtures.
- `scripts/tests/lexicons/test-conformance-fixtures.sh` runs in CI. It checks that the committed
  fixtures match the generator, runs the generator's tests, and runs `sealed/reference/verify.py`.

To run the Python reference on its own:

```bash
pip install cryptography
python3 conformance/sealed/reference/verify.py
```

## Changing the suite

Change a lexicon, then regenerate and review the diff:

```bash
./run conformance:update   # rewrite the fixtures from the lexicons
./run lint:conformance     # check they are up to date (CI runs this)
```

`lint:conformance` fails when a fixture differs from what the generator produces, when the
reference implementation disagrees with a fixture's label (a record meant to be valid is rejected,
or an invalid one fails in a different way), and when a fixture file has no source any more.

Records come from the schemas, so the cases follow them. For each record type the generator
builds a record with only its required fields, one with every field, one with a field from a newer
version of the schema, and the example records in `tools/lexicon-check/examples/`. It builds each
invalid case by breaking one thing in the smallest valid record. A schema that gains a constraint
gets cases for it with no extra work.

A change to the sealed-record format itself (the key derivation, padding, additional data or
envelope layout) is a change to `tools/lexicon-check/conformance/sealed.mjs` and to
`sealed/reference/verify.py`, and it needs a new `version` in `manifest.json`: every client has to
update. Existing sealed data can't be opened by a client that doesn't follow the change.
