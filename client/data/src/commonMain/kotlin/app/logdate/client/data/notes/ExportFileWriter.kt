package app.logdate.client.data.notes

internal expect fun writeExportFile(
    destination: String,
    content: String,
    overwrite: Boolean,
)
