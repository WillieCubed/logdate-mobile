# LogDate: A Home for Your Memories

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
metaAppId="<your-meta-app-id>"
```

## Generating Documentation

TODO: Figure out why documentation task fails

To generate the documentation for all modules for this project, run the following command:

```shell
./gradlew dokkaHtmlMultiModule
```