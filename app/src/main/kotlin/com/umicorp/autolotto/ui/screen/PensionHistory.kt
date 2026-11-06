@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.umicorp.autolotto.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umicorp.autolotto.R
import com.umicorp.autolotto.data.Rank720
import com.umicorp.autolotto.data.lumpPrize
import com.umicorp.autolotto.data.matchedDigits
import com.umicorp.autolotto.dhlottery.Round720
import com.umicorp.autolotto.ui.theme.LgGold
import com.umicorp.autolotto.ui.theme.LgTeal
import com.umicorp.autolotto.ui.theme.MotionSpecs
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.vm.PensionHistoryFilter
import com.umicorp.autolotto.ui.vm.PensionRoundGroup
import com.umicorp.autolotto.ui.vm.filteredBy
import com.umicorp.autolotto.ui.vm.pensionRoundGroups
import com.umicorp.autolotto.ui.vm.summarizePensionHistory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 내역 총 기간 수. [com.umicorp.autolotto.ui.vm.pensionHistoryWindows]와 같다. */
private const val PENSION_HISTORY_WINDOWS = 4
private val PENSION_HISTORY_WIDE_MAX = 1100.dp

/**
 * 연금 내역: 6/45 내역과 같은 구성(통계·거르기·넓은 화면 2단·회차 카드)이다.
 * 1·2등과 보너스는 매달 받는 연금식이라 금액 합계에 넣지 않고 건수로 따로 보인다.
 */
@Composable
internal fun PensionHistoryPane(state: PensionUiState, actions: PensionActions, onGoBuy: () -> Unit) {
    if (!state.isLoggedIn) {
        PensionEmptyMessage(
            text = stringResource(R.string.pensionHistoryLogin),
            action = stringResource(R.string.pensionGoLogin),
            onAction = actions.onNavigateToSettings,
        )
        return
    }
    val groups = remember(state.tickets, state.winningByRound) {
        pensionRoundGroups(state.tickets, state.winningByRound)
    }
    var filter by rememberSaveable { mutableStateOf(PensionHistoryFilter.ALL) }
    val visible = remember(groups, filter) { groups.filteredBy(filter) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val onFilter: (PensionHistoryFilter) -> Unit = {
        filter = it
        scope.launch { listState.scrollToItem(0) }
    }

    PullToRefreshBox(
        isRefreshing = state.historyLoading && !state.loadingAll,
        onRefresh = { if (!state.historyLoading) actions.onRefreshHistory() },
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 720.dp && maxHeight >= 480.dp
            val overview: @Composable () -> Unit = { PensionHistoryOverview(state, groups, actions) }
            val controls: @Composable () -> Unit = { PensionHistoryFilters(filter, onFilter) }
            if (wide) {
                Row(
                    Modifier.widthIn(max = PENSION_HISTORY_WIDE_MAX).fillMaxSize()
                        .padding(horizontal = 20.dp).testTag("pension-history-wide"),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        Modifier.width(280.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        overview()
                        controls()
                    }
                    PensionHistoryList(state, visible, filter, actions, onGoBuy, listState, Modifier.weight(1f), null)
                }
            } else {
                PensionHistoryList(
                    state, visible, filter, actions, onGoBuy, listState,
                    Modifier.widthIn(max = PENSION_MAX_WIDTH).fillMaxSize(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        overview()
                        controls()
                    }
                }
            }
        }
    }
}

