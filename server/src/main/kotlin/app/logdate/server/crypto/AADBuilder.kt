package app.logdate.server.crypto

object AADBuilder {
    fun forMedia(
        userId: String,
        mediaId: String,
        contentId: String,
    ): ByteArray =
        "type=MEDIA|v=1|userId=$userId|mediaId=$mediaId|contentId=$contentId"
            .toByteArray(Charsets.UTF_8)

    fun forBackup(
        userId: String,
        backupId: String,
    ): ByteArray =
        "type=BACKUP|v=1|userId=$userId|backupId=$backupId"
            .toByteArray(Charsets.UTF_8)
}
