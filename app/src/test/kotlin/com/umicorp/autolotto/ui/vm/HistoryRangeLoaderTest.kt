package com.umicorp.autolotto.ui.vm

import com.umicorp.autolotto.data.Purchase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HistoryRangeLoaderTest {
    private val today = LocalDate.of(2026, 10, 19)

    @Test
    fun `empty windows still cover all four windows and do not query twice`() = runBlocking {
        val calls = mutableListOf<Pair<LocalDate, LocalDate>>()
        val loader = HistoryRangeLoader(today) { from, to ->
            calls.add(from to to)
            emptyList()
        }
        while (loader.canLoadMore) loader.loadNext()
        loader.loadNext()
        assertEquals(4, calls.size)
        assertEquals(4, loader.windowsLoaded)
        assertFalse(loader.canLoadMore)
        assertEquals(today.minusYears(1), loader.loadedFrom)
        assertEquals(today, loader.loadedTo)
        calls.zipWithNext().forEach { (newer, older) ->
            assertEquals(newer.first.minusDays(1), older.second)
        }
        assertTrue(calls.all { it.first >= today.minusYears(1) })
    }

    @Test
    fun `failed window preserves purchases and retry requests exactly the failed range`() = runBlocking {
        val calls = mutableListOf<Pair<LocalDate, LocalDate>>()
        var failSecond = true
        val loader = HistoryRangeLoader(today) { from, to ->
            calls.add(from to to)
            if (calls.size == 2 && failSecond) error("unavailable")
            listOf(purchase(if (calls.size == 1) 1200 else 1190))
        }
        loader.loadNext()
        try {
            loader.loadNext()
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(1, loader.windowsLoaded)
        assertEquals(listOf(1200), loader.purchases.map { it.round })
        failSecond = false
        loader.loadNext()
        assertEquals(calls[1], calls[2])
        assertEquals(2, loader.windowsLoaded)
        assertEquals(listOf(1200, 1190), loader.purchases.map { it.round })
    }

    @Test
    fun `same round split across nonoverlapping windows merges without losing orders`() = runBlocking {
        val loader = HistoryRangeLoader(today) { _, _ -> listOf(purchase(1200)) }
        loader.loadNext()
        loader.loadNext()
        assertEquals(1, loader.purchases.size)
        assertEquals(2000, loader.purchases.single().amount)
        assertEquals(2, loader.purchases.single().totalGames)
    }

    @Test
    fun `refresh preserves full coverage and replaces rather than doubles purchases`() = runBlocking {
        var calls = 0
        val loader = HistoryRangeLoader(today) { _, _ ->
            calls++
            listOf(purchase(1200))
        }
        repeat(4) { loader.loadNext() }
        loader.refresh()
        assertEquals(8, calls)
        assertEquals(4, loader.windowsLoaded)
        assertEquals(today.minusYears(1), loader.loadedFrom)
        assertEquals(4000, loader.purchases.single().amount)
    }

    @Test
    fun `failed refresh retains old windows and original coverage`() = runBlocking {
        var calls = 0
        val loader = HistoryRangeLoader(today) { _, _ ->
            calls++
            if (calls == 6) error("unavailable")
            listOf(purchase(1200))
        }
        repeat(4) { loader.loadNext() }
        try {
            loader.refresh()
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(4, loader.windowsLoaded)
        assertEquals(4000, loader.purchases.single().amount)
        assertFalse(loader.canLoadMore)
    }

    @Test
    fun `next day refresh shifts all four windows without shortening coverage`() = runBlocking {
        val calls = mutableListOf<Pair<LocalDate, LocalDate>>()
        val loader = HistoryRangeLoader(today) { from, to ->
            calls.add(from to to)
            listOf(purchase(1200))
        }
        repeat(4) { loader.loadNext() }
        val nextDay = today.plusDays(1)
        val refreshed = loader.refreshedAt(nextDay)
        assertEquals(8, calls.size)
        assertEquals(nextDay, calls[4].second)
        assertEquals(nextDay, refreshed.loadedTo)
        assertEquals(nextDay.minusYears(1), refreshed.loadedFrom)
        assertEquals(4, refreshed.windowsLoaded)
        assertEquals(4000, refreshed.purchases.single().amount)
        assertEquals(today, loader.loadedTo)
    }

    @Test
    fun `failed next day refresh leaves original full range untouched`() = runBlocking {
        var calls = 0
        val loader = HistoryRangeLoader(today) { _, _ ->
            calls++
            if (calls == 6) error("unavailable")
            listOf(purchase(1200))
        }
        repeat(4) { loader.loadNext() }
        try {
            loader.refreshedAt(today.plusDays(1))
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(4, loader.windowsLoaded)
        assertEquals(today, loader.loadedTo)
        assertEquals(today.minusYears(1), loader.loadedFrom)
        assertEquals(4000, loader.purchases.single().amount)
    }

    @Test
    fun `cancellation propagates and never advances cursor`() = runBlocking {
        val loader = HistoryRangeLoader(today) { _, _ -> throw CancellationException("cancelled") }
        try {
            loader.loadNext()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(0, loader.windowsLoaded)
        assertTrue(loader.purchases.isEmpty())
    }

    @Test
    fun `late response after cancellation cannot commit purchases`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val loader = HistoryRangeLoader(today) { _, _ ->
            withContext(NonCancellable) { release.await() }
            listOf(purchase(1200))
        }
        val request = launch(start = CoroutineStart.UNDISPATCHED) { loader.loadNext() }
        request.cancel()
        release.complete(Unit)
        request.join()
        assertEquals(0, loader.windowsLoaded)
        assertTrue(loader.purchases.isEmpty())
    }

    @Test
    fun `leap year and month end plans stay disjoint within one year`() {
        listOf(LocalDate.of(2024, 2, 29), LocalDate.of(2026, 3, 31)).forEach { date ->
            val loader = HistoryRangeLoader(date) { _, _ -> emptyList() }
            assertEquals(date.minusYears(1), loader.windows.last().start)
            loader.windows.zipWithNext().forEach { (newer, older) ->
                assertEquals(newer.start.minusDays(1), older.end)
                assertTrue(older.start <= older.end)
            }
        }
    }

    private fun purchase(round: Int) = Purchase(
        round = round,
        date = today.atStartOfDay(),
        numbers = listOf(listOf(1, 2, 3, 4, 5, 6)),
        autoCount = 1,
        manualCount = 0,
        amount = 1000,
    )
}
