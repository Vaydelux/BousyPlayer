package com.vaydelux.bousyplayer.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * One audio-focus request and one noisy-audio receiver for the foreground player session.
 * Native engines must not each request focus independently. A system interruption never
 * silently changes the user's Play/Pause intention; only a permanent loss does.
 */
internal class AudioFocusCoordinator(
    context: Context,
    private val onTransientLoss: () -> Unit,
    private val onPermanentLoss: () -> Unit,
    private val onGain: () -> Unit,
    private val onNoisy: () -> Unit
) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private var disposed = false
    private var held = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            main.post {
                if (disposed || !held) return@post
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> onGain()
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        held = false
                        onPermanentLoss()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onTransientLoss()
                }
            }
        }, main)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!disposed && intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onNoisy()
        }
    }

    private val receiverRegistered: Boolean = runCatching {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(noisyReceiver, filter)
        }
        true
    }.getOrDefault(false)

    fun acquire(): Boolean {
        if (disposed) return false
        if (held) return true
        held = runCatching {
            manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        return held
    }

    fun abandon() {
        if (!held) return
        held = false
        runCatching { manager.abandonAudioFocusRequest(focusRequest) }
    }

    fun close() {
        if (disposed) return
        disposed = true
        abandonAfterClose()
        if (receiverRegistered) runCatching { app.unregisterReceiver(noisyReceiver) }
        main.removeCallbacksAndMessages(null)
    }

    private fun abandonAfterClose() {
        if (!held) return
        held = false
        runCatching { manager.abandonAudioFocusRequest(focusRequest) }
    }
}
