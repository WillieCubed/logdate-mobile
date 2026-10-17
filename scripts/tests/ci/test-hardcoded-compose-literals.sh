#!/usr/bin/env bash
# shellcheck disable=SC2016 # the `$` in single-quoted Kotlin fixtures is literal on purpose
# The hardcoded Compose literal check: on-screen text is reported wherever Compose puts it, including
# UI state built in ViewModels and domain models, and keys, logs, animation labels, previews and tests are not.
set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT
mkdir -p "$SANDBOX/scripts/lib"
cp scripts/check_hardcoded_compose_literals.sh "$SANDBOX/scripts/"
cp scripts/lib/hardcoded-compose-literals.awk "$SANDBOX/scripts/lib/"
git -C "$SANDBOX" init -q

UI="client/feature/demo/src/commonMain/kotlin/app/logdate/demo"

write() {
    mkdir -p "$SANDBOX/$(dirname "$1")"
    cat > "$SANDBOX/$1"
}

run_check() {
    local status=0
    OUTPUT="$(bash "$SANDBOX/scripts/check_hardcoded_compose_literals.sh" 2>&1)" || status=$?
    STATUS="$status"
}

reported() { assert_contains "$1" "$OUTPUT"; }
not_reported() { assert_not_contains "$1" "$OUTPUT"; }

write "$UI/Components.kt" <<'KT'
@Composable
fun PanelHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {}

@Composable
fun EmptyState(
    headline: String,
    body: String,
    onRetry: () -> Unit = {},
) {}

@Composable
fun PaneAction(icon: ImageVector, label: String, onClick: () -> Unit) {}

@Composable
fun SearchField(hint: String = "Search everything") {}

@Composable
fun Timeline() {}
KT

write "$UI/Screen.kt" <<'KT'
@Composable
fun Screen(state: State, playing: Boolean) {
    Text("Single line")
    Text(
        "Visits and journeys",
    )
    Text(text = "Named text")
    Text("Arrived on ${state.date}")
    Text("${state.count} · ${state.total}")
    Text("•")
    Icon(Icons.Rounded.Add, "Add to journal")
    Icon(
        Icons.Rounded.Pause,
        contentDescription = if (playing) "Pause" else "Play",
    )
    PanelHeader("Memory")
    EmptyState(
        headline = "Let's get started",
        body = "Grant photo access",
    )
    PaneAction(
        icon = Icons.Rounded.Sort,
        label =
            if (state.newestFirst) {
                "Show oldest first"
            } else {
                "Show newest first"
            },
        onClick = {},
    )
    Text(text = state.title.ifEmpty { "Untitled" })
    val description =
        when {
            state.recording -> "Stop recording"
            else -> "Start recording"
        }
    val error = state.error ?: "Give this place a name."
    // Text("Commented out")
    /* Text("Block comment") */
    Text(stringResource(Res.string.screen_title))
    Box(Modifier.testTag("screen_title")) {}
    val alpha by animateFloatAsState(1f, label = "Screen alpha")
    Napier.d(message = "Screen opened")
    when (state.kind) {
        "PHOTO" -> Text(stringResource(Res.string.photo))
        else -> Unit
    }
    File(dir, "export.json").writeText("Exported data")
    clipboard.setClip(ClipData.newPlainText("Copied text", state.title))
    check(state.ready) { "State is not ready" }
    throw IllegalStateException("Screen failed to load")
}

private fun Tool.displayName(): String =
    when (this) {
        Tool.SELECT -> "Select"
        Tool.INK -> "Ink"
    }

private fun Tool.key(): String = when (this) {
    Tool.SELECT -> "select_tool"
    Tool.INK -> "ink_tool"
}

@Preview
@Composable
private fun ScreenPreview() {
    Text("Preview sample")
    PanelHeader(title = "Preview title")
}

private val previewState = State(title = "Preview journal")
KT

write "$UI/Extras.kt" <<'KT'
@Composable
fun Extras(count: Int) {
    Sheet(summary = "$count places")
    Pinch(animationLabel = "Pinch transition")
}

@Composable
fun Sheet(summary: String) {}

@Composable
fun Pinch(animationLabel: String = "Pinch container") {}

internal fun sampleMemory(): Memory = Memory(summary = "Sample memory")

internal fun sampleRotatingMemory(): Memory {
    return Memory(summary = "Sample rotating memory")
}
KT

write "client/feature/demo/src/commonMain/kotlin/app/logdate/demo/Destinations.kt" <<'KT'
enum class Destination(val label: String) {
    Timeline(label = "Timeline"),
}

fun log() {
    Napier.i("Destination changed")
}
KT

write "client/feature/demo/src/commonMain/kotlin/app/logdate/demo/DemoViewModel.kt" <<'KT'
class DemoViewModel {
    fun fail(e: Exception) {
        _state.update { it.copy(errorMessage = "Couldn't save event") }
        _state.update { it.copy(errorMessage = "Failed to load devices: ${e.message}") }
        importContacts(logMessage = "Failed to import contacts")
        runPhase(exceptionLabel = "Upload failed")
        request(failureMessage = "Failed to load identity status")
        io.github.aakira.napier.Napier.d(tag = "Demo", message = "Fetching notes for today")
        download(logLabel = "journal")
        AVAudioPlayer(contentsOfURL = url, fileTypeHint = "m4a", error = null)
        val label = "Only checked in UI files"
        val chosen = if (e is IOException) "Not checked here" else "Nor here"
    }
}

