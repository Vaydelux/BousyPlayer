package com.vaydelux.bousyplayer.data

import android.content.Context
import android.net.Uri
import com.vaydelux.bousyplayer.model.MediaSource
import org.json.JSONArray
import org.json.JSONObject
import com.vaydelux.bousyplayer.engine.PlaybackResumePolicy

/** Stores only user-selected SAF document URIs; never records signed stream URLs. */
class RecentStore(context: Context) {
    private val prefsContext = context.applicationContext
    private val prefs = prefsContext.getSharedPreferences("recent_media_v1", Context.MODE_PRIVATE)

    data class Entry(val source: MediaSource, val positionMs: Long, val durationMs: Long)

    fun read(): List<Entry> = runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = item.optString("uri", "")
            if (uri.isBlank() || Uri.parse(uri).scheme != "content") return@mapNotNull null
            Entry(
                MediaSource(Uri.parse(uri), item.optString("title", "Video"), true),
                item.optLong("position", 0L), item.optLong("duration", 0L)
            )
        }
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
        if (!source.isDocument || source.uri.scheme != "content") return
        val others = read().filterNot { it.source.uri == source.uri }
        val completed = PlaybackResumePolicy.isCompleted(positionMs, durationMs)
        val first = if (completed) emptyList() else listOf(Entry(source, positionMs, durationMs))
        write((first + others).take(20))
    }

    private fun write(entries: List<Entry>) {
        val array = JSONArray()
        entries.take(20).forEach { entry ->
            array.put(JSONObject().put("uri", entry.source.uri.toString())
                .put("title", entry.source.title)
                .put("position", entry.positionMs)
                .put("duration", entry.durationMs))
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }
}
