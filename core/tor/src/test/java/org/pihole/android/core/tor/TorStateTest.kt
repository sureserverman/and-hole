package org.pihole.android.core.tor

import org.junit.Assert.assertTrue
import org.junit.Test

class TorStateTest {

    @Test
    fun bootstrapTransitions() {
        var s: TorState = TorState.Stopped
        s = TorState.Starting
        assertTrue(s is TorState.Starting)
        s = TorState.Ready
        assertTrue(s is TorState.Ready)
        s = TorState.Failed("x")
        assertTrue(s is TorState.Failed)
    }
}
