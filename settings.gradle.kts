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
include(":core:ui")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:media")
include(":core:lib")
include(":core:util")
include(":core:coroutines")
include(":core:world")
include(":core:testing")
include(":dynamic")
include(":feature:editor")
include(":feature:journals")
include(":feature:timeline")
include(":feature:rewind")
include(":feature:library")
//include(":feature:wear:home")
include(":test:navigation")
