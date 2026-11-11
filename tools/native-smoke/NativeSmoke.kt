import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.vaydelux.bousyplayer.engine.MpvEngine
import com.vaydelux.bousyplayer.engine.VlcEngine
import com.vaydelux.bousyplayer.engine.SubtitleAppearance
import com.vaydelux.bousyplayer.engine.SubtitleSize
import com.vaydelux.bousyplayer.engine.SubtitleColor
import dev.jdtech.mpv.MPVLib
import org.videolan.libvlc.MediaPlayer

fun main() {
    val context = Context()
    val uri = Uri.parse("content://media/video.ts")
    val mpv = MpvEngine(context)
    mpv.load(uri, 30000L, true)
    val mpvFake = checkNotNull(MPVLib.last)
    check(mpv.applySubtitleAppearance(SubtitleAppearance(SubtitleSize.LARGE, SubtitleColor.YELLOW))) {
        "libmpv did not accept accessible caption style"
    }
    check(mpvFake.doubleProperties["sub-font-size"] == 76.0 &&
        mpvFake.stringProperties["sub-color"] == "#FFFF00FF") {
        "libmpv accessible subtitle settings not applied"
    }
    check(mpvFake.commands.none { it.firstOrNull() == "loadfile" }) { "mpv loaded before surface exists" }
    (mpv.view as SurfaceView).holder.create()
    check(mpvFake.commands.count { it.firstOrNull() == "loadfile" } == 1) { "mpv did not start once after surface" }
    // FILE_LOADED can precede native MPEG-TS seekability. When it becomes
    // seekable on a later Looper turn, the resume target must still be applied.
    mpvFake.setSeekable(false)
    mpvFake.emitEvent(MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED)
    check(mpvFake.commands.none { it.firstOrNull() == "seek" }) {
        "mpv sought while native seekability was false"
    }
    mpvFake.setSeekable(true)
    android.os.Handler.drainDelayedOnce()
    check(mpvFake.commands.count { it.firstOrNull() == "seek" && it.getOrNull(1) == "30.0" } == 1) {
        "mpv did not resume after seekability became available"
    }
    (mpv.view as SurfaceView).holder.destroy()
    (mpv.view as SurfaceView).holder.create()
    check(mpvFake.commands.count { it.firstOrNull() == "loadfile" } == 1) {
        "mpv reloaded the video unnecessarily on surface recreation"
    }
    mpv.release()
    check(mpvFake.destroyed) { "mpv native resource not destroyed" }
    println("PASS: libmpv delayed saved seek and single-load surface recreation")

    val overrideEngine = MpvEngine(context)
    overrideEngine.load(uri, 82_000L, true)
    val overrideFake = checkNotNull(MPVLib.last)
    (overrideEngine.view as SurfaceView).holder.create()
    overrideFake.setSeekable(false)
    overrideFake.emitEvent(MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED)
    overrideFake.setSeekable(true)
    overrideEngine.seek(92_000L)
    android.os.Handler.drainDelayedOnce()
    check(overrideFake.commands.count { it.firstOrNull() == "seek" } == 1 &&
        overrideFake.commands.any { it.firstOrNull() == "seek" && it.getOrNull(1) == "92.0" }) {
        "explicit user seek was overridden by delayed saved resume"
    }
    overrideEngine.release()
    println("PASS: libmpv explicit seek supersedes delayed resume")
    println("PASS: libmpv waits for surface, loads once and destroys")
    val vlc = VlcEngine(context)
    check(!vlc.applySubtitleAppearance(SubtitleAppearance())) {
        "LibVLC must not claim unsupported live subtitle styling"
    }
    vlc.load(uri, 40000L, true)
    val vlcFake = checkNotNull(MediaPlayer.last)
    check(vlcFake.playCount == 0) { "VLC played before surface exists" }
    (vlc.view as SurfaceView).holder.create()
    check(vlcFake.playCount == 1) { "VLC did not start after surface" }
    vlc.pause()
    (vlc.view as SurfaceView).holder.destroy()
    (vlc.view as SurfaceView).holder.create()
    check(vlcFake.playCount == 1) { "VLC resumed after explicit pause" }
    vlc.release()
    println("PASS: LibVLC waits for surface and doesn't override Pause")
}
