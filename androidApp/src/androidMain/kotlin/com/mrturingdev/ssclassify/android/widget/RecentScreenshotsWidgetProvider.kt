package com.mrturingdev.ssclassify.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.mrturingdev.ssclassify.android.MainActivity
import com.mrturingdev.ssclassify.android.R
import com.mrturingdev.ssclassify.data.MediaScanner
import com.mrturingdev.ssclassify.data.ScreenshotRepository
import com.mrturingdev.ssclassify.data.createSqlDriver
import com.mrturingdev.ssclassify.data.initAndroid
import com.mrturingdev.ssclassify.model.ImageRecord
import com.mrturingdev.ssclassify.widget.WidgetFeed
import com.mrturingdev.ssclassify.widget.WidgetItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen grid of the newest analyzed screenshots in one category, captioned
 * like the app's grid. The header arrows cycle the category; a tile opens its
 * detail page in the app.
 */
class RecentScreenshotsWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when {
            intent.action == ACTION_CYCLE -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    render(context, AppWidgetManager.getInstance(context), intArrayOf(id), step = intent.getIntExtra(EXTRA_STEP, 0))
                }
            }
            // :shared broadcasts a bare update when the library changes; it cannot know the widget ids.
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE &&
                !intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) -> {
                val manager = AppWidgetManager.getInstance(context)
                onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isNotEmpty()) render(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context, manager, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(categoryKey(it)) } }.apply()
    }

    /** Re-renders [ids]; a non-zero [step] first moves each one's category by that much. */
    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, step: Int = 0) {
        // A widget update can cold-start the process without MainActivity, so bootstrap here too.
        initAndroid(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val images = try {
                    ScreenshotRepository(MediaScanner(), createSqlDriver()).cached()
                } catch (e: Exception) {
                    emptyList()
                }
                val prefs = prefs(context)
                for (id in ids) {
                    var key = prefs.getString(categoryKey(id), WidgetFeed.ALL_KEY) ?: WidgetFeed.ALL_KEY
                    if (step != 0) {
                        key = WidgetFeed.step(WidgetFeed.categories(images), key, step)
                        prefs.edit().putString(categoryKey(id), key).apply()
                    }
                    manager.updateAppWidget(id, views(context, id, manager.getAppWidgetOptions(id), images, key))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun views(
        context: Context,
        widgetId: Int,
        options: Bundle,
        images: List<ImageRecord>,
        key: String,
    ): RemoteViews {
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val grid = gridFor(widthDp, heightDp)
        val categories = WidgetFeed.categories(images)
        val category = WidgetFeed.resolve(categories, key)
        val items = WidgetFeed.items(images, category.key, grid.count)

        val views = RemoteViews(context.packageName, R.layout.widget_recent_screenshots)
        views.setTextViewText(R.id.widget_title, "${category.label} (${category.count})")
        val arrows = if (categories.size > 1) View.VISIBLE else View.INVISIBLE
        views.setViewVisibility(R.id.widget_prev, arrows)
        views.setViewVisibility(R.id.widget_next, arrows)
        views.setOnClickPendingIntent(R.id.widget_prev, cycleIntent(context, widgetId, -1))
        views.setOnClickPendingIntent(R.id.widget_next, cycleIntent(context, widgetId, 1))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, null))
        views.setViewVisibility(R.id.widget_empty, if (items.isEmpty()) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_grid, if (items.isEmpty()) View.GONE else View.VISIBLE)

        val (cellWidth, cellHeight) = cellSizePx(context, widthDp, heightDp, grid)
        ROWS.forEachIndexed { r, row ->
            views.removeAllViews(row)
            views.setViewVisibility(row, if (r < grid.rows) View.VISIBLE else View.GONE)
            if (r >= grid.rows) return@forEachIndexed
            for (c in 0 until grid.columns) {
                views.addView(row, cell(context, items.getOrNull(r * grid.columns + c), cellWidth, cellHeight))
            }
        }
        return views
    }

    private suspend fun cell(context: Context, item: WidgetItem?, widthPx: Int, heightPx: Int): RemoteViews {
        val cell = RemoteViews(context.packageName, R.layout.widget_cell)
        if (item == null) {
            // Keeps the grid's shape when a category has fewer screenshots than cells.
            cell.setViewVisibility(R.id.cell_root, View.INVISIBLE)
            return cell
        }
        loadPreview(item.id, widthPx, heightPx)?.let { cell.setImageViewBitmap(R.id.cell_image, it) }
        cell.setTextViewText(R.id.cell_badge, item.badge.orEmpty())
        cell.setViewVisibility(R.id.cell_badge, if (item.badge != null) View.VISIBLE else View.GONE)
        cell.setTextViewText(R.id.cell_summary, item.summary.orEmpty())
        cell.setViewVisibility(R.id.cell_summary, if (item.summary != null) View.VISIBLE else View.GONE)
        val captioned = item.badge != null || item.summary != null
        cell.setViewVisibility(R.id.cell_caption, if (captioned) View.VISIBLE else View.GONE)
        cell.setOnClickPendingIntent(R.id.cell_root, openApp(context, item.id))
        return cell
    }

    /** Opens the app, on [screenshotId]'s detail page when given. */
    private fun openApp(context: Context, screenshotId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (screenshotId != null) {
            intent.putExtra(EXTRA_SCREENSHOT_ID, screenshotId)
            // Distinct data keeps each tile's PendingIntent (and its extra) separate.
            intent.data = Uri.parse("imagecategorizer://screenshot/${Uri.encode(screenshotId)}")
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun cycleIntent(context: Context, widgetId: Int, step: Int): PendingIntent {
        val intent = Intent(context, RecentScreenshotsWidgetProvider::class.java)
            .setAction(ACTION_CYCLE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(EXTRA_STEP, step)
        val requestCode = widgetId * 2 + if (step > 0) 1 else 0
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** One cell's pixel size, capped so all bitmaps fit the ~1MB RemoteViews transaction. */
    private fun cellSizePx(context: Context, widthDp: Int, heightDp: Int, grid: WidgetGrid): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density / grid.columns).takeIf { it > 0 } ?: MAX_CELL_PX.toFloat()
        val height = ((heightDp - HEADER_HEIGHT_DP) * density / grid.rows).takeIf { it > 0 } ?: MAX_CELL_PX.toFloat()
        // ponytail: 9 cells x 160px RGB_565 is ~460KB of the ~1MB binder budget; raise only with fewer cells
        val shrink = minOf(1f, MAX_CELL_PX / maxOf(width, height))
        return (width * shrink).toInt().coerceAtLeast(1) to (height * shrink).toInt().coerceAtLeast(1)
    }

    companion object {
        /** Extra on the MainActivity intent naming the screenshot to open. */
        const val EXTRA_SCREENSHOT_ID = "com.mrturingdev.ssclassify.SCREENSHOT_ID"

        private const val ACTION_CYCLE = "com.mrturingdev.ssclassify.widget.CYCLE_CATEGORY"
        private const val EXTRA_STEP = "step"
        private const val MAX_CELL_PX = 160
        private val ROWS = intArrayOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2)

        private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        private fun categoryKey(widgetId: Int) = "category_$widgetId"
    }
}

internal data class WidgetGrid(val columns: Int, val rows: Int) {
    val count get() = columns * rows
}

/**
 * The grid for a widget of [widthDp] x [heightDp]: the most tiles (up to 9, at most
 * 3 rows) whose cells stay close to square and big enough for a caption, like the
 * app's square tiles. Falls back to the squarest grid when none qualify.
 */
internal fun gridFor(widthDp: Int, heightDp: Int): WidgetGrid {
    if (widthDp <= 0 || heightDp <= HEADER_HEIGHT_DP) return WidgetGrid(3, 3) // size not reported yet
    val gridHeight = (heightDp - HEADER_HEIGHT_DP).toFloat()
    val candidates = (1..4).flatMap { columns -> (1..3).map { rows -> WidgetGrid(columns, rows) } }
        .filter { it.count <= 9 }
    fun aspect(grid: WidgetGrid) = (widthDp.toFloat() / grid.columns) / (gridHeight / grid.rows)
    fun squareness(grid: WidgetGrid) = kotlin.math.abs(kotlin.math.ln(aspect(grid)))
    val fitting = candidates.filter { grid ->
        aspect(grid) in 0.7f..1.45f &&
            minOf(widthDp.toFloat() / grid.columns, gridHeight / grid.rows) >= MIN_CELL_DP
    }
    return fitting.maxWithOrNull(compareBy<WidgetGrid> { it.count }.thenByDescending { squareness(it) })
        ?: candidates.minBy { squareness(it) }
}

/** Height of the category header row in widget_recent_screenshots.xml. */
internal const val HEADER_HEIGHT_DP = 36

/** Below this a tile cannot fit the badge and a line of summary. */
private const val MIN_CELL_DP = 60f
