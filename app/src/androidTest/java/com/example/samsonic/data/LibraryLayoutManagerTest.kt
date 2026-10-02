package com.example.samsonic.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLayoutManagerTest {

    private lateinit var manager: LibraryLayoutManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = LibraryLayoutManager(context)
    }

    @Test
    fun testDefaultColumnsBasedOnHorizontalDp() {
        // <= 300 dp -> 2 columns
        manager.setGridForm(GridForm.PHONE, width = 280f, height = 600f)
        assertEquals(2, manager.layouts.value[LibrarySection.ARTISTS]?.columns)
        assertEquals(2, manager.layouts.value[LibrarySection.ALBUMS]?.columns)

        manager.setGridForm(GridForm.PHONE, width = 300f, height = 600f)
        assertEquals(2, manager.layouts.value[LibrarySection.ARTISTS]?.columns)
        assertEquals(2, manager.layouts.value[LibrarySection.ALBUMS]?.columns)

        // <= 500 dp -> 3 columns
        manager.setGridForm(GridForm.PHONE, width = 360f, height = 800f)
        assertEquals(3, manager.layouts.value[LibrarySection.ARTISTS]?.columns)
        assertEquals(3, manager.layouts.value[LibrarySection.ALBUMS]?.columns)

        manager.setGridForm(GridForm.PHONE, width = 500f, height = 800f)
        assertEquals(3, manager.layouts.value[LibrarySection.ARTISTS]?.columns)
        assertEquals(3, manager.layouts.value[LibrarySection.ALBUMS]?.columns)

        // <= 900 dp -> 4 columns
        manager.setGridForm(GridForm.TABLET_PORTRAIT, width = 600f, height = 900f)
        assertEquals(4, manager.layouts.value[LibrarySection.ARTISTS]?.columns)
        assertEquals(4, manager.layouts.value[LibrarySection.ALBUMS]?.columns)

        manager.setGridForm(GridForm.TABLET_PORTRAIT, width = 900f, height = 1200f)
        assertEquals(4, manager.layouts.value[LibrarySection.ARTISTS]?.columns)
        assertEquals(4, manager.layouts.value[LibrarySection.ALBUMS]?.columns)

        // > 900 dp -> 6 columns
        manager.setGridForm(GridForm.TABLET_LANDSCAPE, width = 915f, height = 412f)
        assertEquals(6, manager.layouts.value[LibrarySection.ARTISTS]?.columns)
        assertEquals(6, manager.layouts.value[LibrarySection.ALBUMS]?.columns)

        manager.setGridForm(GridForm.TABLET_LANDSCAPE, width = 1280f, height = 800f)
        assertEquals(6, manager.layouts.value[LibrarySection.ARTISTS]?.columns)
        assertEquals(6, manager.layouts.value[LibrarySection.ALBUMS]?.columns)
    }
}
