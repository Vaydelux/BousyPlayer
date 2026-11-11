package dev.jdtech.mpv
import android.content.Context
import android.view.Surface
class MPVLib {
    companion object {
        var last: MPVLib? = null
        fun create(context: Context): MPVLib? = MPVLib().also { last = it }
    }
    object MpvFormat {
        const val MPV_FORMAT_DOUBLE = 2
        const val MPV_FORMAT_FLAG = 1
        const val MPV_FORMAT_NODE = 7
    }
    object MpvEvent {
        const val MPV_EVENT_START_FILE = 1
        const val MPV_EVENT_FILE_LOADED = 2
        const val MPV_EVENT_PLAYBACK_RESTART = 3
        const val MPV_EVENT_END_FILE = 4
    }
    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: String)
        fun eventProperty(property: String, value: Double)
        fun eventProperty(property: String, value: Boolean)
        fun event(eventId: Int)
    }
    val commands = mutableListOf<List<String>>()
    var failNextStop = false
    val stringProperties = mutableMapOf<String, String>()
    val doubleProperties = mutableMapOf<String, Double>()
    var destroyed = false
    var destroyCount = 0
    var surfaceReady = false
    var attachCount = 0
    var detachCount = 0
    private val bools = mutableMapOf<String,Boolean>()
    private var observer: EventObserver? = null
    fun setSeekable(value: Boolean) { bools["seekable"] = value }
    fun emitEvent(eventId: Int) { observer?.event(eventId) }
    fun emitBoolean(name: String, value: Boolean) { bools[name] = value; observer?.eventProperty(name, value) }
    fun setOptionString(name: String, value: String) {}
    fun init() {}
    fun addObserver(observer: EventObserver) { this.observer = observer }
    fun removeObserver(observer: EventObserver) { if (this.observer === observer) this.observer = null }
    fun observeProperty(name: String, format: Int) {}
    fun command(args: Array<String>) {
        if (args.firstOrNull() == "stop" && failNextStop) {
            failNextStop = false
            throw IllegalStateException("synthetic libmpv stop failure")
        }
        commands += args.toList()
    }
    fun getPropertyBoolean(name: String): Boolean? = bools[name] ?: (name == "seekable")
    fun getPropertyInt(name: String): Int? = 0
    fun getPropertyString(name: String): String? = null
    fun setPropertyBoolean(name: String, value: Boolean) { bools[name] = value }
    fun setPropertyDouble(name: String, value: Double) { doubleProperties[name] = value }
    fun setPropertyInt(name: String, value: Int) {}
    fun setPropertyString(name: String, value: String) { stringProperties[name] = value }
    fun attachSurface(surface: Surface) { surfaceReady = true; attachCount++ }
    fun detachSurface() { surfaceReady = false; detachCount++ }
    fun destroy() { destroyed = true; destroyCount++ }
}
