import android.net.Uri
import com.vaydelux.bousyplayer.model.SourcePolicy

private var checks = 0
fun ensure(description: String, passes: Boolean) {
    check(passes) { "FAIL: $description" }
    checks++
    println("PASS: $description")
}
fun main() {
    ensure("valid HTTPS accepted", SourcePolicy.isValidNetworkUrl("https://example.org/video.mp4"))
    ensure("HTTPS URL with query accepted", SourcePolicy.isValidNetworkUrl("https://example.org/index.m3u8?token=abc"))
    ensure("HTTP rejected", !SourcePolicy.isValidNetworkUrl("http://example.org/movie.mp4"))
    ensure("JavaScript scheme rejected", !SourcePolicy.isValidNetworkUrl("javascript:alert(1)"))
    ensure("file scheme rejected", !SourcePolicy.isValidNetworkUrl("file:///data/data/secret"))
    ensure("userinfo rejected", !SourcePolicy.isValidNetworkUrl("https://user:password@example.org/video"))
    ensure("fragment rejected", !SourcePolicy.isValidNetworkUrl("https://example.org/video#secret"))
    ensure("missing host rejected", !SourcePolicy.isValidNetworkUrl("https:///movie.mp4"))
    ensure("unparseable percent escapes rejected", !SourcePolicy.isValidNetworkUrl("https://example.org/bad%GG"))
    ensure("HLS hint recognized", SourcePolicy.inferMimeType(Uri.parse("https://example.org/x.m3u8?token=a")) != null)
    ensure("DASH hint recognized", SourcePolicy.inferMimeType(Uri.parse("https://example.org/x.mpd")) != null)
    ensure("MP4 is not falsely HLS", SourcePolicy.inferMimeType(Uri.parse("https://example.org/x.mp4")) == null)
    ensure("seek is clamped to duration", SourcePolicy.clampSeek(20_000L, 10_000L) == 10_000L)
    ensure("seek negative is clamped", SourcePolicy.clampSeek(-5L, 10_000L) == 0L)
    ensure("unknown-duration seek is zero", SourcePolicy.clampSeek(5_000L, 0L) == 0L)
    ensure("time displays correctly", SourcePolicy.displayTime(3_661_000L) == "1:01:01")
    println("PASS: $checks URL/model policy checks")
}
