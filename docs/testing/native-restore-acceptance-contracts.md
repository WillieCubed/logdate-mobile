# Native restore fixture contracts

Restore acceptance must describe the selected Android transport and the destination's
actual authority. A passing test does not authorize changing either contract.

## Privacy metadata

The missing-privacy profile uses encrypted cloud selection. Its retained database,
owner, and recovery keys travel without either original privacy preference. It must
refuse every database consumer, remain protected across retry and Activity recreation,
refuse another backup, preserve the key, and recover when Android delivers the original
policy. The blocked, backup-refusal, and repaired phases are separate assertions.

Device transfer also carries an authenticated journal snapshot with its own preferences.
Removing only the outer preferences does not remove privacy from that snapshot. The
fixture must not call this unknown privacy, damage the snapshot's authenticated manifest,
or change production privacy admission to force an expected failure. The partial-policy
mutation rejects such a payload; the profile selects cloud backup instead.

The host records exception types and startup-state types when database refusal fails.
It must not print journal text, credentials, or preference values as diagnostics.

Unknown privacy is checked against an owned, isolated database copy before canonical
migrations, writable Room callbacks, or key confirmation. Known policies avoid that
inspection. Fresh storage and previously created databases proven empty can continue;
unreadable copies, failed queries, cancellation, and orphaned sidecars never establish
emptiness. Existing retained-draft checks still apply without a database.

## Originals and recording files

Device transfer carries committed originals. Cloud backup excludes them. Signed-out
`data-only` acceptance describes account state, not transport selection. Pass the
selected recording transport to every initial, camera, partial-media, trust-replay,
and cold-restart probe. Verify the existing offline edit before a mutating verifier
could recreate it.

The committed-recording fixture is a live saved audio note containing valid audio, not
an orphan file or plaintext with an audio suffix. Recovery may import a verified legacy
original into immutable canonical storage and update the note's reference. Verify exact
original bytes through that current reference, with a bounded recovery deadline, rather
than requiring the obsolete raw filename. Source and in-place checks still preserve
their actual bytes; cloud absence and unfinished raw/remux exclusions remain strict.

Complete legacy audio, user media, and authenticated library staging in replay custody
must wake reconciliation even after canonical payloads have been acknowledged. This does
not make legacy files independent canonical/database candidates or authorize an import.
The existing completed source receipt, inventory digest, owner/backend scope, current
reference, media-header validation, and reference CAS still control admission.

## Installation-local publication state

Watch links, sessions, keys, and delivery revisions belong to the installation. A source
with revision 3 must retain that revision; a replacement must see revision 0 for the old
scope. A new exchange starts at 0, reserves 1, and retains its new counter across restart
and replay. Native restore history prevents adopting copied legacy trust and counters,
even if a matching key is available. Do not make excluded installation state portable to
satisfy a fixture.

## Occupied destinations and callback fixtures

A native callback must preserve an already accepted local journal. Incoming data enters
custody rather than replacing canonical content. A successful raw-delivery fixture needs
a genuinely vacant destination. To exercise live preference readers within one delivery,
cache values delivered by framework callbacks and finish that delivery once. This does
not replace the separate requirement to repair privacy after a completed partial restore.
Never forge initialized, vacant, or accepted-working receipts in a test.

## Host and device evidence

The native backup entry point retains its CLI and helper API. Payload parsing, platform
operations, UI observation, probes, source preparation, and scenario dispatch are separate
helpers with an explicit dependency namespace. Host tests replace those dependencies;
they do not establish native acceptance. Native runs use the existing Gradle-managed
pair, nonce-bearing driver and process-generation custody, with resource guards and one
coordinated device owner. Ambiguous custody remains a hard failure.
