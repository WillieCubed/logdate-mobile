# Quick Settings Tiles

Status: planned, behind `QUICK_SETTINGS_TILES`. The Wear app already has its own tiles; this guide
covers phone and tablet Quick Settings.

## What it is

Quick Settings is the grid of toggles that opens when someone swipes down twice from the top of the
screen. Apps can add their own tiles with a `TileService`. The person chooses which tiles to show,
from the edit screen of the shade, so a tile is a shortcut the person has deliberately placed.

A tile has a label, an optional subtitle, an icon, and a state: inactive, active, or unavailable.
The system draws it. The app's `TileService` is bound only while the shade is open, and it is told
when the tile is added, shown, hidden, or clicked.

## What the person sees

| Tile | Inactive | Active | On click |
| --- | --- | --- | --- |
| New entry | "New entry" | n/a | The shade closes and the editor opens in text mode |
| Record voice | "Record voice" | "Recording · 0:42" | Starts a voice entry, or returns to the running recording |

Screenshots are added here when the tiles ship: both tiles in the shade, the active voice tile, and
light and dark themes on phone and tablet.

## How it works

- **Services.** `NewEntryTileService` and `RecordVoiceTileService` live in
  `app/compose-main/src/androidMain/`. Each is declared in the manifest with the
  `android.service.quicksettings.action.QS_TILE` intent filter and the
  `BIND_QUICK_SETTINGS_TILE` permission.
- **Opening the app.** Since Android 14 a tile must open activities with
  `startActivityAndCollapse(PendingIntent)`. Both tiles send the
  [capture launch contract](capture-launch-contract.md) intent.
- **Active state.** The voice tile declares `TileService.META_DATA_ACTIVE_TILE`, so the app can update
  it while the shade is closed. When recording starts or stops, `RecordingServiceController` calls
  `TileService.requestListeningState`. The tile then reads the current state and shows "Recording"
  with the elapsed time.
- **Starting the microphone.** The voice tile's click behavior depends on a spike. Android limits
  starting a microphone foreground service from the background. If a tile click counts as a valid
  start, the tile starts recording directly. Otherwise it opens the editor in voice mode, and the
  editor starts recording. The spike result is recorded in the "Spike result" section below.
- **Asking to add the tile.** `StatusBarManager.requestAddTileService` can show a system prompt that
  asks the person to add a tile. If LogDate uses it, the prompt appears only from a settings action
  the person chose, never on its own.

### Spike result

Pending.

## Flag and rollout

`QUICK_SETTINGS_TILES`, off by default. The services are declared `android:enabled="false"`, and
`PlatformComponentToggler` enables them when the flag turns on. While the flag is off, the tiles do
not appear in the tile picker at all.

## Privacy

Tiles show only the recording state and elapsed time, never entry content. Capture from a tile still
passes the app lock before the rest of the journal is reachable.

## How to test

- Host tests cover the tile state mapping (idle, recording, paused) and the click intents.
- On an emulator with the flag on, add the tile from the shade's edit screen.

## Updating screenshots

Tiles are system UI, so they are Lane 2 captures. `OsSurfaceScreenshotsTest` opens Quick Settings with
`UiDevice.openQuickSettings()` in demo mode. Record them with `./run screenshots:os`. They land in
`docs/screenshots/android-os-integration/quick-settings-tiles/`.
