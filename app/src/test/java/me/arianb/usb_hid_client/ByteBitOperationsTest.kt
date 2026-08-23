package me.arianb.usb_hid_client

import me.arianb.usb_hid_client.report_senders.pointer_device_senders.isBitSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

// NOTE: largely LLM-generated. was reviewed and slight edits were made, but issues could have slipped through.

/**
 * Unit tests for the `Byte.isBitSet` helper declared in `TouchpadSender.kt`
 */
class ByteBitOperationsTest {

    // ---------- Byte.isBitSet(Int) ----------

    @ParameterizedTest
    @MethodSource("byteBitIndexAndExpectedState")
    fun `isBitSet returns whether the given bit index is set`(testCase: Triple<Byte, Int, Boolean>) {
        val (byte, bitIndex, expectedIsSet) = testCase

        // Method under test
        val actualIsSet = byte.isBitSet(bitIndex)

        assertEquals(expectedIsSet, actualIsSet)
    }

    companion object {
        // ---------- Argument Generators ----------

        // A representative spread of bit patterns: all-zero, single low bit, single high bit, all-ones, and
        // both alternating patterns (so bit 0 is checked as both set and unset across a byte with mixed bits).
        @JvmStatic
        fun byteBitIndexAndExpectedState(): Iterable<Triple<Byte, Int, Boolean>> = listOf(
            // 0b00000000 - no bits set
            Triple(0x00.toByte(), 0, false),
            Triple(0x00.toByte(), 7, false),

            // 0b00000001 - only bit 0 set
            Triple(0x01.toByte(), 0, true),
            Triple(0x01.toByte(), 1, false),

            // 0b10000000 - only bit 7 set (this is negative as a signed byte)
            Triple(0x80.toByte(), 7, true),
            Triple(0x80.toByte(), 0, false),

            // 0b11111111 - all bits set (-1 as a signed byte)
            Triple(0xFF.toByte(), 0, true),
            Triple(0xFF.toByte(), 7, true),

            // 0b01010101 - alternating bits, starting with bit 0 set
            Triple(0x55.toByte(), 0, true),
            Triple(0x55.toByte(), 1, false),
            Triple(0x55.toByte(), 6, true),
            Triple(0x55.toByte(), 7, false),

            // 0b10101010 - alternating bits, starting with bit 0 unset
            Triple(0xAA.toByte(), 0, false),
            Triple(0xAA.toByte(), 1, true),
            Triple(0xAA.toByte(), 6, false),
            Triple(0xAA.toByte(), 7, true),
        )
    }
}