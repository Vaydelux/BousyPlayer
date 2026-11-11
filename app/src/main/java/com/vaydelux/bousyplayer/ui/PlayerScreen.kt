package com.vaydelux.bousyplayer.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vaydelux.bousyplayer.engine.*
import com.vaydelux.bousyplayer.model.SourcePolicy
import kotlinx.coroutines.delay

enum class PlayerMenu { NONE, OPTIONS, TRACKS, QUEUE }

@Composable
fun PlayerScreen(session: PlayerSession, onClose: () -> Unit, onPickSubtitle: () -> Unit) {
    var hudVisible by remember(session) { mutableStateOf(true) }
    var menu by remember(session) { mutableStateOf(PlayerMenu.NONE) }
    var interaction by remember(session) { mutableIntStateOf(0) }
    var scrubTarget by remember(session) { mutableFloatStateOf(-1f) }
    var showBuffering by remember(session) { mutableStateOf(false) }
    var seekHint by remember(session) { mutableStateOf<String?>(null) }
    val menuFocus = remember { FocusRequester() }
    val queueFocus = remember { FocusRequester() }
    val queueListState = remember(session) { LazyListState(firstVisibleItemIndex = session.index) }
    val surfaceFocus = remember { FocusRequester() }
    val transportFocus = remember { FocusRequester() }
    val screen = session.snapshot
    val lifecycle = LocalLifecycleOwner.current
    fun activate() { hudVisible = true; interaction++ }
    fun goBack() {
        when {
            menu != PlayerMenu.NONE -> { menu = PlayerMenu.NONE; activate() }
            !hudVisible -> activate()
            else -> onClose()
        }
    }
    BackHandler { goBack() }
    LaunchedEffect(session, screen.loading, screen.error) {
        showBuffering = false
        if (screen.loading && screen.error == null) {
            delay(350) // no flashing overlay for fast initial loads or brief rebuffers
            showBuffering = true
        }
    }
    LaunchedEffect(session, menu, hudVisible, session.index) {
        when {
            menu == PlayerMenu.QUEUE -> {
                // Scroll before focusing; selected rows near the end of a long queue
                // are not composed until LazyColumn brings them into view.
                queueListState.scrollToItem(session.index)
                withFrameNanos { }
                runCatching { queueFocus.requestFocus() }
                    .onFailure { runCatching { menuFocus.requestFocus() } }
            }
            menu != PlayerMenu.NONE -> runCatching { menuFocus.requestFocus() }
            hudVisible -> runCatching { transportFocus.requestFocus() }
            else -> runCatching { surfaceFocus.requestFocus() }
        }
    }
    LaunchedEffect(session, session.index, session.kind) { scrubTarget = -1f }
    LaunchedEffect(seekHint) {
        if (seekHint != null) { delay(1_000); seekHint = null }
    }
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.onBackground()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); session.close() }
    }
    LaunchedEffect(session) {
        while (true) { session.refresh(); delay(400) }
    }
    LaunchedEffect(session, interaction, menu, hudVisible, screen.isPlaying) {
        if (hudVisible && menu == PlayerMenu.NONE && session.wantsPlay) {
            delay(5_000)
            hudVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)
        // The preview key handler must wrap the focused surface / button descendants.
        .onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> { session.play(); activate(); true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { session.pause(); activate(); true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { session.playPause(); activate(); true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { session.seekBy(30_000); activate(); true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { session.seekBy(-30_000); activate(); true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { session.next(); activate(); true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { session.previous(); activate(); true }
                KeyEvent.KEYCODE_MEDIA_STOP -> { session.pause(); onClose(); true }
                // AndroidX BackHandler exclusively owns Back. Handling KEYCODE_BACK
                // here as well can close two UI layers on a single remote press.
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CAPTIONS -> {
                    menu = if (menu == PlayerMenu.NONE) PlayerMenu.TRACKS else PlayerMenu.NONE
                    activate(); true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (menu == PlayerMenu.NONE && !hudVisible) {
                    session.seekBy(-10_000)
                    seekHint = if (screen.canSeek) "−10s · ${SourcePolicy.displayTime(session.seekDisplayPosition())}" else "Not seekable"
                    true
                } else false
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (menu == PlayerMenu.NONE && !hudVisible) {
                    session.seekBy(10_000)
                    seekHint = if (screen.canSeek) "+10s · ${SourcePolicy.displayTime(session.seekDisplayPosition())}" else "Not seekable"
                    true
                } else false
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> if (!hudVisible) {
                    activate(); true
                } else false
                KeyEvent.KEYCODE_DPAD_DOWN -> if (!hudVisible) {
                    activate(); menu = PlayerMenu.QUEUE; true
                } else false
                KeyEvent.KEYCODE_DPAD_UP -> if (!hudVisible) {
                    activate(); true
                } else false
                else -> false
            }
        }
        // The video becomes D-pad focusable only while controls are hidden.
        // Otherwise, actual transport/menu buttons receive normal navigation focus.
        .then(if (menu == PlayerMenu.NONE && !hudVisible)
            Modifier.focusRequester(surfaceFocus).focusable() else Modifier)
    ) {
        val active = session.engine
        if (active != null) {
            key(active) {
                AndroidView(
                    factory = { active.view },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // Do not keep phones/TVs awake indefinitely after Pause,
                        // EOF or an unrecoverable decoder error.
                        view.keepScreenOn = session.wantsPlay && !screen.ended && screen.error == null
                    },
                    onRelease = { view ->
                        view.keepScreenOn = false
                        active.detachView()
                    }
                )
            }
        }
        // Touch surface remains available after HUD auto-hides; chrome draws above it.
        // Pointer-only video tap target: avoid creating a full-screen invisible
        // focusable control that competes with real D-pad transport buttons.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { activate() }) })
        seekHint?.let { hint ->
            Surface(
                color = CinemaColor.panel.copy(alpha = .91f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(hint, Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = CinemaColor.text)
            }
        }
        if (showBuffering && screen.loading && screen.error == null) {
            Surface(
                color = CinemaColor.panel.copy(alpha = .87f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp)
            ) { Text("Buffering…", Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = CinemaColor.text) }
        }
        screen.error?.let { error ->
            Column(Modifier.align(Alignment.Center).background(CinemaColor.panel, MaterialTheme.shapes.large)
                .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Playback unavailable", color = CinemaColor.text, fontWeight = FontWeight.Bold)
                val diagnostic = session.diagnostic
                Text(diagnostic?.guidance ?: "Playback stopped. Try Retry or another engine.",
                    color = CinemaColor.muted)
                diagnostic?.let {
                    Text("Diagnostic: ${it.code}", color = CinemaColor.muted,
                        style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ActionButton("Retry", { session.retry(); activate() })
                    EngineKind.entries.filter { it != session.kind }.forEach { other ->
                        ActionButton("Try ${other.label}", {
                            session.switchEngine(other); activate()
                        }, highlighted = true)
                    }
                }
            }
        }
        if (hudVisible) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(CinemaColor.background.copy(alpha = .80f))
                    .padding(horizontal = 26.dp, vertical = 15.dp)
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 570.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                ActionButton("‹", onClose)
                                Column(Modifier.weight(1f)) {
                                    Text(session.source.title, color = CinemaColor.text,
                                        fontWeight = FontWeight.Bold, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(session.kind.label, color = CinemaColor.muted,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                                ActionButton("Restart", { session.startOver(); activate() }, enabled = screen.canSeek)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionButton("‹  Back", onClose)
                            Column(Modifier.weight(1f)) {
                                Text(session.source.title, color = CinemaColor.text,
                                    fontWeight = FontWeight.Bold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text("${session.kind.label}  ·  ${if (screen.canSeek) "Seekable" else "Live / non-seekable"}",
                                    color = CinemaColor.muted, style = MaterialTheme.typography.labelSmall)
                            }
                            ActionButton("Start over", { session.startOver(); activate() }, enabled = screen.canSeek)
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(CinemaColor.background.copy(alpha = .90f))
                    .padding(horizontal = 26.dp, vertical = 14.dp)
            ) {
                if (screen.duration > 0) {
                    val target = if (scrubTarget >= 0) scrubTarget else
                        (screen.position.toFloat() / screen.duration).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(SourcePolicy.displayTime((target * screen.duration).toLong()),
                            color = CinemaColor.text, style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = target,
                            onValueChange = { scrubTarget = it; interaction++ },
                            onValueChangeFinished = {
                                if (scrubTarget >= 0) session.seekTo((scrubTarget * screen.duration).toLong())
                                scrubTarget = -1f
                            },
                            enabled = screen.canSeek,
                            modifier = Modifier.weight(1f)
                        )
                        Text(SourcePolicy.displayTime(screen.duration),
                            color = CinemaColor.text, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text("LIVE / duration unavailable", color = CinemaColor.muted)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 680.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                ActionButton("−10", { session.seekBy(-10_000); activate() },
                                    modifier = Modifier.weight(1f), enabled = screen.canSeek)
                                ActionButton(if (session.wantsPlay) "❚❚" else "▶", {
                                    session.playPause(); activate()
                                }, highlighted = true,
                                    modifier = Modifier.weight(1.25f).focusRequester(transportFocus))
                                ActionButton("+10", { session.seekBy(10_000); activate() },
                                    modifier = Modifier.weight(1f), enabled = screen.canSeek)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                ActionButton("Queue", { menu = PlayerMenu.QUEUE; activate() },
                                    modifier = Modifier.weight(1f))
                                ActionButton("Audio", { menu = PlayerMenu.TRACKS; activate() },
                                    modifier = Modifier.weight(1f))
                                ActionButton("More", { menu = PlayerMenu.OPTIONS; activate() },
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ActionButton("−10s", { session.seekBy(-10_000); activate() }, enabled = screen.canSeek)
                            ActionButton(if (session.wantsPlay) "❚❚  Pause" else "▶  Play", {
                                session.playPause(); activate()
                            }, highlighted = true, modifier = Modifier.focusRequester(transportFocus))
                            ActionButton("+10s", { session.seekBy(10_000); activate() }, enabled = screen.canSeek)
                            ActionButton("Queue", { menu = PlayerMenu.QUEUE; activate() })
                            ActionButton("Audio / CC", { menu = PlayerMenu.TRACKS; activate() })
                            ActionButton("⚙  More", { menu = PlayerMenu.OPTIONS; activate() })
                        }
                    }
                }
                session.message?.let { Text(it, color = CinemaColor.muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (menu != PlayerMenu.NONE) {
            Column(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(min = 290.dp, max = 390.dp)
                    .background(CinemaColor.panel.copy(alpha = .98f))
                    .then(if (menu == PlayerMenu.QUEUE) Modifier
                        else Modifier.verticalScroll(rememberScrollState()))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (menu) {
                        PlayerMenu.OPTIONS -> "PLAYER OPTIONS"
                        PlayerMenu.TRACKS -> "AUDIO & SUBTITLES"
                        PlayerMenu.QUEUE -> "PLAYLIST"
                        PlayerMenu.NONE -> ""
                    }, modifier = Modifier.weight(1f), color = CinemaColor.text,
                        style = MaterialTheme.typography.titleMedium)
                    ActionButton("✕", { menu = PlayerMenu.NONE; activate() },
                        modifier = Modifier.focusRequester(menuFocus))
                }
                HorizontalDivider(color = CinemaColor.panelLight)
                when (menu) {
                    PlayerMenu.OPTIONS -> {
                        Text("Playback engine", color = CinemaColor.muted)
                        EngineKind.entries.forEach { other ->
                            ActionButton("${if (session.kind == other) "✓  " else ""}${other.label}", {
                                session.switchEngine(other); activate()
                            }, modifier = Modifier.fillMaxWidth())
                        }
                        Text("Playback speed", color = CinemaColor.muted)
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                            ActionButton("${if (session.speed == value) "✓  " else ""}${value}×", {
                                session.changeSpeed(value); activate()
                            }, modifier = Modifier.fillMaxWidth())
                        }
                        Text("Video aspect", color = CinemaColor.muted)
                        AspectMode.entries.forEach { mode ->
                            ActionButton("${if (session.aspect == mode) "✓  " else ""}${mode.label}", {
                                session.changeAspect(mode); activate()
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    PlayerMenu.TRACKS -> {
                        ActionButton("Add external SRT / VTT / ASS", onPickSubtitle, modifier = Modifier.fillMaxWidth())
                        Text("Embedded audio", color = CinemaColor.muted)
                        screen.tracks.filter { it.kind == TrackKind.AUDIO }.forEach { track ->
                            ActionButton("${if (track.selected) "✓  " else ""}${track.label}", {
                                session.setTrack(track.id, TrackKind.AUDIO); activate()
                            }, modifier = Modifier.fillMaxWidth())
                        }
                        Text("Subtitles", color = CinemaColor.muted)
                        ActionButton("Disable captions", { session.disableSubtitles(); activate() }, modifier = Modifier.fillMaxWidth())
                        screen.tracks.filter { it.kind == TrackKind.SUBTITLE }.forEach { track ->
                            ActionButton("${if (track.selected) "✓  " else ""}${track.label}", {
                                session.setTrack(track.id, TrackKind.SUBTITLE); activate()
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    PlayerMenu.QUEUE -> {
                        Text("${session.index + 1} / ${session.playlist.size} · D-pad to browse",
                            color = CinemaColor.muted, style = MaterialTheme.typography.labelMedium)
                        LazyColumn(
                            state = queueListState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(session.playlist, key = { index, _ -> index }) { index, item ->
                                ActionButton("${if (index == session.index) "▶  " else ""}${item.title}", {
                                    session.openPlaylistIndex(index)
                                    menu = PlayerMenu.NONE
                                    activate()
                                }, modifier = Modifier.fillMaxWidth()
                                    .then(if (index == session.index)
                                        Modifier.focusRequester(queueFocus) else Modifier))
                            }
                        }
                    }
                    PlayerMenu.NONE -> Unit
                }
            }
        }
    }
}
