import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
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

    // Repeated source replacement must never accumulate file descriptors. The
    // previous native demuxer is stopped BEFORE we close its old SAF handle.
    val beforeReplacement = ParcelFileDescriptor.activeCount
    val replacement = MpvEngine(context)
    val replacementNative = checkNotNull(MPVLib.last)
    replacement.load(uri, 11_000L, false)
    check(ParcelFileDescriptor.activeCount == beforeReplacement + 1) {
        "mpv failed to acquire one SAF descriptor for local source"
    }
    replacement.load(Uri.parse("content://media/second.ts"), 22_000L, false)
    check(ParcelFileDescriptor.activeCount == beforeReplacement + 1) {
        "mpv leaked the first SAF descriptor on source replacement"
    }
    replacementNative.failNextStop = true
    val newMpvLoad = runCatching {
        replacement.load(Uri.parse("content://media/third.ts"), 33_000L, false)
    }
    check(newMpvLoad.isFailure && ParcelFileDescriptor.activeCount == beforeReplacement + 1) {
        "mpv failed stop must dispose newly opened FD and retain previous FD"
    }
    replacement.release()
    replacement.release()
    check(ParcelFileDescriptor.activeCount == beforeReplacement &&
        replacementNative.commands.any { it.firstOrNull() == "stop" } &&
        replacementNative.destroyed && replacementNative.destroyCount == 1) {
        "mpv release did not stop/destroy decoder or close SAF descriptors exactly once"
    }
    println("PASS: libmpv repeated local load, failed stop, and idempotent FD teardown")

    val beforeVlc = ParcelFileDescriptor.activeCount
    val resourceVlc = VlcEngine(context)
    resourceVlc.load(uri, 42_000L, true)
    val resourceNative = checkNotNull(MediaPlayer.last)
    check(ParcelFileDescriptor.activeCount == beforeVlc + 1) {
        "VLC failed to keep one SAF descriptor for its active media"
    }
    resourceVlc.load(Uri.parse("content://media/second.mkv"), 55_000L, true)
    check(ParcelFileDescriptor.activeCount == beforeVlc + 1) {
        "VLC leaked old SAF descriptor after successful source replacement"
    }
    resourceNative.failNextStop = true
    val failedVlcLoad = runCatching {
        resourceVlc.load(Uri.parse("content://media/third.mkv"), 77_000L, true)
    }
    check(failedVlcLoad.isFailure && ParcelFileDescriptor.activeCount == beforeVlc + 1) {
        "VLC must not close or replace an FD while the old native demuxer refuses to stop"
    }
    resourceVlc.release()
    resourceVlc.release()
    check(ParcelFileDescriptor.activeCount == beforeVlc && resourceNative.releaseCount == 1) {
        "VLC release leaked SAF descriptor or released the native decoder twice"
    }
    println("PASS: LibVLC source replacement, native stop failure and idempotent FD teardown")

    val beforeSeek = ParcelFileDescriptor.activeCount
    val seekVlc = VlcEngine(context)
    seekVlc.load(uri, 82_000L, true)
    val seekNative = checkNotNull(MediaPlayer.last)
    (seekVlc.view as SurfaceView).holder.create()
    seekVlc.seek(92_000L)
    seekNative.emit(MediaPlayer.Event.Playing)
    android.os.Handler.drainDelayedOnce()
    check(seekNative.time == 92_000L) {
        "VLC delayed saved-resume retry overrode explicit manual seek"
    }
    seekVlc.release()
    check(ParcelFileDescriptor.activeCount == beforeSeek) {
        "VLC manual-seek test leaked an SAF descriptor"
    }
    println("PASS: LibVLC manual seek supersedes delayed saved resume")

}
