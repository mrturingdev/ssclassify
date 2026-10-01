package com.agy.imagecategorizer.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.agy.imagecategorizer.android.MainActivity
import com.agy.imagecategorizer.android.R
import com.agy.imagecategorizer.data.MediaScanner
import com.agy.imagecategorizer.data.ScreenshotRepository
import com.agy.imagecategorizer.data.createSqlDriver
import com.agy.imagecategorizer.data.initAndroid
import com.agy.imagecategorizer.model.ImageRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen grid of the most recently analyzed screenshots. */
class RecentScreenshotsWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        // :shared broadcasts a bare update after each scan; it cannot know the widget ids.
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE &&
            !intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
        ) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isNotEmpty()) render(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context, manager, intArrayOf(id))
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
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
                for (id in ids) manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id), images))
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun views(context: Context, options: Bundle, images: List<ImageRecord>): RemoteViews {
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val grid = gridFor(widthDp, heightDp)
        // ponytail: RemoteViews bitmaps ride one ~1MB binder transaction, so cells cap at 160px RGB_565 (~460KB for 9)
        val cellPx = (widthDp * context.resources.displayMetrics.density / grid.columns).toInt()
            .takeIf { it > 0 }?.coerceAtMost(MAX_CELL_PX) ?: MAX_CELL_PX

        val views = RemoteViews(context.packageName, R.layout.widget_recent_screenshots)
        val shown = images.take(grid.count)
        views.setViewVisibility(R.id.widget_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_grid, if (shown.isEmpty()) View.GONE else View.VISIBLE)
        ROWS.forEachIndexed { r, row -> views.setViewVisibility(row, if (r < grid.rows) View.VISIBLE else View.GONE) }

        var next = 0
        CELLS.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell ->
                if (r >= grid.rows || c >= grid.columns) {
                    views.setViewVisibility(cell, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(cell, View.VISIBLE)
                val bitmap = shown.getOrNull(next++)?.let { loadPreview(it.id, cellPx) }
                if (bitmap != null) views.setImageViewBitmap(cell, bitmap) else views.setImageViewResource(cell, 0)
            }
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }

    private companion object {
        const val MAX_CELL_PX = 160
        val ROWS = intArrayOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2)
        val CELLS = arrayOf(
            intArrayOf(R.id.widget_cell_0, R.id.widget_cell_1, R.id.widget_cell_2),
            intArrayOf(R.id.widget_cell_3, R.id.widget_cell_4, R.id.widget_cell_5),
            intArrayOf(R.id.widget_cell_6, R.id.widget_cell_7, R.id.widget_cell_8),
        )
    }
}

internal data class WidgetGrid(val columns: Int, val rows: Int) {
    val count get() = columns * rows
}

/** 2x2 launcher cells show 4, 3x3 or larger show 9, anything else 6. */
internal fun gridFor(widthDp: Int, heightDp: Int): WidgetGrid {
    // A widget spanning n launcher cells is about 70n - 30 dp.
    val columns = (widthDp + 30) / 70
    val rows = (heightDp + 30) / 70
    return when {
        columns in 1..2 && rows in 1..2 -> WidgetGrid(2, 2)
        columns >= 3 && rows >= 3 -> WidgetGrid(3, 3)
        rows > columns -> WidgetGrid(2, 3)
        else -> WidgetGrid(3, 2)
    }
}
