# Android Media Storage

Where the Android app keeps the photos, videos and recordings that entries show, and how they appear in the
device's photo library. Entries name these files with portable `logdate-media://` references, described in
[Media References](./media-references.md).

## Where files live

- **Photos and videos** that LogDate saves (`saveMedia`, `saveMediaFromFile`) go into a private,
  content-addressed store under `filesDir/media/objects/sha256/<first two hex digits>/<sha256>.<extension>`.
  Two entries that hold identical bytes share one file. An entry stores it as
  `logdate-media://library/objects/sha256/...`.
- **Voice recordings** are written to `filesDir/audio_notes` and stored as `logdate-media://recordings/<file>`.
- **Unsupported media types** are not stored; the manager refuses them rather than writing a file nothing can open.

Files are only deleted through `deleteOwnedMedia`, which removes a file inside the content-addressed store and
nothing else, so a photo that came from the user's own library is never touched. Callers delete a file only
after checking that no other entry still references it, comparing references in their stored form.

## The device photo library

- `addToDefaultCollection` publishes an entry's image or video to the system `MediaStore` under `Pictures/LogDate`
  or `Movies/LogDate`, so it appears in the device library and the editor picker.
- `getRecentMedia` and `queryMediaByDate` query `MediaStore` directly.
- Files that older builds kept in `filesDir/user_media` are published to `MediaStore` the first time the library
  is read. The backfill is idempotent, does not delete `user_media`, and uses `DATE_TAKEN` when it is available
  and `DATE_ADDED` for older rows.
- Existing `content://` and legacy file URIs still open.

## Validation

The Android regression tests for this behavior are
`app/android-main/src/androidTest/kotlin/app/logdate/client/media/AndroidMediaManagerTest.kt` and
`app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/PortableMediaReferencesE2ETest.kt`. Run them on a
Gradle Managed Device, never on a connected physical device:

```bash
./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.media.AndroidMediaManagerTest
```
