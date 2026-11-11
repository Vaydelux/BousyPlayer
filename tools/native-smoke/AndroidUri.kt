package android.net
class Uri private constructor(val value: String) {
    val scheme: String? get() = value.substringBefore(':', "")
    val path: String? get() = value.substringAfter("//", "").substringAfter('/', "")
    companion object { fun parse(s: String) = Uri(s) }
    override fun toString() = value
}
