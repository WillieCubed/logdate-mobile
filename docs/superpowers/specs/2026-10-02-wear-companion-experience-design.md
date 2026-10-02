# Wear OS and Phone Companion Redesign

## Goal

LogDate on a watch should work on its own. You raise your wrist, see today, and record a thought by holding a button. You can replay last week's voice notes on a run with the phone at home, and everything ends up backed up without you thinking about sync. When the phone is nearby, the two devices behave like one product: a recording on the watch shows up live on the phone, a moment marked on the watch shows up in the phone's timeline with the photos you took around it, and audio plays on whichever device you choose.

This document describes that experience and the architecture underneath it. It covers the watch app, the phone surfaces that set up and manage the watch, moment-aware reminders, and the sync, identity and server changes that let the watch act as a full peer. Each delivery track gets its own implementation plan in `docs/superpowers/plans/`.

All of it ships behind one feature flag, `FeatureFlag.WATCH_EXPERIENCE_V2`. The current watch experience stays the default until the flag flips.

## Problem

### The watch depends on the phone for everything

The watch has no account and never talks to the server; the phone is the only way out. On LTE with the phone at home, nothing gets backed up. When the phone does receive a watch note, its acknowledgement means "stored on the phone", not "backed up". Going the other way, the phone republishes *every* note whenever the watch asks, and the watch only asks when Voice Memories opens.

### Sync has real bugs

- **A duplicate upload is not harmless.** `PUT /contents/{id}` always creates a new repository commit and a new server version, and it clears `deleted`. So a late duplicate upload brings a deleted entry back and can overwrite a newer edit.
- **One unknown note type breaks content sync on older clients.** `ContentChange.toJournalNote()` throws on a type it doesn't know, and `readEach` only catches unreadable payloads, so the whole page fails. iOS and desktop share this code, so they break too.
- **Watch captures fall out of backup.** After five failed attempts, the watch dead-letters an item by marking it synced, so it leaves the backup queue for good. The phone keeps its dead letters.
- **Retries never fire on time.** Nothing wakes the watch at the retry times it stores.
- **Acks race with edits.** Acks are applied outside the sync lock and carry no version, so a stale ack can clear a newer edit.
- **Watch journals aren't backed up.** Journals and journal links created on the watch are applied on the phone without being queued for cloud backup.
- **Downloaded audio can become a duplicate note.** Phone audio pulled to the watch lands where crash recovery looks for interrupted recordings, so it can be adopted as a new note.

### Setup is a chore, and parts of it are broken

Watch onboarding is four pages and asks for every permission up front. Linking the phone and watch through Companion Device Manager is a separate manual step. "Open LogDate on watch" sends an `ACTION_MAIN` intent, which `RemoteActivityHelper` rejects; it only accepts `ACTION_VIEW` with a browsable data URI.

### Settings claim things that aren't true

- **Watch sync toggles do nothing.** The phone's toggles for voice, text, mood and health are stored but never read.
- **`WatchSyncSettingsScreen` can't be reached.** The "Sync settings" row on the Watch page opens the cloud sync screen instead.
- **The status line is fake.** `lastSynced` and `pendingCount` are hard-coded, so a connected watch always shows "Not yet synced".
- **The watch's prompt toggles do nothing.** The morning and evening prompts reschedule on every launch whatever the toggles say.

### The watch app is fragmented

Home, More, Timeline, Voice Memories and Health are separate screens. Rewind, the Health dashboard and Remote Camera can't be reached from anywhere. Tiles and complications never update when data changes. The Record complication opens Home instead of recording. The Mood complication reads its notes wrong and always shows "—".

### Nothing looks like LogDate, and nothing shows where a moment came from

The watch uses the default Wear theme. The phone falls back to the template purples on devices without dynamic color. A moment recorded on the watch looks exactly like one written on the phone.

## Product contract

- **No sign-in on the watch.** The phone sets the watch up silently. Someone with a cloud account gets a watch that backs up on its own. Someone without one gets a watch that syncs with the phone.
- **Capture is never blocked.** Recording, marking and writing work before setup, without the phone, and without a network. Anything captured before setup joins the account once setup finishes.
- **The watch works without the phone.** The library window (below) stays on the watch, so browsing and replaying recent memories works offline.
- **Status shows only problems.** When sync is healthy the watch shows nothing about it. When something is stuck, a chip names what failed and how to fix it.
- **No dead controls.** A button or setting appears only when the feature behind it works on both devices.
- **Permissions are asked when they're needed**, never up front.
- **Copy is literal.** It never speaks as "we", and it follows the locale for language and time.

