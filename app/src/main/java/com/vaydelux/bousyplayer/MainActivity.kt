package com.vaydelux.bousyplayer

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vaydelux.bousyplayer.data.RecentStore
import com.vaydelux.bousyplayer.engine.PlayerSession
import com.vaydelux.bousyplayer.engine.QueuePolicy
import com.vaydelux.bousyplayer.engine.SessionRestorePolicy
import com.vaydelux.bousyplayer.model.MediaSource
import com.vaydelux.bousyplayer.model.HistoryPolicy
import com.vaydelux.bousyplayer.model.SourcePolicy
import com.vaydelux.bousyplayer.ui.BousyPlayerTheme
import com.vaydelux.bousyplayer.ui.CinemaTheme
import com.vaydelux.bousyplayer.ui.HomeScreen
import com.vaydelux.bousyplayer.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Uri?>(null)
    private var activePlayerSession: PlayerSession? = null

    internal fun bindPlayerSession(session: PlayerSession?) {
        activePlayerSession = session
    }

    internal fun unbindPlayerSession(session: PlayerSession?) {
        if (activePlayerSession === session) activePlayerSession = null
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // A stopped/UI-hidden player can drop its native decoder while retaining
        // only a lightweight, paused foreground session and Continue Watching.
        activePlayerSession?.onMemoryPressure(level)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
        val appearance = getSharedPreferences("appearance_v1", Context.MODE_PRIVATE)
        setContent {
            // User theme choice survives process death without retaining URLs or media.
            var selectedTheme by remember {
                mutableStateOf(CinemaTheme.fromStored(appearance.getString("theme", null)))
            }
            BousyPlayerTheme(theme = selectedTheme) {
                PlayerApp(this@MainActivity, incoming,
                    clearIncoming = { incoming = null },
                    selectedTheme = selectedTheme,
                    onThemeSelected = { next ->
                        selectedTheme = next
                        appearance.edit().putString("theme", next.name).apply()
                    })
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = if (intent.action == Intent.ACTION_VIEW) intent.data else null
    }
}

private fun titleForUri(activity: ComponentActivity, uri: Uri): String {
    if (uri.scheme == "content") {
        val name = runCatching {
            activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor: Cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        if (!name.isNullOrBlank()) return HistoryPolicy.safeTitle(name)
    }
    return HistoryPolicy.safeTitle(uri.lastPathSegment?.substringAfterLast('/')
        ?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: "Untitled video")
}

@Composable
private fun PlayerApp(
    activity: MainActivity,
    incoming: Uri?,
    clearIncoming: () -> Unit,
    selectedTheme: CinemaTheme,
    onThemeSelected: (CinemaTheme) -> Unit
) {
    val recentsStore = remember { RecentStore(activity) }
    val lifecycle = LocalLifecycleOwner.current
    var recents by remember { mutableStateOf(recentsStore.readAccessible()) }
    // Construct a new session only AFTER releasing the old one. Keeping a PlayerSession
    // inside remember(queue) can briefly overlap two large native decoder instances.
    var session by remember { mutableStateOf<PlayerSession?>(null) }
    // Android saves only the current SAF document identifier/title, never an HTTPS URL,
    // transient content intent, or a large queue. SavedState is not a long-term database.
    var savedDocumentUri by rememberSaveable { mutableStateOf("") }
    var savedDocumentTitle by rememberSaveable { mutableStateOf("") }
    fun rememberActiveDocument(current: MediaSource?) {
        val safe = current?.takeIf {
            it.isDocument && it.uri.scheme == "content" &&
                it.uri.toString().length <= SessionRestorePolicy.MAX_URI_LENGTH
        }
        savedDocumentUri = safe?.uri?.toString().orEmpty()
        savedDocumentTitle = safe?.title?.let(SessionRestorePolicy::safeTitle).orEmpty()
    }
    fun openSources(sources: List<MediaSource>, restored: Boolean = false) {
        if (sources.isEmpty()) return
        session?.close()
        session = PlayerSession(activity, QueuePolicy.bounded(sources), 0,
            recentsStore, initialPlay = !restored)
        rememberActiveDocument(session?.source)
    }
    fun leavePlayer() {
        session?.close()
        session = null
        rememberActiveDocument(null)
        recents = recentsStore.readAccessible()
    }

    LaunchedEffect(Unit) {
        // Never resume a newly received VIEW intent with a stale saved document.
        val storedUri = savedDocumentUri
        if (incoming == null && storedUri.isNotBlank() && session == null) {
            val granted = runCatching {
                activity.contentResolver.persistedUriPermissions
                    .asSequence().filter { it.isReadPermission }
                    .map { it.uri.toString() }.toSet()
            }.getOrDefault(emptySet())
            if (SessionRestorePolicy.canRestore(storedUri, granted)) {
                openSources(listOf(MediaSource(Uri.parse(storedUri),
                    SessionRestorePolicy.safeTitle(savedDocumentTitle), true)), restored = true)
            } else {
                rememberActiveDocument(null)
            }
        }
    }
    LaunchedEffect(session?.index, session) {
        val current = session?.source ?: return@LaunchedEffect
        rememberActiveDocument(current)
    }
    DisposableEffect(activity, session, lifecycle) {
        activity.bindPlayerSession(session)
        val observer = LifecycleEventObserver { _, event ->
            // A document-provider read grant may become available again while
            // BousyPlayer is backgrounded. Re-read the visible cards on return,
            // without rewriting or deleting any persisted history.
            if (event == Lifecycle.Event.ON_START && session == null) {
                recents = recentsStore.readAccessible()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            activity.unbindPlayerSession(session)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val chosen = uris.distinct()
            if (chosen.size > QueuePolicy.MAX_ITEMS) {
                Toast.makeText(activity,
                    "Loaded the first ${QueuePolicy.MAX_ITEMS} videos to protect TV memory",
                    Toast.LENGTH_LONG).show()
            }
            val sources = QueuePolicy.bounded(chosen).map { uri ->
                val persisted = runCatching {
                    activity.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    true
                }.getOrDefault(false)
                // Playback of a temporary grant is still possible, but don't persist a
                // Continue Watching entry whose permission won't survive app restart.
                MediaSource(uri, titleForUri(activity, uri), persisted)
            }
            openSources(sources)
        }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            session?.addSubtitle(uri)
        }
    }

    LaunchedEffect(incoming) {
        val uri = incoming ?: return@LaunchedEffect
        if (uri.scheme == "content" ||
            (uri.scheme == "https" && SourcePolicy.isValidNetworkUrl(uri.toString()))) {
            // External VIEW permissions are often transient; don't persist as a SAF recent.
            openSources(listOf(MediaSource(uri, titleForUri(activity, uri), false)))
        }
        clearIncoming()
    }

    val activeSession = session
    if (activeSession == null) {
        HomeScreen(
            recents = recents,
            onPickFiles = { filePicker.launch(arrayOf("*/*")) },
            onOpenUrl = { url ->
                if (SourcePolicy.isValidNetworkUrl(url)) {
                    val uri = Uri.parse(url)
                    openSources(listOf(MediaSource(uri, titleForUri(activity, uri), false)))
                }
            },
            onRecent = { entry ->
                openSources(listOf(entry.source))
            },
            onClearHistory = {
                recentsStore.clear()
                recents = emptyList()
            },
            selectedTheme = selectedTheme,
            onThemeSelected = onThemeSelected
        )
    } else {
        PlayerScreen(session = activeSession, onClose = { leavePlayer() },
            onPickSubtitle = { subtitlePicker.launch(arrayOf("*/*")) },
            selectedTheme = selectedTheme, onThemeSelected = onThemeSelected)
    }
}
