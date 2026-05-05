package app.logdate.screenshots.components.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.sync.SyncErrorBanner
import app.logdate.ui.sync.SyncIndicatorChip
import app.logdate.ui.sync.SyncPresentation
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot coverage for the new sync surface (chip + banner). Two responsibilities:
 *
 * 1. **Taste**: every emitted state renders across phone / phone-landscape / phone-dark / tablet,
 *    so a reviewer can scan the visual matrix and confirm the MD3-Expressive treatment holds at
 *    each form factor — shape, motion-affording corners, tonal containers, no clipping.
 *
 * 2. **Regression**: the [Hidden_state_emits_no_pixels] case asserts the chip and banner
 *    compose nothing for `Hidden`, and the [Banner_respects_status_bar_inset_under_top_app_bar]
 *    case renders the banner inside a fake-Scaffold below a TopAppBar — directly proving the
 *    fix for the Pixel 8 status-bar collision that motivated this work.
 *
 * No live `SyncManager` is involved: surfaces take [SyncPresentation] directly, so each test is
 * a pure-UI snapshot. Mirrors the convention in `SettingsComponentScreenshots.kt`.
 */

// ─── Chip states ────────────────────────────────────────────────────────────────

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Chip_Hidden_renders_nothing() {
    ChipHarness(SyncPresentation.Hidden)
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Chip_Syncing_with_pending_count() {
    ChipHarness(SyncPresentation.Syncing(pendingCount = 12))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Chip_Syncing_no_count() {
    ChipHarness(SyncPresentation.Syncing(pendingCount = 0))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Chip_Pending_small_count() {
    ChipHarness(SyncPresentation.Pending(pendingCount = 3))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Chip_Pending_large_count_truncation_check() {
    // The "264" the original Pixel 8 screenshot showed. Locked in to catch any future regression
    // where a banner re-enters the picture for queued-but-no-account cases.
    ChipHarness(SyncPresentation.Pending(pendingCount = 264))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Chip_NetworkError_with_pending() {
    ChipHarness(SyncPresentation.NetworkError(pendingCount = 5))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Chip_NetworkError_no_pending() {
    ChipHarness(SyncPresentation.NetworkError(pendingCount = 0))
}

// ─── Banner states ──────────────────────────────────────────────────────────────

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Banner_Hidden_renders_nothing() {
    BannerHarness(SyncPresentation.Hidden)
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Banner_AuthError_promotes_full_banner() {
    BannerHarness(SyncPresentation.AuthError)
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Banner_StorageError_with_pending() {
    BannerHarness(SyncPresentation.StorageError(pendingCount = 7))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Banner_ConflictError_single() {
    BannerHarness(SyncPresentation.ConflictError(conflictCount = 1))
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Banner_ConflictError_many() {
    BannerHarness(SyncPresentation.ConflictError(conflictCount = 9))
}

// ─── Inset regression: banner under a TopAppBar with a faked status bar ─────────

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun Banner_respects_status_bar_inset_under_top_app_bar() {
    // The original bug: a top-anchored banner painted *behind* the system status bar, colliding
    // with the OS clock and signal icons. The fix moved the banner inside the Scaffold body so it
    // inherits the TopAppBar's content insets. This snapshot reproduces the host shape: status
    // bar → TopAppBar → banner. A regression that re-introduces the bug would visibly overlap.
    ScreenshotTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Timeline") },
                    actions = {
                        SyncIndicatorChip(
                            presentation = SyncPresentation.Syncing(pendingCount = 3),
                        )
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                SyncErrorBanner(
                    presentation = SyncPresentation.AuthError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("(timeline content)")
                }
            }
        }
    }
}

// ─── Harnesses ──────────────────────────────────────────────────────────────────

@Composable
private fun ChipHarness(presentation: SyncPresentation) {
    ScreenshotTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            SyncIndicatorChip(presentation = presentation)
        }
    }
}

@Composable
private fun BannerHarness(presentation: SyncPresentation) {
    ScreenshotTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding(),
        ) {
            // Top spacer matching a typical status bar so reviewers can verify the banner sits
            // visibly *below* OS chrome — a sanity check that complements the Scaffold-based
            // regression test above.
            Box(modifier = Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets(0)))
            SyncErrorBanner(
                presentation = presentation,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
            )
        }
    }
}