## Decisions

| Topic | Decision |
|---|---|
| Independence | The watch is a full sync peer. It has its own device-scoped server session, handed over by the phone, and syncs directly over Wi-Fi or LTE. The Data Layer is preferred when the phone is nearby. Without a cloud account, the watch syncs over the Data Layer only. |
| Hardware | Wear OS 5 and newer (`:app:wear` minSdk 34). |
| Watch home | **Today**: a summary of the day so far, with Record at the bottom and Mark and + above it. |
| Recorder | Hold to talk; slide up to lock. A tap starts a locked recording. Releasing saves, followed by the existing 5 s Undo. |
| Color | Dynamic color on both devices. Without it, a neutral graphite scheme, with orange used only in the campfire glyph. |
| Watch library | Today, the last 14 days, On this day, this week's Rewind, and starred items. Audio downloads on the charger or Wi-Fi. |
| Continuity | Mark the moment, Open on phone, live recording on the phone, memories on the watch, and "Play on" across watch, headphones and phone. |
| Reminders | Moment-aware, off until turned on, at most one a day, delivered on the device being worn. |
| Watch privacy | No extra privacy gating. Tiles and complications show full previews. |
| Libraries | The newest AndroidX Wear stack (see "Libraries"). Legacy and Horologist pieces that AndroidX now covers are removed. |

### Defaults

These were chosen during planning and can be changed before the code that implements them lands.

- **Removing a watch:**
  - If the watch is reachable, it hands its pending captures to the phone, then erases itself.
  - If it's unreachable, for example lost, it erases itself as soon as it learns its session was revoked.
  - Either way, the phone first warns with the watch's last reported count of unsent moments.
- **Signing out on the phone** revokes and unlinks the watch.
- **A watch already set up for another account** is refused. The phone offers to erase it and shows how many moments on it haven't synced.
- **Companion Device Manager:** the association is made without a device profile, using a `BluetoothDeviceFilter` on the watch's name with `setSingleDevice(true)`. `DEVICE_PROFILE_WATCH` would silently grant calendar and contacts access in the same tap.
- **Marks and the campfire:** a mark counts toward the campfire, and toward "has content today", only once it's filled in with a caption or attachments.
- **Existing reminder users:** people who had capture nudges turned on start with reminders on. Draft-rescue and memory-recall nudges fall under the same switch and the same once-a-day limit.
- **Notification permission** is asked on the first locked recording. The Ongoing Activity and its Stop control need it.
- **Provenance is encrypted.** Whether a moment was captured on the phone or the watch travels inside the encrypted payload.
- **Testing in release builds:** a hidden Labs screen, opened by tapping the version seven times in About, turns `WATCH_EXPERIENCE_V2` on in release builds. The setting mirrors to the watch.

## The watch

### First run

There is no onboarding. The app opens to Today.

- **When the phone finishes setup:** a one-line message says "Connected to <phone name>".
- **If LogDate isn't on the phone:** a card says "Get LogDate on your phone". It opens the Play listing on the phone through `RemoteActivityHelper`. `PhoneTypeHelper` confirms the phone runs Android before the card appears.

Recording works immediately. Each permission is asked the first time something needs it:
- **Microphone:** the first recording.
- **Location:** after the first save ("Save places with your moments?").
- **Heart rate:** the first Mark.
- **Notifications:** the first locked recording, or turning on reminders.
- **Activity recognition:** turning on reminders.

### Today and the moment list

Home is two pages in a `VerticalPagerScaffold`. The split isn't cosmetic. An `EdgeButton` reaches its full size only at the end of a scrolling list, so Record can't share one long list with the moments.

**Today** shows:
- the date in `TimeText`;
- the campfire;
- a line like "3 moments · 2 places · 4:12 voice";
- the latest moment.

Below that, a `ButtonGroup` holds Mark and +, and the Record `EdgeButton` sits at the bottom.

**+** opens a sheet with:
- **Write:** RemoteInput, so voice or keyboard.
- **Mood.**
- **Phone camera:** shown only while the phone is in reach.
- **Microphone:** shown only when there is more than one input to choose from.

