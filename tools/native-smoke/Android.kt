package android.content
import android.net.Uri
import android.os.ParcelFileDescriptor
open class Context {
    open val applicationContext: Context get() = this
    val contentResolver: ContentResolver = ContentResolver()
}
class ContentResolver {
    fun openFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor? = ParcelFileDescriptor()
}
