package me.arianb.usb_hid_client.hid_utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import me.arianb.usb_hid_client.getParcelableArrayCompat
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.troubleshooting.LogBuffer
import me.arianb.usb_hid_client.troubleshooting.LogEntry
import timber.log.Timber

class UsbGadgetServiceConnection : ServiceConnection {
    private var mService: Messenger? = null

    val isBound: Boolean
        get() = mService != null

    val mMessenger: Messenger = Messenger(
        Handler(Looper.getMainLooper(), IncomingHandler())
    )

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        mService = Messenger(service)
    }

    override fun onServiceDisconnected(className: ComponentName) {
        // This is called when the connection with the service has been
        // unexpectedly disconnected; that is, its process crashed.
        Timber.w("onServiceDisconnected called for $className")
        mService = null
    }

    private class IncomingHandler : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            Timber.d("Message received in service, running with UID = ${Process.myUid()}")

            val logArray: Array<LogEntry>? = run {
                val bundle = msg.data.apply {
                    classLoader = LogEntry::class.java.classLoader
                }
                bundle.getParcelableArrayCompat<LogEntry>(null)
            }
            if (logArray == null) {
                Timber.e("Failed to unmarshal log entries")
                return false
            }
            Timber.d("logs array received from RootService: ${logArray.contentToString()}")
            when (msg.what) {
                UsbGadgetService.MSG_GET_LOGS -> {
                    Timber.i("Appending log entries to buffer: num entries=${logArray.size}")
                    LogBuffer.addLogArray(logArray)
                }
                else -> {
                    Timber.w("Unhandled message: $msg")
                    return false
                }
            }

            return true
        }
    }

    private fun send(messageType: Int, preferences: GadgetUserPreferences?) {
        if (!isBound) {
            Timber.w("Attempted to communicate with service using unbound connection")
            return
        }

        val msg = Message.obtain(null, messageType).apply {
            if (preferences != null) {
                data.putParcelable(UsbGadgetService.GADGET_PREF_BUNDLE_KEY, preferences)
            }
            replyTo = mMessenger
        }

        try {
            mService!!.send(msg)
        } catch (e: RemoteException) {
            Timber.e(e)
        }
    }

    fun createGadget(preferences: GadgetUserPreferences) {
        Timber.i("making MSG_CREATE call to service")
        send(UsbGadgetService.MSG_CREATE, preferences)
    }

    fun deleteGadget(preferences: GadgetUserPreferences) {
        Timber.i("making MSG_DELETE call to service")
        send(UsbGadgetService.MSG_DELETE, preferences)
    }

    fun getLogs() {
        Timber.i("making MSG_GET_LOGS call to service")
        send(UsbGadgetService.MSG_GET_LOGS, null)
    }
}