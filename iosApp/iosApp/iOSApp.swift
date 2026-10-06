import Sentry
import SwiftUI
import WidgetKit
import Shared

@main
struct iOSApp: App {
    init() {
        #if DEBUG
        let reporting = false
        #else
        let reporting = !TelemetryConfig.shared.SENTRY_DSN.isEmpty
        #endif
        MainViewControllerKt.startTelemetry(
            sink: reporting ? SentryTelemetrySink(dsn: TelemetryConfig.shared.SENTRY_DSN) : NoopTelemetrySink.shared
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
                // Widget tile taps: imagecategorizer://screenshot?id=<PHAsset localIdentifier>
                .onOpenURL { url in
                    guard url.host == "screenshot",
                          let id = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                              .queryItems?.first(where: { $0.name == "id" })?.value
                    else { return }
                    MainViewControllerKt.openScreenshot(id: id)
                }
                // Posted by WidgetSnapshot.ios.kt; WidgetCenter has no Objective-C API for Kotlin to call.
                .onReceive(NotificationCenter.default.publisher(for: Notification.Name("SSWidgetSnapshotUpdated"))) { _ in
                    WidgetCenter.shared.reloadAllTimelines()
                }
        }
    }
}

/**
 * Sentry (EU region) behind the shared TelemetrySink. It only applies what the
 * shared Telemetry decided: crash events need crash consent, logs need quality
 * consent, and the install ID is set as the user only for crash events. Every
 * crash message is redacted by the shared CrashRedactor; screenshots, view
 * hierarchy, tracing and automatic breadcrumbs are off.
 */
final class SentryTelemetrySink: NSObject, TelemetrySink {
    private let dsn: String
    private var crashReports = false
    private var qualityStats = false
    private var started: (String?, Bool, Bool)?

    init(dsn: String) {
        self.dsn = dsn
    }

    func start(installId: String?, crashReports: Bool, qualityStats: Bool) {
        if let started, started == (installId, crashReports, qualityStats) { return }
        if started != nil { SentrySDK.close() }
        self.crashReports = crashReports
        self.qualityStats = qualityStats
        started = (installId, crashReports, qualityStats)

        SentrySDK.start { options in
            options.dsn = self.dsn
            // Launch with -SentryTelemetryDebug to see what is sent, for verification.
            options.debug = ProcessInfo.processInfo.arguments.contains("-SentryTelemetryDebug")
            options.sendDefaultPii = false
            options.attachScreenshot = false
            options.attachViewHierarchy = false
            options.tracesSampleRate = nil
            options.enableAutoPerformanceTracing = false
            options.enableAutoSessionTracking = crashReports
            // Session replay records the screen: never, whatever a future SDK default is.
            options.sessionReplay.sessionSampleRate = 0
            options.sessionReplay.onErrorSampleRate = 0
            options.enableAutoBreadcrumbTracking = false
            options.enableNetworkBreadcrumbs = false
            options.enableCaptureFailedRequests = false
            options.enableLogs = qualityStats
            options.beforeSend = { [weak self] event in
                guard let self, self.crashReports else { return nil }
                return Self.redact(event)
            }
            options.beforeSendLog = { [weak self] log in
                guard let self, self.qualityStats else { return nil }
                // Quality stats are unlinked: no user, and no device model beyond the OS version.
                log.attributes = log.attributes.filter { key, _ in
                    !(key.hasPrefix("user.") || key.hasPrefix("device.model") || key.hasPrefix("device.brand") || key.hasPrefix("device.family"))
                }
                return log
            }
        }
        SentrySDK.setUser(installId.map { User(userId: $0) })
    }

    func stop() {
        if started != nil { SentrySDK.close() }
        started = nil
        crashReports = false
        qualityStats = false
        // Delete anything queued offline.
        if let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first {
            try? FileManager.default.removeItem(at: caches.appendingPathComponent("io.sentry"))
        }
    }

    func log(name: String, attributes: [String: String]) {
        guard qualityStats else { return }
        SentrySDK.logger.info(name, attributes: attributes)
    }

    private static func redact(_ event: Event) -> Event {
        let redactor = CrashRedactor.shared
        if let message = event.message {
            event.message = SentryMessage(formatted: redactor.redact(message: message.formatted))
        }
        event.exceptions?.forEach { exception in
            if let value = exception.value { exception.value = redactor.redact(message: value) }
        }
        return event
    }
}
