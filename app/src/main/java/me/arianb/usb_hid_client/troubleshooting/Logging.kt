package me.arianb.usb_hid_client.troubleshooting

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import timber.log.Timber

class ProductionTree(logLevel: Level) : Timber.DebugTree() {
    val androidLogLevel: Int = logLevel.priority

    constructor() : this(Level.VERBOSE)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < androidLogLevel) {
            return
        }

        LogBuffer.log(priority, tag, message, t)
    }
}

@Parcelize
data class LogEntry(
    val priority: String,
    val tag: String,
    val message: String,
    val throwableString: String? = null,
) : Parcelable {
    override fun toString(): String {
        val strWithoutThrowable = String.format("%-50s %-10s %s", tag, priority, message)

        return if (throwableString == null) {
            strWithoutThrowable
        } else {
            strWithoutThrowable + ("\t\t" + throwableString)
        }
    }
}

enum class Level(val priority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    ASSERT(7)
}

object LogBuffer {
    // NOTE: adjust limit if this uses too much memory
    private const val LIMIT = 50000
    private val buffer = ArrayDeque<LogEntry>(LIMIT)

    fun log(priority: Int, tag: String?, message: String, t: Throwable? = null) {
        val entry = LogEntry(
            priority = priorityToLevel(priority),
            tag = tag ?: "unknown_tag",
            message = message,
            throwableString = t?.toString()
        )

        add(entry)
    }

    private fun add(entry: LogEntry) {
        if (buffer.size >= LIMIT) {
            buffer.removeFirst()
        }

        buffer.add(entry)
    }

    fun addLogArray(entries: Array<LogEntry>) {
        for (entry in entries) {
            add(entry)
        }
    }

    @JvmStatic
    fun priorityToLevel(priority: Int): String {
        return when (priority) {
            Level.VERBOSE.priority -> Level.VERBOSE.name
            Level.DEBUG.priority -> Level.DEBUG.name
            Level.INFO.priority -> Level.INFO.name
            Level.WARN.priority -> Level.WARN.name
            Level.ERROR.priority -> Level.ERROR.name
            Level.ASSERT.priority -> Level.ASSERT.name
            else -> "UNKNOWN_(${priority})"
        }
    }

    fun getLogList(): List<LogEntry> {
        return buffer.toList()
    }

    fun getLogArray(): Array<LogEntry> {
        return buffer.toTypedArray()
    }

    /**
     * This is synchronized to (hopefully) avoid a race condition where someone `get`s the log list more than once
     * before the `clear()` call has been run.
     */
    @Synchronized
    fun getAndClearLogList(): Array<LogEntry> {
        val list = getLogArray()
        buffer.clear()

        return list
    }
}
