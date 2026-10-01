import SwiftUI
import WidgetKit
import Shared

@main
struct iOSApp: App {
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
