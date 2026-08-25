package me.arianb.usb_hid_client.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.arianb.usb_hid_client.shell_utils.IRootStateHolder
import me.arianb.usb_hid_client.shell_utils.RootMethod
import me.arianb.usb_hid_client.shell_utils.RootState

class FakeRootStateHolder(val rootMethod: RootMethod) : IRootStateHolder {
    override val uiState: StateFlow<RootState> = MutableStateFlow(
        RootState(
            missingRootPrivileges = !hasRootPermissions()
        )
    )

    override val sepolicyCommand: String?
        get() = throw UnsupportedOperationException(
            "During tests, the method using this property should be stubbed, so this property should never be accessed."
        )

    override fun hasRootPermissions(): Boolean {
        when (rootMethod) {
            RootMethod.UNROOTED -> return false
            else -> return true
        }
    }

    override fun detectRootMethod(): RootMethod {
        return rootMethod
    }
}