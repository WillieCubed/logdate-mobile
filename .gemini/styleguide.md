# Gemini Guidelines for LogDate

Refer to `AGENTS.md` at the repository root for full development workflow, commit conventions, and coding standards.

## Device Safety

LogDate ships as `studio.hypertext.logdate`, and the retired `<logdate-package>` may still be installed alongside it. Either may hold the developer's real data. **Never execute or suggest:**

(`<logdate-package>` is either `studio.hypertext.logdate` or `co.reasonabletech.logdate`.)

- `adb uninstall` targeting this package (any variant, any flags)
- `adb shell pm uninstall <logdate-package>`
- `adb shell pm clear <logdate-package>` (clears all app data)
- `adb shell cmd package uninstall <logdate-package>`
- `adb shell rm -rf /data/data/<logdate-package>`
- Gradle `uninstall*` tasks (e.g., `./gradlew uninstallDebug`)

**Safe commands** (preserve app data):
- `adb install <path>.apk` (upgrade in place)
- `./gradlew installDebug` or `./run run:android`

If uninstall or data wipe is needed, stop and ask the developer.