After a save, a row of five feelings stays up for 4 seconds next to Undo, so a mood takes one tap.

**The second page** is a `TransformingLazyColumn` with:
- moments from today back through the last 7 days, each playable inline;
- "On this day" and this week's Rewind, shown as cards when they exist;
- "Earlier", which opens the rest of the library window;
- Settings at the end.

Turning the crown up at the top of the list returns to Today. Paging is locked while recording, because Stop lives on Today.

### Moment detail

- Playback with crown scrubbing, which ticks as you scrub.
- Speed: 1×, 1.5×, 2×.
- Skip silence.
- Captions from the transcript, when the phone has one.
- The place.
- Star. Stars are the phone's favorites, so while the phone is away the request waits and is sent later.
- Play on…
- Open on phone, through `OpenOnPhoneDialog`.
- Delete, confirmed with an `AlertDialog`.

### Recording

Recording is built around one gesture:
- **Hold:** hold Record to talk.
- **Lock:** while holding, slide up about 56 dp to a lock. A haptic click confirms it, and the button turns into Stop, with Pause and Discard beside it.
- **Release:** an unlocked recording saves.
- **Tap:** starts a recording that is already locked.
- **TalkBack:** a "Start recording" action.

Telling a hold apart from a scroll works like this:
- **Before the commit point:** the gesture only watches. The commit point is the system long-press timeout, so it respects the accessibility touch-and-hold delay. If the pager claims the drag first, the gesture steps aside and the pager scrolls.
- **After the commit point:** the gesture owns every touch event. Sliding sideways off the button doesn't end the recording, which keeps the fix from `8959ec5ac`.

While recording:
- an Ongoing Activity shows the recording on the watch face;
- ambient mode shows a timer that updates once a minute;
- a waveform follows the edge of the screen.

### Audio

"Play on" offers three targets:
- **The watch speaker.**
- **Headphones connected to the watch.** These go through the system output switcher (`com.android.settings.panel.action.MEDIA_OUTPUT`), or Bluetooth settings that close once a device connects.
- **The phone.** It plays through its own speaker, earbuds, car or Cast, and the watch becomes the remote.

The choice is remembered per output context, and earbuds are picked as soon as they connect.

Playback starts without waiting for the whole file. The watch reads audio by byte offset, from the phone over a channel or from the cloud with HTTP range requests. A Media3 session on each device puts playback in the system media controls, and "Continue on phone" or "Continue on watch" resumes at the same position. Audio inside the library window downloads ahead of time on the charger or Wi-Fi.

### Settings on the watch

- **A status card for the phone and the cloud.** It's empty when everything is fine.
- **Reminders, Places, Haptics** (All or Essential), and **Hold to talk**.
- **"More on your phone"**, which opens the phone's Watch page.

### Tiles and complications

Tiles use `Material3TileService` with `protolayout-material3`, so they follow dynamic color:
- **Today:** a `primaryLayout` with a Record `textEdgeButton`.
- **On this day.**

Complications:
- **Campfire:** a `RANGED_VALUE` whose color follows the campfire phase.
- **Record:** opens the app and starts recording. A microphone foreground service can't start from the background, so this one has to open the app.
- **Mark:** saves the moment and confirms with a haptic, without opening the app. Coming from the background, it can't read location, so the phone fills in the place afterwards.
- **On this day.**

Entry Count and Mood are retired. Every surface updates right after a capture. Updates caused by sync are limited to one every 5 minutes or so, per the complication guidance.

## The phone companion

### Setup

- **In onboarding:** if a Wear OS watch is connected, an optional "Your watch" step follows Notifications.
  - **LogDate already on the watch:** setup has already happened in the background, and the step says "LogDate is set up on your watch".
  - **Not installed yet:** one "Add to watch" button links the watch and opens LogDate's Play listing on it. The step follows along and moves on by itself once the app is installed.
- **A watch paired later:** a one-time card on Home says "LogDate is ready on your <watch name>".

### The Watch page

The watch moves out of Privacy & Security into a device card near the top of Settings. The card shows the watch's name, "Up to date", and "14 days on your watch". The page itself has:

