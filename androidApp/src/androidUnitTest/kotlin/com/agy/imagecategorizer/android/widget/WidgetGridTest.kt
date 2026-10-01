package com.agy.imagecategorizer.android.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetGridTest {
    @Test
    fun gridKeepsTilesCloseToSquare() {
        assertEquals(WidgetGrid(4, 2), gridFor(360, 234)) // wide 3x3 on a Pixel launcher: 90x99dp tiles
        assertEquals(WidgetGrid(3, 3), gridFor(250, 380)) // tall widget
        assertEquals(WidgetGrid(3, 3), gridFor(360, 400)) // large square
        assertEquals(WidgetGrid(2, 2), gridFor(170, 170)) // 2x2 launcher cells
        assertEquals(WidgetGrid(3, 3), gridFor(0, 0)) // options not reported yet
    }

    @Test
    fun tooShortForCaptionsFallsBackToTheSquarestRow() {
        assertEquals(WidgetGrid(4, 1), gridFor(360, 90)) // 4x1 launcher cells
    }
}
