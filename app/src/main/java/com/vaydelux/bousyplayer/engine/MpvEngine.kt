package com.vaydelux.bousyplayer.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib

/**
 * Standalone libmpv adapter. The view owns only its surface; this object owns exactly one
 * native player and any SAF file descriptors until native teardown has completed.
 */
class MpvEngine(private val context: Context) : PlaybackEngine {
    override val kind = EngineKind.MPV
    private val main = Handler(Looper.getMainLooper())
    private val native: MPVLib = MPVLib.create(context.applicationContext)
        ?: error("libmpv could not initialize on this device")
    @Volatile private var released = false
    @Volatile private var fileLoaded = false
    @Volatile private var sourceRequested = false
    @Volatile private var intendedPlay = false
    @Volatile private var pausedForCache = false
    @Volatile private var ended = false
    @Volatile private var errorMessage: String? = null
    @Volatile private var positionMs = 0L
    @Volatile private var durationMs = 0L
    private var seekAfterLoadMs = 0L
    // Saved resumes can become seekable a little AFTER FILE_LOADED on MPEG-TS.
    // Retry briefly; every delayed callback is tied to the current media generation.
    private fun retrySavedSeek(generation: Long, attempt: Int) {
        if (released || generation != loadGeneration || !sourceRequested ||
            !fileLoaded || seekAfterLoadMs <= 0L) return
        if (runCatching { native.getPropertyBoolean("seekable") == true }.getOrDefault(false)) {
            val desired = seekAfterLoadMs
            runCatching {
                native.command(arrayOf("seek", (desired / 1000.0).toString(), "absolute"))
            }.onSuccess {
                if (generation == loadGeneration && seekAfterLoadMs == desired) seekAfterLoadMs = 0L
            }.onFailure { errorMessage = "libmpv could not apply the saved seek" }
        } else if (attempt < 12) {
            main.postDelayed({ retrySavedSeek(generation, attempt + 1) }, 250L)
        }
    }

    private var subtitleAppearance = SubtitleAppearance()
    private var speedValue = 1f
    private var aspectMode = AspectMode.FIT
    @Volatile private var nativeTracks: List<MediaTrack> = emptyList()
    private var descriptor: ParcelFileDescriptor? = null
    private var surfaceAttached = false
    // SurfaceView is attached after PlayerSession.load(); defer native loadfile until
    // the output Surface exists, avoiding an early GPU/vout failure on Android TV.
    private var pendingMediaUrl: String? = null
    @Volatile private var loadGeneration = 0L

