package me.arianb.usb_hid_client

import me.arianb.usb_hid_client.report_senders.OneByteBitSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BitSetTest {
    @Test
    fun bitSetSet_isCorrect() {
        val byte = OneByteBitSet()
        assertEquals(0b0.toByte(), byte.asByte())

        byte.set(0, true)
        assertEquals(0b0000001.toByte(), byte.asByte())
    }
}
