package me.arianb.usb_hid_client.hid_utils

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.ui.utils.SnackbarQueue

class GadgetViewModel(application: Application) : AndroidViewModel(application) {
    private val gadgetStateHolder = GadgetStateHolder.getInstance(application)
    val gadgetState: StateFlow<GadgetState> = gadgetStateHolder.state

    private val snackbarQueue = SnackbarQueue()
    val snackbarFlow: Flow<String> = snackbarQueue.flow

    fun createCharacterDevices() = viewModelScope.launch {
        gadgetStateHolder.createCharacterDevices().errors.forEach(snackbarQueue::send)
    }

    fun deleteCharacterDevices() = viewModelScope.launch {
        gadgetStateHolder.deleteCharacterDevices().errors.forEach(snackbarQueue::send)
    }

    fun recreateCharacterDevices() = viewModelScope.launch {
        gadgetStateHolder.recreateCharacterDevices().errors.forEach(snackbarQueue::send)
    }

    fun fixCharacterDevicePermissions(device: String) {
        gadgetStateHolder.fixCharacterDevicePermissions(device).errors.forEach(snackbarQueue::send)
    }
}
