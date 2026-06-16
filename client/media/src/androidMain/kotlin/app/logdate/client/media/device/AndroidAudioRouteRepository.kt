package app.logdate.client.media.device

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidAudioRouteRepository(
    context: Context,
) : AudioRouteRepository {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _inputDevices = MutableStateFlow(buildInputState())
    private val _outputDevices = MutableStateFlow(buildOutputState())

    private val deviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                refreshDevices()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                refreshDevices()
            }
        }

    override val inputDevices: StateFlow<MediaDeviceSelectionUiState> = _inputDevices
    override val outputDevices: StateFlow<MediaDeviceSelectionUiState> = _outputDevices

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        refreshDevices()
    }

    override fun selectInputDevice(deviceId: String) {
        val selectedDevice =
            audioManager
                .getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { AndroidAudioRouteDevices.deviceKey(it, MediaDeviceKind.AUDIO_INPUT) == deviceId }

        if (selectedDevice == null) {
            Napier.w("Ignored unavailable microphone route selection: $deviceId")
            return
        }

        AndroidAudioRouteDevices.setPreferredInputDeviceId(appContext, deviceId)
        refreshDevices()
    }

    override fun selectOutputDevice(deviceId: String) {
        val selectedDevice =
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { AndroidAudioRouteDevices.deviceKey(it, MediaDeviceKind.AUDIO_OUTPUT) == deviceId }

        if (selectedDevice == null) {
            Napier.w("Ignored unavailable output route selection: $deviceId")
            return
        }

        AndroidAudioRouteDevices.setPreferredOutputDeviceId(appContext, deviceId)
        refreshDevices()
    }

    private fun refreshDevices() {
        _inputDevices.value = buildInputState()
        _outputDevices.value = buildOutputState()
    }

    private fun buildInputState(): MediaDeviceSelectionUiState {
        val devices =
            audioManager
                .getDevices(AudioManager.GET_DEVICES_INPUTS)
                .filter { it.isSource }
                .map { it.toMediaDeviceUiState(MediaDeviceKind.AUDIO_INPUT) }

        return MediaDeviceSelectionResolver.resolveAudioInput(
            devices = devices,
            preferredDeviceId = AndroidAudioRouteDevices.getPreferredInputDeviceId(appContext),
        )
    }

    private fun buildOutputState(): MediaDeviceSelectionUiState {
        val devices =
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.isSink }
                .map { it.toMediaDeviceUiState(MediaDeviceKind.AUDIO_OUTPUT) }

        return MediaDeviceSelectionResolver.resolveAudioOutput(
            devices = devices,
            preferredDeviceId = AndroidAudioRouteDevices.getPreferredOutputDeviceId(appContext),
        )
    }
}

object AndroidAudioRouteDevices {
    private const val PREFS_NAME = "logdate_media_routes"
    private const val KEY_INPUT_DEVICE_ID = "preferred_input_device_id"
    private const val KEY_OUTPUT_DEVICE_ID = "preferred_output_device_id"

    fun getPreferredInputDeviceId(context: Context): String? =
        context
            .applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_INPUT_DEVICE_ID, null)

    fun setPreferredInputDeviceId(
        context: Context,
        deviceId: String,
    ) {
        context
            .applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_INPUT_DEVICE_ID, deviceId)
            .apply()
    }

    fun getPreferredOutputDeviceId(context: Context): String? =
        context
            .applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_OUTPUT_DEVICE_ID, null)

    fun setPreferredOutputDeviceId(
        context: Context,
        deviceId: String,
    ) {
        context
            .applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OUTPUT_DEVICE_ID, deviceId)
            .apply()
    }

    fun findPreferredInputDevice(
        context: Context,
        preferredDeviceId: String?,
    ): AudioDeviceInfo? {
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val deviceId = preferredDeviceId ?: getPreferredInputDeviceId(context)
        return audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.isSource && deviceKey(it, MediaDeviceKind.AUDIO_INPUT) == deviceId }
    }

    fun deviceKey(
        device: AudioDeviceInfo,
        kind: MediaDeviceKind,
    ): String = "${kind.name.lowercase()}-${device.type}-${device.id}"
}

private fun AudioDeviceInfo.toMediaDeviceUiState(kind: MediaDeviceKind): MediaDeviceUiState =
    MediaDeviceUiState(
        id = AndroidAudioRouteDevices.deviceKey(this, kind),
        label = labelFor(kind),
        kind = kind,
        category = categoryForType(type),
        isExternal = isExternalType(type),
    )

private fun AudioDeviceInfo.labelFor(kind: MediaDeviceKind): String {
    val productName = productName?.toString()?.trim().orEmpty()
    if (productName.isNotBlank()) return productName

    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in microphone"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> if (kind == MediaDeviceKind.AUDIO_INPUT) "Headset microphone" else "Wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio device"
        AudioDeviceInfo.TYPE_USB_HEADSET -> if (kind == MediaDeviceKind.AUDIO_INPUT) "USB microphone" else "USB headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth audio"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth broadcast"
        else -> if (kind == MediaDeviceKind.AUDIO_INPUT) "External microphone" else "Audio output"
    }
}

private fun categoryForType(type: Int): MediaDeviceCategory =
    when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        -> MediaDeviceCategory.BUILT_IN
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        -> MediaDeviceCategory.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        -> MediaDeviceCategory.BLUETOOTH
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        -> MediaDeviceCategory.WIRED
        else -> MediaDeviceCategory.EXTERNAL
    }

private fun isExternalType(type: Int): Boolean =
    when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        -> false
        else -> true
    }
