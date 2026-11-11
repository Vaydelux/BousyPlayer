import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.vaydelux.bousyplayer.engine.MpvEngine
import com.vaydelux.bousyplayer.engine.VlcEngine
import dev.jdtech.mpv.MPVLib
import org.videolan.libvlc.MediaPlayer

fun main() {
    val context = Context()
    val uri = Uri.parse("content://media/video.ts")
    val mpv = MpvEngine(context)
    mpv.load(uri, 30000L, true)
    val mpvFake = checkNotNull(MPVLib.last)
    check(mpvFake.commands.none { it.firstOrNull() == "loadfile" }) { "mpv loaded before surface exists" }
    (mpv.view as SurfaceView).holder.create()
    check(mpvFake.commands.count { it.firstOrNull() == "loadfile" } == 1) { "mpv did not start once after surface" }
    mpv.release()
    check(mpvFake.destroyed) { "mpv native resource not destroyed" }
    println("PASS: libmpv waits for surface, loads once and destroys")
    val vlc = VlcEngine(context)
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