- **At the top:** a drawing of the watch showing the real Today tile, with a status line.
- **On your watch:** how many days to keep (7, 14 or 30), On this day, Rewind, and when audio downloads.
- **Capture:** places, mood after recording, and the microphone.
- **Reminders.**
- **Tiles & complications:** a gallery with how-to steps.
- **Notifications:** watch notes arrive silently unless you ask to be told.
- **Check connection:** runs a list of checks and fixes what it can (reinstall, set up again, resync) instead of listing tips.
- **Remove watch.**

The watch also appears in Devices as Wear OS, with when it was last active.

### Continuity

- **Provenance.** Moments captured on the watch show a small watch glyph in the timeline and the editor.
- **Live recording.** While the watch records, the phone shows "Recording on your watch · 0:42" with a Stop button.
- **Marked moments.**
  - A mark shows up in the phone timeline as its own entry, such as "3:42 PM · Golden Gate Park".
  - Photos taken within 15 minutes either side are suggested inline with one-tap Attach, plus an optional "What was happening?".
  - There is no notification for each mark. If the evening reminder fires, it can add a line like "2 marks to fill in".
- **Open on phone.** Tapping it on the watch opens the editor or the share sheet at that moment.
- **Play on phone** and **Continue on watch.**

## Reminders

Reminders replace the fixed morning and evening prompts. They're off until turned on, through one switch shared by the phone and the watch.

**Triggers.** When reminders are on, a reminder can follow:
- **Arriving somewhere new:** after staying at least 10 minutes.
- **A workout ending:** within 30 minutes.
- **The evening wind-down:** when nothing has been written today, or marks are waiting to be filled in.
- **The campfire reaching embers:** with nothing logged and 3 hours or less left in the day.

**Limits.** At most one reminder per day across both devices. Each device records when it last sent one, and the later of the two times wins. Before sending, a device claims the day from its peer, so two devices only double up when they can't reach each other.

**Which device rings.** The watch reports whether it's on the wrist using its off-body sensor:
- **Watch on the wrist** and supporting reminders: the phone asks it to post the reminder, with a Record action.
- **Otherwise:** the phone posts it, with a bridge tag that the watch excludes through `BridgingManager`, so it never rings on both.

## Visual system

- **Color.** Dynamic color everywhere, using `dynamicColorScheme(context)` on Wear OS 6. Shared neutral fallbacks replace the template purples on both devices.
- **Shared tokens** live in `client/ui` and are used by both apps:
  - spring motion;
  - the Record shape change, using `graphics-shapes`;
  - glyphs for watch, mark, record, lock and campfire. Monochrome copies are generated for tiles and complications, and a test keeps the two sets matching.
- **Haptics.** The watch adopts the phone's semantic `HapticEvent` vocabulary, extended with RecordingLocked, Paused, Resumed, MomentMarked, LimitApproaching, ActionRejected and DetentTick. Haptics are reserved for changes of state.

## How sync works

### The watch as a peer

The watch runs the same sync engine as the phone: `DefaultSyncManager`, `SyncPayloadCipher` and the outbox. It runs with a `LinkedWatch` profile:

- It never creates an identity key. If the key is missing, sync pauses with `NEEDS_DEVICE_LINK`.
- It skips the first-sync sweep, which would otherwise queue everything the phone sent.
- It defers media downloads.
- It skips remote items outside the library window. The cursor still advances past them.
- It turns off drafts and location history.

### Choosing a path

A `WatchSyncRouter` decides what happens to each item. It records the decision in a durable `peer_delivery` ledger, so the decision survives restarts.

- **The phone is nearby:** the item goes over the Data Layer.
- **The phone is reachable only through Google's cloud relay:** records still go over the Data Layer. Media goes straight to the cloud if the watch is linked.
- **The phone has the item but no cloud account:** the item is done.
- **The phone has the item and is signed in:** the router waits up to 15 minutes for the phone to report it backed up, then uploads it directly if it's allowed to.
- **Uploading directly requires all of:**
  - the watch is linked;
  - the server supports device linking and conditional create;
  - the watch has a validated internet connection that isn't the Bluetooth proxy through the phone.
- **Audio on LTE** waits for Wi-Fi or the charger.
- **Anything else:** the router waits and records the reason.

### Receipts

Each item moves through `ON_PHONE`, then `BACKED_UP`. Two other outcomes exist: `DELETED_ON_PHONE`, and `REJECTED_UNSUPPORTED` for an older phone that can't read it.

