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
    // When native seekability changes after the last session snapshot, the
    // adapter must reject the request rather than silently acknowledging it.
    val beforeRejectedMpv = overrideFake.commands.count { it.firstOrNull() == "seek" }
    check(runCatching { overrideEngine.seek(91_000L) }.isFailure &&
        overrideFake.commands.count { it.firstOrNull() == "seek" } == beforeRejectedMpv) {
        "libmpv silently accepted an unseekable user request"
    }
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
    seekNative.isSeekable = false
    val beforeRejectedVlc = seekNative.time
    check(runCatching { seekVlc.seek(91_000L) }.isFailure &&
        seekNative.time == beforeRejectedVlc) {
        "LibVLC silently accepted an unseekable user request"
    }
    seekNative.isSeekable = true
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

    // A native read exception is unknown status, not a valid 0:00 timestamp.
    // The real PlayerSession owns retries and preserves the trusted timeline.
    val statusVlc = VlcEngine(context)
    statusVlc.load(uri, 44_000L, false)
    val statusNative = checkNotNull(MediaPlayer.last)
    statusNative.setTime(44_000L)
    statusNative.failNextPositionRead = true
    check(runCatching { statusVlc.snapshot() }.isFailure) {
        "LibVLC masked a failed time getter as a valid 0:00 timeline"
    }
    check(statusVlc.snapshot().position == 44_000L) {
        "LibVLC did not recover after one transient timeline read fault"
    }
    statusNative.failNextDurationRead = true
    check(runCatching { statusVlc.snapshot() }.isFailure) {
        "LibVLC masked a duration failure as an unknown zero duration"
    }
    statusNative.failNextPlayingRead = true
    check(runCatching { statusVlc.snapshot() }.isFailure) {
        "LibVLC masked a native isPlaying exception as a pause"
    }
    statusNative.failNextSeekableRead = true
    check(runCatching { statusVlc.snapshot() }.isFailure) {
        "LibVLC masked a native seekability read failure"
    }
    statusVlc.release()
    println("PASS: LibVLC snapshot read faults propagate to bounded session recovery without fake 0:00")

    // Hammer Android's SurfaceHolder lifecycle without starting multiple
    // video renderers, retaining a stale output or reopening the video.
    val beforeSurfaceLoop = ParcelFileDescriptor.activeCount
    val surfaceMpv = MpvEngine(context)
    val loopMpvNative = checkNotNull(MPVLib.last)
    surfaceMpv.load(uri, 25_000L, false)
    val loopMpvView = surfaceMpv.view as SurfaceView
    loopMpvView.holder.create()
    loopMpvView.holder.create()
    check(loopMpvNative.attachCount == 1) { "duplicate mpv surfaceCreated called native attach twice" }
    repeat(20) {
        loopMpvView.holder.destroy()
        loopMpvView.holder.create()
        loopMpvView.holder.create()
    }
    check(loopMpvNative.attachCount == 21 &&
        loopMpvNative.commands.count { it.firstOrNull() == "loadfile" } == 1 &&
        !loopMpvNative.destroyed) {
        "rapid mpv reattachment leaked or restarted native decoding"
    }
    surfaceMpv.release()
    loopMpvView.holder.create()
    check(loopMpvNative.attachCount == 21 && loopMpvNative.destroyCount == 1 &&
        !loopMpvView.keepScreenOn &&
        ParcelFileDescriptor.activeCount == beforeSurfaceLoop) {
        "mpv reacted to surface callback after release or retained document descriptor"
    }
    println("PASS: repeated mpv surface recreation, idempotent native attachment and cleanup")

    val beforeVlcSurfaces = ParcelFileDescriptor.activeCount
    val surfaceVlc = VlcEngine(context)
    val loopVlcNative = checkNotNull(MediaPlayer.last)
    surfaceVlc.load(uri, 35_000L, false)
    val loopVlcView = surfaceVlc.view as SurfaceView
    loopVlcView.holder.create()
    loopVlcView.holder.create()
    check(loopVlcNative.vlcVout.attachCount == 1 && loopVlcNative.playCount == 0) {
        "VLC attached duplicate surfaces or started video while paused"
    }
    repeat(20) {
        loopVlcView.holder.destroy()
        loopVlcView.width = 1280 + it
        loopVlcView.height = 720 + it
        loopVlcView.holder.create()
        loopVlcView.holder.create()
    }
    check(loopVlcNative.vlcVout.attachCount == 21 &&
        loopVlcNative.vlcVout.windowWidth == 1299 &&
        loopVlcNative.vlcVout.windowHeight == 739 &&
        loopVlcNative.playCount == 0) {
        "VLC surface recreation double-attached, retained stale dimensions or overrode Pause"
    }
    surfaceVlc.release()
    loopVlcView.holder.create()
    check(loopVlcNative.vlcVout.attachCount == 21 &&
        loopVlcNative.releaseCount == 1 && !loopVlcView.keepScreenOn &&
        ParcelFileDescriptor.activeCount == beforeVlcSurfaces) {
        "VLC retained native output or SAF handle after release"
    }
    println("PASS: repeated VLC surface recreation, window size and Pause preservation")

    val failedAttachMpv = MpvEngine(context)
    val failedAttachMpvNative = checkNotNull(MPVLib.last)
    failedAttachMpv.load(uri, 0L, false)
    failedAttachMpvNative.failNextAttach = true
    val failedMpvView = failedAttachMpv.view as SurfaceView
    failedMpvView.holder.create()
    check(failedAttachMpv.snapshot().error != null && failedAttachMpvNative.attachCount == 0) {
        "mpv did not surface a failed native attach"
    }
    failedMpvView.holder.create()
    check(failedAttachMpv.snapshot().error == null && failedAttachMpvNative.attachCount == 1) {
        "mpv did not recover from a transient surface failure"
    }
    failedAttachMpv.release()

    val failedAttachVlc = VlcEngine(context)
    val failedAttachVlcNative = checkNotNull(MediaPlayer.last)
    failedAttachVlc.load(uri, 0L, false)
    failedAttachVlcNative.vlcVout.failNextAttach = true
    val failedVlcView = failedAttachVlc.view as SurfaceView
    failedVlcView.holder.create()
    check(failedAttachVlc.snapshot().error != null && failedAttachVlcNative.vlcVout.attachCount == 0) {
        "VLC did not report a failed native video attach"
    }
    failedVlcView.holder.create()
    check(failedAttachVlc.snapshot().error == null &&
        failedAttachVlcNative.vlcVout.attachCount == 1) {
        "VLC failed to recover from a transient native attach failure"
    }
    failedAttachVlc.release()
    check(ParcelFileDescriptor.activeCount == beforeVlcSurfaces) {
        "a failed-then-restored surface leaked an SAF descriptor"
    }
    println("PASS: transient mpv/VLC surface faults recover without leaving stale errors or leaking FDs")

}