@Composable
private fun PensionHistoryFilters(selected: PensionHistoryFilter, onSelect: (PensionHistoryFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            PensionHistoryFilter.ALL to R.string.historyFilterAll,
            PensionHistoryFilter.WINNING to R.string.historyFilterWinning,
            PensionHistoryFilter.PENDING to R.string.historyFilterPending,
        ).forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag("pension-filter-${value.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun PensionHistoryOverview(state: PensionUiState, groups: List<PensionRoundGroup>, actions: PensionActions) {
    val summary = remember(groups) { summarizePensionHistory(groups) }
    SectionCard(contentPadding = PaddingValues(16.dp), modifier = Modifier.testTag("pension-history-summary")) {
        Text(
            stringResource(R.string.historySummaryTitle),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.loadedFrom != null && state.loadedTo != null) {
                stringResource(R.string.historyDateRange, state.loadedFrom.toString(), state.loadedTo.toString())
            } else {
                stringResource(R.string.historyNoLoadedRange)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        PensionAmount(R.string.historyTotalSpent, summary.spent)
        PensionAmount(R.string.pensionSummaryLump, summary.lumpPrize)
        PensionAmount(R.string.historyNetAmount, summary.net)
        if (summary.annuityWins > 0) {
            Text(
                stringResource(R.string.pensionSummaryAnnuity, summary.annuityWins),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.historyRoundCounts, summary.winningRounds, summary.pendingRounds),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pensionSummaryScope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.historyLoading) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(
            when {
                state.loadingAll -> stringResource(R.string.historyLoadingAll, state.historyProgress)
                state.windowsLoaded >= PENSION_HISTORY_WINDOWS -> stringResource(R.string.historyArchiveComplete)
                else -> stringResource(R.string.historyArchivePartial, state.windowsLoaded)
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.canLoadMore) {
            TextButton(
                onClick = actions.onLoadAllHistory,
                enabled = !state.historyLoading,
                modifier = Modifier.testTag("pension-history-load-all"),
            ) { Text(stringResource(R.string.historyLoadAll)) }
        }
        if (state.historyFailed) {
            Text(
                stringResource(R.string.pensionHistoryFailed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = actions.onRefreshHistory, enabled = !state.historyLoading) {
                Text(stringResource(R.string.pensionRetry))
            }
        }
    }
}

@Composable
private fun PensionAmount(label: Int, amount: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.historyWon, formatNumber(amount)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun PensionHistoryList(
    state: PensionUiState,
    visible: List<PensionRoundGroup>,
    filter: PensionHistoryFilter,
    actions: PensionActions,
    onGoBuy: () -> Unit,
    listState: LazyListState,
    modifier: Modifier,
    header: (@Composable () -> Unit)?,
) {
    LazyColumn(
        modifier.testTag("pension-history"),
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "overview") {
            Column {
                header?.invoke()
                if (header != null) Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.historyVisibleRounds, visible.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                when {
                    state.historyLoading ->
                        Text(stringResource(R.string.pensionHistoryLoading), Modifier.padding(vertical = 28.dp))
                    filter == PensionHistoryFilter.WINNING ->
                        Text(stringResource(R.string.historyNoWinningRecords), Modifier.padding(vertical = 28.dp))
                    filter == PensionHistoryFilter.PENDING ->
                        Text(stringResource(R.string.historyNoPendingRecords), Modifier.padding(vertical = 28.dp))
                    state.historyFailed -> Unit   // 실패 안내와 다시 시도는 통계 칸에 있다.
                    else -> PensionEmptyMessage(
                        text = stringResource(R.string.pensionHistoryEmpty),
                        action = stringResource(R.string.pensionGoBuy),
                        onAction = onGoBuy,
                    )
                }
            }
        }
        itemsIndexed(visible, key = { _, group -> group.round }) { index, group -> PensionRoundCard(group, index) }
        if (state.canLoadMore) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    if (state.historyLoading) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        TextButton(onClick = actions.onLoadMoreHistory) {
                            Text(stringResource(R.string.historyLoadMore), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PensionEmptyMessage(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}

/** 회차 카드: 머리(회차·추첨일·상태와 당첨번호 공), 본문(조별 번호와 등수), 당첨이면 금 띠. */
@Composable
private fun PensionRoundCard(group: PensionRoundGroup, index: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MotionSpecs.staggerDelay(index).toLong())
        shown = true
    }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MotionSpecs.emphasized(),
        label = "pensionCardAppear",
    )
    val waiting = group.pending && group.winning == null
    val shape = MaterialTheme.shapes.large
    val headerAccent = if (!waiting && !group.won) LgTeal else LgGold
    val headerBg = lerp(headerAccent, MaterialTheme.colorScheme.surfaceContainerLowest, if (group.won) 0.70f else 0.86f)

    SectionCard(
        modifier = Modifier
            .graphicsLayer {
                alpha = appear
                translationY = (1f - appear) * 24.dp.toPx()
            }
            .then(if (group.won) Modifier.border(2.dp, LgGold, shape) else Modifier)
            .testTag("pension-round-${group.round}"),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().background(headerBg).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.roundLabel, group.round),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Round720.getDrawDate(group.round).toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when {
                    waiting -> StatusPill(stringResource(R.string.pensionRankPending), PillTone.Pending)
                    group.won -> StatusPill(stringResource(R.string.pensionWonPill), PillTone.Win)
                    else -> StatusPill(stringResource(R.string.statusNoWin), PillTone.Lose)
                }
            }
            group.winning?.let { win ->
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.pensionWinningCaption),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                PensionNumberRow(stringResource(R.string.pensionGroupLabel, win.jo), win.number, maxBall = 26.dp)
                Spacer(Modifier.height(6.dp))
                PensionNumberRow(stringResource(R.string.pensionBonusLabel), win.bonusNumber, maxBall = 26.dp)
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            group.tickets.zip(group.ranks).forEach { (ticket, rank) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PensionNumberRow(
                        stringResource(R.string.pensionGroupLabel, ticket.jo),
                        ticket.number,
                        matchedDigits = if (rank == Rank720.PENDING) null else rank.matchedDigits,
                        groupMatched = rank == Rank720.FIRST,
                    )
                    if (rank != Rank720.PENDING) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(pensionRankText(rank), if (rank == Rank720.NONE) PillTone.Lose else PillTone.Win)
                            val prize = pensionPrizeLabel(rank)
                            if (prize != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(prize, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if (group.won) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(lerp(LgGold, MaterialTheme.colorScheme.surfaceContainerLowest, 0.70f))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (group.lumpTotal > 0) {
                    Text(
                        stringResource(R.string.pensionLumpTotal, formatNumber(group.lumpTotal)),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (group.annuityWins > 0) {
                    Text(
                        stringResource(R.string.pensionAnnuityCount, group.annuityWins),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun pensionRankText(rank: Rank720): String = stringResource(
    when (rank) {
        Rank720.FIRST -> R.string.pensionRank1
        Rank720.SECOND -> R.string.pensionRank2
        Rank720.THIRD -> R.string.pensionRank3
        Rank720.FOURTH -> R.string.pensionRank4
        Rank720.FIFTH -> R.string.pensionRank5
        Rank720.SIXTH -> R.string.pensionRank6
        Rank720.SEVENTH -> R.string.pensionRank7
        Rank720.BONUS -> R.string.pensionRankBonus
        Rank720.NONE -> R.string.statusNoWin
        Rank720.PENDING -> R.string.pensionRankPending
    },
)

@Composable
private fun pensionPrizeLabel(rank: Rank720): String? = when (rank) {
    Rank720.FIRST -> stringResource(R.string.pensionPrizeFirst)
    Rank720.SECOND, Rank720.BONUS -> stringResource(R.string.pensionPrizeSecond)
    Rank720.NONE, Rank720.PENDING -> null
    else -> stringResource(R.string.historyWon, formatNumber(rank.lumpPrize))
}