The watch asks for receipts with a request/response call (`MessageClient.sendRequest`), and the phone also pushes them on a best-effort basis. Every receipt carries the content version it applies to, so a receipt for an older version can't clear a newer edit.

### Only creates and deletes go direct

When the watch uploads to the cloud itself, it only sends creates and deletes. Edits go through the phone, so resolving conflicts never happens on the watch.

Creates use a new conditional form, `If-None-Match: *`. The server answers:
- **201:** created.
- **412:** the entry already exists, along with its server version.
- **410:** the entry was deleted.

This makes a duplicate upload genuinely harmless. Against an older server without this, the watch never uploads directly.

### Setting up the watch without a sign-in

The phone and watch run a short exchange over the Data Layer:

1. **The phone offers:** it sends a one-time public key and a nonce (`/logdate/link/offer`).
2. **The watch accepts:** it replies with its own public key and nonce.
3. **Both derive keys:** each side runs X25519, then HKDF-SHA256 over a transcript of the exchange.
4. **The phone sends the bundle,** sealed with AES-256-GCM (`/logdate/link/bundle`). It contains:
   - the account and server details;
   - a single-use link code that expires in 120 seconds;
   - the derived identity key and media key;
   - feature flags;
   - a snapshot of settings.
   
   The recovery phrase is never sent, because it can also recover the account itself.
5. **The watch confirms** with an HMAC.

Offers expire after 60 seconds, and replayed nonces are rejected. None of this goes into DataItems, because DataItems are stored and replicated.

The watch then redeems the link code for its own session. A journaled `LinkedDeviceInstaller` installs the keys, saves the session and moves anything captured before setup to the account. Because each step is journaled, a crash midway can resume.

Without a cloud account, the same exchange runs in pair-only mode: the devices share settings and state, and no cloud link is made.

### The library window

The phone keeps the watch's library current. Each item is its own DataItem at `/logdate/v2/p2w/window/<kind>/<id>`, and deletions are published as tombstones.

- **Reasons.** Each item records why it's on the watch: recent, on this day, Rewind, or starred.
- **Large bodies.** Anything over 90 KB is sent over a channel and referenced by its SHA-256.
- **Daily refresh.** A daily job rolls the window forward.
- **Reconciliation.** The watch reads `getDataItems` on start and whenever the phone comes back, which picks up any change it missed.

The campfire is computed on the phone and sent over. A 14-day window can't produce a 41-day run, so the watch only marks today as lit when it captures something itself.

### Shared state

Each device publishes one state document at `/logdate/state` (schema 1, under 8 KB). Every field has a single owner:

- **Phone-owned:** feature flags, cloud status, the campfire, the day-start hour, the time zone, open marks, and the phone's supported features.
- **Watch-owned:** outbox counts, free space, link state, whether it's being worn (with the time it was checked), and the watch's supported features.
- **Shared:** preferences and the reminders switch. The most recent write wins; on a tie, the phone wins.

Features are named, for example `receipts.v1`, `window.v1`, `moments.v1`, `live.v1`, `reminders.v1`, `link.cloud.v1`, `phone.camera.v1`, `phone.playback.v1` and `prefs.v1`. A surface appears only when the other device advertises the feature it needs.

The new protocol switches on only when three things are true: the flag is on, the other device advertises the new sync capability (`logdate_phone_sync_v2` or `logdate_watch_sync_v2`), and it lists the feature in use. Otherwise both devices keep using today's protocol, so an old phone with a new watch, or the other way round, keeps working.

### Live messages and requests

Streams of state use messages; commands use requests that wait for a reply.

| Path | Kind | Purpose |
|---|---|---|
| `/logdate/live/recording` | message | Watch recording state, in sequence order |
| `/logdate/live/recording/command` | request | Stop or pause from the phone |
| `/logdate/live/playback` | message | Playback state |
| `/logdate/live/playback/command` | request | Play, seek, speed, continue |
| `/logdate/reminders/deliver`, `/logdate/reminders/claim` | request | Reminders |
| `/logdate/presence` | request | One off-body reading, 2 s timeout |
| `/logdate/diag/ping` | request | Check connection |
| `/logdate/favorites/set` | request | Star from the watch |

### Marked moments

A marked moment is its own synced record, not a kind of note, for two reasons. Older phones decode notes with a closed serializer: an unknown note type would be dropped and never acknowledged, and the watch would resend it forever. And an unknown content type breaks the cloud download on older clients.

