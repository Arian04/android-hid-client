package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import timber.log.Timber
import java.util.BitSet

// TODO:
//  - remove report ID if it ends up not being necessary
//  - overall cleanup unnecessary parts of the report descriptor
open class TouchpadSender(
    characterDevicePath: String = CharacterDeviceManager.TOUCHPAD_DEVICE_PATH
) : ReportSender(
    characterDevicePath = characterDevicePath
) {
    fun send(contactID: Byte, tipSwitch: Boolean, x: Short, y: Short, scanTime: UShort, contactCount: Byte) {
        super.addReportToChannel(
            getTouchpadReport(contactID, tipSwitch, x, y, scanTime, contactCount)
        )
    }

    // TODO: improve readability, maybe use a bitset for the entire thing?
    private fun getTouchpadReport(
        contactID: Byte,
        tipSwitch: Boolean,
        x: Short,
        y: Short,
        scanTime: UShort,
        contactCount: Byte
    ): ByteArray {
        // Only send send non-zero contact count in the report of contact ID 0 as per the spec
        val realContactCount: Byte = if (contactID.toInt() == 0) {
            contactCount
        } else {
            0
        }

        val secondByteBitSet = BitSet(8)
        val isConfident = true
        secondByteBitSet.set(0, isConfident)
        secondByteBitSet.set(1, tipSwitch)

        // Padding
        secondByteBitSet.clear(2)
        secondByteBitSet.clear(3)

        // Contact ID
        // Copy lower 4 bits of contactID into high 4 bits of the BitSet
        for (i in 4..7) {
            secondByteBitSet.set(i, contactID.isBitSet(i - 4))
        }

        // Turn it into a byte
        var bitSetByteArray = secondByteBitSet.toByteArray()
        if (bitSetByteArray.size > 1) {
            Timber.wtf("ok guys this is not cool. bitSetByteArray.size = %d", bitSetByteArray.size)
        } else if (bitSetByteArray.isEmpty()) {
            Timber.wtf("ok guys this is REALLY not cool. bitSetByteArray is EMPTY somehow!!")
            bitSetByteArray = byteArrayOf(0)
        }
        val secondByte: Byte = bitSetByteArray.first()

        val buttonByte: Byte = 0
        val vendorUsageLowByte: Byte = 0
        val vendorUsageHighByte: Byte = 0

        return byteArrayOf(
            TOUCHPAD_REPORT_ID,
            secondByte,
            x.toLowByte(),
            x.toHighByte(),
            y.toLowByte(),
            y.toHighByte(),
            scanTime.toLowByte(),
            scanTime.toHighByte(),
            realContactCount,
            buttonByte,
            vendorUsageLowByte,
            vendorUsageHighByte
        )
    }

    companion object {
        private const val TOUCHPAD_REPORT_ID: Byte = 4
    }
}

/**
 * make sure index is [0, 7]
 */
fun Byte.isBitSet(index: Int): Boolean =
    rotateRight(index).takeLowestOneBit().toInt() == 1

fun Short.toLowByte(): Byte =
    toByte()

fun Short.toHighByte(): Byte =
    rotateRight(Byte.SIZE_BITS).toByte()

fun UShort.toLowByte(): Byte =
    toByte()

fun UShort.toHighByte(): Byte =
    rotateRight(Byte.SIZE_BITS).toByte()
