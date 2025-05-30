package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import timber.log.Timber
import java.util.BitSet

// TODO:
//  Preferably finish messing with report descriptors before v3.0.0 to minimize the number of breaking releases
//  - remove report ID if it ends up not being necessary
//  - overall cleanup unnecessary parts of the report descriptor
open class TouchpadSender(
    touchpadDevicePath: TouchpadDevicePath
) : ReportSender(
    touchpadDevicePath
) {
    fun send(contactID: Byte, tipSwitch: Boolean, x: Short, y: Short, scanTime: UShort, contactCount: Byte) {
        super.addReportToChannel(
            getTouchpadReport(contactID, tipSwitch, x, y, scanTime, contactCount)
        )
    }

    private fun getTouchpadReport(
        contactID: Byte,
        tipSwitch: Boolean,
        x: Short,
        y: Short,
        scanTime: UShort,
        contactCount: Byte
    ): ByteArray {
        // Only send non-zero contact count in the report of contact ID 0 as per the spec
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

        val secondByte: Byte = run {
            // Turn it into a byte
            val bitSetByteArray = secondByteBitSet.toByteArray()
            if (bitSetByteArray.size < 1) {
                Timber.wtf("ok guys this is REALLY not cool. bitSetByteArray is EMPTY somehow!!")
                0
            } else {
                if (bitSetByteArray.isEmpty()) {
                    Timber.wtf("ok guys this is not cool. bitSetByteArray.size = %d", bitSetByteArray.size)
                }
                bitSetByteArray.first()
            }
        }

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
