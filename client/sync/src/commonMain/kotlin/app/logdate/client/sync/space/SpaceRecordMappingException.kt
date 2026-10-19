package app.logdate.client.sync.space

/**
 * A value that can't become a record, or a record that can't become LogDate data, without losing
 * something. The message names the record and the field, never the value.
 *
 * Mappers throw this instead of dropping or guessing a value. The transport catches it for the one
 * item or record it concerns and keeps syncing everything else:
 * - on upload, a [retryable] failure is retried later and any other is set aside in Sync Issues;
 * - on download, the record is kept as unreadable with its reason and opened again after an update.
 *
 * @property retryable whether the same item can map later without anyone changing it, such as a
 *   note whose media file hasn't finished uploading. A value the record can't hold is not.
 */
class SpaceRecordMappingException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false,
) : IllegalArgumentException(message, cause)
