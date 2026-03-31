# Android Media Storage

LogDate's Android media manager now publishes supported image and video attachments to the platform `MediaStore` so they appear automatically in the device library and editor picker.

## Behavior

- `saveMedia` and `saveMediaFromFile` publish supported media types to `MediaStore` under `Pictures/LogDate` or `Movies/LogDate`.
- `addToDefaultCollection` upgrades entry attachments into `MediaStore` when they are not already there.
- `getRecentMedia` and `queryMediaByDate` query `MediaStore` directly.
- Legacy app-private files under `filesDir/user_media` are backfilled on first library access so older entry media becomes visible without manual migration.
- Unsupported MIME types still fall back to app-private storage.
- Existing legacy URIs remain readable for compatibility.

## Backfill Rules

- Backfill is idempotent.
- Existing files in `filesDir/user_media` are not deleted.
- The Android implementation uses `DATE_TAKEN` when available and falls back to `DATE_ADDED` for older rows.

## Validation

The Android regression test for this behavior lives in:

`app/android-main/src/androidTest/kotlin/app/logdate/client/media/AndroidMediaManagerTest.kt`

Run it with:

```bash
./gradlew :app:android-main:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.client.media.AndroidMediaManagerTest
```
