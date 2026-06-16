package app.logdate.client.media.device

internal fun systemControlledRouteControlMessage(kind: MediaDeviceKind): String =
    when (kind) {
        MediaDeviceKind.CAMERA -> "Camera selection is currently controlled by the system."
        MediaDeviceKind.AUDIO_INPUT -> "Microphone selection is currently controlled by the system."
        MediaDeviceKind.AUDIO_OUTPUT -> "Use Android's output switcher to route playback."
    }

internal fun unavailableRouteMessage(
    kind: MediaDeviceKind,
    replacementLabel: String,
): String =
    when (kind) {
        MediaDeviceKind.CAMERA -> "Selected camera is unavailable. LogDate will use $replacementLabel."
        MediaDeviceKind.AUDIO_INPUT -> "Selected microphone is unavailable. LogDate will use $replacementLabel."
        MediaDeviceKind.AUDIO_OUTPUT -> "Selected output is unavailable. LogDate will use $replacementLabel."
    }

fun systemControlledSelection(kind: MediaDeviceKind): MediaDeviceSelectionUiState =
    when (kind) {
        MediaDeviceKind.CAMERA ->
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.CAMERA,
                devices = listOf(DefaultMediaDevices.backCamera),
                selectedDeviceId = DefaultMediaDevices.backCamera.id,
                isSelectionControllable = false,
                routeControlMessage = systemControlledRouteControlMessage(MediaDeviceKind.CAMERA),
            )

        MediaDeviceKind.AUDIO_INPUT ->
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.AUDIO_INPUT,
                devices = listOf(DefaultMediaDevices.systemMicrophone),
                selectedDeviceId = DefaultMediaDevices.systemMicrophone.id,
                isSelectionControllable = false,
                routeControlMessage = systemControlledRouteControlMessage(MediaDeviceKind.AUDIO_INPUT),
            )

        MediaDeviceKind.AUDIO_OUTPUT ->
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.AUDIO_OUTPUT,
                devices = listOf(DefaultMediaDevices.systemOutput),
                selectedDeviceId = DefaultMediaDevices.systemOutput.id,
                isSelectionControllable = false,
                routeControlMessage = systemControlledRouteControlMessage(MediaDeviceKind.AUDIO_OUTPUT),
            )
    }

fun MediaDeviceSelectionUiState.asSystemControlledSelection(
    routeControlMessage: String = systemControlledRouteControlMessage(kind),
): MediaDeviceSelectionUiState =
    copy(
        isSelectionControllable = false,
        routeControlMessage = routeControlMessage,
    )
