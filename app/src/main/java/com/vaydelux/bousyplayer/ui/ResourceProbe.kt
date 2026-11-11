package com.vaydelux.bousyplayer.ui

import android.os.Debug
import java.io.File

/**
 * Explicitly sampled in-process memory/descriptor counts. No filenames,
 * descriptor link targets, media URIs, device IDs, or account data are read,
 * stored, transmitted, or logged. OS counters may be unavailable on some TVs.
 */
data class ResourceSample(val javaHeapMiB: Long, val nativeHeapMiB: Long, val openFds: Int?)

object ResourceProbe {
    private const val MiB = 1024L * 1024L

    fun sample(): ResourceSample {
        val runtime = Runtime.getRuntime()
        val javaHeap = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val nativeHeap = runCatching { Debug.getNativeHeapAllocatedSize() }
            .getOrDefault(0L).coerceAtLeast(0L)
        // Count entries only; never inspect procfs link targets or names.
        val fdCount = runCatching { File("/proc/self/fd").list()?.size }
            .getOrNull()?.coerceIn(0, 100_000)
        return ResourceSample(javaHeap / MiB, nativeHeap / MiB, fdCount)
    }
}
