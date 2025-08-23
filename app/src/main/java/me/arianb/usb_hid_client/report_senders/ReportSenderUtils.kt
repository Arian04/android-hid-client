package me.arianb.usb_hid_client.report_senders

import timber.log.Timber
import java.util.BitSet

// Minimal class for specifically creating bitsets of 8 bits (1 byte).
// The BitSet class has some weird quirks like storing the bitset with trailing unset bits truncated.
// Returning a byte array is also more error-prone due to the aforementioned quirk, because then I need to check the size.
class OneByteBitSet {
    private var data: Byte = 0

    fun asByte(): Byte {
        return data
    }

    fun set(bitIndex: Int, state: Boolean) {
        if (bitIndex !in 0..7) {
            Timber.wtf("LOGICAL ERROR!! BitSet bitIndex out of bounds: $bitIndex")
            return
        }

        // NOTE: This is an `Int`, but will only ever have the low 8 bits set, due to `bitIndex` being constrained to [0, 7]
        val byteWithSetBit: Int = 1 shl bitIndex

        data = if (state) {
            (data.toInt() or byteWithSetBit).toByte()
        } else {
            (data.toInt() and (byteWithSetBit.inv())).toByte()
        }
    }

    fun clear(bitIndex: Int) {
        set(bitIndex, false)
    }

    fun clear(startBitIndex: Int, endBitIndex: Int) {
        for (i in startBitIndex until endBitIndex) {
            set(i, false)
        }
    }
}

// LEGACY: eventually migrate all usages of this (and `BitSet(8)`) to use my implementation above
fun safeBitSetToByte(bitSet: BitSet): Byte {
    // Turn it into a byte
    val bitSetByteArray = bitSet.toByteArray()
    if (bitSetByteArray.isEmpty()) {
        Timber.wtf("ok guys this is REALLY not cool. bitSetByteArray is EMPTY somehow!!")
        return 0
    } else {
        if (bitSetByteArray.size > 1) {
            Timber.wtf("ok guys this is not cool. bitSetByteArray.size = %d", bitSetByteArray.size)
        }
        return bitSetByteArray.first()
    }
}
