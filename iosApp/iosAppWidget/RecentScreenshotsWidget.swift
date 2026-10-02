import SwiftUI
import WidgetKit

// Colors from .design/DESIGN.md and the app's grid card (ImageCard in HomeScreen.kt).
private enum Tokens {
    static let canvas = Color(light: 0xF5F5F7, dark: 0x272729)
    static let placeholder = Color(light: 0xE0E0E0, dark: 0x2A2A2C)
    static let ink = Color(light: 0x1D1D1F, dark: 0xFFFFFF)
    static let actionBlue = Color(light: 0x0066CC, dark: 0x0066CC)
}

/// Spacing per widget size. Large uses the design tokens (spacing.sm padding,
/// spacing.xs header gap, the app grid's 6pt gutter) with square tiles like the app.
/// Small trades a compact header and spacing.xs padding for four tiles that fill
/// the grid. Tile corners stay concentric with the widget's (~21.6pt minus padding).
private struct Layout {
    let columns: Int
    let padding: CGFloat
    let headerHeight: CGFloat
    let headerGap: CGFloat
    let gutter: CGFloat
    let titleSize: CGFloat
    let buttonSize: CGFloat
    let tileRadius: CGFloat
    let squareTiles: Bool

    static func of(_ family: WidgetFamily) -> Layout {
        family == .systemSmall
            ? Layout(columns: 2, padding: 8, headerHeight: 20, headerGap: 4, gutter: 4,
                     titleSize: 12, buttonSize: 20, tileRadius: 12, squareTiles: false)
            : Layout(columns: 3, padding: 12, headerHeight: 28, headerGap: 8, gutter: 6,
                     titleSize: 14, buttonSize: 28, tileRadius: 10, squareTiles: true)
    }

    /// The area below the header in a widget of [size].
    func gridArea(in size: CGSize) -> CGSize {
        CGSize(width: size.width - 2 * padding, height: size.height - 2 * padding - headerHeight - headerGap)
    }

    /// One tile of a full columns x columns grid in [grid]; square on large.
    /// Shared by the view and the thumbnail request.
    func tileSize(inGrid grid: CGSize) -> CGSize {
        let gaps = CGFloat(columns - 1) * gutter
        let width = max(((grid.width - gaps) / CGFloat(columns)).rounded(.down), 1)
        let height = max(((grid.height - gaps) / CGFloat(columns)).rounded(.down), 1)
        if squareTiles {
            let side = min(width, height)
            return CGSize(width: side, height: side)
        }
        return CGSize(width: width, height: height)
    }
}

struct RecentScreenshotsEntry: TimelineEntry {
    struct Tile {
        let id: String
        let image: UIImage?
        let badge: String?
        let summary: String?
    }

    enum Content {
        case placeholder
        case needsAccess
        /// [title] is nil before the app has categorized anything.
        case tiles(title: String, canCycle: Bool, tiles: [Tile])
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
        let layout = Layout.of(context.family)
        let limit = layout.columns * layout.columns
        let tile = layout.tileSize(inGrid: layout.gridArea(in: context.displaySize))
        // ponytail: assumes a 3x display; 2x devices get a slightly larger image that the system downsamples
        let pixels = max(tile.width, tile.height) * 3

        // The app's snapshot carries categories and captions; without one (never scanned)
        // fall back to the newest screenshots straight from Photos.
        let snapshot = WidgetSnapshot.load()
        let category = snapshot?.selected
        let items = category.map { Array($0.items.prefix(limit)) }
        Task {
            var tiles: [RecentScreenshotsEntry.Tile] = []
            if let items {
                let assets = ScreenshotLibrary.assets(ids: items.map(\.id))
                for item in items {
                    guard let asset = assets.first(where: { $0.localIdentifier == item.id }) else { continue }
                    let image = await ScreenshotLibrary.thumbnail(for: asset, pixels: pixels)
                    tiles.append(.init(id: item.id, image: image, badge: item.badge, summary: item.summary))
                }
            } else {
                for asset in ScreenshotLibrary.recent(limit: limit) {
                    let image = await ScreenshotLibrary.thumbnail(for: asset, pixels: pixels)
                    tiles.append(.init(id: asset.localIdentifier, image: image, badge: nil, summary: nil))
                }
            }
            let title = category.map { "\($0.label) (\($0.count))" } ?? "Recent Screenshots"
            let canCycle = (snapshot?.categories.count ?? 0) > 1
            let content = RecentScreenshotsEntry.Content.tiles(title: title, canCycle: canCycle, tiles: tiles)
            completion(Timeline(entries: [RecentScreenshotsEntry(date: now, content: content)], policy: .after(next)))
        }
    }
}

struct RecentScreenshotsView: View {
    let entry: RecentScreenshotsEntry
    @Environment(\.widgetFamily) private var family
    private var layout: Layout { Layout.of(family) }

    var body: some View {
        content
            .padding(layout.padding)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .widgetBackground(Tokens.canvas)
            .widgetURL(widgetURL)
    }

