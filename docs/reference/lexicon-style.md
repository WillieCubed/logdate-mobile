# Writing LogDate lexicons

LogDate describes every kind of record it stores as a [Lexicon](https://atproto.com/specs/lexicon)
schema in the `lexicons/` folder. The apps' data models, the sync format and the export archive
will all be generated from these files, and other AT Protocol apps can read them once they're
published. A schema mistake therefore spreads everywhere, and a published one is hard to take back.
This guide covers the conventions that keep the family consistent, and the rules for changing a
schema safely.

For what each record is and where it lives, see [LogDate lexicons](./lexicons.md).

## The shape of a schema

A lexicon file has an `id`, a `description`, and a set of definitions under `defs`. A record type
has one definition called `main` with `"type": "record"`, a record `key` type, and an object
describing the record's fields. Supporting objects, such as the blocks inside an entry, are extra
definitions in the same file and are referred to with `#name`.

```json
{
  "lexicon": 1,
  "id": "app.logdate.graph.circle",
  "description": "A named group of the owner's connections, such as Family. Only the owner can see it.",
  "defs": {
    "main": {
      "type": "record",
      "description": "A named group of the owner's connections, such as Family. Only the owner can see it.",
      "key": "any",
      "record": {
        "type": "object",
        "required": ["name", "createdAt"],
        "properties": {
          "name": { "type": "string", "maxGraphemes": 64, "maxLength": 640 },
          "createdAt": { "type": "string", "format": "datetime" },
          "updatedAt": { "type": "string", "format": "datetime" }
        }
      }
    }
  }
}
```

## Conventions

**Names.** Schemas live under `app.logdate.<group>.<name>`, with the group naming the area of the
product (`journal`, `place`, `people`, `graph`, `share`). Names are lowerCamelCase, singular, and
say what the record is, not how it's stored: `entry`, not `noteRow`. Definitions shared across
groups go in `app.logdate.defs`.

**Descriptions.** Every schema, record and object definition has a description written for someone
who has never seen LogDate. Say what the record means to the person, and who can see it when that
isn't obvious.

**Timestamps.** Use `"format": "datetime"` strings, never integers. Every record a person creates
has `createdAt`, and records that can change have `updatedAt`. Name other times for what they mark:
`startsAt`, `endsAt`, `measuredAt`, `sharedAt`.

Make `createdAt` required unless a record's existing data has no time to put there. Never make one
up. Today these records have an optional `createdAt`, and their descriptions say when it's absent:
transcripts, places that only came from location history, corrections, entry links, visits the
person added, and journal memberships, whose `createdAt` also keeps its earliest value so two
devices writing the same membership agree.

**No floating-point numbers.** The AT Protocol data model has no floats. Use integers with the unit
in the name (`accuracyCentimeters`, `durationMs`, `speedCentimetersPerSecond`), confidences in
thousandths (`942` means 94.2%), and decimal strings for coordinates, which is what the community
location schema does.

**Limits on every string and array.** Give strings a `maxLength` (bytes) and, for text people
write, a `maxGraphemes` (visible characters). Give arrays a `maxLength`. Limits stop one bad record
from breaking sync for everyone.

**Open sets of values.** Use `knownValues`, not `enum`, for lists like a transcript's `status` or a
journey's `mode`. Old apps then keep working when a new value appears. Values are lowerCamelCase.

**Unions stay open.** A union, such as an entry's blocks, lists the kinds it knows today. Leave it
open so a new kind of block can be added later, and make sure apps skip kinds they don't understand.

**Referring to other records.** Records in a personal, circle or group space aren't publicly
addressable, so they refer to each other with `app.logdate.defs#recordRef` (a type and a key) rather
than an AT URI and CID. A record can't embed another record type directly. To share an entry, its
copy is stored as an ordinary `app.logdate.journal.entry` record in the shared space and the share
item lists it.

**Media.** Photos, video and audio use `app.logdate.defs#media`. In private and shared spaces the
stored blob is ciphertext, so the decrypted file's type goes in `mimeType`, not in the blob.

**Record keys.** Most records use `"key": "any"` so records moved from today's database keep their
existing IDs. Records that exist once per account use `"literal:self"`. Public records that are
always new use `"tid"`.

**Activity Streams.** Every record type needs an entry in `lexicons/activity-streams.json`: the
Activity Streams 2.0 type it maps to, or `null` when it has no Activity Streams form. The mapping
rules are in [LogDate lexicons](./lexicons.md#activity-streams-mapping).

**Examples.** Add an example record to `tools/lexicon-check/examples/` for any record type with
unions, nested objects or media, so the check proves a real record validates.

## Space declarations and permission sets

Two kinds of lexicon describe access instead of data. Neither defines fields, so neither becomes a
data class: the generated Kotlin is an object of constants.

**A space declaration** names a kind of AT Protocol space. Its `main` definition has
`"type": "space"` and needs:

- a `description` for developers;
- a `name` of at most 64 characters, which people see when an app asks for access to spaces of this
  kind, optionally translated in `name:lang`;
- a `key` rule for its spaces' keys, written like a record key rule: `literal:self` for one space
  per account, `any` for spaces with random keys;
- `collections`, the record types apps should expect inside. No `*`. Private LogDate spaces list
  only `app.logdate.crypto.sealed`, because everything in them is sealed.

```json
{
  "lexicon": 1,
  "id": "app.logdate.space.personal",
  "description": "The owner's own space, which only they can read.",
  "defs": {
    "main": {
      "type": "space",
      "description": "The owner's own space, which only they can read.",
      "key": "literal:self",
      "name": "Your LogDate journal",
      "collections": ["app.logdate.crypto.sealed"]
    }
  }
}
```

The generated `Personal` object holds `TYPE`, `NAME`, `KEY` and `COLLECTIONS`. Code that builds
space URIs uses `Personal.TYPE` rather than a copy of the string.

Spaces is still an alpha in the AT Protocol, and the stable reference implementation doesn't read
space declarations yet. Until it does, `./run lint:lexicons` checks them with its own rules from
the Spaces proposal.

**A permission set** bundles access an app asks for as one scope, `include:<set NSID>`. Its `main`
definition has `"type": "permission-set"`, a `title` and `detail` written for the people approving
the app, and a list of `permissions`. The rules come from the
[permission specification](https://atproto.com/specs/permission):

- A set can only grant record types, methods and space types in its own namespace or below it:
  `app.logdate.authFull` can grant anything under `app.logdate.`. A space permission's `collection`
  is the one exception, and may name any record type.
- No `*` for a `repo` permission's collection, an `rpc` method or a space type. A `space`
  permission's `collection` may be `*`, but LogDate's sets leave it out instead.
- No `blob`, `account` or `identity` permissions; apps ask for those directly.
- An `rpc` permission uses `"inheritAud": true` instead of naming a service.
- Each resource takes only its own parameters: `collection` and `action` for `repo`; `lxm`, `aud`
  and `inheritAud` for `rpc`; `spaceType`, `authority`, `skey`, `collection`, `action` and
  `manage` for `space`. `collection`, `action`, `lxm` and `manage` are non-empty lists, even for one
  value; the others are single values. An authorization server silently ignores an entry it can't
  read, so a misspelled parameter or a single value where a list belongs would quietly drop the
  whole permission. The lint refuses both.
- Grant `manage` only under the default authority, `self`, so an app can manage only the spaces the
  person controls.

Prefer leaving out a space permission's `collection`. It then grants the collections the space
declaration lists, and follows the declaration when it grows, so a change to a declaration's
collections needs the same review as a change to the sets. What each LogDate set grants, and why,
is in [LogDate lexicons](./lexicons.md#permission-sets).

## Changing a schema

Until a schema is published, it's a draft: change anything you need to, and update the examples and
the reference along with it.

Once a schema is published, other apps may already read and write it, so it follows the AT Protocol
rules for evolution:

- **Allowed:** adding an optional field, adding a value to `knownValues`, adding a kind to an open
  union, loosening a limit, and improving descriptions.
- **Not allowed:** removing or renaming a field, making an optional field required, changing a
  field's type, or tightening a limit that existing records might exceed.
- **Breaking changes get a new name.** If a change can't follow those rules, create a new schema,
  such as `app.logdate.journal.entryV2`, and migrate to it. Never change the meaning of a published
  one.
- **Experiments stay unpublished.** A schema being tried out stays a draft in this folder until it's
  stable.

Space declarations and permission sets are read again while apps' sessions are live, so a change
reaches every app that already has access. Adding access is allowed; taking any away isn't:

- **Allowed:** adding a collection to a space declaration, adding a permission to a set, widening a
  permission (more collections, more actions, `authority: "*"`), and rewording names, titles and
  details.
- **Not allowed:** removing a collection from a space declaration, changing its `key` rule, and
  removing or narrowing anything a published set grants. The lint names each grant that would
  disappear, such as `the set no longer grants repo delete on app.logdate.actor.profile`.

A schema counts as published once its NSID is listed in
[`lexicons/published.json`](../../lexicons/published.json). From then on, `./run lint:lexicons`,
which CI also runs, compares it with its version where your branch left `main`, and fails on any
change these rules don't allow, naming the field and the rule. A listed schema can't be removed from
the list. Drafts aren't listed, so they can still change freely.

## Checking your work

Run the check before you commit:

```bash
./run lint:lexicons
```

It validates each file with the AT Protocol reference implementation, checks that every reference
resolves and every record type has an Activity Streams mapping, checks space declarations and
permission sets against their own rules and that everything they name is defined in `lexicons/`,
and validates the example records against their schemas.
