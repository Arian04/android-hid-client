package me.arianb.usb_hid_client.hid_utils.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import me.arianb.usb_hid_client.getParcelableArrayCompat
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.troubleshooting.LogBuffer
import me.arianb.usb_hid_client.troubleshooting.LogEntry
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class UsbGadgetServiceConnection : ServiceConnection {
    private var mService: Messenger? = null
    private val pendingOperations = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

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
        pendingOperations.values.forEach { it.complete(false) }
        pendingOperations.clear()
    }

    private inner class IncomingHandler : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            Timber.i("Message (what = ${msg.what}) received in service, running with UID = ${Process.myUid()}")

            when (msg.what) {
                UsbGadgetService.MSG_GET_LOGS -> {
                    val didSucceed = handleGetLogsResponse(msg)
                    Timber.v("handleGetLogsResponse returned $didSucceed")
                    pendingOperations.remove(UsbGadgetService.MSG_GET_LOGS)?.complete(didSucceed)
                }

                UsbGadgetService.MSG_OPERATION_COMPLETE -> {
                    val completedMsgWhat = msg.arg1
                    val didSucceed = msg.arg2 == 1
                    Timber.i("Received MSG_OPERATION_COMPLETE for what=$completedMsgWhat, didSucceed=$didSucceed")
                    pendingOperations.remove(completedMsgWhat)?.complete(didSucceed)
                }

                else -> {
                    Timber.w("Unhandled message: $msg")
                    return false
                }
            }

            return true
        }

        private fun handleGetLogsResponse(msg: Message): Boolean {
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

            Timber.v("logs array received from RootService: ${logArray.contentToString()}")

            Timber.i("Appending log entries to buffer: num entries=${logArray.size}")
            LogBuffer.addLogArray(logArray)

            return true
        }
    }

    private fun send(messageType: Int, preferences: GadgetUserPreferences?): Boolean {
        if (!isBound) {
            Timber.w("Attempted to communicate with service using unbound connection")
            return false
        }

        val msg = Message.obtain(null, messageType).apply {
            if (preferences != null) {
                data.putParcelable(UsbGadgetService.GADGET_PREF_BUNDLE_KEY, preferences)
            }
            replyTo = mMessenger
        }

        try {
            mService!!.send(msg)
            return true
        } catch (e: RemoteException) {
            Timber.e(e)
            return false
        }
    }

    private suspend fun sendAndAwait(messageType: Int, preferences: GadgetUserPreferences?): Boolean {
        val deferred = CompletableDeferred<Boolean>()

        // If this messageType is already being awaited, don't allow sending another.
        // NOTE: Eventually, maybe we could allow this, but for now it should be okay to just reject it to avoid bugs
        if (pendingOperations.putIfAbsent(messageType, deferred) != null) {
            Timber.w("[POTENTIAL LOGIC BUG] Message type $messageType is already being awaited. Not sending another.")
            return false
        }

        val sent = send(messageType, preferences)
        if (!sent) {
            // If fails to send, complete the operation immediately
            pendingOperations.remove(messageType)
            return false
        }

        // TODO: should I try/catch CancellationException and remove from pendingOperations in the catch block?
        return deferred.await()
    }

    suspend fun createGadget(preferences: GadgetUserPreferences): Boolean {
        Timber.i("making MSG_CREATE call to service")
        return sendAndAwait(UsbGadgetService.MSG_CREATE, preferences)
    }

    suspend fun deleteGadget(preferences: GadgetUserPreferences): Boolean {
        Timber.i("making MSG_DELETE call to service")
        return sendAndAwait(UsbGadgetService.MSG_DELETE, preferences)
    }

    suspend fun getLogs(): Boolean {
        Timber.i("making MSG_GET_LOGS call to service")
        return sendAndAwait(UsbGadgetService.MSG_GET_LOGS, null)
    }
}