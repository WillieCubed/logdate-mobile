# Gemini Guidelines for LogDate

Refer to `AGENTS.md` at the repository root for full development workflow, commit conventions, and coding standards.

## Device Safety

The LogDate app (`co.reasonabletech.logdate`) may be installed on the developer's personal device with real data. **Never execute or suggest:**

- `adb uninstall` targeting this package (any variant, any flags)
- `adb shell pm uninstall co.reasonabletech.logdate`
- `adb shell pm clear co.reasonabletech.logdate` (clears all app data)
- `adb shell cmd package uninstall co.reasonabletech.logdate`
- `adb shell rm -rf /data/data/co.reasonabletech.logdate`
- Gradle `uninstall*` tasks (e.g., `./gradlew uninstallDebug`)

**Safe commands** (preserve app data):
- `adb install <path>.apk` (upgrade in place)
- `./gradlew installDebug` or `./run run:android`

If uninstall or data wipe is needed, stop and ask the developer.