    override val view: SurfaceView = SurfaceView(context).apply {
        keepScreenOn = true
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (released || surfaceAttached || !holder.surface.isValid) return
                runCatching {
                    native.attachSurface(holder.surface)
                    surfaceAttached = true
                    if (errorMessage == "libmpv video surface could not be attached") {
                        errorMessage = null
                    }
                    startPendingMedia()
                }.onFailure { errorMessage = "libmpv video surface could not be attached" }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) { detachView() }
        })
    }

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {
            if (property == "track-list") {
                val generation = loadGeneration
                main.post {
                    if (!released && generation == loadGeneration && sourceRequested) updateTracks()
                }
            }
        }
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: String) = Unit
        override fun eventProperty(property: String, value: Double) {
            if (released || !sourceRequested || !value.isFinite() || value < 0) return
            val millis = (value * 1000.0).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
            when (property) {
                "time-pos" -> positionMs = millis
                "duration", "duration/full" -> if (millis > 0L) durationMs = millis
            }
        }
        override fun eventProperty(property: String, value: Boolean) {
            if (released || !sourceRequested) return
            when (property) {
                "paused-for-cache" -> {
                    pausedForCache = value
                    if (!value && intendedPlay && fileLoaded) {
                        val generation = loadGeneration
                        main.post {
                            if (!released && generation == loadGeneration && intendedPlay && !pausedForCache) {
                                runCatching { native.setPropertyBoolean("pause", false) }
                            }
                        }
                    }
                }
                "seekable" -> if (value && fileLoaded && seekAfterLoadMs > 0L) {
                    val generation = loadGeneration
                    main.post { retrySavedSeek(generation, 0) }
                }
                "eof-reached" -> if (value && fileLoaded) {
                    ended = true
                    intendedPlay = false
                }
            }
        }
        override fun event(eventId: Int) {
            if (released || !sourceRequested) return
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_START_FILE -> { fileLoaded = false; ended = false }
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    fileLoaded = true
                    val generation = loadGeneration
                    main.post {
                        if (released || !sourceRequested || generation != loadGeneration) return@post
                        runCatching { updateTracks() }
                        runCatching { native.setPropertyDouble("speed", speedValue.toDouble()) }
                        runCatching { applyAspect() }
                        runCatching { applySubtitleAppearance(subtitleAppearance) }
                        retrySavedSeek(generation, 0)
                        // Never force Play when the user paused during loading or seeking.
                        runCatching { native.setPropertyBoolean("pause", !intendedPlay) }
                            .onFailure { errorMessage = "libmpv could not apply playback intent" }
                    }
                }
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                    val generation = loadGeneration
                    main.post {
                        if (!released && sourceRequested && generation == loadGeneration) {
                            runCatching { native.setPropertyBoolean("pause", !intendedPlay) }
                        }
                    }
                }
                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                    // EOF notifications and END_FILE can be delivered in different orders.
                    // Do not misclassify a genuine finished video as a failed decoder.
                    val generation = loadGeneration
                    main.postDelayed({
                        if (!released && generation == loadGeneration && sourceRequested && !ended) {
                            if (runCatching { native.getPropertyBoolean("eof-reached") == true }.getOrDefault(false)) {
                                ended = true
                            } else {
                                errorMessage = if (fileLoaded) {
                                    "libmpv stopped before a confirmed end of file"
                                } else {
                                    "libmpv could not open or decode this source"
                                }
                            }
                        }
                    }, 400L)
                }
            }
        }
    }

    init {
        try {
            native.setOptionString("vo", "gpu")
            native.setOptionString("gpu-context", "android")
            native.setOptionString("hwdec", "mediacodec-copy")
            native.setOptionString("hwdec-codecs", "all")
            native.setOptionString("ao", "audiotrack,opensles")
            native.setOptionString("tls-verify", "yes")
            native.setOptionString("video-sync", "audio")
            native.setOptionString("keep-open", "yes")
            native.setOptionString("demuxer-max-bytes", "16777216")
            native.setOptionString("demuxer-max-back-bytes", "4194304")
            native.setOptionString("demuxer-readahead-secs", "15")
            native.init()
            native.addObserver(observer)
            native.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            native.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            native.observeProperty("duration/full", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            native.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            native.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            native.observeProperty("seekable", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            native.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NODE)
        } catch (failure: Throwable) {
            runCatching { native.removeObserver(observer) }
            runCatching { native.destroy() }
            throw IllegalStateException("libmpv could not start", failure)
        }
    }

    override fun load(uri: Uri, startMs: Long, shouldPlay: Boolean, subtitle: Uri?) {
        check(!released)
        // We own the FD; fd:// never closes it. It stays valid until native teardown.
        val nextFd = if (uri.scheme == "content") context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open document") else null
        val nextUrl = when (uri.scheme) {
            "content" -> "fd://${nextFd!!.fd}"
            "file" -> uri.path ?: error("Invalid file path")
            "https" -> uri.toString()
            else -> error("Unsupported media scheme")
        }
        // Keep previous FD live until the old demuxer has been stopped.
        try {
            native.command(arrayOf("stop"))
        } catch (failure: Throwable) {
            nextFd?.close()
            throw failure
        }
        runCatching { descriptor?.close() }
        descriptor = nextFd
        loadGeneration++
        pendingMediaUrl = nextUrl
        sourceRequested = true
        fileLoaded = false
        ended = false
        pausedForCache = false
        errorMessage = null
        durationMs = 0L
        positionMs = 0L
        nativeTracks = emptyList()
        intendedPlay = shouldPlay
        seekAfterLoadMs = startMs.coerceAtLeast(0L)
        try {
            native.setPropertyBoolean("pause", !shouldPlay)
            startPendingMedia()
        } catch (failure: Throwable) {
            sourceRequested = false
            pendingMediaUrl = null
            descriptor?.close(); descriptor = null
            throw failure
        }
    }

    private fun startPendingMedia() {
        val url = pendingMediaUrl ?: return
        if (!PlaybackPolicy.canStartNative(true, sourceRequested, surfaceAttached) || released) return
        val generation = loadGeneration
        runCatching { native.command(arrayOf("loadfile", url, "replace")) }
            .onSuccess { if (generation == loadGeneration) pendingMediaUrl = null }
            .onFailure {
                if (generation == loadGeneration) {
                    pendingMediaUrl = null
                    errorMessage = "libmpv could not open the selected source"
                }
            }
    }

    private fun updateTracks() {
        if (released) return
        val count = (native.getPropertyInt("track-list/count") ?: 0).coerceIn(0, 64)
        nativeTracks = (0 until count).mapNotNull { i ->
            val type = when (native.getPropertyString("track-list/$i/type")) {
                "audio" -> TrackKind.AUDIO
                "sub" -> TrackKind.SUBTITLE
                else -> return@mapNotNull null
            }
            val id = native.getPropertyInt("track-list/$i/id") ?: return@mapNotNull null
            val title = native.getPropertyString("track-list/$i/title")
                ?: native.getPropertyString("track-list/$i/lang") ?: "Track $id"
            MediaTrack("${if (type == TrackKind.AUDIO) "a" else "s"}:$id", title.take(120),
                type, native.getPropertyBoolean("track-list/$i/selected") == true)
        }
    }

    override fun snapshot(): PlaybackSnapshot {
        if (released) return PlaybackSnapshot(loading = false)
        return PlaybackSnapshot(
            position = positionMs.coerceAtLeast(0), duration = durationMs,
            isPlaying = fileLoaded && intendedPlay && !pausedForCache && !ended && errorMessage == null &&
                native.getPropertyBoolean("pause") != true,
            canSeek = fileLoaded && native.getPropertyBoolean("seekable") == true,
            loading = sourceRequested && !fileLoaded || pausedForCache,
            ended = ended, error = errorMessage, tracks = nativeTracks
        )
    }
    override fun play() {
        intendedPlay = true
        if (released) return
        if (ended) {
            ended = false
            native.command(arrayOf("seek", "0", "absolute"))
        }
        native.setPropertyBoolean("pause", false)
    }
    override fun pause() { intendedPlay = false; if (!released) native.setPropertyBoolean("pause", true) }
    override fun seek(positionMs: Long) {
        if (!released && fileLoaded && native.getPropertyBoolean("seekable") == true) {
            // Explicit D-pad/timeline seeking must supersede a pending saved resume.
            // Clear the old request only after the native seek command succeeds.
            native.command(arrayOf("seek", (positionMs.coerceAtLeast(0L) / 1000.0).toString(), "absolute"))
            seekAfterLoadMs = 0L
        }
    }
    override fun speed(value: Float) {
        speedValue = value
        if (!released) native.setPropertyDouble("speed", value.toDouble())
    }
    override fun aspect(value: AspectMode) { aspectMode = value; applyAspect() }
    private fun applyAspect() {
        if (released) return
        when (aspectMode) {
            AspectMode.FIT -> { native.setPropertyBoolean("keepaspect", true); native.setPropertyDouble("panscan", 0.0) }
            AspectMode.ZOOM -> { native.setPropertyBoolean("keepaspect", true); native.setPropertyDouble("panscan", 1.0) }
            AspectMode.STRETCH -> { native.setPropertyBoolean("keepaspect", false); native.setPropertyDouble("panscan", 0.0) }
        }
    }
    override fun selectTrack(id: String, kind: TrackKind) {
        val value = id.substringAfter(':', "").toIntOrNull() ?: return
        if (released) return
        native.setPropertyInt(if (kind == TrackKind.AUDIO) "aid" else "sid", value)
        updateTracks()
    }
    override fun disableSubtitles() { if (!released) { native.setPropertyString("sid", "no"); updateTracks() } }
    override fun applySubtitleAppearance(appearance: SubtitleAppearance): Boolean {
        if (released) return false
        subtitleAppearance = appearance
        return runCatching {
            native.setPropertyDouble("sub-font-size", appearance.size.mpvFontSize)
            native.setPropertyString("sub-color", appearance.color.mpvHex)
            native.setPropertyString("sub-border-color",
                if (appearance.color == SubtitleColor.BLACK) "#FFFFFFFF" else "#000000FF")
            native.setPropertyString("sub-back-color",
                if (appearance.color == SubtitleColor.BLACK) "#FFFFFFD0" else "#000000B0")
        }.isSuccess
    }
    override fun detachView() {
        if (!released && surfaceAttached) {
            runCatching { native.detachSurface() }
            surfaceAttached = false
        }
    }
    override fun release() {
        if (released) return
        released = true
        sourceRequested = false
        pendingMediaUrl = null
        nativeTracks = emptyList()
        seekAfterLoadMs = 0L
        loadGeneration++
        main.removeCallbacksAndMessages(null)
        runCatching { native.removeObserver(observer) }
        // Stop demuxing before native teardown; destroy remains authoritative,
        // and the SAF descriptor is retained until both calls have completed.
        runCatching { native.command(arrayOf("stop")) }
        runCatching { native.detachSurface() }
        runCatching { native.destroy() }
        runCatching { descriptor?.close() }
        descriptor = null
        surfaceAttached = false
        view.keepScreenOn = false
    }
}
