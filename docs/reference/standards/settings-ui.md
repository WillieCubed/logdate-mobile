# Settings UI composition

Settings pages use the same row grouping as the main Settings page. Start with
`SettingsOverviewSections.kt` and `NativeBackupSettingsSection.kt` before adding a
new presentation. These are production examples of navigation and passive status rows.

## Choose an existing component

| Content | Component | Interaction |
| --- | --- | --- |
| Settings destination | `SettingsScaffold` | Owns page title, back action, scrolling and insets |
| Related rows | `SettingsSection` | Owns heading, container shape and spacing between rows |
| Opens another screen | `SettingsNavigationItem` | Whole row is clickable; supplies the trailing chevron |
| Status or an inline action | `SimpleSettingsItem` | Use `onClick = null` for passive information; supply an action only when useful |
| Boolean preference | `ToggleSettingsItem` | Represents a preference the app can actually change |
| Linked preference | `LinkedToggleSettingsItem` | Uses the established linked-toggle behavior |
| Feature-wide enablement | `MasterFeatureToggle` | Enables or disables a real feature, not a status indicator |
| Account or backup summary | `MaterialCard` | Uses theme shape and filled-card defaults; semantic colors are paired |

Android cloud backup and device transfer are platform-managed capabilities. Their
coverage rows are passive information, not in-app switches or navigation targets.
Keep LogDate Cloud account actions in the shared `SyncPromotionCard` and signed-in
status in `BackupStatusItem` rather than recreating those cards in each page.

## Emit rows directly

`SettingsSection` delegates to `MaterialContainer`, which spaces its **direct layout
children** by `Spacing.xs`. These visible gaps separate rows; the component does not
insert `HorizontalDivider` lines. Each row must remain a separate child.

```kotlin
SettingsSection(title = stringResource(Res.string.data_management)) {
    ExportDataItem(onShowExportOptions = onShowExportOptions)
    ImportBackupItem(onShowRestoreSheet = onShowRestoreSheet)
}
```

This wrapper loses the spacing because the container receives one `Column`:

```kotlin
// Incorrect: multiple rows become a single child of the settings container.
SettingsSection(title = stringResource(Res.string.data_management)) {
    Column {
        ExportDataItem(onShowExportOptions = onShowExportOptions)
        ImportBackupItem(onShowRestoreSheet = onShowRestoreSheet)
    }
}
```

Remove the wrapper to repair grouping. Do not compensate with dividers, nested
containers, per-row backgrounds or a new section API. A form with radio buttons,
explanatory text or controls may still need its own layout inside a section.

`checkSettingsUiContract` parses Kotlin and rejects layout wrappers around multiple
known settings rows. It runs with the core module's `check` and `ktlintCheck` tasks.
Its fixtures in `SettingsUiContractTest.kt` cover direct rows, import aliases,
conditional rows, forms, comments and strings. Extend the row-name set when adding
a shared row primitive. This check cannot inspect rows emitted by arbitrary helper
functions; review the emitted layout when extracting a helper.

## Use theme roles and tokens

Use `Spacing` for padding and gaps, and `MaterialTheme.typography` for text roles.
`MaterialCard` defaults to the theme's large shape, `surfaceContainerHighest` and
zero elevation. A semantic card may override both container and content colors;
LogDate Cloud pairs `secondaryContainer` with `onSecondaryContainer`. Preserve
those paired roles in loading, signed-in and signed-out states.

Use string resources for user-facing copy. Keep status descriptions short and put
recovery in the underlying platform. A status row should explain what is covered
or what happens next without exposing internal exceptions or offering choices the
app can resolve automatically.

## Examples and visual verification

`NativeBackupScreenshotScenes.kt` supplies the same signed-out, signed-in and
quota-limited states to Android previews and desktop screenshot tests. It explicitly
passes `NativeBackupStatus.Available`; the default `Unsupported` hides Android rows
and cannot demonstrate their layout. These are presentation fixtures, not evidence
of a Google provider backup or setup transfer.

Run the composition check and shared scene tests:

```sh
./gradlew :client:feature:core:checkSettingsUiContract :client:screenshot-scenes:desktopTest
```

Render and validate the shared native states with the desktop harness:

```sh
./gradlew -Dlogdate.desktopScreenshots.sceneFilter=native-backup \
  :app:compose-main:updateDesktopScreenshotTest --tests '*shared catalog matches baselines*'
./gradlew -Dlogdate.desktopScreenshots.sceneFilter=native-backup \
  :app:compose-main:desktopTest --tests '*shared catalog matches baselines*'
```

Inspect the images before accepting references. Compare the changed page with the
main Settings page at matching dimensions. Check row separation, card roles,
leading icons, action placement and readable wrapping in light, dark, compact,
landscape, tablet, RTL and 200% text configurations. Also inspect other callers when
changing a shared primitive: `MaterialCard` is used by audio recording displays.
See [Screenshot tests](../../testing/screenshot-tests.md) for the Android harness.

Validate Android references with
`./gradlew :app:android-main:validateDebugScreenshotTest --tests '*NativeBackup*'`.
For an intentional visual change, record candidates with the corresponding
`updateDebugScreenshotTest` task and inspect them before committing.
`NativeBackupSettingsTest.largeTextKeepsTheAccountFreeExportReachable` checks that
scrolling still reaches the archive action at 200% text, including quota feedback.
