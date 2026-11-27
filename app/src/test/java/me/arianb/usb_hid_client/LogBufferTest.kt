package me.arianb.usb_hid_client

import me.arianb.usb_hid_client.troubleshooting.Level
import me.arianb.usb_hid_client.troubleshooting.LogBuffer
import me.arianb.usb_hid_client.troubleshooting.LogEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class LogBufferTest {

    @BeforeEach
    @AfterEach
    fun clearBuffer() {
        LogBuffer.getAndClearLogList()
    }

    // ---------- Basic Buffering & Ordering ----------

    @Test
    fun `log preserves FIFO order and maps entry metadata`() {
        val cause = IllegalStateException("boom")

        LogBuffer.log(Level.INFO.priority, "MainTag", "first")
        LogBuffer.log(Level.ERROR.priority, null, "second", cause)

        // Extracting this out to a local var so IntelliJ's "swapped arguments" warning doesn't get triggered
        val expected = listOf(
            LogEntry(priority = "INFO", tag = "MainTag", message = "first", throwableString = null),
            LogEntry(
                priority = "ERROR",
                tag = "unknown_tag",
                message = "second",
                throwableString = cause.toString()
            ),
        )
        assertEquals(
            expected,
            LogBuffer.getLogList(),
        )
    }

    @Test
    fun `addLogArray appends entries in order after existing entries`() {
        LogBuffer.log(Level.DEBUG.priority, "Main", "before")
        val synced = arrayOf(
            LogEntry(priority = "INFO", tag = "Service", message = "synced-1"),
            LogEntry(priority = "WARN", tag = "Service", message = "synced-2"),
        )

        LogBuffer.addLogArray(synced)

        assertEquals(listOf("before", "synced-1", "synced-2"), LogBuffer.getLogList().map { it.message })
    }

    @Test
    fun `getAndClearLogList returns buffered entries and empties the buffer`() {
        LogBuffer.log(Level.INFO.priority, "Tag", "one")
        LogBuffer.log(Level.INFO.priority, "Tag", "two")

        val drained = LogBuffer.getAndClearLogList()

        assertEquals(listOf("one", "two"), drained.map { it.message })
        assertTrue(LogBuffer.getLogList().isEmpty())
    }

    // ---------- Capacity Bound ----------

    @Test
    fun `logging past capacity evicts oldest entries`() {
        val overflowCount = 5
        val totalEntries = EXPECTED_BUFFER_LIMIT + overflowCount

        repeat(totalEntries) { i ->
            LogBuffer.log(Level.INFO.priority, "Tag", "msg-$i")
        }

        val entries = LogBuffer.getLogList()
        assertEquals(EXPECTED_BUFFER_LIMIT, entries.size)
        assertEquals("msg-$overflowCount", entries.first().message)
        assertEquals("msg-${totalEntries - 1}", entries.last().message)
    }

    // ---------- Thread Safety ----------

    @Test
    @Timeout(10)
    fun `concurrent logging and draining preserves every entry without errors`() {
        val writerCount = 4
        val messagesPerWriter = 2_000
        val totalMessages = writerCount * messagesPerWriter

        val startLatch = CountDownLatch(1)
        val writersDone = AtomicBoolean(false)
        val drainedEntries = ConcurrentLinkedQueue<LogEntry>()
        val errors = ConcurrentLinkedQueue<Throwable>()

        fun startWorker(block: () -> Unit): Thread = thread {
            startLatch.await()
            runCatching(block).onFailure { errors.add(it) }
        }

        val writers = List(writerCount) { writerId ->
            startWorker {
                repeat(messagesPerWriter) { i ->
                    LogBuffer.log(Level.INFO.priority, "Writer-$writerId", "$writerId:$i")
                }
            }
        }
        val drainers = List(2) {
            startWorker {
                while (!writersDone.get()) {
                    drainedEntries.addAll(LogBuffer.getAndClearLogList())
                    LogBuffer.getLogList()
                }
            }
        }

        startLatch.countDown()
        writers.forEach(Thread::join)
        writersDone.set(true)
        drainers.forEach(Thread::join)

        assertTrue(errors.isEmpty()) { "Concurrent access threw ${errors.size} error(s), first: ${errors.peek()}" }

        drainedEntries.addAll(LogBuffer.getAndClearLogList())
        assertEquals(totalMessages, drainedEntries.size)
        assertEquals(totalMessages, drainedEntries.map { it.message }.toSet().size)
    }

    companion object {
        private const val EXPECTED_BUFFER_LIMIT = LogBuffer.LIMIT
    }
}
