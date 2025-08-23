package me.arianb.usb_hid_client

import me.arianb.usb_hid_client.report_senders.OneByteBitSet
import org.junit.Assert
import org.junit.Test

class BitSetTest {
    @Test
    fun bitSetSet_isCorrect() {
        val byte = OneByteBitSet()
        Assert.assertEquals(byte.asByte(), 0b0.toByte())

        byte.set(0, true)
        Assert.assertEquals(byte.asByte(), 0b0000001.toByte())
    }
}
