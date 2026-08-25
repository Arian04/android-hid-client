package me.arianb.usb_hid_client.shell_utils

import kotlinx.coroutines.flow.StateFlow

enum class RootMethod {
    UNKNOWN,
    UNROOTED,
    MAGISK,
    KERNELSU
}

data class RootState(
    val missingRootPrivileges: Boolean = false,
)

interface IRootStateHolder {
    val uiState: StateFlow<RootState>
    val sepolicyCommand: String?

    fun hasRootPermissions(): Boolean
    fun detectRootMethod(): RootMethod
}