pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("app\\.logdate.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // TODO: Remove suppress once stable
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LogDate"
include(":app:mobile")
include(":core:model")
include(":core:data")
include(":core:ui")
include(":core:assets")
include(":core:database")
include(":core:datastore")
include(":core:camera")
include(":core:media")
include(":core:account")
include(":core:lib")
include(":core:sharing")
include(":core:audiorecorder")
include(":core:util")
include(":core:notifications")
include(":core:coroutines")
include(":core:billing")
include(":core:network")
include(":core:world")
include(":core:assist")
include(":core:theme")
include(":core:testing")
include(":core:sync")
include(":core:install-referrer")
include(":dynamic")
include(":feature:onboarding")
include(":feature:editor")
include(":feature:account")
include(":feature:journals")
include(":feature:timeline")
include(":feature:rewind")
include(":feature:library")
include(":feature:widgets")
include(":test:navigation")
