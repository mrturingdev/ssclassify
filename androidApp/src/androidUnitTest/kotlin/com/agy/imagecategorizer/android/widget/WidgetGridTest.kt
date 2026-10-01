package com.agy.imagecategorizer.android.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetGridTest {
    @Test
    fun gridFollowsLauncherCells() {
        assertEquals(4, gridFor(110, 110).count) // 2x2
        assertEquals(9, gridFor(180, 180).count) // 3x3
        assertEquals(9, gridFor(320, 250).count) // 4x4
        assertEquals(WidgetGrid(3, 2), gridFor(250, 110)) // 4x2
        assertEquals(WidgetGrid(2, 3), gridFor(110, 250)) // 2x4
        assertEquals(6, gridFor(0, 0).count) // options not reported yet
    }
}