```kotlin
MarkedMoment(
    id, capturedAt, timeZoneId, location?, heartRate?,
    state,            // OPEN < DISMISSED < FILLED; merges take the highest
    caption,          // encrypted
    resolvedNoteIds,  // merges take the union
    originDeviceId, lastUpdated, syncVersion,
)
```

- **On the device:** marked moments live in the Room table `marked_moments`, with `EntityType.MOMENT` in the outbox.
- **On the server:** they live in a new `MOMENT` collection (`MOMENTS_V1`). Older clients never request it.
- **Excluded elsewhere:** marks are left out of moment inference, Rewind, widgets and the library.

### Provenance

`JournalNote` gains `capturedOn: CaptureDevice?`, either `PHONE` or `WATCH`. For notes from older watch builds, the phone stamps `WATCH` as they arrive.

## Server changes

- **Device sessions:**
  - **Tables:** `device_sessions` and `device_link_codes`.
  - **Tokens:** every JWT gets a `jti`, and decoding now tolerates unknown claims. Linked devices also get `sid` (the session) and `scp=sync`.
  - **Refresh:** a refresh with a `sid` checks that the session is still active, and extends its expiry when fewer than 15 days are left.
  - **`/auth/me`:** keeps the `sid`, so a revoked device can't mint a fresh full-access token.
  - **Scope limits:** `scp=sync` tokens can't touch account, passkey, link-minting, identity or billing routes.
- **Routes:**
  - `POST /auth/devices/link`
  - `POST /auth/devices/link/redeem`
  - `GET /auth/devices`
  - `DELETE /auth/devices/{id}`
  - `DELETE /auth/devices/self`
- **Conditional create:**
  - `If-None-Match: *` on `PUT /contents`, `/journals` and `/moments`.
  - `HEAD /contents/{id}`.
  - `POST /associations?ifAbsent=true`.
  - `reserveIfAbsent` locks the collection state row (`SELECT … FOR UPDATE`), so two concurrent creates can't both win.
- **The `MOMENT` collection**, with its own routes.
- **Rollout order:** the server change that tolerates unknown claims must deploy before any client holds a `sid` token. Otherwise rolling the server back would lock linked watches out.

## Compatibility and rollout

- **No mixed-version breakage.** Every pairing of old and new phone and watch builds keeps working: the new protocol only switches on when both devices advertise it.
- **The unknown-type fix ships first.** It fixes a bug that exists today. Marks don't depend on it reaching users, because they live in their own collection.
- **Migrations.** Room 50 adds `marked_moments` and `peer_delivery`. Room 51 adds the `captured_on` columns and the device-only `mark_photo_decisions`. Renumber against main when these land.
- **Flipping the flag** waits for a dogfood pass on real hardware through the Play internal track.

## Libraries

These are the versions on Google Maven as of 2026-10-02.

| Artifact | Version | Replaces |
|---|---|---|
| `androidx.wear.compose:compose-material3`, `compose-foundation` | 1.7.0 | 1.6.1 |
| `androidx.wear.compose:compose-navigation3` | 1.7.0 | the unused `compose-navigation` |
| `androidx.wear.compose:compose-ui-tooling` | 1.7.0 | `horologist-compose-tools` |
| `androidx.wear.tiles:tiles` (+ tooling), using `Material3TileService` | 1.6.2 | `horologist-tiles` |
| `androidx.wear.protolayout:protolayout`, `-expression`, `-material3` | 1.4.2 | `tiles-material` |
| `androidx.wear:wear-ongoing` | 1.1.0 | (new) |
| `androidx.wear:wear` | 1.4.0 | (new, for ambient mode) |
| `androidx.wear:wear-input` | 1.2.0 | (new, for RemoteInput) |
| `androidx.wear:wear-remote-interactions` | 1.2.0 | (new on the watch) |
| `androidx.wear:wear-phone-interactions` | 1.1.1 | (new) |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 | (new) |
| `androidx.health:health-services-client` | 1.1.0 | 1.1.0-rc01 |

Also removed from the watch: `compose-material`, `com.google.android.material`, and `material-icons-extended`.

## Delivery

Each track below gets its own implementation plan. Every step is a test-first commit that leaves main shippable.

