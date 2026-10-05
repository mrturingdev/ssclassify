package com.mrturingdev.ssclassify.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.mrturingdev.ssclassify.db.ScreenshotDatabase

actual fun createSqlDriver(): SqlDriver = NativeSqliteDriver(ScreenshotDatabase.Schema, "screenshots.db")
