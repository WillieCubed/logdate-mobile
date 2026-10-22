// Share only Gradle's declared reusable task outputs within the same workflow run.
// Gradle still validates every task's inputs; renderers and image comparisons run normally.
val cacheDirectory = requireNotNull(System.getenv("LOGDATE_CI_BUILD_CACHE_DIR"))

settingsEvaluated {
    buildCache {
        local {
            directory = java.io.File(cacheDirectory)
            isEnabled = true
        }
    }
}
