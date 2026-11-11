package com.vaydelux.bousyplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousyplayer.data.RecentStore
import com.vaydelux.bousyplayer.model.SourcePolicy

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var hasFocus by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp)
            .onFocusChanged { hasFocus = it.isFocused }
            .border(if (hasFocus) BorderStroke(3.dp, CinemaColor.accent)
                else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(15.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (highlighted) CinemaColor.accent else CinemaColor.panelLight,
            contentColor = if (highlighted) CinemaColor.background else CinemaColor.text
        ),
        shape = RoundedCornerShape(14.dp)
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun HomeScreen(
    recents: List<RecentStore.Entry>,
    onPickFiles: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onRecent: (RecentStore.Entry) -> Unit,
    onClearHistory: () -> Unit,
    selectedTheme: CinemaTheme,
    onThemeSelected: (CinemaTheme) -> Unit
) {
    var isUrlDialogOpen by remember { mutableStateOf(false) }
    var isClearHistoryDialogOpen by remember { mutableStateOf(false) }
    var isThemeDialogOpen by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    val valid = SourcePolicy.isValidNetworkUrl(url)
    BoxWithConstraints(Modifier.fillMaxSize().background(CinemaColor.background)) {
        val compact = maxWidth < 600.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 18.dp else 36.dp, vertical = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BOUSY  /  PLAYER", style = MaterialTheme.typography.labelLarge,
                    color = CinemaColor.accent)
                TextButton(onClick = { isThemeDialogOpen = true }) {
                    Text("Theme · ${selectedTheme.label}", color = CinemaColor.text)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Every frame deserves a great player.", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = CinemaColor.text)
            Text("A local-first, cinema-inspired player for Android TV, phones & tablets.",
                color = CinemaColor.muted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ActionButton("＋  Open local media", onPickFiles,
                        highlighted = true, modifier = Modifier.fillMaxWidth())
                    ActionButton("↗  Open HTTPS stream", { isUrlDialogOpen = true },
                        modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("＋  Open local media", onPickFiles, highlighted = true)
                    ActionButton("↗  Open HTTPS stream", { isUrlDialogOpen = true })
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CONTINUE WATCHING", style = MaterialTheme.typography.titleMedium,
                    color = CinemaColor.muted)
                TextButton(onClick = { isClearHistoryDialogOpen = true }) {
                    Text("Clear history")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (recents.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 110.dp)
                    .background(CinemaColor.panel, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.CenterStart) {
                    Text("Open a video to begin. Recent local videos will appear here.",
                        Modifier.padding(24.dp), color = CinemaColor.muted)
                }
            } else {
                // Bounded to 20 entries in RecentStore; no nested infinitely measured LazyColumn.
                recents.forEach { item ->
                    var hasFocus by remember(item.source.id) { mutableStateOf(false) }
                    Surface(
                        onClick = { onRecent(item) },
                        shape = RoundedCornerShape(14.dp),
                        color = CinemaColor.panel,
                        border = BorderStroke(if (hasFocus) 3.dp else 1.dp,
                            if (hasFocus) CinemaColor.accent else CinemaColor.panelLight),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
                            .onFocusChanged { hasFocus = it.isFocused }
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("▶", color = CinemaColor.accent)
                            Column(Modifier.weight(1f)) {
                                Text(item.source.title, color = CinemaColor.text, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text("${SourcePolicy.displayTime(item.positionMs)} / ${SourcePolicy.displayTime(item.durationMs)}",
                                    color = CinemaColor.muted, style = MaterialTheme.typography.labelSmall)
                            }
                            if (!compact) Text("Resume", color = CinemaColor.accent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("MP4 • MKV • TS • HLS / DASH • Audio & subtitles • 3 playback engines",
                style = MaterialTheme.typography.labelSmall, color = CinemaColor.muted)
        }
    }
    if (isThemeDialogOpen) {
        AlertDialog(
            onDismissRequest = { isThemeDialogOpen = false },
            title = { Text("Choose appearance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CinemaTheme.entries.forEach { option ->
                        ActionButton(
                            "${if (selectedTheme == option) "✓  " else ""}${option.label}",
                            onClick = {
                                onThemeSelected(option)
                                isThemeDialogOpen = false
                            },
                            highlighted = selectedTheme == option,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isThemeDialogOpen = false }) { Text("Close") }
            }
        )
    }
    if (isClearHistoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { isClearHistoryDialogOpen = false },
            title = { Text("Clear Continue Watching?") },
            text = { Text("This removes saved local playback progress from this app. " +
                "Your media files and Android document permissions are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    isClearHistoryDialogOpen = false
                }) { Text("Clear history") }
            },
            dismissButton = {
                TextButton(onClick = { isClearHistoryDialogOpen = false }) { Text("Cancel") }
            }
        )
    }
    if (isUrlDialogOpen) {
        AlertDialog(
            onDismissRequest = { isUrlDialogOpen = false },
            title = { Text("Open secure stream") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("HTTPS media URL") },
                        placeholder = { Text("https://example.com/video.m3u8") },
                        singleLine = true
                    )
                    Text("HTTPS entry URL only. Remote stream addresses are never saved in Continue Watching.",
                        style = MaterialTheme.typography.bodySmall, color = CinemaColor.muted)
                }
            },
            confirmButton = { TextButton(onClick = {
                if (valid) { onOpenUrl(url.trim()); url = ""; isUrlDialogOpen = false }
            }, enabled = valid) { Text("Play") } },
            dismissButton = { TextButton(onClick = { isUrlDialogOpen = false }) { Text("Cancel") } }
        )
    }
}
