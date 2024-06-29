package me.arianb.usb_hid_client

import android.util.Log
import timber.log.Timber

class ProductionTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority <= Log.DEBUG) {
            return
        }

        LogBuffer.log(priority, tag, message, t)
    }
}

data class LogEntry(
    val priority: String,
    val tag: String,
    val message: String,
    val t: Throwable? = null
)

enum class Level(val priority: Int) {
    UNKNOWN(-1),
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    ASSERT(7)
}

sealed class LogBuffer {
    companion object {
        // NOTE: adjust limit if this uses too much memory
        private const val LIMIT = 1000
        private val buffer = ArrayDeque<LogEntry>(LIMIT)

        fun log(priority: Int, tag: String?, message: String, t: Throwable? = null) {
            val entry = LogEntry(
                priority = priorityToLevel(priority),
                tag = tag ?: "unknown_tag",
                message = message,
                t = t
            )

            add(entry)
        }

        private fun add(entry: LogEntry) {
            if (buffer.size >= LIMIT) {
                buffer.removeFirst()
            } else {
                buffer.add(entry)
            }
        }

        private fun priorityToLevel(priority: Int): String {
            return when (priority) {
                Level.VERBOSE.priority -> Level.VERBOSE.name
                Level.DEBUG.priority -> Level.DEBUG.name
                Level.INFO.priority -> Level.INFO.name
                Level.WARN.priority -> Level.WARN.name
                Level.ERROR.priority -> Level.ERROR.name
                Level.ASSERT.priority -> Level.ASSERT.name
                else -> Level.UNKNOWN.name
            }
        }

        fun getLogList(): List<LogEntry> {
            return buffer.toList()
        }
    }
}
