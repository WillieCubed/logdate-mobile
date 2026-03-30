# Copilot Instructions for LogDate

Refer to `AGENTS.md` at the repository root for full development workflow, commit conventions, and coding standards.

## Device Safety

The LogDate app (`co.reasonabletech.logdate`) may be installed on the developer's personal device with real data. **Never suggest or generate** any of the following:

- `adb uninstall co.reasonabletech.logdate` (or any variant with flags like `-k`, `--user`, `-s <serial>`)
- `adb shell pm uninstall co.reasonabletech.logdate`
- `adb shell pm clear co.reasonabletech.logdate`
- `adb shell cmd package uninstall co.reasonabletech.logdate`
- `adb shell rm -rf /data/data/co.reasonabletech.logdate`
- Any Gradle `uninstall*` task (e.g., `./gradlew uninstallDebug`)

**Safe commands** (preserve app data):
- `adb install <path>.apk` (upgrade in place)
- `./gradlew installDebug` or `./run run:android`

If uninstall or data clearing is needed, the developer will do it manually.
