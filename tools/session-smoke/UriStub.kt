package android.net
class Uri(private val raw: String) {
    val scheme: String? get() = raw.substringBefore(':', "")
    val lastPathSegment: String? get() = raw.substringBefore('?').substringAfterLast('/', "")
    override fun toString() = raw
    override fun equals(other: Any?) = other is Uri && other.raw == raw
    override fun hashCode() = raw.hashCode()
    companion object { fun parse(value: String) = Uri(value) }
}
