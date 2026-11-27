package me.arianb.usb_hid_client

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.hid_utils.GadgetStateHolder
import me.arianb.usb_hid_client.hid_utils.UHID
import me.arianb.usb_hid_client.report_senders.CharDeviceTransport
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.report_senders.ReportTransport
import me.arianb.usb_hid_client.report_senders.UhidTransport
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.TouchpadSender
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import me.arianb.usb_hid_client.ui.utils.SnackbarQueue
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Data class that represents the UI state
 */
data class MyUiState(
    // Character Device Stuff
    val isCharacterDevicePermissionsBroken: String? = null,

    // Other Stuff
    val isDeviceUnplugged: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState

    private val snackbarQueue = SnackbarQueue()
    val snackbarFlow: Flow<String> = snackbarQueue.flow

    private val gadgetStateHolder = GadgetStateHolder.getInstance(application)
    private val userPreferencesStateFlow = UserPreferencesRepository.getInstance(application).userPreferencesFlow

    val keySender: StateFlow<KeySender> = userPreferencesStateFlow
        .mapState {
            KeySender(CharDeviceTransport(it.keyboardCharacterDevicePath))
        }

    val touchpadSender: StateFlow<PointerDeviceSender> = userPreferencesStateFlow
        .mapState {
            // TODO:
            //  maybe make it clear to the user that the Loopback Mode always uses precision touchpad, and that these
            //  settings are therefore mutually exclusive.
            if (it.isLoopbackModeEnabled) {
                gadgetStateHolder.fixCharacterDevicePermissions(UHID.PATH).errors.forEach(snackbarQueue::send)
                TouchpadSender(UhidTransport)
            } else if (it.enablePrecisionTouchpad) {
                TouchpadSender(CharDeviceTransport(it.touchpadCharacterDevicePath))
            } else {
                MouseSender(CharDeviceTransport(it.touchpadCharacterDevicePath))
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
                            val transport = sender.transport
                            if (
                                e is FileNotFoundException &&
                                transport is CharDeviceTransport &&
                                gadgetStateHolder.characterDeviceMissing(transport.devicePath)
                            ) {
                                Timber.w("Character device '${transport.devicePath}' doesn't exist. The user probably skipped the character device creation prompt.")

                                // TODO:
                                //  uncomment this once I make it not show on startup (since if it's missing on startup,
                                //  it will be showing the prompt)
                                //sendSnackbar("Character device '${transport.devicePath}' doesn't exist. Did you skip the character device creation prompt?")
                            } else {
                                handleException(e, transport)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun handleException(e: IOException, transport: ReportTransport) {
        val exceptionString = e.message ?: Log.getStackTraceString(e)
        val lowercaseExceptionString = exceptionString.lowercase()

        if (lowercaseExceptionString.contains("errno 108")) {
            Timber.i("device might be unplugged")
            _uiState.update { it.copy(isDeviceUnplugged = true) }
        } else if (lowercaseExceptionString.contains("permission denied") && transport is CharDeviceTransport) {
            Timber.i("char dev perms are wrong")
            _uiState.update { it.copy(isCharacterDevicePermissionsBroken = transport.devicePath.path) }
        } else if (lowercaseExceptionString.contains("enxio")) {
            Timber.i("somehow the HID gadget is disabled but the character devices are still present")
            snackbarQueue.send("ERROR: Failed to send HID report. HID gadget has been disabled (but character devices are still present)")
        } else {
            val message = "unknown error has occurred while trying to write to character device"
            Timber.e(e, message)
            snackbarQueue.send("ERROR: $message")
        }

        Timber.v("in MainViewModel, new state is: %s", uiState.value.toString())
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