1. **Prerequisites.** Land the dogfood branch on main. Require Wear OS 5. Add `WATCH_EXPERIENCE_V2`, the Labs screen and the `reminders` commit scope.
2. **Track A: sync, identity and server.**
   - **A. Reliability fixes.** Unflagged, and compatible with today's protocol in both directions.
   - **B. Server work.** Tolerant tokens, device sessions, conditional create, the `MOMENT` collection.
   - **C. Foundations.** Protocol types, flag mirroring, migrations, key handling, the `LinkedWatch` profile.
   - **D. Behind the flag.** Pairing, cloud linking, the router, the library window, streaming, marked-moment sync, shared reminder state, live messages, revocation.
3. **Track B: the watch app.** Fixes first. Then the design system and shell, Today, the recorder, capture, the moment list and playback, settings, reminders, tiles and complications.
4. **Track C: the phone companion and reminders.** The unknown-type fix first. Then settings cleanup, the watch status model, provenance, marked moments in the timeline, the reminder engine, the Watch page, the onboarding step, and removing a watch.
5. **Track D: continuity.** Live recording on the phone, Open on phone, Play on phone, Continue on watch.
6. **Finish.** Flip the flag, delete the old screens and fixed-time prompts, and update the docs.

Tracks A and B run in parallel from the start. C's first wave needs nothing from the others. D comes last, because it needs live messages and the matching watch screens.

## Testing

- **Unit tests** for every policy and calculator:
  - the router;
  - state merging;
  - the window policy, including DST, Feb 29 and the day-start hour;
  - the reminder orchestrator;
  - the Today summary;
  - photo suggestion windows.
- **Crypto tests** check the handshake against the RFC 7748 and RFC 5869 test vectors, and confirm that tampering with the transcript or ciphertext fails.
- **Server tests:**
  - **Duplicate create:** makes zero commits, leaves the version unchanged and adds nothing to the change feed.
  - **Create after delete:** returns 410.
  - **Device links:** each link works exactly once.
  - **Revocation:** after a device is revoked, its refresh fails and `/auth/me` can't get around it.
- **Client and server together** (`:integration:server-client-e2e`):
  - linking a watch;
  - uploading what it captured before it was linked;
  - revoking it;
  - backup through the watch's own connection producing exactly one server version;
  - marked moments syncing.
- **Watch, on Gradle Managed Devices:**
  - **The record gesture:** tap; hold; lock; a quick fling that scrolls; no paging after commit; sliding sideways still holds; TalkBack; a long system long-press timeout.
  - **Navigation:** the pager with the crown, navigation, the capture sheet and moment detail.
  - **Platform behavior:** the Ongoing Activity, the Mark receiver, the flag gate, manifest filters, the sync worker and the dependency graph.
- **Phone, on Gradle Managed Devices:**
  - the onboarding watch step;
  - attaching photos to a mark;
  - the reminder notification;
  - handoff links;
  - the live recording chip;
  - Play on phone.
- **Screenshots:**
  - **Watch:** every state, on small and large round screens, plus large-font versions of Today, moment detail and the + sheet.
  - **Phone:** every new scene in `SharedScreenshotCatalog`.
- **On real hardware**, through the internal track and `docs/testing/wear-dogfood-checklist.md`: Companion Device Manager presence, starting a foreground service from the background, the off-body sensor and LTE.

## Risks

- **Play review.** Bringing back `ACTIVITY_RECOGNITION` and the boot permission changes what Play shows users. Health Connect workout reads need a Play Console declaration; without it, reminders ship without the workout trigger.
- **Dynamic color reach.** Dynamic color exists only on Wear OS 6, so most watches will show the fallback scheme, and screenshot tests can't render dynamic color.
- **The off-body sensor** is missing on emulators and some watches. When the watch can't tell whether it's worn, reminders go to the phone.
- **Speculative recording start.** The recorder starts on press so nothing is lost, and the start is discarded if the touch turns into a scroll. If that flickers on real hardware, recording starts at the commit point instead.
- **Concurrent writes.** Phone and watch uploading at the same time contend for the AT Protocol repository head, which retries five times. Client backoff covers the rest.
- **Update limits.** Complications should update about once every 5 minutes on average. Tiles can update at most every 20 seconds.
- **Instant playback** depends on reading by byte offset, because the m4a index sits at the end of the file. This needs checking against real recordings.
