package androidx.compose.runtime
import kotlin.reflect.KProperty
class SnapshotState<T>(var value: T)
fun <T> mutableStateOf(value: T): SnapshotState<T> = SnapshotState(value)
operator fun <T> SnapshotState<T>.getValue(any: Any?, property: KProperty<*>): T = value
operator fun <T> SnapshotState<T>.setValue(any: Any?, property: KProperty<*>, next: T) { value = next }
