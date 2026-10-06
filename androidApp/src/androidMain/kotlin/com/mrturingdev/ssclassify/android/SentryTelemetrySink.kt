package com.mrturingdev.ssclassify.android

import android.content.Context
import android.util.Log
import com.mrturingdev.ssclassify.telemetry.CrashRedactor
import com.mrturingdev.ssclassify.telemetry.TelemetrySink
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryAttribute
import io.sentry.SentryAttributes
import io.sentry.SentryEvent
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.logger.SentryLogParameters
import io.sentry.protocol.User
import java.io.File

/**
 * Sentry (EU region) behind [TelemetrySink]. It only applies what
 * [com.mrturingdev.ssclassify.telemetry.Telemetry] decided: crash events
 * need crash consent, logs need quality consent, and the install ID is set
 * as the user only for crash events. Every crash message is redacted, and
 * screenshots, view hierarchy, tracing and tap breadcrumbs are off.
 */
class SentryTelemetrySink(private val context: Context, private val dsn: String) : TelemetrySink {

    @Volatile private var crashReports = false
    @Volatile private var qualityStats = false
    private var started: Triple<String?, Boolean, Boolean>? = null

    private val cacheDir get() = File(context.cacheDir, "sentry")

    @Synchronized
    override fun start(installId: String?, crashReports: Boolean, qualityStats: Boolean) {
        val config = Triple(installId, crashReports, qualityStats)
        if (config == started) return
        if (started != null) Sentry.close()
        this.crashReports = crashReports
        this.qualityStats = qualityStats
        started = config

        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.cacheDirPath = cacheDir.absolutePath
            // `adb shell setprop log.tag.SentryTelemetry DEBUG` shows what is sent, for verification.
            options.isDebug = Log.isLoggable(TAG, Log.DEBUG)
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.tracesSampleRate = null
            options.isEnableAutoSessionTracking = crashReports
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableSystemEventBreadcrumbs = false
            options.isEnableNetworkEventBreadcrumbs = false
            options.isEnableAppComponentBreadcrumbs = false
            options.logs.isEnabled = qualityStats
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { crumb, _ ->
                crumb.takeIf { it.category in LIFECYCLE_CATEGORIES }
            }
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                if (this.crashReports) redact(event) else null
            }
            options.logs.beforeSend = SentryOptions.Logs.BeforeSendLogCallback { log ->
                if (!this.qualityStats) return@BeforeSendLogCallback null
                // Quality stats are unlinked: no user, and no device model beyond the OS version.
                log.attributes?.keys?.removeAll { key -> DROPPED_LOG_ATTRIBUTE_PREFIXES.any { key.startsWith(it) } }
                log
            }
        }
        Sentry.setUser(installId?.let { id -> User().apply { this.id = id } })
    }

    @Synchronized
    override fun stop() {
        if (started != null) Sentry.close()
        started = null
        crashReports = false
        qualityStats = false
        cacheDir.deleteRecursively()
    }

    override fun log(name: String, attributes: Map<String, String>) {
        if (!qualityStats) return
        val attrs = attributes.map { (key, value) -> SentryAttribute.stringAttribute(key, value) }
        Sentry.logger().log(
            SentryLogLevel.INFO,
            SentryLogParameters.create(SentryAttributes.of(*attrs.toTypedArray())),
            name,
        )
    }

    private fun redact(event: SentryEvent): SentryEvent {
        event.message?.let { message ->
            message.formatted = message.formatted?.let(CrashRedactor::redact)
            message.message = message.message?.let(CrashRedactor::redact)
            message.params = null
        }
        event.exceptions?.forEach { exception -> exception.value = exception.value?.let(CrashRedactor::redact) }
        event.breadcrumbs?.forEach { crumb: Breadcrumb -> crumb.message = crumb.message?.let(CrashRedactor::redact) }
        return event
    }

    private companion object {
        const val TAG = "SentryTelemetry"
        val LIFECYCLE_CATEGORIES = setOf("app.lifecycle", "ui.lifecycle")
        val DROPPED_LOG_ATTRIBUTE_PREFIXES = listOf("user.", "device.model", "device.brand", "device.family")
    }
}
