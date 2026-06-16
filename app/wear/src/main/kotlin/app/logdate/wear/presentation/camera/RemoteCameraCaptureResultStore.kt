package app.logdate.wear.presentation.camera

import app.logdate.client.sync.datalayer.RemoteCameraCaptureResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface RemoteCameraCaptureResultStore {
    val captureResults: Flow<RemoteCameraCaptureResult>
}

object WearRemoteCameraCaptureResultStore : RemoteCameraCaptureResultStore {
    private val _captureResults = MutableSharedFlow<RemoteCameraCaptureResult>(extraBufferCapacity = 1)
    override val captureResults: Flow<RemoteCameraCaptureResult> = _captureResults.asSharedFlow()

    fun publish(result: RemoteCameraCaptureResult) {
        _captureResults.tryEmit(result)
    }
}
