package android.os
import java.io.FileDescriptor
class ParcelFileDescriptor : java.io.Closeable {
    companion object {
        var opened = 0
            private set
        var closed = 0
            private set
        val activeCount: Int get() = opened - closed
    }
    val fd: Int = ++opened + 8
    val fileDescriptor = FileDescriptor()
    private var didClose = false
    override fun close() {
        if (!didClose) {
            didClose = true
            closed++
        }
    }
}
class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(looper: Looper) {
    private val delayed = mutableListOf<Runnable>()
    companion object {
        private val instances = mutableListOf<Handler>()
        // Simulate one controlled Android Looper turn in native smoke tests.
        fun drainDelayedOnce() { instances.toList().forEach { it.drainOnce() } }
    }
    init { instances += this }
    fun post(task: Runnable): Boolean { task.run(); return true }
    fun postDelayed(task: Runnable, delayMillis: Long): Boolean {
        delayed += task
        return true
    }
    private fun drainOnce() {
        val tasks = delayed.toList()
        delayed.clear()
        tasks.forEach { it.run() }
    }
    fun removeCallbacksAndMessages(token: Any?) { delayed.clear() }
}
object SystemClock { fun elapsedRealtime() = 0L }
