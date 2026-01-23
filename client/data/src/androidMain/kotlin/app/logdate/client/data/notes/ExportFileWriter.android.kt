package app.logdate.client.data.notes

import java.io.File

internal actual fun writeExportFile(
    destination: String,
    content: String,
    overwrite: Boolean
) {
    val file = File(destination)
    if (file.exists() && !overwrite) {
        throw IllegalStateException("File already exists and overwrite is set to false.")
    }
    file.writeText(content)
}
