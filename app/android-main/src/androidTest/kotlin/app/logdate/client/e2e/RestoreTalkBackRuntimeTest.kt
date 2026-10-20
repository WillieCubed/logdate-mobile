package app.logdate.client.e2e

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.ArchivePreview
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.feature.core.settings.ui.ExportSettingsContent
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Recovery actions use host-supplied native touch input; only known system setup dialogs are dismissed directly. */
class RestoreTalkBackRuntimeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var automation: UiAutomation
    private lateinit var receipt: File
    private lateinit var nonce: String
    private lateinit var originals: JSONObject
    private val focus = CopyOnWriteArrayList<String>()

    @Test
    fun recoveryActionsSupportTalkBackSwipesAndDoubleTap() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("restoreTalkBackAcceptance") == "true")
        require(Build.HARDWARE in setOf("ranchu", "goldfish"))
        require(context.packageName.endsWith(".debug"))
        nonce = requireNotNull(args.getString("restoreTalkBackNonce"))
        require(nonce.matches(Regex("[a-z0-9-]+")))
        val fontScale = requireNotNull(args.getString("restoreTalkBackFontScale"))
        require(fontScale in setOf("1.0", "2.0"))
        receipt = File(context.noBackupFilesDir, "talkback-acceptance-$nonce.json")
        check(!receipt.exists())
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        val original =
            listOf("secure enabled_accessibility_services", "secure accessibility_enabled", "system font_scale")
                .associateWith { shell("settings get $it").trim() }
        val originalBluetooth = shell("settings get global bluetooth_on").trim()
        val notificationPermission = notificationPermission()
        originals = JSONObject(original).put("bluetoothEnabled", originalBluetooth).put("notificationPermission", notificationPermission)
        persist(JSONObject().put("phase", "originals"))
        val component = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        assertTrue(shell("pm path com.google.android.marvin.talkback").startsWith("package:"), "Managed image must include TalkBack")
        val clicked = AtomicInteger()
        val observedFontScale = AtomicReference(0f)
        var notificationSetupHandled = false
        try {
            // The API 34 ARM image can show a Bluetooth crash dialog before the fixture starts.
            // Recovery sheets do not use Bluetooth; preserve the radio setting and disable it here.
            shell("svc bluetooth disable")
            automation.rootInActiveWindow?.let { root ->
                if (root.packageName?.toString() == "android" && windowText(root).contains("Bluetooth keeps stopping")) {
                    val close = root.findAccessibilityNodeInfosByText("Close app").single()
                    assertTrue(close.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                }
            }
            shell("settings put system font_scale $fontScale")
            val enabled =
                original
                    .getValue("secure enabled_accessibility_services")
                    .takeUnless { it == "null" || it.isBlank() }
                    ?.split(':')
                    .orEmpty()
            shell("settings put secure enabled_accessibility_services ${(enabled + component).distinct().joinToString(":")}")
            shell("settings put secure accessibility_enabled 1")
            val manager = context.getSystemService(AccessibilityManager::class.java)
            await {
                manager.isTouchExplorationEnabled &&
                    manager
                        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                        .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" }
            }
            automation.setOnAccessibilityEventListener { event ->
                if (event.packageName?.toString() == context.packageName &&
                    event.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                ) {
                    val source = event.source
                    val label =
                        source
                            ?.let(::windowText)
                            ?.trim()
                            .orEmpty()
                            .ifBlank { event.text.joinToString(" ") }
                            .ifBlank { event.contentDescription?.toString().orEmpty() }
                    focus += label.ifBlank { "[unlabeled ${source?.className}]" }
                }
            }
            val summary =
                RestoreSummary(
                    source = "Journal archive.zip",
                    journalsImported = 2,
                    notesImported = 12,
                    draftsImported = 1,
                    journalLinksImported = 12,
                    mediaImported = 3,
                    recoveryId = "talkback-fixture",
                )
            val scenes =
                listOf(
                    Scene("partial", RestoreState.Completed(summary.copy(unavailableOriginals = 2)), "Choose archive"),
                    Scene(
                        "paused",
                        RestoreState.Completed(summary.copy(metadataIncomplete = true, canResume = true)),
                        "Resume import",
                    ),
                    Scene(
                        "preview",
                        RestoreState.Previewing(
                            ArchivePreview(
                                ExportSchemaVersion.CURRENT,
                                Instant.parse("2026-10-15T12:00:00Z"),
                                "1.0",
                                ExportStats(2, 12, 1, 3),
                            ),
                            "Journal archive.zip",
                        ),
                        "Import",
                    ),
                )
            ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)).use { activity ->
                scenes.forEach { scene ->
                    focus.clear()
                    clicked.set(0)
                    activity.onActivity {
                        assertEquals(fontScale.toFloat(), it.resources.configuration.fontScale)
                        it.setContent {
                            val density = LocalDensity.current.fontScale
                            SideEffect { observedFontScale.set(density) }
                            LogDateTheme(dynamicColor = false) {
                                ExportSettingsContent(
                                    onBack = {},
                                    exportState = ExportState.Idle,
                                    onShowExportOptions = {},
                                    onUpdateExportOptions = {},
                                    onConfirmExport = {},
                                    onCancelExport = {},
                                    onRetryExport = {},
                                    onDismissExport = {},
                                    onBrowseExport = {},
                                    restoreState = scene.state,
                                    onShowRestoreSheet = {},
                                    onSelectRestoreFile = {},
                                    onUpdateImportOptions = {},
                                    onConfirmImport = { clicked.incrementAndGet() },
                                    onCancelRestore = {},
                                    onRetryRestore = { clicked.incrementAndGet() },
                                    onDismissRestore = {},
                                    snackbarHostState = SnackbarHostState(),
                                )
                            }
                        }
                    }
                    await {
                        val root = automation.rootInActiveWindow
                        persist(
                            JSONObject()
                                .put("phase", "waiting-window")
                                .put("windowPackage", root?.packageName?.toString())
                                .put("windowText", root?.let(::windowText))
                                .put("observedFontScale", observedFontScale.get()),
                        )
                        if (!notificationSetupHandled &&
                            scene.name == "partial" &&
                            root?.packageName?.toString() == "com.google.android.permissioncontroller" &&
                            windowText(root).contains("to send you notifications?") &&
                            (windowText(root).contains("TalkBack") || windowText(root).contains("Android Accessibility Suite"))
                        ) {
                            // Deny TalkBack's optional notification permission during service setup.
                            notificationSetupHandled = root
                                .findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button")
                                .singleOrNull()
                                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
                        }
                        root?.packageName?.toString() == context.packageName &&
                            observedFontScale.get() == fontScale.toFloat()
                    }
                    awaitTalkBackAction(scene.name, scene.action, observedFontScale.get()) { clicked.get() > 0 }
                    assertEquals(1, clicked.get(), "Double-tap must invoke the action once")
                }
            }
            StartupRecoveryTalkBackScenario(automation) { scene, action, activated ->
                awaitTalkBackAction(scene, action, fontScale.toFloat(), activated)
            }.run(fontScale.toFloat())
        } finally {
            automation.setOnAccessibilityEventListener(null)
            val actions =
                original.map { (setting, value) ->
                    suspend {
                        if (value == "null") shell("settings delete $setting") else shell("settings put $setting $value")
                        assertEquals(value, shell("settings get $setting").trim())
                    }
                } +
                    listOf(
                        suspend {
                            if (originalBluetooth == "1") shell("svc bluetooth enable") else shell("svc bluetooth disable")
                            if (originalBluetooth == "null") shell("settings delete global bluetooth_on")
                            await { originalBluetooth == "null" || shell("settings get global bluetooth_on").trim() == originalBluetooth }
                            assertEquals(originalBluetooth, shell("settings get global bluetooth_on").trim())
                        },
                        suspend {
                            listOf("USER_SET" to "user-set", "USER_FIXED" to "user-fixed").forEach { (flag, argument) ->
                                if (!notificationPermission.contains(flag)) {
                                    shell(
                                        "pm clear-permission-flags com.google.android.marvin.talkback android.permission.POST_NOTIFICATIONS $argument",
                                    )
                                }
                            }
                            assertEquals(notificationPermission, notificationPermission())
                        },
                    )
            runBlocking { restoreFixtureState(*actions.toTypedArray()) }
            persist(JSONObject().put("phase", "cleaned").put("settingsRestored", true))
        }
        persist(
            JSONObject()
                .put("phase", "passed")
                .put("observedFontScale", observedFontScale.get())
                .put("fontScale", fontScale)
                .put("sceneCount", 7)
                .put("actualStartupKeyRecovery", true)
                .put("syntheticLateKeyDelivery", true)
                .put("lockedJournalSemanticsHidden", true)
                .put("settingsRestored", true),
        )
    }

    private fun awaitTalkBackAction(
        name: String,
        action: String,
        fontScale: Float,
        activated: () -> Boolean,
    ) {
        focus.clear()
        persist(
            JSONObject()
                .put(
                    "phase",
                    "ready",
                ).put("observedFontScale", fontScale)
                .put("scene", name)
                .put("action", action),
        )
        val deadline = SystemClock.elapsedRealtime() + 90_000
        while (!activated()) {
            val focused =
                automation.rootInActiveWindow
                    ?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            val currentLabel =
                focused
                    ?.let(::windowText)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { focused?.contentDescription?.toString().orEmpty() }
            if (currentLabel.isNotBlank() && focus.lastOrNull() != currentLabel) focus += currentLabel
            check(
                SystemClock.elapsedRealtime() < deadline,
            ) { "Native TalkBack activation timed out for $name; focus=$focus" }
            persist(
                JSONObject()
                    .put("phase", "ready")
                    .put("observedFontScale", fontScale)
                    .put("scene", name)
                    .put("action", action)
                    .put("focus", JSONArray(focus.toList())),
            )
            SystemClock.sleep(100)
        }
        SystemClock.sleep(400)
        val labels = focus.toList()
        val first = labels.indexOfFirst { it == action }
        assertTrue(first >= 0, "TalkBack must focus the labeled action")
        val away = (first + 1 until labels.size).firstOrNull { labels[it] != action }
        assertTrue(
            away != null && (away + 1 until labels.size).any { labels[it] == action },
            "Forward, backward, and return focus must precede activation: $labels",
        )
    }

    private fun persist(state: JSONObject) {
        state.put("nonce", nonce).put("original", originals)
        val pending = File(receipt.path + ".tmp")
        FileOutputStream(pending).use {
            it.write(state.toString().encodeToByteArray())
            it.fd.sync()
        }
        check(pending.renameTo(receipt))
    }

    private fun notificationPermission(): String =
        shell("dumpsys package com.google.android.marvin.talkback")
            .lineSequence()
            .first { it.contains("android.permission.POST_NOTIFICATIONS: granted=") }
            .trim()

    private fun windowText(node: android.view.accessibility.AccessibilityNodeInfo): String =
        buildString {
            append(node.text?.toString().orEmpty()).append(' ')
            for (index in 0 until node.childCount) node.getChild(index)?.let { append(windowText(it)) }
        }

    private fun shell(command: String): String =
        ParcelFileDescriptor
            .AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "TalkBack or recovery window did not become ready" }
            SystemClock.sleep(100)
        }
    }

    private data class Scene(
        val name: String,
        val state: RestoreState,
        val action: String,
    )
}
