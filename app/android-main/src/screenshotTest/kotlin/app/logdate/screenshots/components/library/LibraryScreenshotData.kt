package app.logdate.screenshots.components.library

import app.logdate.client.repository.journals.NoteLocation
import app.logdate.feature.library.ui.LibraryGridGroup
import app.logdate.feature.library.ui.LibraryMediaItem
import app.logdate.feature.library.ui.LibraryUiState
import app.logdate.feature.library.ui.detail.ExifDisplayData
import app.logdate.feature.library.ui.detail.JournalReference
import app.logdate.feature.library.ui.detail.MediaDetailUiState
import app.logdate.feature.library.ui.detail.PresenterMediaItem
import app.logdate.feature.library.ui.detail.PresenterState
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Canned test data for Library screenshot previews.
 */
object LibraryScreenshotData {
    private val march2026 = Instant.fromEpochMilliseconds(1_741_000_000_000L)
    private val feb2026 = Instant.fromEpochMilliseconds(1_738_000_000_000L)

    private val sampleItems =
        (1..9).map { i ->
            LibraryMediaItem(
                uid = Uuid.random(),
                uri = "content://media/external/images/$i",
                thumbnailUri = null,
                isVideo = i == 3 || i == 7,
                timestamp = march2026,
            )
        }

    val gridContent =
        LibraryUiState.Content(
            groups =
                listOf(
                    LibraryGridGroup(label = "March 2026", items = sampleItems.take(6)),
                    LibraryGridGroup(label = "February 2026", items = sampleItems.drop(6)),
                ),
            totalCount = 9,
        )

    val imageDetail =
        MediaDetailUiState.ImageContent(
            noteId = Uuid.random(),
            mediaRef = "content://media/external/images/1",
            createdAt = march2026,
            location = null,
            locationDisplayName = "San Francisco, CA",
            journals = listOf(JournalReference(id = Uuid.random(), title = "Trip to California")),
            exif =
                ExifDisplayData(
                    cameraMake = "Google",
                    cameraModel = "Pixel 9 Pro",
                    aperture = 1.68,
                    iso = 58,
                    focalLength = 6.9,
                    shutterSpeed = "1/120",
                ),
        )

    val presenterActive =
        PresenterState(
            isExternalDisplayAvailable = true,
            isPresenting = true,
            currentIndex = 2,
            totalItems = sampleItems.size,
            mediaItems =
                sampleItems.map {
                    PresenterMediaItem(uid = it.uid, uri = it.uri, isVideo = it.isVideo)
                },
        )
}
