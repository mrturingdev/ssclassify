import Foundation

/// What the app last published for the widget, written by WidgetSnapshot.ios.kt
/// (WidgetFeed in Kotlin decides categories and captions); keep the two in step.
struct WidgetSnapshot: Decodable {
    struct Item: Decodable {
        let id: String
        let badge: String?
        let summary: String?
    }

    struct Category: Decodable {
        let key: String
        let label: String
        let count: Int
        let items: [Item]
    }

    let categories: [Category]

    static let appGroup = "group.com.agy.imagecategorizer"
    private static let fileName = "widget-snapshot.json"
    private static let selectionKey = "widget.category"

    /// Nil until the app has analyzed the library once.
    static func load() -> WidgetSnapshot? {
        guard let url = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appendingPathComponent(fileName),
            let data = try? Data(contentsOf: url)
        else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    /// The chosen category; All when none was chosen or it has since emptied.
    var selected: Category? {
        let key = Self.defaults?.string(forKey: Self.selectionKey)
        return categories.first { $0.key == key } ?? categories.first
    }

    /// Moves the selection by [step], wrapping around, like WidgetFeed.step in Kotlin.
    func cycle(by step: Int) {
        guard !categories.isEmpty else { return }
        let current = categories.firstIndex { $0.key == selected?.key } ?? 0
        let next = ((current + step) % categories.count + categories.count) % categories.count
        Self.defaults?.set(categories[next].key, forKey: Self.selectionKey)
    }

    // ponytail: one selection shared by every widget instance; per-instance needs an AppIntentConfiguration
    private static var defaults: UserDefaults? { UserDefaults(suiteName: appGroup) }
}
