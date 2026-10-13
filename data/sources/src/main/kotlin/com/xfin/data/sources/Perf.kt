package com.xfin.data.sources

import android.util.Log

/** Timing logs for the expensive paths; `adb logcat -s xFIN-perf` to read them. */
object Perf {
    private const val TAG = "xFIN-perf"

    inline fun <T> time(label: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            log("$label: ${(System.nanoTime() - start) / 1_000_000} ms")
        }
    }

    fun log(message: String) {
        Log.i(TAG, message)
    }
}
