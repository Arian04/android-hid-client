package me.arianb.usb_hid_client.report_senders

import androidx.annotation.IntRange
import timber.log.Timber
import java.util.BitSet

/**
 * Minimal class for specifically creating bitsets of 8 bits (1 byte).
 *
 * This was created to replace our use-case for the java.util.BitSet class, which has some weird quirks like storing
 * the bitset with leading unset bits entirely truncated, meaning that if you try to convert a BitSet with no bits set into
 * a ByteArray, it will return an empty array. This is more error-prone because then I need to check the size.
 */
class OneByteBitSet {
    private var data: Byte = 0

    fun asByte(): Byte {
        return data
    }

    fun set(@IntRange(0, 7) bitIndex: Int, state: Boolean) {
        if (isIndexOutOfBounds(bitIndex)) return

        // NOTE: This is an `Int`, but will only ever have the low 8 bits set, due to `bitIndex` being constrained to [0, 7]
        val byteWithSetBit: Int = 1 shl bitIndex

        data = if (state) {
            (data.toInt() or byteWithSetBit).toByte()
        } else {
            (data.toInt() and (byteWithSetBit.inv())).toByte()
        }
    }

    fun clear(@IntRange(0, 7) bitIndex: Int) {
        set(bitIndex, false)
    }

    fun clear(@IntRange(0, 7) startBitIndex: Int, @IntRange(1, 8) endBitIndex: Int) {
        if (isIndexOutOfBounds(startBitIndex)) return
        if (endBitIndex !in 1..8) {
            Timber.wtf("CRITICAL LOGIC ERROR: endBitIndex must be in range [1, 8], but was: $endBitIndex")
            return
        }

        if (endBitIndex < startBitIndex) {
            Timber.wtf("endBitIndex (%d) must be >= to startBitIndex (%d)", endBitIndex, startBitIndex)
            return
        }

        for (i in startBitIndex until endBitIndex) {
            clear(i)
        }
    }

    private fun isIndexOutOfBounds(bitIndex: Int): Boolean {
        if (bitIndex !in 0..7) {
            Timber.wtf("CRITICAL LOGIC ERROR: bitIndex must be in range [0, 7], but was: $bitIndex")
            return true
        }

        return false
    }
}

// LEGACY: eventually migrate all usages of this (and `BitSet(8)`) to use my implementation above
fun safeBitSetToByte(bitSet: BitSet): Byte {
    // Turn it into a byte
    val bitSetByteArray = bitSet.toByteArray()
    if (bitSetByteArray.isEmpty()) {
        Timber.v("bitSetByteArray is empty. assuming it was all 0s and returning 0")
        return 0
    } else {
        if (bitSetByteArray.size > 1) {
            Timber.wtf("ok guys this is not cool. bitSetByteArray.size = %d", bitSetByteArray.size)
        }
        return bitSetByteArray.first()
    }
}
