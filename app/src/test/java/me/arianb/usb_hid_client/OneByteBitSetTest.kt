package me.arianb.usb_hid_client

import me.arianb.usb_hid_client.report_senders.OneByteBitSet
import me.arianb.usb_hid_client.util.TestTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import timber.log.Timber
import java.util.BitSet

class OneByteBitSetTest {

    @ParameterizedTest
    @MethodSource("setCases")
    fun `set matches BitSet reference`(setTestArgs: SetTestArgs) {
        val (initialByte: Byte, bitIndex: Int, expectedByte: Byte) = setTestArgs

        performOperationToBothImplsAndVerifyExpectedResult(initialByte, expectedByte) { actual, referenceImpl ->
            // Perform same operation on both
            actual.set(bitIndex, true)
            referenceImpl.set(bitIndex, true)
        }
    }

    @ParameterizedTest
    @MethodSource("clearCases")
    fun `clear matches BitSet reference`(clearTestArgs: ClearTestArgs) {
        val (initialByte: Byte, bitIndex: Int, expectedByte: Byte) = clearTestArgs

        performOperationToBothImplsAndVerifyExpectedResult(initialByte, expectedByte) { actual, referenceImpl ->
            // Perform same operation on both
            actual.clear(bitIndex)
            referenceImpl.clear(bitIndex)
        }
    }

    @ParameterizedTest
    @MethodSource("rangeClearCases")
    fun `clear range matches BitSet reference`(rangeClearTestArgs: RangeClearTestArgs) {
        val (initialByte: Byte, start: Int, end: Int, expectedByte: Byte) = rangeClearTestArgs

        performOperationToBothImplsAndVerifyExpectedResult(initialByte, expectedByte) { actual, referenceImpl ->
            // Perform same operation on both
            actual.clear(start, end)
            referenceImpl.clear(start, end)
        }
    }

    @ParameterizedTest
    @MethodSource("outOfRangeCases")
    fun `invalid indexes are ignored`(outOfRangeTestArgs: OutOfRangeTestArgs) {
        val (initialByte: Byte, invalidIndex: Int) = outOfRangeTestArgs

        performOperationToBothImplsAndVerifyExpectedResult(initialByte, null) { actual, referenceImpl ->
            // Our TestTree throws an assertion error for "wtf" level logs, but this is a test for intentionally bad indices,
            // so we'll just catch it
            try {
                actual.set(invalidIndex, true)
            } catch (e: AssertionError) {
                Timber.i("Caught expected AssertionError caused by invalid index: $invalidIndex")
            }

            // Reference impl throws an exception, but ours doesn't, so we'll just catch it
            try {
                referenceImpl.set(invalidIndex, true)
            } catch (e: IndexOutOfBoundsException) {
                Timber.i("Caught expected IndexOutOfBoundsException from reference impl")
            }
        }
    }

    /**
     * Performs the given operation on both implementations and verifies that the result matches
     * the behavior of java.util.BitSet and (if expectedByte != null) that the result matches the expected byte.
     */
    private fun performOperationToBothImplsAndVerifyExpectedResult(
        initialByte: Byte,
        expectedByte: Byte?,
        operation: (OneByteBitSet, BitSet) -> Unit
    ) {
        val actual = byteToOneByteBitSet(initialByte)
        val referenceImpl = byteToBitSet(initialByte)

        // Perform operation on both. It is the responsibility of the caller to ensure that the operation is
        // valid and equivalent for both implementations.
        operation(actual, referenceImpl)

        // Result matches behavior of java.util.BitSet
        assertEquals(
            bitSetAsByte(referenceImpl), actual.asByte(),
            "BitSet reference implementation does not match actual byte"
        )

        if (expectedByte != null) {
            // Result matches expected byte
            assertEquals(expectedByte, actual.asByte(), "Expected byte does not match actual byte")
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup(): Unit {
            Timber.plant(TestTree())
        }

        @JvmStatic
        fun setCases(): Iterable<Arguments> = listOf<SetTestArgs>(
            SetTestArgs(0b00000000.toByte(), 0, 0b00000001),
            SetTestArgs(0b00000000.toByte(), 7, 0b10000000.toByte()),
            SetTestArgs(0b00000101.toByte(), 3, 0b00001101),
            SetTestArgs(0b10000000.toByte(), 0, 0b10000001.toByte()),
            SetTestArgs(0b11111111.toByte(), 4, 0b11111111.toByte()),
        ).map(Arguments::of)

        @JvmStatic
        fun clearCases(): Iterable<Arguments> = listOf<ClearTestArgs>(
            ClearTestArgs(0b00000001.toByte(), 0, 0b00000000),
            ClearTestArgs(0b10000000.toByte(), 7, 0b00000000),
            ClearTestArgs(0b00001111.toByte(), 2, 0b00001011),
            ClearTestArgs(0b11111111.toByte(), 4, 0b11101111.toByte()),
        ).map(Arguments::of)

        @JvmStatic
        fun rangeClearCases(): Iterable<Arguments> = listOf<RangeClearTestArgs>(
            RangeClearTestArgs(0b11111111.toByte(), 0, 3, 0b11111000.toByte()),
            RangeClearTestArgs(0b11111111.toByte(), 2, 5, 0b11100011.toByte()),
            RangeClearTestArgs(0b10101010.toByte(), 1, 6, 0b10000000.toByte()),
            RangeClearTestArgs(0b11111111.toByte(), 1, 6, 0b11000001.toByte()),
            RangeClearTestArgs(0b11111111.toByte(), 0, 8, 0b00000000),
        ).map(Arguments::of)

        @JvmStatic
        fun outOfRangeCases(): Iterable<Arguments> = listOf<OutOfRangeTestArgs>(
            OutOfRangeTestArgs(0x00.toByte(), -1),
            OutOfRangeTestArgs(0x00.toByte(), 8),
            OutOfRangeTestArgs(0x00.toByte(), Int.MIN_VALUE),
            // NOTE: we CANNOT test any absurdly big values here (ex: Int.MAX_VALUE) because the reference impl that we test against will automatically grow to that amount and cause the tests to fail due to OOM
            OutOfRangeTestArgs(0x00.toByte(), 999),
            OutOfRangeTestArgs(0xFF.toByte(), -1),
            OutOfRangeTestArgs(0xFF.toByte(), 8),
        ).map(Arguments::of)

        // Data classes for test cases so we can have some amount of type safety instead of dealing
        // with Arguments<Object[]> all the time
        data class SetTestArgs(val initialByte: Byte, val bitIndex: Int, val expectedByte: Byte)
        data class ClearTestArgs(val initialByte: Byte, val bitIndex: Int, val expectedByte: Byte)
        data class RangeClearTestArgs(val initialByte: Byte, val start: Int, val end: Int, val expectedByte: Byte)
        data class OutOfRangeTestArgs(val initialByte: Byte, val invalidIndex: Int)
    }

    private fun byteToBitSet(byte: Byte): BitSet {
        val bitSet = BitSet(8)
        for (i in 0..7) {
            if ((byte.toInt() and (1 shl i)) != 0) {
                bitSet.set(i)
            }
        }

        return bitSet
    }

    private fun byteToOneByteBitSet(byte: Byte): OneByteBitSet {
        val bitSet = OneByteBitSet()
        for (i in 0..7) {
            if ((byte.toInt() and (1 shl i)) != 0) {
                bitSet.set(i, true)
            }
        }

        return bitSet
    }

    private fun bitSetAsByte(bitSet: BitSet): Byte {
        val byteArray = bitSet.toByteArray()

        if (byteArray.size == 0) {
            return 0x00
        }

        return byteArray.first()
    }
}
