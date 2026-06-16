package app.logdate.client.sync.datalayer

data class RemoteCameraCaptureResult(
    val isSaved: Boolean,
    val message: String,
    val mediaType: String? = null,
)

object RemoteCameraCaptureResultDataMapper {
    const val PATH_CAMERA_CAPTURE_RESULT = "/logdate/camera/capture-result"

    private const val KEY_IS_SAVED = "isSaved"
    private const val KEY_MESSAGE = "message"
    private const val KEY_MEDIA_TYPE = "mediaType"

    fun toDataMap(result: RemoteCameraCaptureResult): Map<String, String> =
        buildMap {
            put(KEY_IS_SAVED, result.isSaved.toString())
            put(KEY_MESSAGE, result.message)
            result.mediaType?.let { put(KEY_MEDIA_TYPE, it) }
        }

    fun fromDataMap(data: Map<String, String>): RemoteCameraCaptureResult =
        RemoteCameraCaptureResult(
            isSaved = data[KEY_IS_SAVED]?.toBooleanStrictOrNull() ?: false,
            message = data[KEY_MESSAGE].orEmpty(),
            mediaType = data[KEY_MEDIA_TYPE],
        )
}
