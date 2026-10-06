package com.mrturingdev.ssclassify.telemetry

import com.mrturingdev.ssclassify.model.ImageCategory
import kotlin.random.Random

/**
 * What the user agreed to send. Crash reports are on by default; quality
 * stats are opt-in. Neither ever carries screenshot content.
 */
data class TelemetryConsent(val crashReports: Boolean, val qualityStats: Boolean) {
    val any: Boolean get() = crashReports || qualityStats

    companion object {
        val Default = TelemetryConsent(crashReports = true, qualityStats = false)
    }
}

/** Where consent and the crash-report install ID persist. */
interface TelemetryStore {
    /** Null until the user makes a choice, so the app knows to ask. */
    var consent: TelemetryConsent?
    var installId: String?
}

/** The reporting backend (Sentry); consent decisions are made by [Telemetry], not here. */
interface TelemetrySink {
    /**
     * Starts or reconfigures reporting. Crash events go out only when
     * [crashReports]; logs only when [qualityStats]. [installId] is set on
     * crash events only, never on logs.
     */
    fun start(installId: String?, crashReports: Boolean, qualityStats: Boolean)

    /** Stops all reporting and deletes anything queued offline. */
    fun stop()

    fun log(name: String, attributes: Map<String, String>)
}

/**
 * The one place that decides what leaves the device. Callers can only record
 * [QualityEvent]s, which are built from enums and buckets, so there is no
 * string parameter for screenshot text to leak through.
 */
class Telemetry(
    private val sink: TelemetrySink,
    private val store: TelemetryStore,
    private val newInstallId: () -> String = ::randomInstallId,
) {
    val consent: TelemetryConsent get() = store.consent ?: TelemetryConsent.Default

    /** True until the user has chosen, so the opt-in card can be shown once. */
    val needsChoice: Boolean get() = store.consent == null

    /** Call once at launch. */
    fun start() = apply()

    fun updateConsent(consent: TelemetryConsent) {
        store.consent = consent
        // A new ID after crash reports are switched back on, so old and new reports can't be joined.
        if (!consent.crashReports) store.installId = null
        apply()
    }

    fun record(event: QualityEvent) {
        if (consent.qualityStats) sink.log(event.name, event.attributes())
    }

    private fun apply() {
        val consent = consent
        if (!consent.any) {
            sink.stop()
            return
        }
        val installId = if (consent.crashReports) store.installId ?: newInstallId().also { store.installId = it } else null
        sink.start(installId, consent.crashReports, consent.qualityStats)
    }
}

private fun randomInstallId(): String =
    Random.nextBytes(16).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/** Opt-in quality events. Adding one means adding it here, in code review. */
sealed class QualityEvent(val name: String) {
    abstract fun attributes(): Map<String, String>

    class ScanFinished(
        val screenshots: CountBucket,
        val duration: DurationBucket,
        val ocrFailures: CountBucket,
        val aiCore: AiCoreState,
        val untitled: ShareBucket,
    ) : QualityEvent("scan_finished") {
        override fun attributes() = mapOf(
            "screenshots" to screenshots.label,
            "duration" to duration.label,
            "ocr_failures" to ocrFailures.label,
            "aicore" to aiCore.name,
            "untitled" to untitled.label,
        )
    }

    class CategoryCorrected(val from: ImageCategory, val to: ImageCategory) : QualityEvent("category_corrected") {
        override fun attributes() = mapOf("from" to from.name, "to" to to.name)
    }

    object ReanalyzeAllUsed : QualityEvent("reanalyze_all_used") {
        override fun attributes() = emptyMap<String, String>()
    }
}

/** On-device Gemini Nano availability; [NotOnPlatform] on iOS. */
enum class AiCoreState { Ready, Downloading, Unavailable, Unsupported, Error, NotOnPlatform }

/** Counts in coarse buckets, so a small library can't be fingerprinted by its exact size. */
enum class CountBucket(val label: String) {
    None("0"), UpTo10("1-10"), UpTo100("11-100"), UpTo1000("101-1000"), Over1000("1000+");

    companion object {
        fun of(count: Int) = when {
            count <= 0 -> None
            count <= 10 -> UpTo10
            count <= 100 -> UpTo100
            count <= 1000 -> UpTo1000
            else -> Over1000
        }
    }
}

enum class DurationBucket(val label: String) {
    Under10s("<10s"), Under1m("10-60s"), Under5m("1-5m"), Under15m("5-15m"), Over15m("15m+");

    companion object {
        fun of(millis: Long) = when {
            millis < 10_000 -> Under10s
            millis < 60_000 -> Under1m
            millis < 300_000 -> Under5m
            millis < 900_000 -> Under15m
            else -> Over15m
        }
    }
}

enum class ShareBucket(val label: String) {
    None("0%"), UpTo10("1-10%"), UpTo50("11-50%"), Over50("51-100%");

    companion object {
        fun of(part: Int, total: Int): ShareBucket {
            if (total <= 0 || part <= 0) return None
            val percent = part * 100.0 / total
            return when {
                percent <= 10 -> UpTo10
                percent <= 50 -> UpTo50
                else -> Over50
            }
        }
    }
}
