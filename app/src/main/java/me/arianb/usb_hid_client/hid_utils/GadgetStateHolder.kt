package me.arianb.usb_hid_client.hid_utils

import android.app.Application
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.settings.UserPreferences
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import me.arianb.usb_hid_client.shell_utils.IRootStateHolder
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

data class GadgetState(
    val areCharacterDevicesMissing: Boolean = false,
    /**
     * null if no preferences are applied (ex: configfs functions or character devices don't exist)
     */
    val appliedPreferences: GadgetUserPreferences? = null,
    val isOperationInProgress: Boolean = false,
) {
    fun needsRecreate(currentPreferences: GadgetUserPreferences): Boolean {
        return appliedPreferences != null && appliedPreferences != currentPreferences
    }
}

data class GadgetOperationResult(
    val errors: List<String> = emptyList(),
)

class GadgetStateHolder @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) internal constructor(
    private val characterDeviceManager: ICharacterDeviceManager,
    private val rootStateHolder: IRootStateHolder,
    private val userPreferencesFlow: StateFlow<UserPreferences>,
) {
    private val characterDeviceUserPrefs: CharacterDeviceManagerUserPreferences
        get() = CharacterDeviceManagerUserPreferences.fromUserPreferences(userPreferencesFlow.value)

    private val currentGadgetPreferences: GadgetUserPreferences
        get() = GadgetUserPreferences.fromUserPreferences(userPreferencesFlow.value)

    private val _state = MutableStateFlow(initialGadgetState())
    val state: StateFlow<GadgetState> = _state.asStateFlow()

    private suspend fun runGadgetOperation(
        operationName: String,
        block: suspend (errors: MutableList<String>) -> Unit,
    ): GadgetOperationResult {
        if (!rootStateHolder.hasRootPermissions()) {
            val message = "Can't run operation '$operationName', missing root permissions"
            Timber.w(message)
            return GadgetOperationResult(errors = listOf(message))
        }

        val previous = _state.getAndUpdate { it.copy(isOperationInProgress = true) }
        if (previous.isOperationInProgress) {
            Timber.w("Ignoring '$operationName' request: another gadget operation is already in progress")
            return GadgetOperationResult()
        }

        val errors = mutableListOf<String>()
        try {
            block(errors)
        } catch (e: CancellationException) {
            // Re-throw so coroutine structured cancellation propagates normally
            throw e
        } catch (e: Exception) {
            Timber.wtf(
                e,
                "Unhandled exception bubbled up to GadgetStateHolder during '$operationName'"
            )
            errors += "[BUG] Unhandled exception thrown during '$operationName': ${e.message}"
        } finally {
            _state.update { it.copy(isOperationInProgress = false) }
        }
        return GadgetOperationResult(errors = errors)
    }

    suspend fun createCharacterDevices(): GadgetOperationResult =
        runGadgetOperation("create character devices") { errors ->
            val gadgetUserPreferences = currentGadgetPreferences
            val succeeded = characterDeviceManager.createCharacterDevices(gadgetUserPreferences)
            if (!succeeded) {
                val message = "Failed to create character devices"
                Timber.e(message)
                errors += message
            }

            // Re-evaluate state
            val anyMissing = anyCharacterDeviceMissing()
            if (succeeded && !anyMissing) {
                _state.update { it.copy(appliedPreferences = gadgetUserPreferences) }
            }
        }

    suspend fun deleteCharacterDevices(): GadgetOperationResult =
        runGadgetOperation("delete character devices") { errors ->
            val gadgetUserPreferences = _state.value.appliedPreferences ?: currentGadgetPreferences
            val succeeded = characterDeviceManager.deleteCharacterDevices(gadgetUserPreferences)
            if (!succeeded) {
                val message = "Failed to delete character devices"
                Timber.e(message)
                errors += message
            }

            // Re-evaluate state
            anyCharacterDeviceMissing()
            if (succeeded) {
                _state.update { it.copy(appliedPreferences = null) }
            }
        }

    suspend fun recreateCharacterDevices(): GadgetOperationResult =
        runGadgetOperation("recreate character devices") { errors ->
            val currentPrefs = currentGadgetPreferences
            val previousPrefs = _state.value.appliedPreferences ?: currentPrefs

            // Delete the old stuff
            val deleted = characterDeviceManager.deleteCharacterDevices(previousPrefs)
            if (!deleted) {
                val message = "Failed to delete existing character devices during recreate"
                Timber.e(message)
                errors += message
            }

            // Create the new stuff :)
            val created = characterDeviceManager.createCharacterDevices(currentPrefs)
            if (!created) {
                val message = "Failed to create character devices"
                Timber.e(message)
                errors += message
            }

            // Re-evaluate state
            val anyMissing = anyCharacterDeviceMissing()
            if (created && !anyMissing) {
                _state.update { it.copy(appliedPreferences = currentPrefs) }
            } else if (deleted && anyMissing) {
                _state.update { it.copy(appliedPreferences = null) }
            }
        }

    fun fixCharacterDevicePermissions(device: String): GadgetOperationResult {
        if (!rootStateHolder.hasRootPermissions()) {
            val message = "Can't fix character device permissions, missing root permissions"
            Timber.w(message)
            return GadgetOperationResult(errors = listOf(message))
        }

        characterDeviceManager.fixCharacterDevicePermissions(device)
        return GadgetOperationResult()
    }

    @OptIn(ModifiesStateDirectly::class)
    fun anyCharacterDeviceMissing(): Boolean {
        val result = characterDeviceManager.anyCharacterDeviceMissing(characterDeviceUserPrefs)

        // NOTE: Intentionally not clearing appliedPreferences when result is true.
        //  This is so that if (for example) the user edits a char device path to one that doesn't exist yet,
        //  we still need appliedPreferences intact so delete/recreate can know what was previously applied.
        _state.update { it.copy(areCharacterDevicesMissing = result) }

        return result
    }

    /**
     * Just a simple little helper for initializing the gadget state StateFlow. Should only be called in one place.
     */
    @OptIn(ModifiesStateDirectly::class)
    private fun initialGadgetState(): GadgetState {
        val areMissing = characterDeviceManager.anyCharacterDeviceMissing(characterDeviceUserPrefs)
        val appliedPrefs = if (areMissing) {
            null
        } else {
            currentGadgetPreferences
        }
        return GadgetState(
            areCharacterDevicesMissing = areMissing,
            appliedPreferences = appliedPrefs,
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: GadgetStateHolder? = null

        fun getInstance(application: Application): GadgetStateHolder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let {
                    return it
                }

                val instance = GadgetStateHolder(
                    characterDeviceManager = CharacterDeviceManager.getInstance(application),
                    rootStateHolder = RootStateHolder.getInstance(),
                    userPreferencesFlow = UserPreferencesRepository.getInstance(application).userPreferencesFlow,
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