    @ViewBuilder private var content: some View {
        switch entry.content {
        case .placeholder:
            VStack(spacing: layout.headerGap) {
                header(title: "All", canCycle: false)
                grid(Array(repeating: nil, count: layout.columns * layout.columns))
            }
        case .needsAccess:
            message("Open S.S. Classify to allow Photos access.")
        case .tiles(_, _, let tiles) where tiles.isEmpty:
            message("No screenshots yet.")
        case .tiles(let title, let canCycle, let tiles):
            VStack(spacing: layout.headerGap) {
                header(title: title, canCycle: canCycle)
                grid(tiles.map { Optional($0) })
            }
        }
    }

    /// Small widgets ignore per-tile links, so a tap there opens the first tile.
    private var widgetURL: URL? {
        if family == .systemSmall, case .tiles(_, _, let tiles) = entry.content, let first = tiles.first {
            return detailURL(first.id)
        }
        return URL(string: "imagecategorizer://recent")
    }

    private func header(title: String, canCycle: Bool) -> some View {
        HStack {
            cycleButton(step: -1, symbol: "chevron.left", visible: canCycle)
            Spacer(minLength: 0)
            Text(title)
                .font(.system(size: layout.titleSize, weight: .semibold))
                .foregroundColor(Tokens.ink)
                .lineLimit(1)
            Spacer(minLength: 0)
            cycleButton(step: 1, symbol: "chevron.right", visible: canCycle)
        }
        .frame(height: layout.headerHeight)
    }

    @ViewBuilder
    private func cycleButton(step: Int, symbol: String, visible: Bool) -> some View {
        if #available(iOSApplicationExtension 17.0, *), visible {
            Button(intent: CycleCategoryIntent(step: step)) {
                Image(systemName: symbol)
                    .font(.system(size: layout.titleSize - 1, weight: .semibold))
                    .foregroundColor(Tokens.ink)
                    .frame(width: layout.buttonSize, height: layout.buttonSize)
            }
            .buttonStyle(.plain)
        } else {
            // Keeps the title centered where buttons cannot exist.
            Color.clear.frame(width: layout.buttonSize, height: layout.buttonSize)
        }
    }

    /// Fixed-size tiles so a full grid always fits below the header; fewer tiles
    /// leave the rest of the grid empty rather than stretching.
    private func grid(_ tiles: [RecentScreenshotsEntry.Tile?]) -> some View {
        GeometryReader { proxy in
            let size = layout.tileSize(inGrid: proxy.size)
            let items = Array(repeating: GridItem(.fixed(size.width), spacing: layout.gutter), count: layout.columns)
            LazyVGrid(columns: items, spacing: layout.gutter) {
                ForEach(tiles.indices, id: \.self) { index in
                    if let tile = tiles[index], family != .systemSmall {
                        Link(destination: detailURL(tile.id)) { tileView(tile, size: size) }
                    } else {
                        tileView(tiles[index], size: size)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }

    /// Mirrors ImageCard: center crop, bottom gradient, sub-category badge, two-line summary.
    private func tileView(_ tile: RecentScreenshotsEntry.Tile?, size: CGSize) -> some View {
        Tokens.placeholder
            .frame(width: size.width, height: size.height)
            .overlay {
                if let image = tile?.image {
                    Image(uiImage: image).resizable().scaledToFill()
                }
            }
            .overlay(alignment: .bottomLeading) {
                if let tile, tile.badge != nil || tile.summary != nil {
                    caption(tile)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: layout.tileRadius, style: .continuous))
    }

    private func caption(_ tile: RecentScreenshotsEntry.Tile) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            if let badge = tile.badge {
                // Wraps rather than truncating, so the whole sub-category always shows.
                Text(badge)
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 1)
                    .background(RoundedRectangle(cornerRadius: 4).fill(Tokens.actionBlue.opacity(0.9)))
            }
            // Small tiles are too narrow for a summary; the badge carries the category.
            if let summary = tile.summary, family != .systemSmall {
                Text(summary)
                    .font(.system(size: 9))
                    .foregroundColor(.white)
                    .lineLimit(2)
            }
        }
        .padding(.horizontal, 5)
        .padding(.top, 10)
        .padding(.bottom, 5)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LinearGradient(colors: [.clear, .black.opacity(0.8)], startPoint: .top, endPoint: .bottom))
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(.footnote)
            .foregroundColor(Tokens.ink)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// Opens the app on this screenshot's detail page; see onOpenURL in iOSApp.swift.
    private func detailURL(_ id: String) -> URL {
        var components = URLComponents()
        components.scheme = "imagecategorizer"
        components.host = "screenshot"
        components.queryItems = [URLQueryItem(name: "id", value: id)]
        return components.url ?? URL(string: "imagecategorizer://recent")!
    }
}

@main
struct RecentScreenshotsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "RecentScreenshotsWidget", provider: RecentScreenshotsProvider()) { entry in
            RecentScreenshotsView(entry: entry)
        }
        .configurationDisplayName("Recent Screenshots")
        .description("Your newest screenshots by category.")
        // The grid needs a square-ish canvas; medium is too short for it.
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
