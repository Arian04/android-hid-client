package me.arianb.usb_hid_client.hid_utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import timber.log.Timber

class UsbGadgetServiceConnection : ServiceConnection {
    private var mService: Messenger? = null

    val isBound: Boolean
        get() = mService != null

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        mService = Messenger(service)
    }

    override fun onServiceDisconnected(className: ComponentName) {
        // This is called when the connection with the service has been
        // unexpectedly disconnected; that is, its process crashed.
        mService = null
    }

    private fun send(messageType: Int, preferences: GadgetUserPreferences) {
        if (!isBound) {
            Timber.w("Attempted to communicate with service using unbound connection")
            return
        }

        val msg = Message.obtain(null, messageType).apply {
            data.putParcelable(UsbGadgetService.GADGET_PREF_BUNDLE_KEY, preferences)
        }
        try {
            mService!!.send(msg)
        } catch (e: RemoteException) {
            Timber.e(e)
        }
    }

    fun createGadget(preferences: GadgetUserPreferences) {
        send(UsbGadgetService.MSG_CREATE, preferences)
    }

    fun deleteGadget(preferences: GadgetUserPreferences) {
        send(UsbGadgetService.MSG_DELETE, preferences)
    }
}