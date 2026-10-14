@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import kotlin.time.Instant

class IosPhotoLibraryMediaManager(
    private val delegate: MediaManager,
) : MediaManager by delegate {
    override fun photoLibraryAccess(): PhotoLibraryAccess =
        when (PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)) {
            PHAuthorizationStatusAuthorized -> PhotoLibraryAccess.FULL
            PHAuthorizationStatusLimited -> PhotoLibraryAccess.LIMITED
            else -> PhotoLibraryAccess.DENIED
        }

    override suspend fun observeMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> =
        kotlinx.coroutines.flow.flow {
            photoLibraryChanges().collect { emit(delegate.queryMediaByDate(start, end).first()) }
        }

    private fun photoLibraryChanges(): Flow<Unit> =
        kotlinx.coroutines.flow.callbackFlow {
            val observer =
                object : platform.darwin.NSObject(), platform.Photos.PHPhotoLibraryChangeObserverProtocol {
                    override fun photoLibraryDidChange(changeInstance: platform.Photos.PHChange) {
                        trySend(Unit)
                    }
                }
            PHPhotoLibrary.sharedPhotoLibrary().registerChangeObserver(observer)
            trySend(Unit)
            awaitClose { PHPhotoLibrary.sharedPhotoLibrary().unregisterChangeObserver(observer) }
        }
}
