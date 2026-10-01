@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.agy.imagecategorizer.widget

import com.agy.imagecategorizer.model.ImageRecord
import platform.Foundation.NSFileManager
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSNotificationCenter
import platform.Foundation.writeToURL

/** Shared with the widget extension; both targets carry this App Group entitlement. */
private const val APP_GROUP = "group.com.agy.imagecategorizer"

/** Read by iosAppWidget/WidgetSnapshot.swift; keep the two in step. */
private const val SNAPSHOT_FILE = "widget-snapshot.json"

/** iOSApp.swift reloads WidgetKit timelines on this; WidgetCenter has no Objective-C API. */
private const val SNAPSHOT_UPDATED = "SSWidgetSnapshotUpdated"

/**
 * Writes what the widget shows to the App Group container: every category with its
 * newest [WidgetFeed.MAX_ITEMS] tiles. The extension cannot link this framework or
 * compute categories itself, so it only ever reads this file.
 */
actual fun publishWidgetSnapshot(records: List<ImageRecord>) {
    val container = NSFileManager.defaultManager
        .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP) ?: return
    val snapshot = mapOf(
        "categories" to WidgetFeed.categories(records).map { category ->
            mapOf(
                "key" to category.key,
                "label" to category.label,
                "count" to category.count,
                "items" to WidgetFeed.items(records, category.key).map { item ->
                    // NSJSONSerialization rejects nil values, so absent captions are left out.
                    buildMap {
                        put("id", item.id)
                        item.badge?.let { put("badge", it) }
                        item.summary?.let { put("summary", it) }
                    }
                },
            )
        },
    )
    val data = NSJSONSerialization.dataWithJSONObject(snapshot, 0u, null) ?: return
    val file = container.URLByAppendingPathComponent(SNAPSHOT_FILE) ?: return
    if (data.writeToURL(file, atomically = true)) {
        NSNotificationCenter.defaultCenter.postNotificationName(SNAPSHOT_UPDATED, null)
    }
}
