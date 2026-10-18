package com.umicorp.autolotto.ui.vm

import com.umicorp.autolotto.data.Purchase
import com.umicorp.autolotto.data.mergePurchasesByRound
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.LocalDate

/** A fixed, non-overlapping one-year query plan. Only successful windows advance the cursor. */
internal class HistoryRangeLoader(
    val today: LocalDate,
    private val fetch: suspend (LocalDate, LocalDate) -> List<Purchase>,
) {
    data class Window(val start: LocalDate, val end: LocalDate)

    val windows: List<Window> = (0 until 4).map { index ->
        Window(
            start = today.minusMonths((index + 1) * 3L).coerceAtLeast(today.minusYears(1)),
            end = if (index == 0) today else today.minusMonths(index * 3L).minusDays(1),
        )
    }
    private val loaded = mutableListOf<List<Purchase>>()
    val windowsLoaded: Int get() = loaded.size
    val canLoadMore: Boolean get() = windowsLoaded < windows.size
    val loadedFrom: LocalDate? get() = windows.getOrNull(windowsLoaded - 1)?.start
    val loadedTo: LocalDate? get() = today.takeIf { loaded.isNotEmpty() }
    val purchases: List<Purchase>
        get() = mergePurchasesByRound(loaded.flatten()).sortedByDescending { it.round }

    suspend fun loadNext() {
        if (!canLoadMore) return
        loaded.add(fetchWindow(windowsLoaded))
    }

    /** Refresh the same coverage, retaining each old window until its replacement succeeds. */
    suspend fun refresh(onProgress: () -> Unit = {}) {
        val count = windowsLoaded.coerceAtLeast(1)
        repeat(count) { index ->
            val result = fetchWindow(index)
            if (index < loaded.size) loaded[index] = result else loaded.add(result)
            onProgress()
        }
    }

    /** Build the shifted coverage separately so a failed refresh cannot shorten the visible range. */
    suspend fun refreshedAt(date: LocalDate): HistoryRangeLoader {
        val replacement = HistoryRangeLoader(date, fetch)
        repeat(windowsLoaded.coerceAtLeast(1)) { replacement.loadNext() }
        return replacement
    }

    private suspend fun fetchWindow(index: Int): List<Purchase> {
        val window = windows[index]
        val result = fetch(window.start, window.end)
        currentCoroutineContext().ensureActive()
        return result
    }
}
