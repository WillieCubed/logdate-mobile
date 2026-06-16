package app.logdate.client.media.device

internal object MediaDeviceSelectionResolver {
    fun resolveAudioInput(
        devices: List<MediaDeviceUiState>,
        preferredDeviceId: String?,
    ): MediaDeviceSelectionUiState {
        val availableDevices = devices.ifEmpty { listOf(DefaultMediaDevices.systemMicrophone) }
        val selectedId =
            preferredDeviceId
                ?.takeIf { preferred -> availableDevices.any { it.id == preferred } }
                ?: availableDevices.first().id
        val routeControlMessage =
            when {
                preferredDeviceId != null && preferredDeviceId != selectedId ->
                    unavailableRouteMessage(MediaDeviceKind.AUDIO_INPUT, availableDevices.first().label)
                availableDevices.size == 1 &&
                    availableDevices.first().category == MediaDeviceCategory.SYSTEM_DEFAULT ->
                    systemControlledRouteControlMessage(MediaDeviceKind.AUDIO_INPUT)
                else -> null
            }

        return MediaDeviceSelectionUiState(
            kind = MediaDeviceKind.AUDIO_INPUT,
            devices = availableDevices,
            selectedDeviceId = selectedId,
            isSelectionControllable = availableDevices.any { it.category != MediaDeviceCategory.SYSTEM_DEFAULT },
            routeControlMessage = routeControlMessage,
        )
    }

    fun resolveAudioOutput(
        devices: List<MediaDeviceUiState>,
        preferredDeviceId: String?,
    ): MediaDeviceSelectionUiState {
        val availableDevices = devices.ifEmpty { listOf(DefaultMediaDevices.systemOutput) }
        val selectedId =
            preferredDeviceId
                ?.takeIf { preferred -> availableDevices.any { it.id == preferred } }
                ?: availableDevices.first().id

        return MediaDeviceSelectionUiState(
            kind = MediaDeviceKind.AUDIO_OUTPUT,
            devices = availableDevices,
            selectedDeviceId = selectedId,
            isSelectionControllable = false,
            routeControlMessage = systemControlledRouteControlMessage(MediaDeviceKind.AUDIO_OUTPUT),
        )
    }
}
