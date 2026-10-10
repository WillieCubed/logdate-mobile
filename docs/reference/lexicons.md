# LogDate lexicons

LogDate is moving to a lexicon-first data model: every kind of record LogDate stores is described
once, as an AT Protocol [Lexicon](https://atproto.com/specs/lexicon) schema, and the app's models,
sync format and export archive are generated from those schemas. This reference is the working map
for that move. The reasoning behind it is in
[LogDate on the AT Protocol](../superpowers/specs/2026-10-09-logdate-atproto-interoperability-design.md),
and the sharing rules the schemas must support are in the
[social model](../feature-design/social-model.md).

The schemas themselves don't exist yet. Today's `studio.hypertext.logdate.*` lexicons cover only
part of the data and don't follow the Lexicon specification. This document records which data
becomes which record, and where each record is allowed to live.

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
| `history_records` | `app.logdate.place.visit`, `app.logdate.place.journey`, plus corrections | Private only | Derived from sensors, with authored corrections | Personal space |
| `location_logs` | `app.logdate.place.sample`, one record per day | Private only | Derived from sensors | Personal space |
| `location_activity` | Part of the visit or journey it explains | Private only | Derived | Personal space |
| `history_cursors` | Not a record | — | Bookkeeping | Device only |
| `places` | Not a record. Nothing writes it; drop it with the migration. | — | — | — |
| `events` | Reused `community.lexicon.calendar.event`, plus `app.logdate.event.annotation` for LogDate's own fields | Shareable | Derived from calendars, edited by the person | Personal space |
| `event_note_links` | Part of `app.logdate.event.annotation` | Follows the event | Authored | Personal space |
| `people` | `app.logdate.people.person` | Private only | Authored or confirmed | Personal space |
| `person_links` | `app.logdate.people.mention` | Private only | Authored or confirmed | Personal space |
| `person_resolution_decisions` | Part of `app.logdate.people.person` | Private only | Authored | Personal space |
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
| Birthday | Part of a private settings record, not the profile | Private only | Authored | Personal space |
| Editor drafts | `app.logdate.journal.draft` | Private only | Authored, unfinished | Personal space |

## Data the sharing features add

These don't exist yet. They come from the [social model](../feature-design/social-model.md).

| Data | Becomes | Who can see it | Lives in |
|---|---|---|---|
| Connections | `app.logdate.graph.connection` | Each side sees only their own | Personal space |
| Mutes | `app.logdate.graph.mute` | Only the owner | Personal space |
| Blocks | `app.logdate.graph.block` | Only the owner | Personal space |
| Circles and their members | `app.logdate.graph.circle`, `app.logdate.graph.circleMember` | Only the owner | Personal space |
| Groups | `app.logdate.space.group` space, with its members | The group's members | Group space |
| A shared item | `app.logdate.share.share`, with an encrypted copy of what was shared | The people it was shared with | Circle or group space |
| Reactions and replies | Records attached to a share | The author and the person responding | Circle or group space |
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
| `app.logdate.journal` | `entry`, `transcript`, `journal`, `membership`, `draft` |
| `app.logdate.place` | `place`, `visit`, `journey`, `sample` |
| `app.logdate.people` | `person`, `mention`, `inference` |
| `app.logdate.event` | `annotation` |
| `app.logdate.rewind` | `rewind`, `promptResponse` |
| `app.logdate.craft` | `postcard`, `sticker` |
| `app.logdate.health` | `snapshot` |
| `app.logdate.actor` | `profile` |
| `app.logdate.graph` | `connection`, `mute`, `block`, `circle`, `circleMember` |
| `app.logdate.share` | `share`, reactions, replies |
| `app.logdate.space` | `personal`, `circle`, `group` (space-type declarations) |
| `app.logdate.crypto` | `keyDeclaration`, `sealed` (the envelope every private record is stored in), `wrappedKey` |
| Permission sets | `app.logdate.authFull`, `app.logdate.authShare` |

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
| `place.sample` | None; daily location records use the lexicon form (and GeoJSON in exports) |
| `people.person` | `Person` as an object, not an actor: `name`, `summary`, `icon`. It describes someone; it doesn't make them an account. |
| `people.mention` | A `Mention` in the entry's `tag` |
| `people.inference` | None; an unconfirmed guess is not something to show outside LogDate |
| Reused `calendar.event` and `event.annotation` | `Event`, with `name`, `startTime`, `endTime` and `location`; linked entries in `context` |
| `rewind.rewind` | `OrderedCollection` of its panels; an `Article` when published |
| `rewind.promptResponse` | `Note` with `inReplyTo` pointing at the prompt |
| `craft.postcard` | `Image` (the rendered postcard), with its parts under a LogDate term |
| `craft.sticker` | `Image` |
| `health.snapshot` | LogDate terms only; Activity Streams has no health vocabulary |
| `actor.profile` | `Person` as the actor, with `name`, `summary`, `icon` and `preferredUsername` |
| `graph.connection` | `Relationship` (`subject`, `object`, `relationship`) |
| `graph.mute` | `Ignore` |
| `graph.block` | `Block` |
| `graph.circle`, `graph.circleMember` | `Collection` of actors; always private |
| `space.group` | `Group`; joining and leaving are `Join` and `Leave`, invitations are `Invite` |
| `share.share` | `Create` of the shared object, with recipients in `to` (circle members never appear in another recipient's copy) |
| Reactions, replies | `Like` (the reaction in `content`), and `Note` with `inReplyTo` |
| Unsharing, removing | `Delete` with a `Tombstone`, and `Undo` of the `Relationship` |
| `crypto.keyDeclaration` | LogDate terms on the actor; Activity Streams has no end-to-end encryption vocabulary yet |
| Published stories (`site.standard.document`) | `Article` |

When the schema files are drafted, each one carries its Activity Streams mapping, and the
toolchain checks that no record type is left without one (or without an explicit "none").

## Decisions

| Date | Decision |
|---|---|
| 2026-10-10 | **One entry type.** `entry` holds ordered blocks (text, photo, video, audio), following the planned multi-block journal entry. The sync migration converts today's separate note tables into entries. |
| 2026-10-10 | **One sealed collection.** Every private record is stored inside a single `app.logdate.crypto.sealed` collection, so the server can't tell which kinds of records someone has. |
| 2026-10-10 | **Daily location records.** Raw location samples are batched into one `place.sample` record per day instead of one record per sample. |
| 2026-10-10 | **Inferred people sync as regular records.** Unconfirmed inferences are `people.inference` records in the personal space, synced like any other content, so every device shows the same suggestions and works from the same underlying data. They stay private and never become shareable until confirmed. |
