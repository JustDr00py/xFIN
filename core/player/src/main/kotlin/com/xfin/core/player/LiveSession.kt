package com.xfin.core.player

import androidx.media3.common.util.UnstableApi
import com.xfin.core.model.ChannelFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The live channel being watched, kept alive across the player and the guide so Back from
 * fullscreen leaves the stream playing in the guide's preview (as TiviMate does), and returning
 * to fullscreen needs no reconnect. Ended when the user leaves both screens.
 */
@UnstableApi
@Singleton
class LiveSession @Inject internal constructor(
    private val players: Provider<LivePlayer>,
) {
    data class Session(val channelId: Long, val filter: ChannelFilter, val catchup: Boolean)

    private var livePlayer: LivePlayer? = null

    private val _current = MutableStateFlow<Session?>(null)
    /** What is on screen; null when nothing plays. */
    val current: StateFlow<Session?> = _current.asStateFlow()

    /** The session's player, created on first use. */
    fun player(): LivePlayer = livePlayer ?: players.get().also { livePlayer = it }

    fun update(channelId: Long, filter: ChannelFilter, catchup: Boolean) {
        _current.value = Session(channelId, filter, catchup)
    }

    /** Stops playback and frees the player (and the provider connection). */
    fun end() {
        _current.value = null
        livePlayer?.release()
        livePlayer = null
    }
}
