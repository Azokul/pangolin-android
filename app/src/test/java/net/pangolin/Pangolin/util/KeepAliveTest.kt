package net.pangolin.Pangolin.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAliveTest {
    @Test
    fun activeOnlyWhenEnabledAndWanted() {
        assertTrue(KeepAlive.isActive(enabled = true, wanted = true))
        assertFalse(KeepAlive.isActive(enabled = true, wanted = false))
        assertFalse(KeepAlive.isActive(enabled = false, wanted = true))
        assertFalse(KeepAlive.isActive(enabled = false, wanted = false))
    }
}
