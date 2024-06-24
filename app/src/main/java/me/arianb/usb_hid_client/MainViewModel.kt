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
import me.arianb.usb_hid_client.hid_utils.ModifiesStateDirectly
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.report_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.ReportSender
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState

    private val characterDeviceManager = CharacterDeviceManager.getInstance(application)
    private val rootStateHolder = RootStateHolder.getInstance()
    val keySender = KeySender()
    val mouseSender = MouseSender()

    init {
        for (sender: ReportSender in listOf(keySender, mouseSender)) {
            viewModelScope.launch(ReportSender.dispatcher) {
                sender.start { e ->
                    val characterDevicePath = sender.characterDevicePath
                    if (e is FileNotFoundException && characterDeviceMissing(characterDevicePath)) {
                        Timber.wtf("Character device '$characterDevicePath' doesn't exist. Its existence is verified on app start, so the only reason this should happen is if it was removed *after* the app started.")
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

    // Character Device Manager
    fun createCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.e("Can't create character devices, missing root permissions")
            return
        }

        viewModelScope.launch {
            characterDeviceManager.createCharacterDevices()
        }
    }

    fun deleteCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.e("Can't delete character devices, missing root permissions")
            return
        }

        characterDeviceManager.deleteCharacterDevices()
    }

    fun fixCharacterDevicePermissions(device: String) {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.e("Can't fix character device permissions, missing root permissions")
            return
        }

        characterDeviceManager.fixCharacterDevicePermissions(device)
    }

    @OptIn(ModifiesStateDirectly::class)
    fun characterDeviceMissing(charDevicePath: String): Boolean {
        val result = characterDeviceManager.characterDeviceMissing(charDevicePath)

        _uiState.update { uiState.value.copy(missingCharacterDevice = result) }

        return result
    }

    @OptIn(ModifiesStateDirectly::class)
    fun anyCharacterDeviceMissing(): Boolean {
        val result = characterDeviceManager.anyCharacterDeviceMissing()

        _uiState.update { uiState.value.copy(missingCharacterDevice = result) }

        return result
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
