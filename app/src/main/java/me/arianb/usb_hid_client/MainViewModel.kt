package me.arianb.usb_hid_client

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManagerUserPreferences
import me.arianb.usb_hid_client.hid_utils.DevicePath
import me.arianb.usb_hid_client.hid_utils.ModifiesStateDirectly
import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import me.arianb.usb_hid_client.hid_utils.UHID
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.LoopbackTouchpadSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.TouchpadSender
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Data class that represents the UI state
 */
data class MyUiState(
    // Character Device Stuff
    val isCharacterDevicePermissionsBroken: String? = null,

    // Other Stuff
    val isDeviceUnplugged: Boolean = false
)

data class GadgetState(
    val areCharacterDevicesMissing: Boolean = false,
    val isOperationInProgress: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState

    // Snackbar queue
    private val _snackbarChannel = Channel<String>(capacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val snackbarFlow: Flow<String> = _snackbarChannel.receiveAsFlow()

    private val characterDeviceManager = CharacterDeviceManager.getInstance(application)
    private val rootStateHolder = RootStateHolder.getInstance()
    private val userPreferencesStateFlow = UserPreferencesRepository.getInstance(application).userPreferencesFlow
    private val characterDeviceUserPrefs = userPreferencesStateFlow.mapState {
        CharacterDeviceManagerUserPreferences.fromUserPreferences(it)
    }

    @OptIn(ModifiesStateDirectly::class)
    private val _gadgetState = MutableStateFlow(
        GadgetState(
            areCharacterDevicesMissing = characterDeviceManager.anyCharacterDeviceMissing(characterDeviceUserPrefs.value)
        )
    )
    val gadgetState: StateFlow<GadgetState> = _gadgetState

    val keySender: StateFlow<KeySender> = userPreferencesStateFlow
        .mapState {
            KeySender(it.keyboardCharacterDevicePath)
        }

    val touchpadSender: StateFlow<PointerDeviceSender> = userPreferencesStateFlow
        .mapState {
            // TODO:
            //  maybe make it clear to the user that the Loopback Mode always uses precision touchpad, and that these
            //  settings are therefore mutually exclusive.
            if (it.isLoopbackModeEnabled) {
                fixCharacterDevicePermissions(UHID.PATH)
                LoopbackTouchpadSender(TouchpadDevicePath(UHID.PATH))
            } else if (it.enablePrecisionTouchpad) {
                TouchpadSender(it.touchpadCharacterDevicePath)
            } else {
                MouseSender(it.touchpadCharacterDevicePath)
            }
        }

    private val senderFlowList = listOf(keySender, touchpadSender)

    init {
        senderFlowList.forEach { senderFlow ->
            viewModelScope.launch {
                senderFlow.collectLatest { sender ->
                    sender.start(
                        onSuccess = {
                            // This is called when no exception was thrown, meaning everything is good :)
                            // so let's set the UI state back to default (no errors)
                            _uiState.update { MyUiState() }
                        },
                        onException = { e ->
                            val characterDevicePath = sender.characterDevicePath
                            if (e is FileNotFoundException && characterDeviceMissing(characterDevicePath)) {
                                Timber.w("Character device '$characterDevicePath' doesn't exist. The user probably skipped the character device creation prompt.")

                                // TODO:
                                //  uncomment this once I make it not show on startup (since if it's missing on startup,
                                //  it will be showing the prompt)
                                //sendSnackbar("Character device '$characterDevicePath' doesn't exist. Did you skip the character device creation prompt?")
                            } else {
                                handleException(e, sender.characterDevicePath)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun sendSnackbar(message: String) {
        _snackbarChannel.trySend(message)
            .onFailure {
                Timber.e("Failed to add snackbar to channel: $it")
            }
            .onClosed {
                Timber.wtf("Failed to add snackbar to channel because it was closed, but it should NEVER be closed.")
            }
    }

    private fun handleException(e: IOException, devicePath: DevicePath) {
        val exceptionString = e.message ?: Log.getStackTraceString(e)
        val lowercaseExceptionString = exceptionString.lowercase()

        if (lowercaseExceptionString.contains("errno 108")) {
            Timber.i("device might be unplugged")
            _uiState.update { it.copy(isDeviceUnplugged = true) }
        } else if (lowercaseExceptionString.contains("permission denied")) {
            Timber.i("char dev perms are wrong")
            _uiState.update { it.copy(isCharacterDevicePermissionsBroken = devicePath.path) }
        } else if (lowercaseExceptionString.contains("enxio")) {
            Timber.i("somehow the HID gadget is disabled but the character devices are still present")
            sendSnackbar("ERROR: Failed to send mouse report. HID gadget has been disabled (but character devices are still present)")
        } else {
            val message = "unknown error has occurred while trying to write to character device"
            Timber.e(e, message)
            sendSnackbar("ERROR: $message")
        }

        Timber.v("in MainViewModel, new state is: %s", uiState.value.toString())
    }

    // Character Device Manager
    fun syncLogsToMainProcess() = viewModelScope.launch {
        if (!characterDeviceManager.syncLogsToMainProcessLogBuffer()) {
            Timber.w("Failed to sync logs from RootService to main process log buffer")
        }
    }

    private fun launchGadgetOperation(
        operationName: String,
        block: suspend () -> Unit,
    ) {
        if (!rootStateHolder.hasRootPermissions()) {
            val message = "Can't run operation '$operationName', missing root permissions"
            Timber.w(message)
            sendSnackbar(message)
            return
        }

        val previous = _gadgetState.getAndUpdate { it.copy(isOperationInProgress = true) }
        if (previous.isOperationInProgress) {
            Timber.w("Ignoring '$operationName' request: another gadget operation is already in progress")
            return
        }

        viewModelScope.launch {
            try {
                block()
            } finally {
                _gadgetState.update { it.copy(isOperationInProgress = false) }
            }
        }
    }

    fun createCharacterDevices() = launchGadgetOperation("create character devices") {
        try {
            val gadgetUserPreferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
            val succeeded = characterDeviceManager.createCharacterDevices(gadgetUserPreferences)
            if (!succeeded) {
                val message = "Failed to create character devices"
                Timber.e(message)
                sendSnackbar(message)
            }

            // Re-evaluate state
            anyCharacterDeviceMissing()
        } catch (e: CancellationException) {
            // Re-throw so coroutine structured cancellation propagates normally
            throw e
        } catch (e: Exception) {
            Timber.wtf(
                e,
                "Unhandled exception bubbled up to MainViewModel while creating character devices"
            )

            sendSnackbar("[BUG] Unhandled exception thrown while creating character devices: ${e.message}")
        }
    }

    fun deleteCharacterDevices() = launchGadgetOperation("delete character devices") {
        val gadgetUserPreferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
        val succeeded = characterDeviceManager.deleteCharacterDevices(gadgetUserPreferences)
        if (!succeeded) {
            val message = "Failed to delete character devices"
            Timber.e(message)
            sendSnackbar(message)
        }

        // Re-evaluate state
        anyCharacterDeviceMissing()
    }

    fun fixCharacterDevicePermissions(device: String) {
        if (!rootStateHolder.hasRootPermissions()) {
            val message = "Can't fix character device permissions, missing root permissions"
            Timber.w(message)
            sendSnackbar(message)
            return
        }

        characterDeviceManager.fixCharacterDevicePermissions(device)
    }

    @OptIn(ModifiesStateDirectly::class)
    fun characterDeviceMissing(charDevicePath: DevicePath): Boolean {
        val result = characterDeviceManager.characterDeviceMissing(charDevicePath, characterDeviceUserPrefs.value)

        _gadgetState.update { it.copy(areCharacterDevicesMissing = result) }

        return result
    }

    @OptIn(ModifiesStateDirectly::class)
    fun anyCharacterDeviceMissing(): Boolean {
        val result = characterDeviceManager.anyCharacterDeviceMissing(characterDeviceUserPrefs.value)

        _gadgetState.update { it.copy(areCharacterDevicesMissing = result) }

        return result
    }

    // Keyboard
    fun addStandardKey(modifier: Byte, key: Byte) =
        keySender.value.addStandardKey(modifier, key)

    fun addMediaKey(key: Byte) =
        keySender.value.addMediaKey(key)

    private inline fun <T, R> StateFlow<T>.mapState(
        crossinline transform: (value: T) -> R
    ) = mapState(viewModelScope, transform)
}
