package com.agy.imagecategorizer.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMaterial3Api::class)
class CollapsingHeaderTest {

    // A 50px app bar above a 100px header.
    private fun state() = CollapsingHeaderState(TopAppBarState(-50f, 0f, 0f)).apply { headerHeightPx = 100 }

    private fun CollapsingHeaderState.scroll(dy: Float): Float =
        connection.onPreScroll(Offset(0f, dy), NestedScrollSource.UserInput).y

    @Test
    fun scrollingDownHidesTheBarThenTheHeaderThenScrollsTheList() {
        val state = state()
        assertEquals(-30f, state.scroll(-30f))
        assertEquals(-30f, state.bar.heightOffset)
        assertEquals(0f, state.headerOffsetPx)

        assertEquals(-60f, state.scroll(-60f))
        assertEquals(-50f, state.bar.heightOffset)
        assertEquals(-40f, state.headerOffsetPx)

        assertEquals(-60f, state.scroll(-500f)) // only what is left of the header
        assertEquals(-100f, state.headerOffsetPx)
        assertEquals(0f, state.scroll(-50f)) // all hidden: the list scrolls
    }

    @Test
    fun anyScrollUpBringsTheBlockBackBeforeTheList() {
        val state = state()
        state.scroll(-150f)
        assertEquals(120f, state.scroll(120f))
        assertEquals(0f, state.headerOffsetPx)
        assertEquals(-30f, state.bar.heightOffset)

        assertEquals(30f, state.scroll(500f))
        assertEquals(0f, state.bar.heightOffset)
        assertEquals(0f, state.scroll(20f)) // all shown: the list scrolls
    }

    @Test
    fun clampsWhenTheHeaderShrinksWhileHidden() {
        val state = state()
        state.scroll(-150f)
        state.headerHeightPx = 40 // e.g. a filter row disappeared
        assertEquals(10f, state.scroll(10f))
        assertEquals(-30f, state.headerOffsetPx)
    }
}

class HeaderSlotsTest {
    @Test
    fun topCollapsesBeforeBottom() {
        assertEquals(HeaderSlots(topShown = 60, bottomShown = 40), headerSlots(0, 60, 40))
        assertEquals(HeaderSlots(topShown = 10, bottomShown = 40), headerSlots(50, 60, 40))
        assertEquals(HeaderSlots(topShown = 0, bottomShown = 15), headerSlots(85, 60, 40))
        assertEquals(HeaderSlots(topShown = 0, bottomShown = 0), headerSlots(500, 60, 40))
    }
}
