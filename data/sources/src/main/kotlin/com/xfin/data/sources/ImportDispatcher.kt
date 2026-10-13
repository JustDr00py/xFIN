package com.xfin.data.sources

import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Background-priority threads for parsing playlists and guides. A 100MB guide keeps a core busy
 * for minutes; at normal priority that competes with rendering and makes scrolling stutter.
 */
internal val ImportDispatcher: CoroutineDispatcher = run {
    val count = AtomicInteger()
    Executors.newFixedThreadPool(2) { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "xfin-import-${count.incrementAndGet()}").apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
