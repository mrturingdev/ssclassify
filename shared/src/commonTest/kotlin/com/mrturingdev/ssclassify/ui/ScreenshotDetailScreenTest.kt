package com.mrturingdev.ssclassify.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenshotDetailScreenTest {

    @Test
    fun formatsEpochDateHandlesZeroOrNegative() {
        assertEquals("Unknown", formatEpochDate(0L))
        assertEquals("Unknown", formatEpochDate(-1000L))
    }

    @Test
    fun formatsEpochDateCalculatesCorrectUtcDate() {
        // 1000L is 1 second after epoch
        assertEquals("1970-01-01 00:00 UTC", formatEpochDate(1000L))

        // 86400 * 1000L is day 1 (1970-01-02)
        assertEquals("1970-01-02 00:00 UTC", formatEpochDate(86400_000L))

        // 1700000000 seconds = 2023-11-14 22:13:20 UTC
        val millis = 1700000000_000L
        val formatted = formatEpochDate(millis)
        assertEquals("2023-11-14 22:13 UTC", formatted)
    }

    @Test
    fun formatsEpochDateHandlesLeapYears() {
        // 2024 is a leap year. Feb 29 2024:
        // 1709164800 seconds = 2024-02-29 00:00:00 UTC
        val leapDayMillis = 1709164800_000L
        assertEquals("2024-02-29 00:00 UTC", formatEpochDate(leapDayMillis))

        // 2024-03-01:
        val marchFirstMillis = leapDayMillis + 86400_000L
        assertEquals("2024-03-01 00:00 UTC", formatEpochDate(marchFirstMillis))
    }
}
