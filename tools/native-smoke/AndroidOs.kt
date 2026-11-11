package android.os
import java.io.FileDescriptor
class ParcelFileDescriptor : java.io.Closeable {
    val fd: Int = 9
    val fileDescriptor = FileDescriptor()
    override fun close() { }
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
