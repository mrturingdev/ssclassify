package com.mrturingdev.ssclassify.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals

class CrashRedactorTest {

    private fun redact(text: String) = CrashRedactor.redact(text)

    @Test
    fun screenshotFileNamesThatNameTheirAppAreRemoved() {
        assertEquals(
            "Failed to decode <redacted>",
            redact("Failed to decode Screenshot_2026-09-06-17-38-19-924_com.f1soft.citizensmobilebanking.jpg"),
        )
    }

    @Test
    fun urisAndPathsAreRemovedWhole() {
        assertEquals("open failed: <redacted>", redact("open failed: content://media/external/images/media/1000049696"))
        assertEquals("ENOENT <redacted>", redact("ENOENT /storage/emulated/0/Pictures/Screenshots/a.png"))
    }

    @Test
    fun quotedValuesAndLongNumbersAreRemoved() {
        assertEquals("constraint failed on <redacted>", redact("constraint failed on 'Invoice 42'"))
        assertEquals("Transaction <redacted> failed", redact("Transaction 122226371 failed"))
    }

    @Test
    fun usefulDiagnosticsSurvive() {
        val message = "no such column: screenshot.title (code 1 SQLITE_ERROR)"
        assertEquals(message, redact(message))
        assertEquals("Index 3 out of bounds for length 2", redact("Index 3 out of bounds for length 2"))
    }
}
