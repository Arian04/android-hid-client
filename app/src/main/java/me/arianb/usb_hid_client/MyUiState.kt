package me.arianb.usb_hid_client

/**
 * Data class that represents the UI state
 */
data class MyUiState(
    val missingRootPrivileges: Boolean = false,
    val missingCharacterDevice: Boolean = false,
    val isCharacterDevicePermissionsBroken: String? = null,
    val isDeviceUnplugged: Boolean = false
)
