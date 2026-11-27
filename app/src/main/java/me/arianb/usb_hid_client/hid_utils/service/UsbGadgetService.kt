package me.arianb.usb_hid_client.hid_utils.service

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import android.os.Process
import android.os.RemoteException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.parcelize.Parcelize
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.getParcelableCompat
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.troubleshooting.Level
import me.arianb.usb_hid_client.troubleshooting.LogBuffer
import me.arianb.usb_hid_client.troubleshooting.ProductionTree
import timber.log.Timber

/**
 * The UsbGadgetService class is a RootService (from libsu). This means that all the code running in that class is
 * running in a separate process than the main application process, and running with root permissions. This is important
 * to keep in mind, as it has consequences like needing to reconfigure logging and such. It also means you can't read
 * some info (like preferences) by simply re-using existing singletons that were created in the main process, as this
 * is a new process.
 */
class UsbGadgetService : RootService() {
    init {
        // This is called in a different process, so we gotta replant Timber stuff
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Shell.enableVerboseLogging = true
        }

        Timber.plant(ProductionTree(Level.VERBOSE))
    }

    private val mMessenger: Messenger by lazy {
        Messenger(
            Handler(Looper.getMainLooper(), MessageHandler())
        )
    }

    private class MessageHandler : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            Timber.i("Message (what = ${msg.what}) received in service, running with UID = ${Process.myUid()}")

            val replyMsg: Message = try {
                when (msg.what) {
                    MSG_CREATE, MSG_DELETE -> {
                        val didSucceed = handleGadgetManagerMessage(msg)
                        operationCompleteMessage(msg.what, didSucceed)
                    }

                    MSG_GET_LOGS -> logsMessage()
                    else -> {
                        Timber.wtf("Unhandled message: $msg")
                        return false
                    }
                }
            } catch (e: Exception) {
                // Catching unhandled exceptions because the logs were complaining about unclosed file descriptors
                // when I didn't
                Timber.e(e, "Oh no, an unhandled exception occurred in UsbGadgetService while handling what=${msg.what}")

                operationCompleteMessage(msg.what, didSucceed = false)
            }

            sendReply(msg.replyTo, replyMsg)
            return true
        }

        private fun handleGadgetManagerMessage(msg: Message): Boolean {
            val gadgetUserPreferences: GadgetUserPreferences? = run {
                val bundle = msg.data.apply {
                    classLoader = GadgetUserPreferences::class.java.classLoader
                }
                bundle.getParcelableCompat(GADGET_PREF_BUNDLE_KEY)
            }
            if (gadgetUserPreferences == null) {
                Timber.e("Failed to unmarshal GadgetUserPreferences")
                return false
            }
            Timber.v("GadgetUserPreferences = $gadgetUserPreferences")

            val usbGadgetManager = UsbGadgetManager(gadgetUserPreferences)
            when (msg.what) {
                MSG_CREATE -> usbGadgetManager.createCharacterDevices()
                MSG_DELETE -> usbGadgetManager.deleteCharacterDevices()
                else -> {
                    Timber.w("Unhandled message: $msg")
                    return false
                }
            }

            return true
        }

        private fun operationCompleteMessage(completedMsgWhat: Int, didSucceed: Boolean): Message =
            Message.obtain(null, MSG_OPERATION_COMPLETE).apply {
                arg1 = completedMsgWhat
                arg2 = if (didSucceed) 1 else 0
            }

        private fun logsMessage(): Message {
            val logArray = LogBuffer.getAndClearLogList()
            Timber.d("RootService sending logs array: ${logArray.contentToString()}")
            return Message.obtain(null, MSG_GET_LOGS).apply {
                data.putParcelableArray(null, logArray)
            }
        }

        private fun sendReply(messenger: Messenger?, replyMsg: Message) {
            if (messenger == null) {
                Timber.w("Attempted to send reply (what = ${replyMsg.what}) using null replyTo messenger")
                replyMsg.recycle()
                return
            }

            try {
                messenger.send(replyMsg)
            } catch (e: RemoteException) {
                Timber.e(e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.v("UsbGadgetService onBind() called")
        return mMessenger.binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Timber.v("UsbGadgetService onUnbind() called")

        // TODO: maybe block if service is still doing some work? Not yet sure if that's a good idea though.

        return super.onUnbind(intent)
    }

    companion object {
        const val GADGET_PREF_BUNDLE_KEY = "data"

        const val MSG_CREATE = 0
        const val MSG_DELETE = 1
        const val MSG_GET_LOGS = 2
        const val MSG_OPERATION_COMPLETE = 3
    }
}

@JvmInline
@Parcelize
value class UsbGadgetPath(val path: String) : Parcelable