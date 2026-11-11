package android.os
import java.io.FileDescriptor
class ParcelFileDescriptor : java.io.Closeable {
    val fd: Int = 9
    val fileDescriptor = FileDescriptor()
    override fun close() { }
}
class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(looper: Looper) {
    fun post(task: Runnable): Boolean { task.run(); return true }
    fun postDelayed(task: Runnable, delayMillis: Long): Boolean = true
    fun removeCallbacksAndMessages(token: Any?) { }
}
object SystemClock { fun elapsedRealtime() = 0L }
