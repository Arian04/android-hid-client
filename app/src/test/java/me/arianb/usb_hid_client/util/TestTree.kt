package me.arianb.usb_hid_client.util

import android.util.Log
import me.arianb.usb_hid_client.troubleshooting.LogBuffer.priorityToLevel
import timber.log.Timber

/**
 * A Timber tree impl for testing purposes that will throw an AssertionError if a log message with
 * a log level >= 'ASSERT' is logged. The purpose of this is to cause noticeable failures when those issues are logged
 * (since they are only logged at that priority for serious problems), rather than just silently ignoring them.
 *
 * An alterative would be for the call sites of these Timber.wtf calls to throw an exception, but due to Kotlin not
 * enforcing Checked Exceptions, I don't like that solution very much, as I wouldn't want to accidentally let one
 * slip through and cause actual crashes outside of tests for users.
 */
class TestTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        println("[$tag] (${priorityToLevel(priority)}) $message")

        if (priority >= Log.ASSERT) {
            throw AssertionError("A log message with log level >= 'ASSERT' has been logged: $message (Throwable? = $t)")
        }
    }
}