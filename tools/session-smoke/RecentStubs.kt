package com.vaydelux.bousyplayer.data
import com.vaydelux.bousyplayer.model.MediaSource
class RecentStore {
    data class Entry(val source: MediaSource, val positionMs: Long, val durationMs: Long)
    private val items = linkedMapOf<String, Entry>()
    fun read(): List<Entry> = items.values.toList()
    fun update(source: MediaSource, positionMs: Long, durationMs: Long) {
        items[source.uri.toString()] = Entry(source, positionMs, durationMs)
    }
}
