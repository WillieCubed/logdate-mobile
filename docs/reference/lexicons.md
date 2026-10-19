# LogDate lexicons

LogDate is moving to a lexicon-first data model: every kind of record LogDate stores is described
once, as an AT Protocol [Lexicon](https://atproto.com/specs/lexicon) schema, and the app's models,
sync format and export archive are generated from those schemas. This reference is the working map
for that move. The reasoning behind it is in
[LogDate on the AT Protocol](../superpowers/specs/2026-10-09-logdate-atproto-interoperability-design.md),
and the sharing rules the schemas must support are in the
[social model](../feature-design/social-model.md).

The schemas are drafted in the [`lexicons/`](../../lexicons) folder and haven't been published.
Nothing reads or writes them yet: today's sync still uses the older `studio.hypertext.logdate.*`
lexicons, which cover only part of the data and don't follow the Lexicon specification. This
document records which data becomes which record, and where each record is allowed to live. How to
write and change a schema is in [Writing LogDate lexicons](./lexicon-style.md).

## How to read the classification

Every piece of data gets three labels.

**Who can ever see it** (sensitivity):

- **Private only**: only the owner, ever. It can't be shared, even on purpose.
- **Shareable**: private by default, and can be shared with connections, circles or groups when the
  owner chooses.
- **Publishable**: private by default, and the owner can also publish it as a public story.

**Where it comes from** (origin):

- **Authored**: something the person made or decided: an entry, a journal, a correction.
- **Derived**: something LogDate computed from other data: a transcript, an inferred person, a
  Rewind.
- **Bookkeeping**: state the app needs to run, which isn't anyone's content.

**Where it lives** (storage, from the design's three kinds of repository):

- **Personal space**: the owner-only, end-to-end encrypted repository. Records are sealed, so the
  server sees neither their type nor their timestamps.
- **Circle or group space**: a copy encrypted to the people it was shared with.
- **Public repository**: visible to the whole AT Protocol network. Only the profile, public sharing
  keys and deliberately published stories go here.
- **Device only**: not a record at all. It stays on one device (or in the server's own tables) and
  never becomes a lexicon.

Derived data is a record when it is expensive or impossible to recompute (a transcript, a Rewind),
when the person has acted on it (confirming an inferred person), or when every device needs to
agree on it (inferred people). Otherwise it stays device-only and is recomputed.

## Client data

Classified from the latest Room schema. When the schema changes,
`ExportCoverageTest` forces a decision about every new table; this table should change with it.

| Table | Becomes | Who can see it | Origin | Lives in |
|---|---|---|---|---|
| `text_notes`, `image_notes`, `video_notes`, `audio_notes` | `app.logdate.journal.entry`, one entry with ordered blocks | Publishable | Authored | Personal space |
| `media_captions` | Part of the entry's media block | Publishable | Authored | Personal space |
| `transcriptions`, `transcription_segments` | `app.logdate.journal.transcript`, attached to an audio block | Shareable, with its audio | Derived, costly | Personal space |
| `audio_tags` | Part of the transcript | Shareable, with its audio | Derived | Personal space |
| `journals` | `app.logdate.journal.journal` | Shareable (a shared journal keeps reaching its audience) | Authored | Personal space |
| `journal_content_links` | `app.logdate.journal.membership` | Follows the journal | Authored | Personal space |
| `journal_merges` | Not a record | — | Bookkeeping | Device only |
| `user_places` | `app.logdate.place.place` | Private only | Authored | Personal space |
| `history_records` | `app.logdate.place.visit`, `app.logdate.place.journey`, and `app.logdate.place.correction` for the person's corrections | Private only | Derived from sensors, with authored corrections | Personal space |
| `location_logs` | `app.logdate.place.sample`, one record per day | Private only | Derived from sensors | Personal space |
| `location_activity` | Part of the visit or journey it explains | Private only | Derived | Personal space |
| `history_cursors` | Not a record | — | Bookkeeping | Device only |
| `places` | Not a record. Nothing writes it; drop it with the migration. | — | — | — |
| `events` | Reused `community.lexicon.calendar.event`, plus `app.logdate.event.annotation` for LogDate's own fields | Shareable | Derived from calendars, edited by the person | Personal space |
| `event_note_links` | Part of `app.logdate.event.annotation` | Follows the event | Authored | Personal space |
| `people` | `app.logdate.people.person` | Private only | Authored or confirmed | Personal space |
| `person_links` | `app.logdate.people.mention` | Private only | Authored or confirmed | Personal space |
| `person_resolution_decisions` | `app.logdate.people.decision` | Private only | Authored | Personal space |
| `inferred_person_clusters`, `inferred_person_evidence` | `app.logdate.people.inference`, with its evidence | Private only | Derived | Personal space |
| `rewinds`, `rewind_text_content`, `rewind_image_content`, `rewind_video_content` | `app.logdate.rewind.rewind`, with its panels | Publishable | Derived, costly | Personal space |
| `rewind_prompt_responses` | `app.logdate.rewind.promptResponse` | Shareable | Authored | Personal space |
| `rewind_generation_requests` | Not a record | — | Bookkeeping | Device only |
| `postcards` | `app.logdate.craft.postcard` | Publishable | Authored | Personal space |
| `stickers` | `app.logdate.craft.sticker` | Publishable | Authored | Personal space |
| `health_snapshots` | `app.logdate.health.snapshot` | Private only | Derived from sensors | Personal space |
| `indexed_media_images`, `indexed_media_videos`, `media_exif_metadata` | Not records. They index the device's own photo library. | — | Derived | Device only |
| `sync_cursors`, `pending_uploads`, `sync_download_inbox`, `sync_download_checkpoints` | Not records | — | Bookkeeping | Device only |
| `search_index_metadata`, `storage_metadata` | Not records | — | Bookkeeping | Device only |
| `user_devices` | Not a record. The server's device registry. | — | Bookkeeping | Server |
| `journal_notes`, `media_images` | Not records. Legacy tables nothing reads; drop them. | — | — | — |

Some data lives outside Room and also becomes records:

| Data | Becomes | Who can see it | Origin | Lives in |
|---|---|---|---|---|
| Profile (display name, photo, bio) | `app.logdate.actor.profile` | Public | Authored | Public repository |
| Birthday | `app.logdate.actor.settings`, not the profile | Private only | Authored | Personal space |
| Editor drafts | `app.logdate.journal.draft` | Private only | Authored, unfinished | Personal space |

## Data the sharing features add

These don't exist yet. They come from the [social model](../feature-design/social-model.md).

| Data | Becomes | Who can see it | Lives in |
|---|---|---|---|
| Connections | `app.logdate.graph.connection` | Each side sees only their own | Personal space |
| Mutes | `app.logdate.graph.mute` | Only the owner | Personal space |
| Blocks | `app.logdate.graph.block` | Only the owner | Personal space |
| Circles and their members | `app.logdate.graph.circle`, `app.logdate.graph.circleMember` | Only the owner | Personal space |
| Groups | `app.logdate.space.groupProfile`, with an `app.logdate.space.member` record per member | The group's members | Group space |
| A shared item | `app.logdate.share.share`, the author's record of what they shared and with whom | Only the author | Personal space |
| What recipients receive | `app.logdate.share.item`, plus copies of the shared records under their usual types | The people it was shared with | Circle or group space |
| Reactions and replies | `app.logdate.share.reaction`, `app.logdate.share.reply` | The author and the person responding | Circle or group space |
| Content keys for recipients | `app.logdate.crypto.wrappedKey` | The recipient | Circle or group space |
| Sharing keys | `app.logdate.crypto.keyDeclaration` | Public, so others can encrypt to you | Public repository |

The social graph is deliberately stored as private records. On most AT Protocol apps follows are
public; in LogDate nobody can see your connections, circles, mutes or blocks.

## Server data that never becomes a lexicon

The server also stores data that isn't anyone's content. It stays in the server's own tables:
accounts, sessions, passkeys, account key envelopes, device enrollments, billing, diagnostic
reports, OAuth state, signing keys, and the sync and repository indexes.

## The schema family

All new schemas use the `app.logdate.*` namespace. Each group below is its own authority under
`logdate.app` when published.

| Group | Records |
|---|---|
| `app.logdate.defs` | `recordRef`, `media`, `aspectRatio`, `location` (shared definitions, not records) |
| `app.logdate.journal` | `entry`, `transcript`, `journal`, `membership`, `draft` |
| `app.logdate.place` | `place`, `visit`, `journey`, `correction`, `sample` |
| `app.logdate.people` | `person`, `mention`, `decision`, `inference` |
| `app.logdate.event` | `annotation` |
| `app.logdate.rewind` | `rewind`, `promptResponse` |
| `app.logdate.craft` | `postcard`, `sticker` |
| `app.logdate.health` | `snapshot` |
| `app.logdate.actor` | `profile`, `settings` |
| `app.logdate.graph` | `connection`, `mute`, `block`, `circle`, `circleMember` |
| `app.logdate.share` | `share`, `item`, `reaction`, `reply` |
| `app.logdate.space` | `groupProfile`, `member`, the `personal`, `circle` and `group` space declarations, and the personal-space methods listed below |
| `app.logdate.crypto` | `keyDeclaration`, `sealed` (the envelope every private record is stored in), `wrappedKey` |
| Permission sets | `app.logdate.authFull`, `app.logdate.authShare` |

### Space declarations

AT Protocol Spaces names every space by three parts: the DID that controls it (its authority), a
space type and a space key, as in `at://{authority}/space/{spaceType}/{skey}`. A space type is
itself a lexicon, a *space declaration*: a file whose `main` definition has `"type": "space"`. It
gives the kind of space a name people see when an app asks for access, says how its spaces are
keyed, and lists the record collections apps should expect inside. Spaces is still an alpha in the
AT Protocol, so these follow its proposal as it stands and may change with it.

| Space type | Name people see | Space key | Collections | What it holds |
|---|---|---|---|---|
| `app.logdate.space.personal` | Your LogDate journal | `literal:self`: one per account | `app.logdate.crypto.sealed` | Everything private, sealed |
| `app.logdate.space.circle` | Things shared in LogDate | `any`: a random key per share | `app.logdate.crypto.sealed` | Something shared with a circle or with people, and the recipients' reactions and replies |
| `app.logdate.space.group` | LogDate groups | `any`: a random key per group | `app.logdate.crypto.sealed` | Everything shared into a group, its `groupProfile` and its `member` records |

Every record in these spaces is sealed, so each declaration lists only `app.logdate.crypto.sealed`.
The records inside the envelopes, such as entries, share items and group profiles, are not
collections the server or another app can see. Where a recipient's `app.logdate.crypto.wrappedKey`
records live is decided by End-to-end encrypted sharing. If they go into circle or group spaces
unsealed, adding them to those declarations' collections is a compatible change.

The group's own description used to be the record `app.logdate.space.group`. That name is now the
group space type, so the description record is `app.logdate.space.groupProfile`.

### Permission sets

A permission set bundles the access an app asks for into one OAuth scope, `include:<set NSID>`,
with a title and a summary that the account's server shows when someone approves the app (see
the [permission specification](https://atproto.com/specs/permission)). A set may only grant access
inside its own namespace, so LogDate's sets can't grant anything outside `app.logdate.*`; an app
that also wants, say, `site.standard.*` for published stories asks for that separately.

Both sets leave out a space permission's `collection` and `action`, so they grant read access and
writes to the collections the space declaration lists. That way a collection added to a
declaration reaches existing sessions without asking people to approve the app again.

**`app.logdate.authFull`, "Use LogDate"**, is everything LogDate's own apps need:

- writing the public profile (`app.logdate.actor.profile`) and the public sharing keys
  (`app.logdate.crypto.keyDeclaration`) to the account's public repository;
- the account's own personal space, including creating it (`manage`);
- circle and group spaces under any authority, because what other people share with you is stored
  in their spaces, including creating, changing and deleting circle and group spaces.

**`app.logdate.authShare`, "Share with your people"**, is for another app that lets people share
through LogDate, such as a camera app. It grants only what sharing needs:

- circle spaces under any authority, including creating, changing and deleting your own: sharing
  something, seeing what others shared with you, and reacting or replying;
- group spaces under any authority, without `manage`: seeing and sharing into groups you're in.

It deliberately leaves out:

- **the personal space**, so the app can never read the journal;
- **the public profile and the sharing keys**, because replacing someone's sharing keys would let
  an app read what is shared with them later. Reading other people's keys to encrypt to them needs
  no permission, because they are public;
- **creating and running groups**, which stay in LogDate's own apps, because a group's owner
  decides who is in it.

Neither set includes `rpc` permissions. The personal-space methods below only accept LogDate's own
access tokens today, and once LogDate moves onto Spaces those calls are covered by the space
permissions.

### Personal-space methods

`app.logdate.space` also defines the six XRPC methods a LogDate device calls to store and read its
owner's sealed records. They are methods, not records, so they don't appear in the classification
tables above. Every record stored through them is an `app.logdate.crypto.sealed` envelope, so the
server stores ciphertext and never learns what a record says. Only the owner's own LogDate devices
can call them: the server accepts a LogDate access token and nothing else, and a request for
anyone else's space gets the same answer as a space that doesn't exist.

| Method | What it does |
|---|---|
| `app.logdate.space.putRecord` | Creates or replaces one sealed record, optionally only if it still has the CID the caller last saw (`swapCid`) |
| `app.logdate.space.deleteRecord` | Deletes one sealed record and leaves a deletion marker, so other devices learn it is gone |
| `app.logdate.space.getRecord` | Reads one sealed record by its key |
| `app.logdate.space.listRepoOps` | Lists changes after a sequence number, deletions included, one page at a time. A device starts from `since=0` to download everything. If the server has already purged deletions after the `since` a device holds, it answers `CursorTooOld` and the device lists again from 0 |
| `app.logdate.space.uploadBlob` | Uploads one encrypted file and returns the blob reference to put in a sealed record |
| `app.logdate.space.getBlob` | Downloads one encrypted file by its CID |

A server only answers these methods when its operator has turned on
[`LOGDATE_PERSONAL_SPACE_ENABLED`](../../server/docs/environment-variables.md#logdate_personal_space_enabled).
Until then they answer `501 MethodNotImplemented`.

**Reused as they are:** `community.lexicon.location.*` inside places and entries,
`community.lexicon.calendar.event` and `rsvp` for events, `com.atproto.repo.strongRef` for
references, and `site.standard.publication` / `document` for published stories only.

**Read only, never written:** Bluesky's follows, profiles, blocks and lists, and other apps'
records for the "Your Atmosphere life in your days" imports (teal.fm, Bookhive, Popfeed, Smoke
Signal, Grain).

## Activity Streams mapping

Every LogDate record also has a defined [Activity Streams 2.0](https://www.w3.org/TR/activitystreams-core/)
shape. Activity Streams is the vocabulary ActivityPub and the fediverse use. Defining the mapping
keeps LogDate's data legible outside AT Protocol, gives the export archive a standard
representation, and means full ActivityPub support later is a projection of existing records, not a
second data model.

The rules:

- **A mapping is a view, not a second store.** The lexicon record is the source of truth. The
  Activity Streams form is generated from it, never stored separately or edited directly.
- **The mapping describes decrypted content.** A sealed or encrypted record has no Activity Streams
  form on the wire. The mapping applies after the owner, or someone it was shared with, decrypts it.
- **Defining a mapping never means federating.** Only publishable records may ever be delivered to
  other servers, and only when published. Everything else uses the mapping for export and
  interoperability alone.
- **Standard terms first, LogDate terms when needed.** Fields with no Activity Streams equivalent go
  under a LogDate JSON-LD context (`https://logdate.app/ns#`) instead of being dropped.
- **Deletions become `Tombstone`s**, so an export or a remote copy can tell "deleted" from "never
  existed".

The Kotlin types for the vocabulary already exist in `shared/activitypub`; the serializer doesn't
yet.

| Record | Activity Streams shape |
|---|---|
| `journal.entry` | `Note`, with `content` (Markdown rendered to HTML, the source kept in `source`), `attachment` for each photo, video and audio block (`Image`, `Video`, `Audio`, each with `mediaType` and `name` as alt text), `published`, `updated` and `location` |
| `journal.transcript` | A `Document` (`mediaType: text/vtt`) attached to its `Audio` |
| `journal.journal` | `OrderedCollection` with `name` and `summary`; its entries are the `orderedItems` |
| `journal.membership` | An item in the journal's collection (`Add` with the journal as `target` when expressed as an activity) |
| `journal.draft` | None; drafts are unfinished and never leave the owner's devices in any other form |
| `place.place` | `Place`, with `name`, `latitude`, `longitude`, `radius` and `units` |
| `place.visit` | `Arrive`, with the `Place` as `location` and the visit's start as `published`; the end time uses a LogDate term |
| `place.journey` | `Travel`, with `origin` and `target` places |
| `place.correction` | `Update` of the visit or journey it corrects |
| `place.sample` | None; daily location records use the lexicon form (and GeoJSON in exports) |
| `people.person` | `Person` as an object, not an actor: `name`, `summary`, `icon`. It describes someone; it doesn't make them an account. |
| `people.mention` | A `Mention` in the entry's `tag` |
| `people.decision` | None; it records an answer to a question LogDate asked, not content |
| `people.inference` | None; an unconfirmed guess is not something to show outside LogDate |
| Reused `calendar.event` and `event.annotation` | `Event`, with `name`, `startTime`, `endTime` and `location`; linked entries in `context` |
| `rewind.rewind` | `OrderedCollection` of its panels; an `Article` when published |
| `rewind.promptResponse` | `Note` with `inReplyTo` pointing at the prompt |
| `craft.postcard` | `Image` (the rendered postcard), with its parts under a LogDate term |
| `craft.sticker` | `Image` |
| `health.snapshot` | LogDate terms only; Activity Streams has no health vocabulary |
| `actor.profile` | `Person` as the actor, with `name`, `summary`, `icon` and `preferredUsername` |
| `actor.settings` | None; private account details |
| `graph.connection` | `Relationship` (`subject`, `object`, `relationship`) |
| `graph.mute` | `Ignore` |
| `graph.block` | `Block` |
| `graph.circle` | `Collection` of actors; always private |
| `graph.circleMember` | `Add` of the actor to the circle's collection; always private |
| `space.groupProfile` | `Group`; invitations are `Invite` |
| `space.member` | `Join` (and `Leave` when removed) |
| `share.share` | `Create` of the shared object, with recipients in `to`; only the author has this form |
| `share.item` | `Create` of the shared object as a recipient sees it, with no other recipients listed |
| `share.reaction` | `Like`, with the reaction in `content` |
| `share.reply` | `Note` with `inReplyTo` pointing at the shared item |
| Unsharing, removing | `Delete` with a `Tombstone`, and `Undo` of the `Relationship` |
| `crypto.keyDeclaration` | None yet; Activity Streams has no end-to-end encryption vocabulary. Exports use LogDate terms on the actor |
| `crypto.sealed`, `crypto.wrappedKey` | None; they're encryption envelopes, not content |
| Published stories (`site.standard.document`) | `Article` |

The machine-readable version of this table is `lexicons/activity-streams.json`. `./run
lint:lexicons` fails if a record type is missing from it; `null` means a record has no Activity
Streams form.

## Decisions

| Date | Decision |
|---|---|
| 2026-10-10 | **One entry type.** `entry` holds ordered blocks (text, photo, video, audio), following the planned multi-block journal entry. The sync migration converts today's separate note tables into entries. |
| 2026-10-10 | **One sealed collection.** Every private record is stored inside a single `app.logdate.crypto.sealed` collection, so the server can't tell which kinds of records someone has. |
| 2026-10-10 | **Daily location records.** Raw location samples are batched into one `place.sample` record per day instead of one record per sample. |
| 2026-10-10 | **Inferred people sync as regular records.** Unconfirmed inferences are `people.inference` records in the personal space, synced like any other content, so every device shows the same suggestions and works from the same underlying data. They stay private and never become shareable until confirmed. |
| 2026-10-10 | **Shared copies are ordinary records.** A shared entry, journal, Rewind, postcard or sticker is copied into the circle or group space under its usual type, and an `app.logdate.share.item` lists the copies. The AT Protocol reference implementation doesn't allow one record type to be embedded inside another, and keeping one type per kind of content means apps read shared and private entries the same way. |
| 2026-10-10 | **References inside private spaces use `recordRef`.** Records in personal, circle and group spaces point at each other by type and key (`app.logdate.defs#recordRef`), because they have no public AT URI or CID. |
| 2026-10-10 | **Encrypted media keeps its real type separately.** `app.logdate.defs#media` stores the blob, which is ciphertext in private spaces, next to the decrypted file's `mimeType`. |
| 2026-10-10 | **No floats.** Measurements are integers with the unit in the field name, confidences are in thousandths, and coordinates are decimal strings, matching the AT Protocol data model and the community location schema. |
| 2026-10-18 | **Space kinds are space declarations.** `app.logdate.space.personal`, `circle` and `group` are AT Protocol space declarations instead of tokens, and the group's description record is `app.logdate.space.groupProfile`. Each declaration lists only `app.logdate.crypto.sealed`, because every record in those spaces is sealed. |
| 2026-10-18 | **Sharing apps never get the journal or the keys.** `app.logdate.authShare` grants circle and group spaces only. The personal space, the profile, the sharing keys and running groups stay with `app.logdate.authFull`, LogDate's own apps. |