data class Recommendation(
    val message: String = "What's going on?",
    val key: String = "home_recommendation",
)
KT

write "client/location/src/androidMain/kotlin/app/logdate/location/RecordingService.kt" <<'KT'
class RecordingService {
    fun notification() =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Recording your day")
            .setContentText(getString(R.string.recording_text))
            .build()

    fun icon() = IconCompat.createWithResource(this, R.drawable.ic).apply { contentDescription = "Recording" }
}
KT

write "client/feature/demo/src/test/kotlin/app/logdate/demo/ScreenTest.kt" <<'KT'
@Composable
fun TestScreen() { Text("Unit test text") }
KT

write "client/feature/demo/src/commonTest/kotlin/app/logdate/demo/ScreenCommonTest.kt" <<'KT'
@Composable
fun CommonTestScreen() { Text("Common test text") }
KT

write "client/screenshot-scenes/src/commonMain/kotlin/app/logdate/screenshots/Scene.kt" <<'KT'
@Composable
fun Scene() { Text("Screenshot fixture") }
KT

run_check
assert_exit_code 1 "$STATUS"
reported "Screen.kt:3: Text(text): \"Single line\""
reported "Screen.kt:5: Text(text): \"Visits and journeys\""
reported "Screen.kt:7: Text(text): \"Named text\""
reported 'Screen.kt:8: Text(text): "Arrived on ${state.date}"'
reported "Screen.kt:11: Icon(contentDescription): \"Add to journal\""
reported "Screen.kt:14: contentDescription =: \"Pause\""
reported "Screen.kt:14: contentDescription =: \"Play\""
reported "Screen.kt:16: PanelHeader(title): \"Memory\""
reported "Screen.kt:18: EmptyState(headline): \"Let's get started\""
reported "Screen.kt:19: EmptyState(body): \"Grant photo access\""
reported "Screen.kt:25: PaneAction(label): \"Show oldest first\""
reported "Screen.kt:27: PaneAction(label): \"Show newest first\""
reported "Screen.kt:31: Text(text): \"Untitled\""
reported "Screen.kt:34: val description: \"Stop recording\""
reported "Screen.kt:35: val description: \"Start recording\""
reported "Screen.kt:37: val error: \"Give this place a name.\""
reported "Screen.kt:56: chosen by if/when: \"Select\""
reported "Screen.kt:57: chosen by if/when: \"Ink\""
reported "Components.kt:15: default hint: \"Search everything\""
reported "RecordingService.kt:4: setContentTitle(): \"Recording your day\""
reported "RecordingService.kt:8: contentDescription =: \"Recording\""

reported 'Extras.kt:3: Sheet(summary): "$count places"'
reported "Destinations.kt:2: Timeline(label): \"Timeline\""
reported "DemoViewModel.kt:3: copy(errorMessage): \"Couldn't save event\""
reported 'DemoViewModel.kt:4: copy(errorMessage): "Failed to load devices: ${e.message}"'
reported "DemoViewModel.kt:17: default message: \"What's going on?\""

not_reported "Screen.kt:9:"
not_reported "Screen.kt:10:"
not_reported "Commented out"
not_reported "Block comment"
not_reported "screen_title"
not_reported "Screen alpha"
not_reported "Screen opened"
not_reported "PHOTO"
not_reported "Exported data"
not_reported "Copied text"
not_reported "State is not ready"
not_reported "Screen failed to load"
not_reported "select_tool"
not_reported "Preview sample"
not_reported "Preview title"
not_reported "Preview journal"
not_reported "Destination changed"
not_reported "Failed to import contacts"
not_reported "Upload failed"
not_reported "Failed to load identity status"
not_reported "Fetching notes for today"
not_reported '"journal"'
not_reported "m4a"
not_reported "Only checked in UI files"
not_reported "Not checked here"
not_reported "Nor here"
not_reported "home_recommendation"
not_reported "Unit test text"
not_reported "Common test text"
not_reported "Screenshot fixture"
not_reported "Pinch transition"
not_reported "Pinch container"
not_reported "Sample memory"
not_reported "Sample rotating memory"

write "$UI/Screen.kt" <<'KT'
@Composable
fun Screen() {
    Text(stringResource(Res.string.screen_title))
}
KT
rm "$SANDBOX/$UI/Components.kt" "$SANDBOX/$UI/Extras.kt" "$SANDBOX/$UI/Destinations.kt" "$SANDBOX/$UI/DemoViewModel.kt" \
    "$SANDBOX/client/location/src/androidMain/kotlin/app/logdate/location/RecordingService.kt"
run_check
assert_exit_code 0 "$STATUS"
assert_contains "No hardcoded static Compose literals found." "$OUTPUT"

print_pass_summary "hardcoded Compose literal"
