# LogDate: A Home for Your Memories

This is a Kotlin Multiplatform project targeting Android, iOS, Desktop, Server.

## Modules Overview

The project is divided into several modules:

- `:app:mobile` - Android app module for phone devices.
- `:app:wear` - Android app module for wearable devices.
- `:build:logic:convention` - Conventions plugins for managing build configurations.
- `:core:testing` - Android library containing testing utilities.
- `:core:ui` - Android library with common Jetpack Compose UI widgets.
- `:core:util` - Kotlin-only module containing utility functions (not an Android library).
- `:data` - Android library for the data layer.
- `:dynamic` - Dynamic delivery module
- `:feature:details` - Android library for the details feature.
- `:feature:list` - Android library for the list feature.
- `:feature:wear:home` - Android library for the wear home feature.
- `:test:navigation` - Test-only module for navigation testing.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* `/server` is for the Ktor server application.

* `/shared` is for the code that will be shared between all targets in the project.
  The most important subfolder is `commonMain`. If preferred, you can add code to the platform-specific folders here too.

## Setup

Open the project in Android Studio and let it sync the project.

This project currently uses some Firebase APIs for analytics and performance monitoring. To set up
Firebase, follow the instructions in
the [Firebase documentation](https://firebase.google.com/docs/android/setup).

This project also requires a Meta app ID for some features. To get a Meta app ID, follow the
instructions in
the [Meta developer documentation](https://developers.facebook.com/docs/android/getting-started#app-id).
Once you have the Meta app ID, add it to your `local.properties` file:

```properties
metaAppId=<your-meta-app-id> # META_APP_ID
apiKeys.googleMaps=<your-meta-app-id> # GOOGLE_MAPS_PLACES_API_KEY
```

## Generating Documentation

To generate the documentation for all modules for this project, run the following command at the
root of the project:

```shell
./gradlew dokkaHtmlMultiModule
```

This will generate the documentation in the `dokka` directory at the project root.