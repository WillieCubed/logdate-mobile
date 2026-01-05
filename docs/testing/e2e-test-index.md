# E2E Test Index

Complete index of all end-to-end tests in the LogDate project with terminal commands for running them.

## Organization Standard

Tests are organized into three categories:
- **Pure Server Tests**: Gradle `testApplication` tests, no device needed
- **Client Gradle Tests**: Android instrumented tests with gradle framework
- **Client ADB Tests**: Verify actual device behavior via adb shell scripts

See [E2E Test Organization Standard](./e2e-test-organization.md) for the complete framework and guidelines for adding new tests.

## Quick Start Commands

```bash
# Run all e2e tests
./gradlew connectedAndroidTest

# Run all server-side e2e tests
./gradlew :server:test -k "E2ETest"

# Run specific e2e test suite
./gradlew :app:compose-main:connectedAndroidTest -k "TestClassName"

# Run with verbose output
./gradlew connectedAndroidTest --info

# List available e2e tests without running
./gradlew --dry-run connectedAndroidTest
```

## Client-Side E2E Tests (Android/Compose)

Client-side tests verify complete user workflows on the Android app using Espresso, Compose testing, and activity scenarios.

### Location
`app/compose-main/src/androidTest/kotlin/app/logdate/client/e2e/`

### 1. Multi-Window Editor E2E Tests

**File**: `MultiWindowEditorE2ETest.kt`

**Scenario**: User edits multiple journal entries simultaneously in separate windows

**What it tests**:
- Opening existing entries in new editor windows
- Creating new blank editor windows
- Independent state management across multiple windows
- Intent extras (entry_id, journal_id, initial_text, attachments)
- Multi-window flags (FLAG_ACTIVITY_NEW_DOCUMENT, FLAG_ACTIVITY_MULTIPLE_TASK)
- Split-screen and multi-window support
- Task affinity configuration for separate task stacks
- Window metrics and configuration changes

**Requirements**:
- Connected Android device or emulator with API 24+ (Android N+)
- Device must support multi-window mode (most modern devices do)
- Recommended: Pixel 4 or Pixel Tablet emulator for full multi-window features

**Commands**:

```bash
# Run all multi-window editor tests
./gradlew :app:compose-main:connectedAndroidTest -k "MultiWindowEditorE2ETest"

# Run specific test
./gradlew :app:compose-main:connectedAndroidTest -k "testOpenEntryInNewWindow"

# Run with debugging enabled
./gradlew :app:compose-main:connectedAndroidTest -k "MultiWindowEditorE2ETest" --debug

# Run with connected device (verify with: adb devices)
adb devices
./gradlew :app:compose-main:connectedAndroidTest -k "MultiWindowEditorE2ETest"
```

**ADB Shell Testing**:

For comprehensive device behavior verification using adb shell commands, use the dedicated shell script:

```bash
# Run full test suite
./tests/e2e/test-multi-window-editor.sh

# Run quick smoke test
./tests/e2e/test-multi-window-editor.sh --smoke

# Run with verbose output
./tests/e2e/test-multi-window-editor.sh --verbose

# See all options
./tests/e2e/test-multi-window-editor.sh --help
```

See: `tests/e2e/test-multi-window-editor.sh` for details on testing actual window behavior, task recents, and multi-window integration.

**Test Coverage**:
- ✅ testOpenEntryInNewWindow - Existing entry loading
- ✅ testCreateNewBlankEditorWindow - New entry creation
- ✅ testCreateEditorWindowWithInitialContent - Initial text/attachments
- ✅ testOpenDifferentEntryTypesInSeparateWindows - Various note types
- ✅ testEditorActivityIsInSeparateTask - Task isolation
- ✅ testEditorActivitySupportsSplitScreen - Split-screen support
- ✅ testEditorWindowHandlesConfigurationChanges - Rotation/foldable support

