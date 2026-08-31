# Copilot Instructions for LogDate

Refer to `AGENTS.md` at the repository root for full development workflow, commit conventions, and coding standards.

## Device Safety

LogDate ships as `studio.hypertext.logdate`, and the retired `<logdate-package>` may still be installed alongside it. Either may hold the developer's real data. **Never suggest or generate** any of the following:

(`<logdate-package>` is either `studio.hypertext.logdate` or `co.reasonabletech.logdate`.)

- `adb uninstall <logdate-package>` (or any variant with flags like `-k`, `--user`, `-s <serial>`)
- `adb shell pm uninstall <logdate-package>`
- `adb shell pm clear <logdate-package>`
- `adb shell cmd package uninstall <logdate-package>`
- `adb shell rm -rf /data/data/<logdate-package>`
- Any Gradle `uninstall*` task (e.g., `./gradlew uninstallDebug`)

**Safe commands** (preserve app data):
- `adb install <path>.apk` (upgrade in place)
- `./gradlew installDebug` or `./run run:android`

If uninstall or data clearing is needed, the developer will do it manually.
