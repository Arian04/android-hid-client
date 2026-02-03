package me.arianb.usb_hid_client.input_views.scripting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ScriptingViewModel: ViewModel() {
    var scriptLog by mutableStateOf("")
        private set

    fun updateScriptLog(newText: String) {
        scriptLog = newText
    }

    var manualInputText by mutableStateOf("")
}