package me.arianb.usb_hid_client.hid_utils

import me.arianb.usb_hid_client.settings.GadgetUserPreferences

interface ICharacterDeviceManager {
    suspend fun syncLogsToMainProcessLogBuffer()
    suspend fun createCharacterDevices(gadgetUserPreferences: GadgetUserPreferences)
    fun fixCharacterDevicePermissions(device: DevicePath)
    fun fixCharacterDevicePermissions(device: String)

    suspend fun deleteCharacterDevices(gadgetUserPreferences: GadgetUserPreferences)

    @ModifiesStateDirectly
    fun characterDeviceMissing(
        charDevicePath: DevicePath,
        userPreferences: CharacterDeviceManagerUserPreferences
    ): Boolean

    @ModifiesStateDirectly
    fun anyCharacterDeviceMissing(userPreferences: CharacterDeviceManagerUserPreferences): Boolean
}