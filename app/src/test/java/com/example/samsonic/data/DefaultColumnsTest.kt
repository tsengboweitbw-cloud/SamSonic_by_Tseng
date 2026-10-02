package com.example.samsonic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultColumnsTest {

    @Test
    fun testDefaultColumnsForWidthThresholds() {
        // <= 300 dp -> 2
        assertEquals(2, defaultColumnsForWidth(250f))
        assertEquals(2, defaultColumnsForWidth(300f))

        // <= 500 dp -> 3 (e.g. tablet ListPane on left side ~ 380dp-480dp)
        assertEquals(3, defaultColumnsForWidth(300.1f))
        assertEquals(3, defaultColumnsForWidth(412f))
        assertEquals(3, defaultColumnsForWidth(480f))
        assertEquals(3, defaultColumnsForWidth(500f))

        // <= 900 dp -> 4 (e.g. detail pane ~ 600dp-800dp or foldable open ~ 704dp)
        assertEquals(4, defaultColumnsForWidth(500.1f))
        assertEquals(4, defaultColumnsForWidth(704f))
        assertEquals(4, defaultColumnsForWidth(800f))
        assertEquals(4, defaultColumnsForWidth(900f))

        // > 900 dp -> 6 (e.g. full-screen tablet ~ 1000dp-1280dp)
        assertEquals(6, defaultColumnsForWidth(900.1f))
        assertEquals(6, defaultColumnsForWidth(1000f))
        assertEquals(6, defaultColumnsForWidth(1280f))
    }
}
