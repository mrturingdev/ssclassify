package com.agy.imagecategorizer.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.agy.imagecategorizer.PERMISSION_DENIED
import com.agy.imagecategorizer.db.ScreenshotDatabase
import com.agy.imagecategorizer.model.CategorySource
import com.agy.imagecategorizer.model.ImageCategory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScreenshotRepositoryTest {

    private class FakeSource(var assets: List<ScreenshotAsset>, var text: Map<String, String>) : ScreenshotSource {
        var granted = true
        val analyzed = mutableListOf<String>()

        override suspend fun ensureAccess() = granted
        override suspend fun listScreenshots() = assets
        override suspend fun analyze(
            assets: List<ScreenshotAsset>,
            onResult: (ScreenshotAsset, ScreenshotAnalysis) -> Unit,
        ) = assets.forEach {
            analyzed += it.id
            onResult(it, ScreenshotAnalysis(text[it.id].orEmpty()))
        }
    }

    private fun asset(id: String, modified: Long = 1L, date: Long = 0L) =
        ScreenshotAsset(id, "Screenshot_$id.png", "Screenshots", "", 1080, 2340, date, modified)

    private fun repository(source: ScreenshotSource) =
        ScreenshotRepository(
            source,
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ScreenshotDatabase.Schema.create(it) },
        )

    @Test
    fun rescansOnlyNewOrChangedScreenshotsAndDropsDeletedOnes() = runBlocking {
        val source = FakeSource(
            assets = listOf(asset("a", date = 2), asset("b", date = 1)),
            text = mapOf("a" to "Boarding pass Flight 12 Gate 4", "b" to "Receipt Subtotal Total Tax"),
        )
        val repo = repository(source)

        val first = assertIs<ScanOutcome.Success>(repo.scan()).images
        assertEquals(listOf("a", "b"), first.map { it.id })
        assertEquals(listOf(ImageCategory.Travel, ImageCategory.Receipts), first.map { it.category })
        assertEquals(listOf("a", "b"), source.analyzed)

        source.analyzed.clear()
        repo.scan()
        assertEquals(emptyList(), source.analyzed, "unchanged screenshots must not be re-analyzed")

        source.assets = listOf(asset("a", modified = 2, date = 2), asset("c", date = 3))
        source.text = source.text + ("a" to "def main(): return null") + ("c" to "")
        val third = assertIs<ScanOutcome.Success>(repo.scan()).images
        assertEquals(listOf("a", "c"), source.analyzed)
        assertEquals(listOf("c", "a"), third.map { it.id }, "deleted 'b' is gone, newest first")
        assertEquals(listOf(ImageCategory.Other, ImageCategory.Code), third.map { it.category })
        assertEquals(third, repo.cached())
    }

    @Test
    fun searchMatchesOcrPrefixesAndStaysInSyncWithRescans() = runBlocking {
        val source = FakeSource(
            assets = listOf(asset("a", date = 2), asset("b", date = 1)),
            text = mapOf("a" to "Boarding pass Flight 12 Gate 4", "b" to "Receipt Subtotal Total Tax"),
        )
        val repo = repository(source)
        repo.scan()

        assertEquals(listOf("a"), repo.search("board").map { it.id }, "prefix match")
        assertEquals(listOf("a"), repo.search("FLIGHT gate").map { it.id }, "all words, case-insensitive")
        assertEquals(emptyList(), repo.search("flight receipt").map { it.id }, "words are ANDed")
        assertEquals(listOf("a", "b"), repo.search("screenshot").map { it.id }.sorted(), "file names are indexed")
        assertEquals(listOf("a", "b"), repo.search("   ").map { it.id }, "blank returns everything")

        // Re-analyzed text replaces the old index entry; deleted rows leave the index.
        source.assets = listOf(asset("a", modified = 2, date = 2))
        source.text = mapOf("a" to "Pizza menu")
        repo.scan()
        assertEquals(emptyList(), repo.search("boarding").map { it.id })
        assertEquals(emptyList(), repo.search("receipt").map { it.id })
        assertEquals(listOf("a"), repo.search("pizza").map { it.id })
    }

    @Test
    fun searchNeverFailsOnFtsSyntaxInUserInput() = runBlocking {
        val source = FakeSource(listOf(asset("a")), mapOf("a" to "total 12.50 \"quoted\" text"))
        val repo = repository(source)
        repo.scan()

        for (input in listOf("\"", "\"total", "total AND", "NOT", "*", "-", "a OR", "(", "12.50", "col:x", "^")) {
            repo.search(input) // must not throw
        }
        assertEquals(listOf("a"), repo.search("\"quoted").map { it.id })
        assertEquals(listOf("a"), repo.search("12.50").map { it.id })
        assertEquals(null, ftsQuery(" - * ( "))
        assertEquals("\"a*\" \"b*\" \"c*\"", ftsQuery("a\"b c"))
        assertEquals("\"12.50*\"", ftsQuery("12.50"))
    }

    @Test
    fun migratingVersion1DatabaseBackfillsSearchIndex() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """
            CREATE TABLE screenshot (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, folder TEXT NOT NULL,
                relative_path TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL,
                date_millis INTEGER NOT NULL, modified_millis INTEGER NOT NULL,
                ocr_text TEXT NOT NULL, sub_category TEXT, description TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            "INSERT INTO screenshot VALUES ('a', 'Screenshot_a.png', '', '', 1, 1, 1, 1, 'Invoice 42', NULL, NULL)",
            0,
        )
        ScreenshotDatabase.Schema.migrate(driver, 1, ScreenshotDatabase.Schema.version)

        val repo = ScreenshotRepository(FakeSource(listOf(asset("a")), emptyMap()), driver)
        assertEquals(listOf("a"), repo.search("invoice").map { it.id })
    }

    @Test
    fun categoryCorrectionSurvivesReanalysisAndResets() = runBlocking {
        val source = FakeSource(listOf(asset("a")), mapOf("a" to "Receipt Subtotal Total Tax"))
        val repo = repository(source)
        repo.scan()
        assertEquals(ImageCategory.Receipts, repo.cached().single().category)

        repo.setCategory("a", ImageCategory.Work)
        val corrected = repo.cached().single()
        assertEquals(ImageCategory.Work, corrected.category)
        assertEquals(ImageCategory.Receipts, corrected.autoCategory)
        assertEquals(true, corrected.isCategoryCorrected)
        assertEquals(ImageCategory.Work, repo.search("receipt").single().category, "search sees corrections")

        // The file changes and is re-analyzed: the user's pick still wins.
        source.assets = listOf(asset("a", modified = 2))
        source.text = mapOf("a" to "Flight Gate Boarding")
        repo.scan()
        val reanalyzed = repo.cached().single()
        assertEquals(ImageCategory.Work, reanalyzed.category)
        assertEquals(ImageCategory.Travel, reanalyzed.autoCategory)

        repo.setCategory("a", null)
        val reset = repo.cached().single()
        assertEquals(ImageCategory.Travel, reset.category)
        assertEquals(false, reset.isCategoryCorrected)
    }

    @Test
    fun deletedScreenshotDropsItsCorrection() = runBlocking {
        val source = FakeSource(listOf(asset("a")), mapOf("a" to "invoice"))
        val repo = repository(source)
        repo.scan()
        repo.setCategory("a", ImageCategory.Food)

        source.assets = emptyList()
        repo.scan()
        source.assets = listOf(asset("a"))
        repo.scan()
        assertEquals(ImageCategory.Receipts, repo.cached().single().category, "a reused id starts clean")
    }

    @Test
    fun unknownStoredCategoryFallsBackToAutomatic() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ScreenshotDatabase.Schema.create(it) }
        val repo = ScreenshotRepository(FakeSource(listOf(asset("a")), mapOf("a" to "invoice")), driver)
        repo.scan()
        ScreenshotDatabase(driver).screenshotQueries.setOverride("a", "RenamedAway")
        val record = repo.cached().single()
        assertEquals(ImageCategory.Receipts, record.category)
        assertEquals(false, record.isCategoryCorrected)
    }

    @Test
    fun correctionTeachesSimilarScreenshotsUntilReset() = runBlocking {
        val gym = "Pulse Fitness Club membership renewal class Trainer Studio Book now"
        val source = FakeSource(
            assets = listOf(asset("a", date = 3), asset("b", date = 2), asset("c", date = 1)),
            text = mapOf(
                "a" to "$gym Yoga Tuesday 6pm Anita",
                "b" to "$gym Zumba Friday 7pm Bikash",
                "c" to "Boarding pass Flight Gate Seat Departure",
            ),
        )
        val repo = repository(source)
        repo.scan()
        val before = repo.cached().associateBy { it.id }
        assertEquals(CategorySource.Rules, before.getValue("b").source)

        repo.setCategory("a", ImageCategory.Health)
        val after = repo.cached().associateBy { it.id }
        assertEquals(CategorySource.User, after.getValue("a").source)
        assertEquals(before.getValue("a").category, after.getValue("a").autoCategory, "own correction is not its own evidence")
        assertEquals(ImageCategory.Health, after.getValue("b").category)
        assertEquals(CategorySource.Learned, after.getValue("b").source)
        assertEquals(before.getValue("c"), after.getValue("c"), "unrelated screenshot untouched")
        assertEquals(ImageCategory.Health, repo.search("zumba").single().category, "search applies learning")

        repo.setCategory("a", null)
        assertEquals(before, repo.cached().associateBy { it.id })
    }

    @Test
    fun deniedAccessKeepsCacheAndReportsPermission() = runBlocking {
        val source = FakeSource(listOf(asset("a")), mapOf("a" to "invoice"))
        val repo = repository(source)
        repo.scan()

        source.granted = false
        assertEquals(ScanOutcome.Failure(PERMISSION_DENIED), repo.scan())
        assertEquals(listOf("a"), repo.cached().map { it.id })
    }

    @Test
    fun cachedRecordsIncludeOcrText() = runBlocking {
        val source = FakeSource(
            assets = listOf(asset("a", date = 1)),
            text = mapOf("a" to "Total $50.00 Paid"),
        )
        val repo = repository(source)
        repo.scan()
        val record = repo.cached().single()
        assertEquals("Total $50.00 Paid", record.ocrText)
    }
}
