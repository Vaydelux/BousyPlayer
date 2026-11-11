package android.view
import android.content.Context
open class View
class Surface { val isValid = true }
class SurfaceHolder {
    val surface = Surface()
    interface Callback {
        fun surfaceCreated(holder: SurfaceHolder)
        fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int)
        fun surfaceDestroyed(holder: SurfaceHolder)
    }
    private val callbacks = mutableListOf<Callback>()
    fun addCallback(callback: Callback) { callbacks.add(callback) }
    fun create() { callbacks.forEach { it.surfaceCreated(this) } }
    fun destroy() { callbacks.forEach { it.surfaceDestroyed(this) } }
}
class SurfaceView(context: Context) : View() {
    val holder = SurfaceHolder()
    var keepScreenOn = false
    var width = 1920
    var height = 1080
}
