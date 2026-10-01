import AppIntents
import WidgetKit

/// The ‹ › buttons on the widget. Widget buttons need iOS 17; earlier versions show All.
@available(iOS 17.0, *)
struct CycleCategoryIntent: AppIntent {
    static var title: LocalizedStringResource = "Show another category"
    static var isDiscoverable = false

    @Parameter(title: "Step")
    var step: Int

    init() {}

    init(step: Int) {
        self.step = step
    }

    // WidgetKit reloads the widget's timeline after this returns.
    func perform() async throws -> some IntentResult {
        WidgetSnapshot.load()?.cycle(by: step)
        return .result()
    }
}
