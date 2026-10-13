package com.xfin.core.model

/** A guide (EPG) entry. Times are epoch milliseconds. */
data class Program(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val iconUrl: String? = null,
    val episode: String? = null,
) {
    val durationMs: Long get() = end - start

    fun isAiring(now: Long) = now in start until end

    fun progress(now: Long): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    fun slot() = ProgramSlot(title, start, end)
}

data class NowNext(val now: Program?, val next: Program?)

data class EpgStatus(
    val updatedAt: Long?,
    val programCount: Int,
    val error: String?,
)

sealed interface EpgRefreshState {
    data object Idle : EpgRefreshState
    data class Running(val sourceId: Long, val programs: Int) : EpgRefreshState
}
