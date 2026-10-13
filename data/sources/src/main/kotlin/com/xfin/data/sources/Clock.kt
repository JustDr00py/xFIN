package com.xfin.data.sources

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current time immediately and then at every wall-clock minute boundary. */
fun minuteTicks(): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(60_000 - now % 60_000)
    }
}
