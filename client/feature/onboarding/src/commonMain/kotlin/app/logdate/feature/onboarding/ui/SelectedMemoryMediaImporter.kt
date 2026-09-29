package app.logdate.feature.onboarding.ui

fun interface SelectedMemoryMediaImporter {
    suspend fun import(sourceUri: String): String

    suspend fun discard(managedUri: String) {}
}
