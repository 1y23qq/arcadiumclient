// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.gui.ServerListDrag
import io.github.arcadiumclient.runtime.client.gui.ServerListDrag.Kind

class ServerListDragTest {
    @Test
    fun `a press only selects and small pointer motion remains a click`() {
        val drag = ServerListDrag<Any>()
        val entry = Any()
        assertFalse(drag.begin(entry, 0, 120, 50, 1))
        assertTrue(drag.begin(entry, 0, 120, 50, 0))
        assertTrue(drag.active())
        assertFalse(drag.dragging())
        assertEquals(Kind.CLICK, drag.release(123, 52, 0, true, 1000).kind())
        assertFalse(drag.active())
    }

    @Test
    fun `a second click joins only after release on the same row`() {
        val drag = ServerListDrag<Any>()
        val entry = Any()
        drag.begin(entry, 1, 120, 70, 0)
        assertEquals(Kind.CLICK, drag.release(120, 70, 1, true, 1000).kind())
        drag.begin(entry, 1, 120, 70, 0)
        assertTrue(drag.active())
        assertEquals(Kind.DOUBLE_CLICK, drag.release(120, 70, 1, true, 1100).kind())
    }

    @Test
    fun `dragging on a second press never joins and clears the double click history`() {
        val drag = ServerListDrag<Any>()
        val entry = Any()
        drag.begin(entry, 0, 120, 50, 0)
        drag.release(120, 50, 0, true, 1000)
        drag.begin(entry, 0, 120, 50, 0)
        drag.update(120, 100)
        assertEquals(Kind.DROP, drag.release(120, 100, 2, true, 1050).kind())
        drag.begin(entry, 2, 120, 100, 0)
        assertEquals(Kind.CLICK, drag.release(120, 100, 2, true, 1100).kind())
    }

    @Test
    fun `releasing outside the list or on a different row cancels a click`() {
        val drag = ServerListDrag<Any>()
        val entry = Any()
        drag.begin(entry, 0, 120, 50, 0)
        assertEquals(Kind.NONE, drag.release(120, 50, 0, false, 1000).kind())
        drag.begin(entry, 0, 120, 50, 0)
        assertEquals(Kind.NONE, drag.release(120, 51, 1, true, 1100).kind())
        drag.begin(entry, 0, 120, 50, 0)
        drag.update(120, 100)
        assertEquals(Kind.NONE, drag.release(120, 100, 2, false, 1150).kind())
    }

    @Test
    fun `a cancelled gesture cannot trigger a later join`() {
        val drag = ServerListDrag<Any>()
        val entry = Any()
        drag.begin(entry, 0, 120, 50, 0)
        drag.release(120, 50, 0, true, 1000)
        drag.begin(entry, 0, 120, 50, 0)
        drag.cancel()
        assertEquals(Kind.NONE, drag.release(120, 50, 0, true, 1100).kind())
        drag.begin(entry, 0, 120, 50, 0)
        assertEquals(Kind.CLICK, drag.release(120, 50, 0, true, 1150).kind())
    }

    @Test
    fun `scrolling offsets and list ends produce only saved server targets`() {
        assertEquals(0, ServerListDrag.targetIndex(2, 36, 0, 36, 5))
        assertEquals(3, ServerListDrag.targetIndex(108, 36, 36, 36, 5))
        assertEquals(4, ServerListDrag.targetIndex(1000, 36, 36, 36, 5))
        assertEquals(-1, ServerListDrag.targetIndex(100, 36, 0, 36, 0))
    }

    @Test
    fun `autoscroll uses elapsed time and stops away from the edges`() {
        assertEquals(0.0, ServerListDrag.scrollDelta(150, 32, 300, 16.0))
        assertTrue(ServerListDrag.scrollDelta(34, 32, 300, 16.0) < 0)
        assertTrue(ServerListDrag.scrollDelta(298, 32, 300, 16.0) > 0)
        assertEquals(ServerListDrag.scrollDelta(310, 32, 300, 16.0) * 2, ServerListDrag.scrollDelta(310, 32, 300, 32.0))
        assertEquals(18.0, ServerListDrag.scrollDelta(310, 32, 300, 10000.0), 1e-9)
    }

    @Test
    fun `moving a card preserves every other server in order`() {
        val servers = mutableListOf("A", "B", "C", "D")
        assertTrue(ServerListDrag.reorder(servers, 0, 3))
        assertEquals(listOf("B", "C", "D", "A"), servers)
        assertTrue(ServerListDrag.reorder(servers, 3, 1))
        assertEquals(listOf("B", "A", "C", "D"), servers)
        assertFalse(ServerListDrag.reorder(servers, 1, 1))
        assertFalse(ServerListDrag.reorder(servers, -1, 3))
        assertFalse(ServerListDrag.reorder(servers, 0, 4))
        assertEquals(listOf("B", "A", "C", "D"), servers)
    }
}
