# Distributing LogDate

For more information on how to build Compose Multiplatform apps,
see [this][compose-publishing-tutorial]
tutorial.

## Building the app

### Android

This assumes you have Android Studio installed and set up.

#### Main App

For information on how to build the app for Android, see
the [Android documentation][android-publishing-tutorial].

For LogDate's automated Play Console publishing flow, required GitHub secrets,
and tag-based Android versioning, see
[`docs/reference/google-play-publishing.md`](./reference/google-play-publishing.md).

#### Wear OS App

```shell
./gradlew :app:wear:assembleRelease
```

The Wear OS APK is built and distributed independently from the phone app. For development
installation on a physical watch, see the [Wear OS README](../app/wear/README.md#installing-on-a-physical-watch).

### Desktop (macOS, Linux, Windows)

To build the app, the app must be packaged per platform.

Run the following commands at the root of the project to build the app for each platform:

#### macOS

```shell
./gradlew :app:compose-main:packageDmg
```

#### Linux

```shell
./gradlew :app:compose-main:packageDeb
```

#### Windows

```shell
./gradlew :app:compose-main:packageMsi
```

[compose-publishing-tutorial]: https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Native_distributions_and_local_execution

[android-publishing-tutorial]: https://developer.android.com/studio/publish/preparing