**Related Documentation**:
- [E2E Test Journeys](./e2e-test-journeys.md#55-multi-window-editor-journey) - Full user journey
- [EditorActivity.kt](../../app/compose-main/src/androidMain/kotlin/app/logdate/client/EditorActivity.kt) - Implementation
- [EditorManager.kt](../../app/compose-main/src/androidMain/kotlin/app/logdate/navigation/EditorManager.kt) - Window management

---

## Server-Side E2E Tests (Ktor)

**Pure Gradle Tests** - No device/emulator required. Use Ktor's `testApplication {}` framework for API endpoint testing.

Server-side tests verify API endpoints and backend functionality using Ktor's test application DSL.

### Location
`server/src/test/kotlin/e2e/`

### 1. Account E2E Tests

**File**: `accounts/AccountE2ETest.kt`

**Scenario**: Account creation, authentication, and management

```bash
./gradlew :server:test -k "AccountE2ETest"
./gradlew :server:test -k "accountCreationBegin"
```

### 2. Authentication Flow E2E Tests

**File**: `auth/AuthenticationFlowsE2ETest.kt`

**Scenario**: User authentication workflows including passkey registration

```bash
./gradlew :server:test -k "AuthenticationFlowsE2ETest"
```

### 3. Stub Endpoints E2E Tests

**File**: `stubs/StubEndpointsE2ETest.kt`

**Scenario**: Stub/mock endpoints for testing

```bash
./gradlew :server:test -k "StubEndpointsE2ETest"
```

### 4. Basic Endpoint Coverage E2E Tests

**File**: `basic-coverage/BasicEndpointCoverageE2ETest.kt`

**Scenario**: Coverage of all API endpoints

```bash
./gradlew :server:test -k "BasicEndpointCoverageE2ETest"
```

### 5. Edge Cases E2E Tests

**File**: `edge/EdgeCasesE2ETest.kt`

**Scenario**: Error handling and edge case scenarios

```bash
./gradlew :server:test -k "EdgeCasesE2ETest"
```

---

## Test Organization Summary

| Test Name | Type | Location | Shell Script |
|-----------|------|----------|--------------|
| MultiWindowEditorE2ETest | Client Gradle | `app/.../e2e/` | ✅ `test-multi-window-editor.sh` |
| AccountE2ETest | Pure Server | `server/.../accounts/` | ❌ None |
| AuthenticationFlowsE2ETest | Pure Server | `server/.../auth/` | ❌ None |
| BasicEndpointCoverageE2ETest | Pure Server | `server/.../basic-coverage/` | ❌ None |
| EdgeCasesE2ETest | Pure Server | `server/.../edge/` | ❌ None |
| StubEndpointsE2ETest | Pure Server | `server/.../stubs/` | ❌ None |

**Legend**:
- **Client Gradle**: Android instrumented test + optional adb shell script
- **Pure Server**: No device needed, pure gradle testApplication
- **Shell Script**: Dedicated script for adb-based device behavior testing

---

## Running All E2E Tests

### Run Everything

```bash
# All e2e tests (client + server)
./gradlew connectedAndroidTest :server:test -k "E2ETest"

# Client only
./gradlew connectedAndroidTest

# Server only
./gradlew :server:test -k "E2ETest"
```

### Run by Category

```bash
# All multi-window tests
./gradlew connectedAndroidTest -k "MultiWindow"

# All account tests
./gradlew :server:test -k "Account"

# All authentication tests
./gradlew :server:test -k "Authentication"
```

### Run with Reports

```bash
# Generate test report
./gradlew connectedAndroidTest --tests "*E2ETest" --info

# Generate HTML report
./gradlew connectedAndroidTest testReport

# View report (after build)
# HTML report location: build/reports/tests/
```

---

## CI/CD Integration

E2E tests are automatically run in continuous integration on:

- **Pull Requests**: Client-side e2e tests on emulator
- **Main Branch**: Full e2e test suite (client + server)
- **Release Builds**: Extended test validation

Configuration: `.github/workflows/ci.yml`

### Manual CI Simulation

```bash
# Run full CI test suite locally
./gradlew clean test :server:test -k "E2ETest"

# Just the main quality gates
./gradlew connectedAndroidTest lint koverVerify
```

---

## Debugging E2E Tests

### Enable Logging

```bash
# Run with napier debug logging
./gradlew :app:compose-main:connectedAndroidTest \
  -k "MultiWindowEditorE2ETest" \
  --info

# Monitor logcat during tests
adb logcat | grep -i "MultiWindowEditorE2ETest\|EditorActivity"
```

### Capture Screenshots on Failure

```bash
# Screenshots are saved to device
adb shell ls /storage/emulated/0/Pictures/

# Pull screenshots from device
adb pull /storage/emulated/0/Pictures/
```

### Debug with Activity Monitoring

```bash
# Watch activity lifecycle during test
adb shell dumpsys activity | grep -A 10 "EditorActivity"

# Monitor window changes
adb shell dumpsys window | grep -i "window\|activity"

# Check memory during multi-window test
adb shell dumpsys meminfo app.logdate | tail -20
```

### Stop on First Failure

```bash
./gradlew :app:compose-main:connectedAndroidTest \
  -k "MultiWindowEditorE2ETest" \
  --no-parallel \
  --fail-fast
```

---

## Test Requirements Checklist

Before running E2E tests, verify:

- [ ] **Device Connected**: `adb devices` shows device
- [ ] **API Level**: Device has API 24+ (Android N+) for multi-window tests
- [ ] **App Installed**: Debug APK installed on device
- [ ] **Network**: If testing with backend, network connectivity available
- [ ] **Storage**: Device has minimum 500MB free storage
- [ ] **Battery**: Device battery > 50% (or connected to power)
- [ ] **Screen On**: Device screen is on or use `adb shell input keyevent KEYCODE_WAKEUP`

### Pre-Test Setup

```bash
# Build debug APK
./gradlew :app:compose-main:assembleDebug

# Ensure app is installed
adb install -r ./app/compose-main/build/outputs/apk/debug/app-debug.apk

# Clear app data before test
adb shell pm clear app.logdate

# Start app once to initialize
adb shell am start -n "app.logdate/.MainActivity"

# Return to home screen
adb shell input keyevent KEYCODE_HOME

# Now run e2e tests
./gradlew :app:compose-main:connectedAndroidTest -k "MultiWindowEditorE2ETest"
```

---

## Troubleshooting

### Device Not Found
```bash
adb kill-server
adb start-server
adb devices  # Should list device
```

### Tests Timeout
```bash
# Increase timeout (in gradle.properties)
android.testInstrumentationRunnerArguments.timeout=60000

# Or run with extended timeout
./gradlew :app:compose-main:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.timeout=120000
```

### Multi-Window Tests Skipped
- Device may not support multi-window (API < 24)
- Try with Pixel emulator: API 24+ recommended
- Check: `adb shell getprop ro.build.version.sdk` ≥ 24

### Activity Not Found
```bash
# Verify EditorActivity is registered in manifest
adb shell pm list activities | grep EditorActivity

# Check package name
adb shell pm list packages | grep logdate
```

---

## Adding New E2E Tests

To add a new e2e test:

1. **Create test class** in appropriate directory:
   - Client: `app/compose-main/src/androidTest/kotlin/app/logdate/client/e2e/`
   - Server: `server/src/test/kotlin/e2e/[category]/`

2. **Name it with E2ETest suffix**: `MyFeatureE2ETest.kt`

3. **Add to this index**: Document commands and requirements

4. **Document in e2e-test-journeys.md**: Add user journey if applicable

5. **Include terminal commands**: In doc comments for easy reference

6. **Test locally before committing**:
   ```bash
   ./gradlew connectedAndroidTest -k "MyFeatureE2ETest"
   ```

7. **Verify CI passes**: PR will run full test suite

---

## See Also

- [E2E Organization Standard](./e2e-test-organization.md) - Test categorization, shell script standards, and guidelines
- [Running E2E Tests](./running-e2e-tests.md) - Quick reference for running tests
- [Testing Strategy](./introduction.md) - Complete testing guide
- [E2E Test Journeys](./e2e-test-journeys.md) - User workflows being tested
- [Build Commands](../../CLAUDE.md#build-commands) - Project build docs
- [Android Testing Guide](https://developer.android.com/training/testing) - Official Android docs
