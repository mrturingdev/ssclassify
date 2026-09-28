package com.agy.imagecategorizer.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.agy.imagecategorizer.db.ScreenshotDatabase

actual fun createSqlDriver(): SqlDriver = NativeSqliteDriver(ScreenshotDatabase.Schema, "screenshots.db")
