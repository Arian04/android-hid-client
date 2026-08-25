package me.arianb.usb_hid_client.shell_utils

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

class RootStateHolder private constructor() : IRootStateHolder {
    private val _uiState = MutableStateFlow(RootState())
    override val uiState = _uiState.asStateFlow()

    private val sepolicyMap: Map<RootMethod, String?> = buildMap {
        put(RootMethod.UNKNOWN, null)
        put(RootMethod.UNROOTED, null)
        put(RootMethod.MAGISK, "magiskpolicy --live")
        put(RootMethod.KERNELSU, "ksud sepolicy patch")
    }
    private val rootBinaryMap: Map<String, RootMethod> = buildMap {
        put("magisk", RootMethod.MAGISK)
        put("magiskpolicy", RootMethod.MAGISK)
        put("ksud", RootMethod.KERNELSU)
    }

    // TODO: should this be part of RootState?
    override val sepolicyCommand: String?
        get() {
            val rootMethod = detectRootMethod()

            return sepolicyMap[rootMethod]
        }

    override fun hasRootPermissions(): Boolean {
        val hasRootPermissions = Shell.getShell().isRoot

        _uiState.update { it.copy(missingRootPrivileges = !hasRootPermissions) }

        return hasRootPermissions
    }

    override fun detectRootMethod(): RootMethod {
        if (!hasRootPermissions()) {
            Timber.i("Failed to get root shell. Device is most likely not rooted or hasn't given the app root permissions")
            return RootMethod.UNROOTED
        }

        for ((binary, matchingRootMethod) in rootBinaryMap) {
            //Timber.d("checking for binary: %s", binary);
            val commandResult = Shell.cmd("type $binary").exec()
            if (commandResult.code == 0) {
                Timber.i("Detected root method as: %s", matchingRootMethod)
                return matchingRootMethod
            }
        }
        return RootMethod.UNKNOWN
    }

    companion object {
        @Volatile
        private var INSTANCE: RootStateHolder? = null
        fun getInstance(): IRootStateHolder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let {
                    return it
                }

                val instance = RootStateHolder()
                INSTANCE = instance
                instance
            }
        }
    }
}
