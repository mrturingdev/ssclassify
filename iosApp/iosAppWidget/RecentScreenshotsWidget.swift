import SwiftUI
import WidgetKit

// Tokens from .design/DESIGN.md: spacing.xxs gutter, spacing.xs padding,
// rounded.sm inline imagery, parchment / tile-1 canvases, hairline / tile-2 placeholders.
private enum Tokens {
    static let gutter: CGFloat = 4
    static let padding: CGFloat = 8
    static let radius: CGFloat = 8
    static let columns = 3
    static let maxItems = 9
    static let canvas = Color(light: 0xF5F5F7, dark: 0x272729)
    static let placeholder = Color(light: 0xE0E0E0, dark: 0x2A2A2C)
    static let ink = Color(light: 0x1D1D1F, dark: 0xFFFFFF)
}

struct RecentScreenshotsEntry: TimelineEntry {
    enum Content {
        case placeholder
        case needsAccess
        case images([UIImage])
    }

    let date: Date
    let content: Content
}

struct RecentScreenshotsProvider: TimelineProvider {

    // Placeholder and snapshot run on every gallery swipe: no Photos, no disk.
    func placeholder(in context: Context) -> RecentScreenshotsEntry {
        RecentScreenshotsEntry(date: Date(), content: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (RecentScreenshotsEntry) -> Void) {
        completion(placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<RecentScreenshotsEntry>) -> Void) {
        let now = Date()
        let next = now.addingTimeInterval(30 * 60)
        guard ScreenshotLibrary.hasAccess else {
            completion(Timeline(entries: [RecentScreenshotsEntry(date: now, content: .needsAccess)], policy: .after(next)))
            return
        }
        // ponytail: assumes a 3x display; 2x devices get a slightly larger image that the system downsamples
        let pixels = cellSide(in: context.displaySize) * 3
        Task {
            var images: [UIImage] = []
            for asset in ScreenshotLibrary.recent(limit: Tokens.maxItems) {
                if let image = await ScreenshotLibrary.thumbnail(for: asset, pixels: pixels) {
                    images.append(image)
                }
            }
            completion(Timeline(entries: [RecentScreenshotsEntry(date: now, content: .images(images))], policy: .after(next)))
        }
    }

    /// Point size of one grid cell, matching RecentScreenshotsView's layout.
    private func cellSide(in size: CGSize) -> CGFloat {
        let side = min(size.width, size.height) - 2 * Tokens.padding
        let gaps = CGFloat(Tokens.columns - 1) * Tokens.gutter
        return max((side - gaps) / CGFloat(Tokens.columns), 1)
    }
}

struct RecentScreenshotsView: View {
    let entry: RecentScreenshotsEntry

    var body: some View {
        content
            .padding(Tokens.padding)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .widgetBackground(Tokens.canvas)
            .widgetURL(URL(string: "imagecategorizer://recent"))
    }

    @ViewBuilder private var content: some View {
        switch entry.content {
        case .placeholder:
            grid(Array(repeating: nil, count: Tokens.maxItems))
        case .needsAccess:
            message("Open S.S. Classify to allow Photos access.")
        case .images(let images) where images.isEmpty:
            message("No screenshots yet.")
        case .images(let images):
            grid(images.map { Optional($0) })
        }
    }

    private func grid(_ images: [UIImage?]) -> some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: Tokens.gutter), count: Tokens.columns)
        return LazyVGrid(columns: columns, spacing: Tokens.gutter) {
            ForEach(images.indices, id: \.self) { index in
                Tokens.placeholder
                    .aspectRatio(1, contentMode: .fit)
                    .overlay {
                        if let image = images[index] {
                            Image(uiImage: image).resizable().scaledToFill()
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: Tokens.radius, style: .continuous))
            }
        }
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(.footnote)
            .foregroundColor(Tokens.ink)
            .multilineTextAlignment(.center)
    }
}

@main
struct RecentScreenshotsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "RecentScreenshotsWidget", provider: RecentScreenshotsProvider()) { entry in
            RecentScreenshotsView(entry: entry)
        }
        .configurationDisplayName("Recent Screenshots")
        .description("Your newest screenshots at a glance.")
        // The 3-column grid needs a square-ish canvas; medium is too short for 3 rows.
        .supportedFamilies([.systemSmall, .systemLarge])
        .contentMarginsDisabled()
    }
}

private extension View {
    /// containerBackground on iOS 17+, plain background on 15/16.
    @ViewBuilder func widgetBackground(_ color: Color) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }
}

private extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((hex >> 16) & 0xFF) / 255,
                green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255,
                alpha: 1
            )
        })
    }
}
