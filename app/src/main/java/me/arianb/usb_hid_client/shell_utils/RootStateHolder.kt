package me.arianb.usb_hid_client.shell_utils

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

enum class RootMethod {
    UNKNOWN,
    UNROOTED,
    MAGISK,
    KERNELSU
}

data class RootState(
    val missingRootPrivileges: Boolean = false,
)

class RootStateHolder private constructor() {
    private val _uiState = MutableStateFlow(RootState())
    val uiState = _uiState.asStateFlow()

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
    val sepolicyCommand: String?
        get() {
            val rootMethod = detectRootMethod()

            return sepolicyMap[rootMethod]
        }

    fun hasRootPermissions(): Boolean {
        val hasRootPermissions = Shell.getShell().isRoot

        _uiState.update { it.copy(missingRootPrivileges = !hasRootPermissions) }

        return hasRootPermissions
    }

    fun detectRootMethod(): RootMethod {
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
        fun getInstance(): RootStateHolder {
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
