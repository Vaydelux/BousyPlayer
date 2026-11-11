package com.vaydelux.bousyplayer.data

import android.content.Context
import android.net.Uri
import com.vaydelux.bousyplayer.model.MediaSource
import com.vaydelux.bousyplayer.model.HistoryPolicy
import org.json.JSONArray
import org.json.JSONObject
import com.vaydelux.bousyplayer.engine.PlaybackResumePolicy

/** Stores only user-selected SAF document URIs; never records signed stream URLs. */
class RecentStore(context: Context) {
    private val prefsContext = context.applicationContext
    private val prefs = prefsContext.getSharedPreferences("recent_media_v1", Context.MODE_PRIVATE)

    data class Entry(val source: MediaSource, val positionMs: Long, val durationMs: Long)

    fun read(): List<Entry> = runCatching {
        val raw = prefs.getString("entries", "[]") ?: "[]"
        // Reject oversized blobs before allocating JSONArray objects; do not
        // eagerly expand provider-controlled metadata on low-RAM TVs.
        if (raw.length > HistoryPolicy.MAX_HISTORY_JSON_CHARS) return@runCatching emptyList<Entry>()
        val array = JSONArray(raw)
        (0 until minOf(array.length(), 20)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = item.optString("uri", "")
            if (!HistoryPolicy.isPersistableDocumentUri(uri)) return@mapNotNull null
            val duration = HistoryPolicy.safeDuration(item.optLong("duration", 0L))
            Entry(MediaSource(Uri.parse(uri),
                HistoryPolicy.safeTitle(item.optString("title", "Video")), true),
                HistoryPolicy.safePosition(item.optLong("position", 0L), duration),
                duration)
        }.distinctBy { it.source.uri }
    }.getOrDefault(emptyList())

    /**
     * Prune revoked document grants before displaying an unusable Continue Watching card.
     * A grant is necessary but not sufficient: cloud providers may still be offline.
     */
    fun readAccessible(): List<Entry> {
        val all = read()
        // Some document providers/OEM builds can fail permission enumeration.
        // A temporary platform error must never erase the user's saved history.
        val granted = runCatching {
            prefsContext.contentResolver.persistedUriPermissions
                .asSequence().filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        }.getOrNull() ?: return all
        val accessible = all.filter { it.source.uri.toString() in granted }
        if (accessible.size != all.size) write(accessible)
        return accessible
    }

    fun update(source: MediaSource, positionMs: Long, durationMs: Long) {
        if (!source.isDocument ||
            !HistoryPolicy.isPersistableDocumentUri(source.uri.toString())) return
        val safeDuration = HistoryPolicy.safeDuration(durationMs)
        val safePosition = HistoryPolicy.safePosition(positionMs, safeDuration)
        val others = read().filterNot { it.source.uri == source.uri }
        val completed = PlaybackResumePolicy.isCompleted(safePosition, safeDuration)
        val first = if (completed) emptyList() else listOf(Entry(
            source.copy(title = HistoryPolicy.safeTitle(source.title)), safePosition, safeDuration))
        write((first + others).take(20))
    }

    /** Forget saved progress only. Never revoke grants needed by other apps/sessions. */
    fun clear() {
        prefs.edit().remove("entries").apply()
    }

    private fun write(entries: List<Entry>) {
        val array = JSONArray()
        entries.take(20).forEach { entry ->
            val uri = entry.source.uri.toString()
            if (!HistoryPolicy.isPersistableDocumentUri(uri)) return@forEach
            val duration = HistoryPolicy.safeDuration(entry.durationMs)
            array.put(JSONObject().put("uri", uri)
                .put("title", HistoryPolicy.safeTitle(entry.source.title))
                .put("position", HistoryPolicy.safePosition(entry.positionMs, duration))
                .put("duration", duration))
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }
}
