package me.arianb.usb_hid_client.hid_utils

import me.arianb.usb_hid_client.settings.GadgetUserPreferences

interface ICharacterDeviceManager {
    suspend fun syncLogsToMainProcessLogBuffer(): Boolean
    suspend fun createCharacterDevices(gadgetUserPreferences: GadgetUserPreferences): Boolean
    fun fixCharacterDevicePermissions(device: DevicePath)
    fun fixCharacterDevicePermissions(device: String)

    suspend fun deleteCharacterDevices(gadgetUserPreferences: GadgetUserPreferences): Boolean

    @ModifiesStateDirectly
    fun anyCharacterDeviceMissing(userPreferences: CharacterDeviceManagerUserPreferences): Boolean
}