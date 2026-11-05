package com.xfin.core.player

import com.xfin.core.model.ChannelFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveSessionTest {

    @Test
    fun `end clears what is playing`() {
        val session = LiveSession { error("no player needed") }
        session.update(channelId = 7, filter = ChannelFilter.All, catchup = false)
        assertEquals(7L, session.current.value?.channelId)
        session.end()
        assertNull(session.current.value)
    }
}
