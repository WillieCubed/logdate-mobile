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

Derived data is still a record when it is expensive or impossible to recompute (a transcript,
a Rewind) or when the person has acted on it (confirming an inferred person). Otherwise it stays
device-only and is recomputed.

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
| `location_logs` | `app.logdate.place.sample` | Private only | Derived from sensors | Personal space |
| `location_activity` | Part of the visit or journey it explains | Private only | Derived | Personal space |
| `history_cursors` | Not a record | — | Bookkeeping | Device only |
| `places` | Not a record. Nothing writes it; drop it with the migration. | — | — | — |
| `events` | Reused `community.lexicon.calendar.event`, plus `app.logdate.event.annotation` for LogDate's own fields | Shareable | Derived from calendars, edited by the person | Personal space |
| `event_note_links` | Part of `app.logdate.event.annotation` | Follows the event | Authored | Personal space |
| `people` | `app.logdate.people.person` | Private only | Authored or confirmed | Personal space |
| `person_links` | `app.logdate.people.mention` | Private only | Authored or confirmed | Personal space |
| `person_resolution_decisions` | Part of `app.logdate.people.person` | Private only | Authored | Personal space |
| `inferred_person_clusters`, `inferred_person_evidence` | Not records until confirmed | Private only | Derived | Device only |
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
| `app.logdate.people` | `person`, `mention` |
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

## Open decisions

- **One entry type.** The `entry` schema follows the planned multi-block journal entry, not
  today's one-table-per-note-type layout. The sync migration converts existing notes into entries.
- **Sealed envelope.** Whether every private record shares one `sealed` collection (hiding even
  which kinds of records someone has) or each type gets its own sealed collection. The storage
  spike decides.
- **Location history volume.** Raw location samples are numerous. They may need batching into
  daily records rather than one record per sample.
- **Inferred people.** Whether unconfirmed inferences should sync between a person's devices as
  records or stay recomputed on each device.
