package app.logdate.client.e2e

import android.appwidget.AppWidgetManager
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import app.logdate.client.feature.widgets.FixedMemoryWidgetConfigActivity
import app.logdate.client.feature.widgets.NewEntryWidgetConfigActivity
import app.logdate.client.feature.widgets.OnThisDayWidgetConfigActivity
import app.logdate.client.feature.widgets.PhotoWidgetShape
import app.logdate.client.feature.widgets.WidgetInstanceSettings
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class WidgetSetupScreenshotsTest {
    @Test
    fun `pinned memory choices show entry content and save the selection`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notesRepository = GlobalContext.get().get<JournalNotesRepository>()
        val timestamp = Instant.parse("2025-09-30T18:00:00Z")
        val photoFile = File.createTempFile("widget-choice-", ".jpg", context.cacheDir)
        context.assets.open("sample_note_photo.jpg").use { source ->
            photoFile.outputStream().use { output -> source.copyTo(output) }
        }
        val textNote = JournalNote.Text(creationTimestamp = timestamp, lastUpdated = timestamp, content = "We waited out the rain")
        val photoNote = JournalNote.Image(
            creationTimestamp = timestamp,
            lastUpdated = timestamp,
            mediaRef = photoFile.absolutePath,
            caption = "Garden walk with Milo",
        )
        try {
            notesRepository.create(textNote)
            notesRepository.create(photoNote)
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: error("Run setup screenshots on a Gradle Managed Device")
            context.startActivity(
                Intent(context, FixedMemoryWidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7100)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            device.waitForIdle()
            if (device.hasObject(By.text("Digital Wellbeing isn't responding"))) {
                device.findObject(By.text("Close app")).click()
                device.waitForIdle()
            }
            assertTrue(device.wait(Until.hasObject(By.text("Pinned memory")), 10_000L))
            assertTrue(device.wait(Until.hasObject(By.text("Garden walk with Milo")), 10_000L))
            assertTrue(device.hasObject(By.text("We waited out the rain")))
            assertFalse(device.hasObject(By.text("Search entries")))
            device.findObject(By.text("Garden walk with Milo")).click()
            device.waitForIdle()
            val selectionDeadline = System.currentTimeMillis() + 5_000L
            while (device.findObject(By.text("Add widget"))?.isEnabled != true && System.currentTimeMillis() < selectionDeadline) {
                Thread.sleep(100)
            }
            assertTrue(device.findObject(By.text("Add widget")).isEnabled)
            assertTrue(device.takeScreenshot(File(outputDir, "pinned-memory-choices-android-${Build.VERSION.SDK_INT}.png")))
            device.findObject(By.text("Add widget")).click()
            device.waitForIdle()
            assertEquals(photoNote.uid.toString(), WidgetInstanceSettings(context).chosenNoteId(7100))
        } finally {
            WidgetInstanceSettings(context).remove(7100)
            notesRepository.removeById(textNote.uid)
            notesRepository.removeById(photoNote.uid)
            photoFile.delete()
        }
    }

    @Test
    fun `capture widget setup and shape preferences on a managed emulator`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screens = listOf(
            "new-entry-setup" to NewEntryWidgetConfigActivity::class.java,
            "memories-setup" to OnThisDayWidgetConfigActivity::class.java,
            "fixed-memory-setup" to FixedMemoryWidgetConfigActivity::class.java,
        )
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: error("Run setup screenshots on a Gradle Managed Device")
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        val hadPermission = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (!hadPermission) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, permission)
        val resolver = context.contentResolver
        val photoUri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "logdate-widget-setup-photo.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.IS_PENDING, 1)
            },
        ) ?: error("Could not create emulator photo")
        try {
            resolver.openOutputStream(photoUri)?.use { output ->
                context.assets.open("sample_note_photo.jpg").use { it.copyTo(output) }
            } ?: error("Could not write emulator photo")
            resolver.update(photoUri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            screens.forEachIndexed { index, (name, activity) ->
                val intent = Intent(context, activity).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7000 + index)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                device.waitForIdle()
                if (device.hasObject(By.text("Digital Wellbeing isn't responding"))) {
                    device.findObject(By.text("Close app")).click()
                    device.waitForIdle()
                }
                val title = when (name) {
                    "memories-setup" -> "Memories"
                    "fixed-memory-setup" -> "Pinned memory"
                    else -> "New Entry shape"
                }
                assertTrue(device.wait(Until.hasObject(By.text(title)), 10_000L), "$title did not appear")
                Thread.sleep(800)
                val output = File(outputDir, "$name-android-${Build.VERSION.SDK_INT}.png")
                assertTrue(device.takeScreenshot(output), "Could not capture $name")
                if (name == "memories-setup") {
                    assertTrue(device.hasObject(By.text("Archive")))
                    assertTrue(device.hasObject(By.text("Photos")))
                    assertTrue(device.hasObject(By.text("Audio")))
                    device.findObject(By.text("Photos")).click()
                    device.waitForIdle()
                    Thread.sleep(700)
                    assertTrue(
                        device.takeScreenshot(File(outputDir, "memories-without-photos-android-${Build.VERSION.SDK_INT}.png")),
                    )
                }
                if (name == "fixed-memory-setup") {
                    assertFalse(device.hasObject(By.text("Choose an entry to keep close")))
                }
                if (name == "new-entry-setup") {
                    assertFalse(device.hasObject(By.text("Recent photo")))
                    for (shape in listOf("Soft", "Circle")) {
                        device.findObject(By.text(shape)).click()
                        device.waitForIdle()
                        val shapeOutput = File(outputDir, "new-entry-${shape.lowercase()}-setup-android-${Build.VERSION.SDK_INT}.png")
                        assertTrue(device.takeScreenshot(shapeOutput), "Could not capture $shape shape setup")
                    }
                    device.findObject(By.text("Save")).click()
                    device.waitForIdle()
                    assertEquals(PhotoWidgetShape.CIRCLE, WidgetInstanceSettings(context).photoShape(7000))
                } else {
                    device.pressBack()
                }
            }
        } finally {
            WidgetInstanceSettings(context).remove(7000)
            resolver.delete(photoUri, null, null)
        }
    }
}
