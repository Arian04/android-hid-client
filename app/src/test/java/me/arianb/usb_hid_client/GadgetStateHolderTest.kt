package me.arianb.usb_hid_client

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManagerUserPreferences
import me.arianb.usb_hid_client.hid_utils.DevicePath
import me.arianb.usb_hid_client.hid_utils.GadgetOperationResult
import me.arianb.usb_hid_client.hid_utils.GadgetState
import me.arianb.usb_hid_client.hid_utils.GadgetStateHolder
import me.arianb.usb_hid_client.hid_utils.ICharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.KeyboardDevicePath
import me.arianb.usb_hid_client.hid_utils.ModifiesStateDirectly
import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import me.arianb.usb_hid_client.hid_utils.UHID
import me.arianb.usb_hid_client.hid_utils.service.UsbGadgetPath
import me.arianb.usb_hid_client.settings.AppTheme
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.settings.UserPreferences
import me.arianb.usb_hid_client.shell_utils.IRootStateHolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class GadgetStateHolderTest {
    private val characterDeviceManager = FakeCharacterDeviceManager()
    private val rootStateHolder: IRootStateHolder = mockk {
        every { hasRootPermissions() } returns true
    }
    private val userPreferencesFlow = MutableStateFlow(defaultUserPreferences())

    private fun createHolder(devicesExist: Boolean = false): GadgetStateHolder {
        if (devicesExist) {
            characterDeviceManager.existingDevices.addAll(DEFAULT_GADGET_PREFS.allCharacterDevicePathsList)
        }
        return GadgetStateHolder(
            characterDeviceManager = characterDeviceManager,
            rootStateHolder = rootStateHolder,
            userPreferencesFlow = userPreferencesFlow,
        )
    }

    // ---------- Initial State ----------

    @Test
    fun `initial state sets appliedPreferences when all character devices exist`() {
        val holder = createHolder(devicesExist = true)

        assertEquals(
            GadgetState(areCharacterDevicesMissing = false, appliedPreferences = DEFAULT_GADGET_PREFS),
            holder.state.value,
        )
    }

    @Test
    fun `initial state leaves appliedPreferences null when character devices are missing`() {
        val holder = createHolder()

        assertEquals(
            GadgetState(areCharacterDevicesMissing = true, appliedPreferences = null),
            holder.state.value,
        )
    }

    // ---------- Operation Guards ----------

    @ParameterizedTest
    @MethodSource("rootGatedOperations")
    fun `operations return error without calling manager when root permissions are missing`(operation: GadgetOperation) =
        runBlocking {
            every { rootStateHolder.hasRootPermissions() } returns false
            val holder = createHolder(devicesExist = true)

            val result = operation.run(holder)

            assertEquals(1, result.errors.size)
            assertTrue(characterDeviceManager.createdPreferences.isEmpty())
            assertTrue(characterDeviceManager.deletedPreferences.isEmpty())
            assertTrue(characterDeviceManager.fixedPermissionDevices.isEmpty())
            assertFalse(holder.state.value.isOperationInProgress)
        }

    @ParameterizedTest
    @MethodSource("asyncGadgetOperations")
    fun `gadget operations catch unexpected exceptions and reset isOperationInProgress`(operation: GadgetOperation) =
        runBlocking {
            characterDeviceManager.nextException = IllegalStateException("boom")
            val holder = createHolder(devicesExist = true)

            val result = operation.run(holder)

            assertEquals(1, result.errors.size)
            assertFalse(holder.state.value.isOperationInProgress)
        }

    @Test
    fun `createCharacterDevices rethrows CancellationException and resets isOperationInProgress`() {
        characterDeviceManager.nextException = CancellationException("cancelled")
        val holder = createHolder()

        assertThrows(CancellationException::class.java) {
            runBlocking { holder.createCharacterDevices() }
        }
        assertFalse(holder.state.value.isOperationInProgress)
    }

    @Test
    fun `concurrent gadget operation is ignored while another operation is in progress`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        characterDeviceManager.createGate = gate
        val holder = createHolder()

        val firstOperation = async { holder.createCharacterDevices() }
        yield()
        assertTrue(holder.state.value.isOperationInProgress)

        val secondResult = holder.deleteCharacterDevices()
        assertTrue(secondResult.errors.isEmpty())
        assertTrue(characterDeviceManager.deletedPreferences.isEmpty())
        assertTrue(holder.state.value.isOperationInProgress)

        gate.complete(Unit)
        assertTrue(firstOperation.await().errors.isEmpty())
        assertFalse(holder.state.value.isOperationInProgress)
    }

    // ---------- Create / Delete / Recreate ----------

    @Test
    fun `createCharacterDevices updates state and sets appliedPreferences on success`() = runBlocking {
        val holder = createHolder()

        val result = holder.createCharacterDevices()

        assertTrue(result.errors.isEmpty())
        assertEquals(listOf(DEFAULT_GADGET_PREFS), characterDeviceManager.createdPreferences)
        assertEquals(
            GadgetState(areCharacterDevicesMissing = false, appliedPreferences = DEFAULT_GADGET_PREFS),
            holder.state.value,
        )
    }

    @Test
    fun `createCharacterDevices returns error and keeps appliedPreferences null on failure`() = runBlocking {
        characterDeviceManager.createResult = false
        val holder = createHolder()

        val result = holder.createCharacterDevices()

        assertEquals(1, result.errors.size)
        assertEquals(
            GadgetState(areCharacterDevicesMissing = true, appliedPreferences = null),
            holder.state.value,
        )
    }

    @Test
    fun `deleteCharacterDevices deletes previously applied preferences and clears appliedPreferences`() = runBlocking {
        val holder = createHolder(devicesExist = true)

        // User edits preferences after creation; delete must still target the previously applied preferences.
        userPreferencesFlow.value = defaultUserPreferences(keyboardPath = KeyboardDevicePath("/dev/hidg2"))

        val result = holder.deleteCharacterDevices()

        assertTrue(result.errors.isEmpty())
        assertEquals(listOf(DEFAULT_GADGET_PREFS), characterDeviceManager.deletedPreferences)
        assertEquals(
            GadgetState(areCharacterDevicesMissing = true, appliedPreferences = null),
            holder.state.value,
        )
    }

    @Test
    fun `deleteCharacterDevices returns error and retains appliedPreferences on failure`() = runBlocking {
        characterDeviceManager.deleteResult = false
        val holder = createHolder(devicesExist = true)

        val result = holder.deleteCharacterDevices()

        assertEquals(1, result.errors.size)
        assertEquals(
            GadgetState(areCharacterDevicesMissing = false, appliedPreferences = DEFAULT_GADGET_PREFS),
            holder.state.value,
        )
    }

    @Test
    fun `recreateCharacterDevices deletes previous preferences and creates current preferences`() = runBlocking {
        val holder = createHolder(devicesExist = true)

        val updatedUserPrefs = defaultUserPreferences(keyboardPath = KeyboardDevicePath("/dev/hidg2"))
        val updatedGadgetPrefs = GadgetUserPreferences.fromUserPreferences(updatedUserPrefs)
        userPreferencesFlow.value = updatedUserPrefs

        val result = holder.recreateCharacterDevices()

        assertTrue(result.errors.isEmpty())
        assertEquals(listOf(DEFAULT_GADGET_PREFS), characterDeviceManager.deletedPreferences)
        assertEquals(listOf(updatedGadgetPrefs), characterDeviceManager.createdPreferences)
        assertEquals(
            GadgetState(areCharacterDevicesMissing = false, appliedPreferences = updatedGadgetPrefs),
            holder.state.value,
        )
    }

    @Test
    fun `recreateCharacterDevices clears appliedPreferences when delete succeeds and create fails`() = runBlocking {
        characterDeviceManager.createResult = false
        val holder = createHolder(devicesExist = true)

        val result = holder.recreateCharacterDevices()

        assertEquals(1, result.errors.size)
        assertEquals(
            GadgetState(areCharacterDevicesMissing = true, appliedPreferences = null),
            holder.state.value,
        )
    }

    @Test
    fun `fixCharacterDevicePermissions delegates to manager when rooted`() {
        val holder = createHolder()

        val result = holder.fixCharacterDevicePermissions(UHID.PATH)

        assertTrue(result.errors.isEmpty())
        assertEquals(listOf(UHID.PATH), characterDeviceManager.fixedPermissionDevices)
    }

    // ---------- Missing Device State ----------

    @Test
    fun `anyCharacterDeviceMissing preserves appliedPreferences when devices become missing`() {
        val holder = createHolder(devicesExist = true)

        // User edits a path to one that doesn't exist yet; appliedPreferences must stay intact for delete/recreate.
        userPreferencesFlow.value = defaultUserPreferences(keyboardPath = KeyboardDevicePath("/dev/hidg2"))

        assertTrue(holder.anyCharacterDeviceMissing())
        assertEquals(
            GadgetState(areCharacterDevicesMissing = true, appliedPreferences = DEFAULT_GADGET_PREFS),
            holder.state.value,
        )
    }

    @Test
    fun `characterDeviceMissing does not mark gadget devices missing for non-gadget paths like loopback uhid`() {
        val holder = createHolder(devicesExist = true)

        holder.characterDeviceMissing(TouchpadDevicePath(UHID.PATH))

        assertFalse(holder.state.value.areCharacterDevicesMissing)
    }

    @Test
    fun `characterDeviceMissing does not clear areCharacterDevicesMissing when another gadget device is still missing`() {
        // Only the keyboard device exists; touchpad is still missing.
        characterDeviceManager.existingDevices.add(DEFAULT_GADGET_PREFS.keyboardCharacterDevicePath)
        val holder = createHolder()
        assertTrue(holder.state.value.areCharacterDevicesMissing)

        assertFalse(holder.characterDeviceMissing(DEFAULT_GADGET_PREFS.keyboardCharacterDevicePath))
        assertTrue(holder.state.value.areCharacterDevicesMissing)
    }

    // ---------- Fakes ----------

    private class FakeCharacterDeviceManager : ICharacterDeviceManager {
        val existingDevices = mutableSetOf<DevicePath>()
        val createdPreferences = mutableListOf<GadgetUserPreferences>()
        val deletedPreferences = mutableListOf<GadgetUserPreferences>()
        val fixedPermissionDevices = mutableListOf<String>()

        var createResult = true
        var deleteResult = true
        var nextException: Throwable? = null
        var createGate: CompletableDeferred<Unit>? = null

        override suspend fun syncLogsToMainProcessLogBuffer(): Boolean = true

        override suspend fun createCharacterDevices(gadgetUserPreferences: GadgetUserPreferences): Boolean {
            createGate?.await()
            nextException?.let { throw it }
            createdPreferences += gadgetUserPreferences
            if (createResult) existingDevices.addAll(gadgetUserPreferences.allCharacterDevicePathsList)
            return createResult
        }

        override fun fixCharacterDevicePermissions(device: DevicePath) = fixCharacterDevicePermissions(device.path)

        override fun fixCharacterDevicePermissions(device: String) {
            fixedPermissionDevices += device
        }

        override suspend fun deleteCharacterDevices(gadgetUserPreferences: GadgetUserPreferences): Boolean {
            nextException?.let { throw it }
            deletedPreferences += gadgetUserPreferences
            if (deleteResult) existingDevices.removeAll(gadgetUserPreferences.allCharacterDevicePathsList.toSet())
            return deleteResult
        }

        @ModifiesStateDirectly
        override fun characterDeviceMissing(
            charDevicePath: DevicePath,
            userPreferences: CharacterDeviceManagerUserPreferences,
        ): Boolean = charDevicePath !in userPreferences.allCharacterDevicePathsList || charDevicePath !in existingDevices

        @ModifiesStateDirectly
        override fun anyCharacterDeviceMissing(userPreferences: CharacterDeviceManagerUserPreferences): Boolean =
            userPreferences.allCharacterDevicePathsList.any { it !in existingDevices }
    }

    companion object {
        data class GadgetOperation(
            val name: String,
            val run: suspend GadgetStateHolder.() -> GadgetOperationResult,
        ) {
            override fun toString(): String = name
        }

        @JvmStatic
        fun asyncGadgetOperations(): List<GadgetOperation> = listOf(
            GadgetOperation("create") { createCharacterDevices() },
            GadgetOperation("delete") { deleteCharacterDevices() },
            GadgetOperation("recreate") { recreateCharacterDevices() },
        )

        @JvmStatic
        fun rootGatedOperations(): List<GadgetOperation> = asyncGadgetOperations() + listOf(
            GadgetOperation("fixCharacterDevicePermissions") { fixCharacterDevicePermissions(UHID.PATH) },
        )

        private fun defaultUserPreferences(
            keyboardPath: KeyboardDevicePath = CharacterDeviceManager.DEFAULT_KEYBOARD_DEVICE_PATH,
            touchpadPath: TouchpadDevicePath = CharacterDeviceManager.DEFAULT_TOUCHPAD_DEVICE_PATH,
        ): UserPreferences = UserPreferences(
            isOnboardingDone = true,
            clearManualInput = false,
            isVolumeButtonPassthroughEnabled = false,
            appTheme = AppTheme.System,
            isDynamicColorEnabled = false,
            isLoopbackModeEnabled = false,
            isTouchpadFullscreenInLandscape = false,
            isExperimentalModeEnabled = false,
            usbGadgetPath = UsbGadgetPath("/config/usb_gadget/g1"),
            keyboardCharacterDevicePath = keyboardPath,
            touchpadCharacterDevicePath = touchpadPath,
            createNewGadgetForFunctions = false,
            disableGadgetFunctionsDuringConfiguration = false,
            enablePrecisionTouchpad = false,
            enableScriptingSupport = false,
        )

        private val DEFAULT_GADGET_PREFS = GadgetUserPreferences.fromUserPreferences(defaultUserPreferences())
    }
}
