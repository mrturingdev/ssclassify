package com.mrturingdev.ssclassify.telemetry

import com.mrturingdev.ssclassify.model.ImageCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryTest {

    private class FakeSink : TelemetrySink {
        var started: Triple<String?, Boolean, Boolean>? = null
        var running = false
        val logs = mutableListOf<Pair<String, Map<String, String>>>()

        override fun start(installId: String?, crashReports: Boolean, qualityStats: Boolean) {
            started = Triple(installId, crashReports, qualityStats)
            running = true
        }

        override fun stop() {
            running = false
        }

        override fun log(name: String, attributes: Map<String, String>) {
            logs += name to attributes
        }
    }

    private class MemoryStore : TelemetryStore {
        override var consent: TelemetryConsent? = null
        override var installId: String? = null
    }

    private var ids = 0
    private fun telemetry(sink: FakeSink, store: MemoryStore = MemoryStore()) =
        Telemetry(sink, store) { "id-${++ids}" }

    private val event = QualityEvent.CategoryCorrected(ImageCategory.Receipts, ImageCategory.Foods)

    @Test
    fun defaultsToCrashReportsOnlyAndAsksOnce() {
        val sink = FakeSink()
        val telemetry = telemetry(sink)
        assertTrue(telemetry.needsChoice)
        telemetry.start()
        assertEquals(Triple("id-1", true, false), sink.started)

        telemetry.record(event)
        assertEquals(emptyList(), sink.logs, "quality stats are opt-in")

        telemetry.updateConsent(TelemetryConsent.Default)
        assertFalse(telemetry.needsChoice)
    }

    @Test
    fun eachToggleCombinationSendsExactlyWhatItAllows() {
        for (crash in listOf(true, false)) for (quality in listOf(true, false)) {
            val sink = FakeSink()
            val telemetry = telemetry(sink)
            telemetry.updateConsent(TelemetryConsent(crash, quality))
            telemetry.record(event)

            val label = "crash=$crash quality=$quality"
            assertEquals(crash || quality, sink.running, label)
            if (sink.running) {
                assertEquals(crash, sink.started!!.second, label)
                assertEquals(quality, sink.started!!.third, label)
                assertEquals(crash, sink.started!!.first != null, "install ID only with crash reports: $label")
            }
            assertEquals(if (quality) 1 else 0, sink.logs.size, label)
        }
    }

    @Test
    fun turningEverythingOffStopsReportingAndForgetsTheInstallId() {
        val sink = FakeSink()
        val store = MemoryStore()
        val telemetry = telemetry(sink, store)
        telemetry.start()
        val first = store.installId

        telemetry.updateConsent(TelemetryConsent(crashReports = false, qualityStats = false))
        assertFalse(sink.running)
        assertNull(store.installId)

        telemetry.updateConsent(TelemetryConsent.Default)
        assertNotEquals(first, sink.started!!.first, "a fresh ID, so old and new reports can't be joined")
    }

    @Test
    fun installIdSurvivesRestarts() {
        val store = MemoryStore()
        telemetry(FakeSink(), store).start()
        val sink = FakeSink()
        telemetry(sink, store).start()
        assertEquals(store.installId, sink.started!!.first)
    }

    @Test
    fun eventsCarryOnlyEnumAndBucketLabels() {
        val sink = FakeSink()
        val telemetry = telemetry(sink)
        telemetry.updateConsent(TelemetryConsent(crashReports = false, qualityStats = true))
        telemetry.record(
            QualityEvent.ScanFinished(
                screenshots = CountBucket.of(309),
                duration = DurationBucket.of(240_000),
                ocrFailures = CountBucket.of(0),
                aiCore = AiCoreState.Unsupported,
                untitled = ShareBucket.of(9, 309),
            ),
        )
        assertEquals(
            "scan_finished" to mapOf(
                "screenshots" to "101-1000",
                "duration" to "1-5m",
                "ocr_failures" to "0",
                "aicore" to "Unsupported",
                "untitled" to "1-10%",
            ),
            sink.logs.single(),
        )
    }

    @Test
    fun bucketEdges() {
        assertEquals(listOf("0", "1-10", "1-10", "11-100", "1000+"), listOf(0, 1, 10, 11, 1001).map { CountBucket.of(it).label })
        assertEquals(listOf("<10s", "10-60s", "15m+"), listOf(9_999L, 10_000L, 900_000L).map { DurationBucket.of(it).label })
        assertEquals(listOf("0%", "1-10%", "11-50%", "51-100%"), listOf(0, 1, 5, 6).map { ShareBucket.of(it, 10).label })
    }
}
