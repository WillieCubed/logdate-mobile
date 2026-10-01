package app.logdate.client.e2e

import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.composeForPreview
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.feature.widgets.OnThisDayWidget
import app.logdate.client.feature.widgets.OnThisDayWidgetReceiver
import app.logdate.client.feature.widgets.OnThisDayWidgetState
import app.logdate.client.feature.widgets.FixedMemoryWidget
import app.logdate.client.feature.widgets.FixedMemoryWidgetReceiver
import app.logdate.client.feature.widgets.NewEntryWidget
import app.logdate.client.feature.widgets.NewEntryWidgetReceiver
import app.logdate.client.feature.widgets.PhotoWidgetShape
import app.logdate.client.feature.widgets.publishWidgetPreviews
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class OnThisDayWidgetPreviewTest {
    @Test
    fun `Android 15 publishes generated previews for all widget types`() = runBlocking {
        if (Build.VERSION.SDK_INT < 35) return@runBlocking
        val context = ApplicationProvider.getApplicationContext<Context>()
        publishWidgetPreviews(context)
        val manager = AppWidgetManager.getInstance(context)
        for (receiver in listOf(OnThisDayWidgetReceiver::class.java, FixedMemoryWidgetReceiver::class.java, NewEntryWidgetReceiver::class.java)) {
            val preview = manager.getWidgetPreview(
                ComponentName(context, receiver),
                android.os.Process.myUserHandle(),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            )
            kotlin.test.assertNotNull(preview, "Generated preview missing for ${receiver.simpleName}")
            if (receiver == NewEntryWidgetReceiver::class.java) {
                val root = preview.apply(context, FrameLayout(context))
                assertTextAbsent(root, "Write")
                assertTextAbsent(root, "Record")
                assertDescriptionPresent(root, "Write entry")
                assertDescriptionPresent(root, "Record entry")
                assertDescriptionPresent(root, "Take photo")
            }
        }
    }
    @Test
    fun `picker has preview fallbacks on every supported Android version`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (receiver in listOf(OnThisDayWidgetReceiver::class.java, FixedMemoryWidgetReceiver::class.java, NewEntryWidgetReceiver::class.java)) {
            val provider = ComponentName(context, receiver)
            val info = AppWidgetManager.getInstance(context).installedProviders.first { it.provider == provider }
            assertNotEquals(0, info.previewImage)
            kotlin.test.assertNotNull(context.getDrawable(info.previewImage))
            assertNotEquals(0, info.initialLayout)
            if (Build.VERSION.SDK_INT >= 31) {
                assertTrue(info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0)
            }
            kotlin.test.assertNotNull(info.configure)
            if (receiver == NewEntryWidgetReceiver::class.java && Build.VERSION.SDK_INT >= 31) {
                assertTrue(info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                assertNotEquals(0, info.previewLayout)
                kotlin.test.assertNotNull(RemoteViews(context.packageName, info.previewLayout).apply(context, FrameLayout(context)))
            }
        }
    }

    @Test
    fun `generated preview renders the supplied memory content`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widget =
            OnThisDayWidget(
                previewState =
                    OnThisDayWidgetState.HasMemory(
                        dateIso = "2025-09-30",
                        dateFormatted = "September 30, 2025",
                        summary = "A walk by the lake",
                        thumbnailUri = null,
                    ),
            )
        val providerInfo = AppWidgetProviderInfo().apply {
            minWidth = 250
            minHeight = 180
        }

        val remoteViews = widget.composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, providerInfo)
        val root = remoteViews.apply(context, FrameLayout(context))
        val matches = arrayListOf<View>()
        root.findViewsWithText(matches, "A walk by the lake", View.FIND_VIEWS_WITH_TEXT)

        assertTrue(matches.isNotEmpty())
        assertTextPresent(root, "A walk by the lake")
        assertTextAbsent(root, "Journal entry")
        assertTextAbsent(root, "September 30, 2025")
        assertNotEquals(exactTextView(root, "“").currentTextColor, quotedBodyView(root, "A walk by the lake").currentTextColor)
        assertNoStandaloneClosingQuote(root)
        assertTextAbsent(root, "MEMORIES")
        assertTextAbsent(root, "View memory ›")

        capture(context, root, "memories-widget")
        assertClosingQuoteRendered(root, "A walk by the lake")
    }

    @Test
    fun `default picker previews show example photos when there are no entries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rotating = OnThisDayWidget().composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        val fixed = FixedMemoryWidget().composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        assertDescriptionPresent(rotating, "Milo after the rain")
        assertDescriptionPresent(fixed, "Milo after the rain")
    }

    @Test
    fun `default picker previews never reveal a private journal entry`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notesRepository = GlobalContext.get().get<JournalNotesRepository>()
        val now = Clock.System.now()
        val note = JournalNote.Text(
            content = "Private widget preview regression entry",
            creationTimestamp = now,
            lastUpdated = now,
        )
        try {
            notesRepository.create(note)
            val rotating = OnThisDayWidget().composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
                .apply(context, FrameLayout(context))
            val fixed = FixedMemoryWidget().composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
                .apply(context, FrameLayout(context))
            assertTextAbsent(rotating, note.content)
            assertTextAbsent(fixed, note.content)
            assertDescriptionPresent(rotating, "Milo after the rain")
            assertDescriptionPresent(fixed, "Milo after the rain")
        } finally {
            notesRepository.removeById(note.uid)
        }
    }

    @Test
    fun `fixed memory preview renders the selected entry`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = OnThisDayWidgetState.FixedMemory(
            noteId = "79a80f6f-51c8-4e68-a3af-50f39ba22bc7",
            dateFormatted = "May 12, 2025",
            summary = "A day in the mountains",
            thumbnailUri = null,
        )
        val root = FixedMemoryWidget(state).composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        val matches = arrayListOf<View>()
        root.findViewsWithText(matches, state.summary, View.FIND_VIEWS_WITH_TEXT)
        assertTrue(matches.isNotEmpty())
        assertTextPresent(root, "A day in the mountains")
        assertTextAbsent(root, "Journal entry")
        assertTextAbsent(root, "May 12, 2025")
        exactTextView(root, "“")
        quotedBodyView(root, "A day in the mountains")
        assertNoStandaloneClosingQuote(root)
        assertTextAbsent(root, "YOUR MEMORY")
        assertTextAbsent(root, "View memory ›")
        capture(context, root, "fixed-memory-widget")
        assertClosingQuoteRendered(root, "A day in the mountains")
    }

    @Test
    fun `audio memory preview shows transcript and a play control`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transcript = "We watched the rain roll across the bay"
        val state = OnThisDayWidgetState.FixedMemory(
            noteId = "79a80f6f-51c8-4e68-a3af-50f39ba22bc7",
            dateFormatted = "May 12, 2025",
            summary = transcript,
            thumbnailUri = null,
            audioUri = "file:///recording.m4a",
        )
        val root = FixedMemoryWidget(state).composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        assertTextPresent(root, "We watched the rain")
        assertTextAbsent(root, "Transcript")
        assertTextAbsent(root, "May 12, 2025")
        exactTextView(root, "“")
        quotedBodyView(root, "We watched the rain")
        assertNoStandaloneClosingQuote(root)
        assertDescriptionPresent(root, "Play recording")
        capture(context, root, "audio-memory-widget")
        assertCleanExcerpt(root, transcript)
    }

    @Test
    fun `photo memory preview shows the image and its caption`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = OnThisDayWidgetState.FixedMemory(
            noteId = "79a80f6f-51c8-4e68-a3af-50f39ba22bc7",
            dateFormatted = "May 12, 2025",
            summary = "Milo after the rain",
            thumbnailUri = "android.resource://${context.packageName}/drawable/sample_note_photo",
        )
        val root = FixedMemoryWidget(state).composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        assertDescriptionPresent(root, state.summary)
        assertDescriptionPresent(root, state.dateFormatted)
        assertTransparentCorners(context, root)
        capture(context, root, "photo-memory-widget")
        captureHomeScreen(context, root, "photo-memory-home-screen")
    }

    @Test
    fun `rotating photo memory uses the pinned print treatment`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = OnThisDayWidgetState.HasMemory(
            dateIso = "2025-09-30",
            dateFormatted = "September 30, 2025",
            summary = "An afternoon in the garden",
            thumbnailUri = "android.resource://${context.packageName}/drawable/sample_note_photo",
        )
        val root = OnThisDayWidget(state).composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        assertDescriptionPresent(root, state.summary)
        assertTransparentCorners(context, root)
        capture(context, root, "rotating-photo-memory-widget")
    }

    @Test
    fun `pinned photo remains readable at narrow widget width`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = OnThisDayWidgetState.FixedMemory(
            noteId = "79a80f6f-51c8-4e68-a3af-50f39ba22bc7",
            dateFormatted = "May 12, 2025",
            summary = "Milo after the rain",
            thumbnailUri = "android.resource://${context.packageName}/drawable/sample_note_photo",
        )
        val root = FixedMemoryWidget(state).composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo(180))
            .apply(context, FrameLayout(context))
        assertDescriptionPresent(root, state.summary)
        assertTransparentCorners(context, root, widthDp = 180)
        capture(context, root, "photo-memory-narrow-widget", widthDp = 180)
    }

    @Test
    fun `new entry preview shows optional photo prompt`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = "android.resource://${context.packageName}/drawable/sample_note_photo"
        val root = NewEntryWidget(OnThisDayWidgetState.PhotoPrompt(uri))
            .composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        assertTextAbsent(root, "Write a new entry")
        assertTextAbsent(root, "NEW ENTRY")
        assertTextAbsent(root, "Write")
        assertTextAbsent(root, "Record")
        assertDescriptionPresent(root, "Write entry")
        assertDescriptionPresent(root, "Record entry")
        assertDescriptionPresent(root, "Take photo")
        assertRoundedWidgetCorners(context, root)
        capture(context, root, "new-entry-widget")
        captureHomeScreen(context, root, "new-entry-home-screen")
    }

    @Test
    fun `new entry shape choices retain the system radius floor`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = "android.resource://${context.packageName}/drawable/sample_note_photo"
        val state = OnThisDayWidgetState.PhotoPrompt(uri)
        val systemRoot = NewEntryWidget(state, PhotoWidgetShape.SYSTEM)
            .composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        val softRoot = NewEntryWidget(state, PhotoWidgetShape.SOFT)
            .composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        val circleRoot = NewEntryWidget(state, PhotoWidgetShape.CIRCLE)
            .composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val system = renderRoot(context, systemRoot)
        val soft = renderRoot(context, softRoot)
        val circle = renderRoot(context, circleRoot)
        val cornerWidth = (48 * density).toInt()
        val cornerHeight = (48 * density).toInt()
        fun cornerCoverage(bitmap: Bitmap): Long =
            (0 until cornerWidth).sumOf { x ->
                (0 until cornerHeight).sumOf { y -> android.graphics.Color.alpha(bitmap.getPixel(x, y)).toLong() }
            }
        assertTrue(cornerCoverage(system) > cornerCoverage(soft), "Soft shape must round more than the system shape")
        assertTrue(android.graphics.Color.alpha(circle.getPixel((10 * density).toInt(), circle.height / 2)) == 0)
        assertTrue(android.graphics.Color.alpha(circle.getPixel(circle.width / 2, circle.height / 2)) > 0)
        capture(context, softRoot, "new-entry-soft-widget")
        capture(context, circleRoot, "new-entry-circle-widget")
        captureHomeScreen(context, softRoot, "new-entry-soft-home-screen")
        captureHomeScreen(context, circleRoot, "new-entry-circle-home-screen")
    }

    @Test
    fun `default new entry preview shows photo and creation actions`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = NewEntryWidget().composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        assertTextAbsent(root, "Write")
        assertTextAbsent(root, "Record")
        assertDescriptionPresent(root, "Write entry")
        assertDescriptionPresent(root, "Record entry")
        assertDescriptionPresent(root, "Take photo")
        assertTextAbsent(root, "What would you like to remember today?")
        assertRoundedWidgetCorners(context, root)
        capture(context, root, "new-entry-widget-default")
    }

    @Test
    fun `new entry without a photo keeps creation actions visible`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = NewEntryWidget(OnThisDayWidgetState.NewEntryReady)
            .composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, previewInfo())
            .apply(context, FrameLayout(context))
        assertTextAbsent(root, "Write")
        assertTextAbsent(root, "Record")
        assertDescriptionPresent(root, "Write entry")
        assertDescriptionPresent(root, "Record entry")
        assertDescriptionPresent(root, "Take photo")
        assertTextAbsent(root, "What would you like to remember today?")
        renderRoot(context, root)
        capture(context, root, "new-entry-without-photo-widget")
    }

    @Test
    fun `wide and tall memory widgets use the available canvas`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = OnThisDayWidgetState.FixedMemory(
            noteId = "79a80f6f-51c8-4e68-a3af-50f39ba22bc7",
            dateFormatted = "May 12, 2025",
            summary = "Milo after the rain",
            thumbnailUri = "android.resource://${context.packageName}/drawable/sample_note_photo",
        )
        val wide = FixedMemoryWidget().compose(context, AppWidgetId(4081), size = DpSize(360.dp, 180.dp), state = state)
            .apply(context, FrameLayout(context))
        val wideBitmap = renderRoot(context, wide, 360, 180)
        capture(context, wide, "photo-memory-wide-widget", 360, 180)
        assertTrue(android.graphics.Color.alpha(wideBitmap.getPixel((40 * context.resources.displayMetrics.density).toInt(), wideBitmap.height / 2)) > 0)

        val tall = FixedMemoryWidget().compose(context, AppWidgetId(4082), size = DpSize(250.dp, 300.dp), state = state)
            .apply(context, FrameLayout(context))
        val tallBitmap = renderRoot(context, tall, 250, 300)
        capture(context, tall, "photo-memory-tall-widget", 250, 300)
        assertTrue(android.graphics.Color.alpha(tallBitmap.getPixel(tallBitmap.width / 2, (270 * context.resources.displayMetrics.density).toInt())) > 0)
    }

    @Test
    fun `new entry actions reposition across narrow and wide sizes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photoState = OnThisDayWidgetState.PhotoPrompt("android.resource://${context.packageName}/drawable/sample_note_photo")
        val narrow = NewEntryWidget().compose(context, AppWidgetId(4083), size = DpSize(180.dp, 180.dp), state = photoState)
            .apply(context, FrameLayout(context))
        renderRoot(context, narrow, 180, 180)
        capture(context, narrow, "new-entry-narrow-widget", 180, 180)
        val narrowWrite = descendantBounds(narrow, "Write entry")
        assertTrue(narrowWrite.left >= 24 * context.resources.displayMetrics.density, "Narrow actions need breathing room")

        val wide = NewEntryWidget().compose(context, AppWidgetId(4084), size = DpSize(360.dp, 180.dp), state = photoState)
            .apply(context, FrameLayout(context))
        renderRoot(context, wide, 360, 180)
        capture(context, wide, "new-entry-wide-widget", 360, 180)
        val wideRecord = descendantBounds(wide, "Record entry")
        assertTrue(wideRecord.centerX() > 210 * context.resources.displayMetrics.density, "Wide actions should offset from the photo center")

        val tall = NewEntryWidget().compose(context, AppWidgetId(4085), size = DpSize(250.dp, 300.dp), state = photoState)
            .apply(context, FrameLayout(context))
        renderRoot(context, tall, 250, 300)
        assertDescriptionPresent(tall, "Take photo")
        capture(context, tall, "new-entry-tall-widget", 250, 300)
    }

    @Test
    fun `wide and tall rotating transcripts remain readable`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = OnThisDayWidgetState.HasMemory(
            dateIso = "2025-09-30",
            dateFormatted = "September 30, 2025",
            summary = "We found a quiet trail after the rain, then watched the light change across the garden while Milo explored every path.",
            thumbnailUri = null,
            audioUri = "file:///recording.m4a",
            audioNoteId = "79a80f6f-51c8-4e68-a3af-50f39ba22bc7",
        )
        val wide = OnThisDayWidget().compose(context, AppWidgetId(4086), size = DpSize(360.dp, 180.dp), state = state)
            .apply(context, FrameLayout(context))
        assertTextAbsent(wide, "Transcript")
        assertTextAbsent(wide, "September 30, 2025")
        capture(context, wide, "rotating-transcript-wide-widget", 360, 180)
        assertCleanExcerpt(wide, state.summary)
        assertNoStandaloneClosingQuote(wide)

        val tall = OnThisDayWidget().compose(context, AppWidgetId(4087), size = DpSize(250.dp, 300.dp), state = state)
            .apply(context, FrameLayout(context))
        assertTextAbsent(tall, "Transcript")
        assertTextAbsent(tall, "September 30, 2025")
        capture(context, tall, "rotating-transcript-tall-widget", 250, 300)
        assertCleanExcerpt(tall, state.summary)
        assertNoStandaloneClosingQuote(tall)
    }

    private fun textViews(root: View): List<android.widget.TextView> {
        val views = mutableListOf<android.widget.TextView>()
        fun visit(view: View) {
            if (view is android.widget.TextView) views += view
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(root)
        return views
    }

    private fun exactTextView(root: View, text: String): android.widget.TextView =
        kotlin.test.assertNotNull(textViews(root).firstOrNull { it.text.toString() == text }, "Missing exact widget text: $text")

    private fun quotedBodyView(root: View, prefix: String): android.widget.TextView =
        kotlin.test.assertNotNull(
            textViews(root).firstOrNull { it.text.startsWith(prefix) },
            "Missing entry text: $prefix",
        )

    private fun assertNoStandaloneClosingQuote(root: View) {
        assertTrue(textViews(root).none { it.text.toString() == "”" }, "Closing quote is detached from the entry")
    }

    private fun assertClosingQuoteRendered(root: View, prefix: String) {
        val body = quotedBodyView(root, prefix)
        assertTrue(body.text.endsWith("”"), "Complete entry needs an inline closing quote")
        val layout = kotlin.test.assertNotNull(body.layout)
        assertTrue(layout.getEllipsisCount(layout.lineCount - 1) == 0, "Android truncated the closing quote")
    }

    private fun assertCleanExcerpt(root: View, source: String) {
        val body = quotedBodyView(root, source.take(8))
        val rendered = body.text.toString()
        assertTrue(rendered.endsWith("…”"), "Excerpt needs a paired closing quote: $rendered")
        val layout = kotlin.test.assertNotNull(body.layout)
        assertTrue(layout.getEllipsisCount(layout.lineCount - 1) == 0, "Android truncated the closing quote")
        val excerpt = rendered.dropLast(2)
        assertTrue(source.startsWith(excerpt), "Excerpt must preserve source text: $rendered")
        assertTrue(source.getOrNull(excerpt.length)?.isWhitespace() == true, "Excerpt must end at a whole word: $rendered")
    }

    private fun descendantBounds(root: View, description: String): android.graphics.Rect {
        fun visit(view: View, left: Int, top: Int): android.graphics.Rect? {
            val x = left + view.left
            val y = top + view.top
            if (view.contentDescription == description) return android.graphics.Rect(x, y, x + view.width, y + view.height)
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index), x, y)?.let { return it }
                }
            }
            return null
        }
        return kotlin.test.assertNotNull(visit(root, -root.left, -root.top), "Missing action: $description")
    }

    private fun assertTextAbsent(root: View, text: String) {
        val matches = arrayListOf<View>()
        root.findViewsWithText(matches, text, View.FIND_VIEWS_WITH_TEXT)
        assertTrue(matches.isEmpty(), "Unexpected widget chrome: $text")
    }

    private fun assertTextPresent(root: View, text: String) {
        val matches = arrayListOf<View>()
        root.findViewsWithText(matches, text, View.FIND_VIEWS_WITH_TEXT)
        assertTrue(matches.isNotEmpty(), "Missing entry context: $text")
    }

    private fun assertDescriptionPresent(root: View, text: String) {
        val matches = arrayListOf<View>()
        root.findViewsWithText(matches, text, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        assertTrue(matches.isNotEmpty(), "Missing photo description: $text")
    }

    private fun assertTransparentCorners(context: Context, root: View, widthDp: Int = 250) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (180 * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(0, 0)) == 0, "Photo still has an outer container")
    }

    private fun assertRoundedWidgetCorners(context: Context, root: View) {
        val density = context.resources.displayMetrics.density
        val bitmap = renderRoot(context, root)
        val inset = (3 * density).toInt()
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(inset, inset)) == 0, "Widget has a sharp corner")
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(bitmap.width / 2, inset)) > 0, "Widget is missing at the top edge")
    }

    private fun renderRoot(context: Context, root: View, widthDp: Int = 250, heightDp: Int = 180): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    private fun previewInfo(widthDp: Int = 250, heightDp: Int = 180) = AppWidgetProviderInfo().apply { minWidth = widthDp; minHeight = heightDp }

    private fun capture(context: Context, root: View, name: String, widthDp: Int = 250, heightDp: Int = 180) {
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(File(outputDir, "$name-android-${Build.VERSION.SDK_INT}.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun captureHomeScreen(context: Context, root: View, name: String) {
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val density = context.resources.displayMetrics.density
        fun px(dp: Float) = dp * density
        val width = px(390f).toInt()
        val height = px(844f).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0xFF7F978F.toInt(), 0xFF536E72.toInt(), 0xFF243E47.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = 0x208FE8D1
        canvas.drawCircle(px(345f), px(275f), px(220f), paint)
        paint.color = 0x17FFFFFF
        canvas.drawCircle(px(15f), px(610f), px(245f), paint)
        paint.color = android.graphics.Color.WHITE
        paint.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        paint.textSize = px(48f)
        canvas.drawText("9:41", px(30f), px(103f), paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = px(15f)
        canvas.drawText("Wednesday, September 30", px(32f), px(133f), paint)

        val widgetWidth = px(250f).toInt()
        val widgetHeight = px(180f).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(widgetWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(widgetHeight, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, widgetWidth, widgetHeight)
        canvas.save()
        canvas.translate(px(24f), px(196f))
        root.draw(canvas)
        canvas.restore()

        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(px(18f), px(731f), px(372f), px(817f), px(32f), px(32f), paint)
        val iconColors = intArrayOf(0xFFE8E4D6.toInt(), 0xFFD3E4DD.toInt(), 0xFFF0DBCB.toInt(), 0xFFDBE0ED.toInt())
        for (index in 0..3) {
            paint.color = iconColors[index]
            canvas.drawRoundRect(px(44f + index * 82f), px(746f), px(97f + index * 82f), px(799f), px(16f), px(16f), paint)
        }
        FileOutputStream(File(outputDir, "$name-android-${Build.VERSION.SDK_INT}.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
