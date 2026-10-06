package com.mrturingdev.ssclassify.telemetry

import com.mrturingdev.ssclassify.settings.loadString
import com.mrturingdev.ssclassify.settings.saveString

/** [TelemetryStore] over the app's persisted settings. */
class SettingsTelemetryStore : TelemetryStore {
    override var consent: TelemetryConsent?
        get() = loadString(KEY_CONSENT)?.split(',')?.takeIf { it.size == 2 }
            ?.let { (crash, quality) -> TelemetryConsent(crash.toBoolean(), quality.toBoolean()) }
        set(value) = saveString(KEY_CONSENT, value?.let { "${it.crashReports},${it.qualityStats}" })

    override var installId: String?
        get() = loadString(KEY_INSTALL_ID)
        set(value) = saveString(KEY_INSTALL_ID, value)

    override var pendingPrompt: PrivacyPrompt?
        get() = PrivacyPrompt.entries.firstOrNull { it.name == loadString(KEY_PROMPT) }
        set(value) = saveString(KEY_PROMPT, value?.name)

    private companion object {
        const val KEY_CONSENT = "telemetry_consent"
        const val KEY_INSTALL_ID = "telemetry_install_id"
        const val KEY_PROMPT = "telemetry_prompt"
    }
}

/** Sends nothing: used until a backend is configured, and in builds without one. */
object NoopTelemetrySink : TelemetrySink {
    override fun start(installId: String?, crashReports: Boolean, qualityStats: Boolean) = Unit
    override fun stop() = Unit
    override fun log(name: String, attributes: Map<String, String>) = Unit
}
