package com.vaydelux.bousyplayer

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
import com.vaydelux.bousyplayer.data.RecentStore
import com.vaydelux.bousyplayer.engine.PlayerSession
import com.vaydelux.bousyplayer.engine.QueuePolicy
import com.vaydelux.bousyplayer.engine.SessionRestorePolicy
import com.vaydelux.bousyplayer.model.MediaSource
import com.vaydelux.bousyplayer.model.SourcePolicy
import com.vaydelux.bousyplayer.ui.BousyPlayerTheme
import com.vaydelux.bousyplayer.ui.HomeScreen
import com.vaydelux.bousyplayer.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
        setContent {
            BousyPlayerTheme {
                PlayerApp(this@MainActivity, incoming) { incoming = null }
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
        if (!name.isNullOrBlank()) return name
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?')
        ?.takeIf { it.isNotBlank() } ?: "Untitled video"
}

@Composable
private fun PlayerApp(activity: ComponentActivity, incoming: Uri?, clearIncoming: () -> Unit) {
    val recentsStore = remember { RecentStore(activity) }
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
            }
        )
    } else {
        PlayerScreen(session = activeSession, onClose = { leavePlayer() },
            onPickSubtitle = { subtitlePicker.launch(arrayOf("*/*")) })
    }
}
