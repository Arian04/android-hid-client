package me.arianb.usb_hid_client

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager.Companion.characterDeviceMissing
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.report_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.ReportSender
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState

    private val characterDeviceManager = CharacterDeviceManager(application)
    val keySender = KeySender()
    val mouseSender = MouseSender()

    init {
        for (sender: ReportSender in listOf(keySender, mouseSender)) {
            viewModelScope.launch(ReportSender.dispatcher) {
                sender.start { e ->
                    val characterDevicePath = sender.characterDevicePath
                    if (e is FileNotFoundException && characterDeviceMissing(characterDevicePath)) {
                        Timber.wtf("Character device '$characterDevicePath' doesn't exist. Its existence is verified on app start, so the only reason this should happen is if it was removed *after* the app started.")
                        _uiState.update { uiState.value.copy(missingCharacterDevice = true) }
                    } else {
                        handleException(e, sender.characterDevicePath)
                    }
                }
            }
        }
    }

    private fun handleException(e: IOException, devicePath: String) {
        val lowercaseStacktrace = Log.getStackTraceString(e).lowercase()

        if (lowercaseStacktrace.contains("errno 108")) {
            Timber.d("device might be unplugged")
            _uiState.update { uiState.value.copy(isDeviceUnplugged = true) }
        } else if (lowercaseStacktrace.contains("permission denied")) {
            Timber.d("char dev perms are wrong")
            _uiState.update { uiState.value.copy(isCharacterDevicePermissionsBroken = devicePath) }
        } else if (lowercaseStacktrace.contains("enxio")) {
            Timber.d("somehow the HID gadget is disabled but the character devices are still present")
        } else {
            Timber.d("something else is wrong, idk")
//            showSnackbar("ERROR: Failed to send mouse report.", Snackbar.LENGTH_SHORT)
        }

        Timber.d("in MainViewModel, new state is: %s", uiState.value.toString())
    }

    // Character Device
    fun createCharacterDevices() {
        viewModelScope.launch {
            if (!characterDeviceManager.createCharacterDevices()) {
                Timber.e("Failed to create character device(s), missing root permissions")
                _uiState.update { MyUiState(missingRootPrivileges = true) }
            }
        }
    }

    fun deleteCharacterDevices() {
        if (!characterDeviceManager.deleteCharacterDevices()) {
            Timber.e("Failed to delete character devices, missing root permissions")
            _uiState.update { MyUiState(missingRootPrivileges = true) }
        }
    }

    fun fixCharacterDevicePermissions(devicePath: String) {
        if (!characterDeviceManager.fixCharacterDevicePermissions(devicePath)) {
            Timber.e("Failed to fix character device permissions, missing root permissions")
            _uiState.update { MyUiState(missingRootPrivileges = true) }
        }
    }

    fun anyCharacterDeviceMissing(): Boolean {
        val isMissing = CharacterDeviceManager.anyCharacterDeviceMissing()

        _uiState.update { uiState.value.copy(missingCharacterDevice = isMissing) }

        return isMissing
    }

    // Keyboard
    fun addStandardKey(modifier: Byte, key: Byte) =
        keySender.addStandardKey(modifier, key)

    fun addMediaKey(key: Byte) =
        keySender.addMediaKey(key)

    // Mouse
    fun click(button: Byte) =
        mouseSender.click(button)

    fun move(x: Byte, y: Byte) =
        mouseSender.move(x, y)
}
