@file:OptIn(ExperimentalMaterial3Api::class)

package com.umicorp.autolotto.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umicorp.autolotto.R
import com.umicorp.autolotto.data.Purchase
import com.umicorp.autolotto.data.isWinning
import com.umicorp.autolotto.data.summarizeHistory
import com.umicorp.autolotto.ui.appViewModel
import com.umicorp.autolotto.ui.theme.LgGold
import com.umicorp.autolotto.ui.theme.LgTeal
import com.umicorp.autolotto.ui.theme.MotionSpecs
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.util.localizedRank
import com.umicorp.autolotto.ui.vm.HistoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 구매/당첨 기록 (원본 history_screen.dart 1:1) — Lucky Gloss 리디자인.
 *
 * 로그인 상태면 dhlottery에서 최근 구매내역 라이브 조회. 회차별 프로스트 카드(틴트 헤더 + StatusPill),
 * 게임 A~E = 글로시 캔디 볼 6개 + 행별 상태 필. 낙첨 회차는 볼 dimmed로 은은하게 뮤트.
 * 시그니처 모션: 카드 스태거 등장(fade + rise, MotionSpecs.staggerDelay).
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val vm: HistoryViewModel = appViewModel()
    val purchases by vm.purchases.collectAsState()
    val loading by vm.loading.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val canLoadMore by vm.canLoadMore.collectAsState()
    val error by vm.error.collectAsState()
    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val loadingAll by vm.loadingAll.collectAsState()
    val windowsLoaded by vm.windowsLoaded.collectAsState()
    val loadedFrom by vm.loadedFrom.collectAsState()
    val loadedTo by vm.loadedTo.collectAsState()

    HistoryContent(
        purchases, loading, loadingMore, loadingAll, canLoadMore, error, isLoggedIn,
        windowsLoaded, loadedFrom, loadedTo, vm::loadHistory, vm::loadMore, vm::loadAll, modifier,
    )
}

/** Read-only content is independent of account/session and can be exercised with synthetic records. */
@Composable
internal fun HistoryContent(
    purchases: List<Purchase>,
    loading: Boolean,
    loadingMore: Boolean,
    loadingAll: Boolean,
    canLoadMore: Boolean,
    error: String?,
    isLoggedIn: Boolean,
    windowsLoaded: Int,
    loadedFrom: LocalDate?,
    loadedTo: LocalDate?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onLoadAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visible = remember(purchases, filter) {
        purchases.filter { when (filter) {
            "winning" -> it.isWinning
            "pending" -> !it.checked
            else -> true
        } }
    }
    val busy = loading || loadingMore || loadingAll

    Scaffold(
        modifier = modifier.creamPageBackground(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.historyTitle), fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = onRefresh, enabled = isLoggedIn && !busy) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.cdRefresh),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { inner ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(inner)) {
            if (!isLoggedIn) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.historyLoginToLoad))
                }
                return@BoxWithConstraints
            }
            val wide = maxWidth >= 720.dp && maxHeight >= 480.dp
            val overview: @Composable () -> Unit = {
                HistoryOverview(purchases, loadingAll, busy, canLoadMore, error, isLoggedIn,
                    windowsLoaded, loadedFrom, loadedTo, onLoadAll, onRefresh)
            }
            val controls: @Composable () -> Unit = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to R.string.historyFilterAll, "winning" to R.string.historyFilterWinning,
                        "pending" to R.string.historyFilterPending).forEach { (value, label) ->
                        FilterChip(selected = filter == value, onClick = {
                            filter = value
                            scope.launch { listState.scrollToItem(0) }
                        },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.testTag("history-filter-$value"))
                    }
                }
            }
            PullToRefreshBox(isRefreshing = loading, onRefresh = { if (!busy) onRefresh() }) {
                if (wide) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("history-wide"),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.width(260.dp).verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            overview()
                            controls()
                        }
                        HistoryList(visible, filter, isLoggedIn, busy, canLoadMore, onLoadMore,
                            listState, Modifier.weight(1f), null)
                    }
                } else {
                    HistoryList(visible, filter, isLoggedIn, busy, canLoadMore, onLoadMore,
                        listState, Modifier.fillMaxSize().testTag("history-compact")) {
                        overview()
                        controls()
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryOverview(
    purchases: List<Purchase>, loadingAll: Boolean, busy: Boolean, canLoadMore: Boolean,
    error: String?, isLoggedIn: Boolean, windowsLoaded: Int,
    loadedFrom: LocalDate?, loadedTo: LocalDate?, onLoadAll: () -> Unit, onRefresh: () -> Unit,
) {
    val summary = remember(purchases) { summarizeHistory(purchases) }
    SectionCard(contentPadding = PaddingValues(16.dp)) {
        Text(stringResource(R.string.historySummaryTitle), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(if (loadedFrom != null && loadedTo != null)
            stringResource(R.string.historyDateRange, loadedFrom.toString(), loadedTo.toString())
            else stringResource(R.string.historyNoLoadedRange), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        HistoryAmount(R.string.historyTotalSpent, summary.totalPurchaseAmount)
        HistoryAmount(R.string.historyTotalPrize, summary.totalPrizeAmount)
        HistoryAmount(R.string.historyNetAmount, summary.netAmount)
        Text(stringResource(R.string.historyRoundCounts, summary.winningRoundCount, summary.pendingRoundCount),
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.historySummaryScope), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(stringResource(if (loadingAll) R.string.historyLoadingAll
            else if (windowsLoaded >= 4) R.string.historyArchiveComplete
            else R.string.historyArchivePartial, windowsLoaded),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall)
        if (isLoggedIn && canLoadMore) {
            TextButton(onClick = onLoadAll, enabled = !busy,
                modifier = Modifier.testTag("history-load-all")) {
                Text(stringResource(R.string.historyLoadAll))
            }
        }
        if (error != null) {
            Text(stringResource(R.string.historyReadFailed), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = if (canLoadMore) onLoadAll else onRefresh, enabled = !busy) {
                Text(stringResource(R.string.historyRetry))
            }
        }
    }
}

@Composable
private fun HistoryAmount(label: Int, amount: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.historyWon, formatNumber(amount)),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun HistoryList(
    visible: List<Purchase>, filter: String, isLoggedIn: Boolean, busy: Boolean,
    canLoadMore: Boolean, onLoadMore: () -> Unit, state: LazyListState, modifier: Modifier,
    header: (@Composable () -> Unit)?,
) {
    LazyColumn(modifier = modifier.testTag("history-list"), state = state,
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "overview") {
            Column {
                header?.invoke()
                Text(stringResource(R.string.historyVisibleRounds, visible.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (visible.isEmpty()) item(key = "empty") {
            Text(stringResource(when {
                !isLoggedIn -> R.string.historyLoginToLoad
                filter == "winning" -> R.string.historyNoWinningRecords
                filter == "pending" -> R.string.historyNoPendingRecords
                busy -> R.string.historyLoadingRecords
                else -> R.string.historyNoRecords
            }), modifier = Modifier.padding(vertical = 28.dp))
        }
        itemsIndexed(visible, key = { _, item -> item.round }) { i, item -> HistoryCard(item, i) }
        if (isLoggedIn && canLoadMore) item(key = "more") {
            LoadMoreButton(loading = busy, onClick = onLoadMore)
        }
    }
}

/** 목록 하단/빈 화면 공용 "더 보기" — 로드 중엔 같은 자리에서 스피너로 교체. */
@Composable
private fun LoadMoreButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        } else {
            TextButton(onClick = onClick) {
                Text(
                    stringResource(R.string.historyLoadMore),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(item: Purchase, index: Int) {
    val winner = item.isWinning
    val dateStr = "%04d-%02d-%02d".format(item.date.year, item.date.monthValue, item.date.dayOfMonth)

    // 시그니처 모션: 스태거 등장 (fade + rise). ponytail: 스크롤로 재진입 시 재생됨 — 짧고 은은해 허용.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MotionSpecs.staggerDelay(index).toLong())
        shown = true
    }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MotionSpecs.emphasized(),
        label = "cardAppear",
    )

    val shape = MaterialTheme.shapes.large
    // 헤더 틴트: 당첨=골드(진하게), 대기=골드(옅게), 낙첨=틸 민트(뮤트).
    val headerAccent = if (item.checked && !winner) LgTeal else LgGold
    val headerBg = lerp(headerAccent, MaterialTheme.colorScheme.surfaceContainerLowest, if (winner) 0.70f else 0.86f)

    SectionCard(
        modifier = Modifier
            .graphicsLayer {
                alpha = appear
                translationY = (1f - appear) * 24.dp.toPx()
            }
            .then(if (winner) Modifier.border(2.dp, LgGold, shape) else Modifier),
        contentPadding = PaddingValues(0.dp),
    ) {
        // 헤더: 제 {n}회 + 날짜 + StatusPill (+ 추첨 완료 시 당첨번호 미니 볼 줄)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.roundLabel, item.round),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        dateStr,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when {
                    !item.checked -> StatusPill(stringResource(R.string.statusPending), PillTone.Pending)
                    winner -> StatusPill(
                        stringResource(R.string.rankWithEmoji, localizedRank(item.rank ?: "nowin")),
                        PillTone.Win,
                    )
                    else -> StatusPill(stringResource(R.string.statusNoWin), PillTone.Lose)
                }
            }
            // 회차 당첨번호 미니 볼 — 아래 게임 행의 할로 볼과 색으로 대조(사용자 피드백). 미추첨이면 없음.
            item.winningNumbers?.let { win ->
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    win.forEach { LottoBall(it, size = 24.dp) }
                    item.bonusNumber?.let { b ->
                        Text(
                            "+",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LottoBall(b, size = 24.dp)
                    }
                }
            }
        }

        // 본문: 게임 A~E — 글로시 볼 + 행별 상태
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item.numbers.forEachIndexed { i, nums ->
                GameRow(
                    letter = stringResource(R.string.historyGameLabel, i + 1),
                    nums = nums,
                    winningNumbers = item.winningNumbers,
                    bonusNumber = item.bonusNumber,
                    checked = item.checked,
                    gameRank = item.gameRanks?.getOrNull(i),
                )
            }
        }

        // 푸터: 당첨금 (골드 밴드)
        if (winner) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lerp(LgGold, MaterialTheme.colorScheme.surfaceContainerLowest, 0.70f))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.prizeLabel, formatNumber(item.prize)),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/**
 * 게임 1줄: 라벨 + 글로시 볼(당첨 강조/흐림/보너스 림) + 상태 필(우측 고정).
 * 좁은 기기에선 FlowRow 줄바꿈으로 필이 아래줄 왼쪽에 떨어지던 문제 → 볼 크기를 가용 폭에 맞춰
 * 축소해 항상 한 줄 유지 (갤럭시 유효 폭 384dp 사용자 리포트).
 */
@Composable
private fun GameRow(
    letter: String,
    nums: List<Int>,
    winningNumbers: List<Int>?,
    bonusNumber: Int?,
    checked: Boolean,
    gameRank: String?,
) {
    val hasResult = checked && winningNumbers != null
    val gameWinner = gameRank != null && gameRank != "nowin" && gameRank != "pending"
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(letter, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
            if (gameRank != null) StatusPill(
                text = if (gameWinner) stringResource(R.string.rankWithEmoji, localizedRank(gameRank))
                    else localizedRank(gameRank),
                tone = when {
                    gameWinner -> PillTone.Win
                    gameRank == "pending" -> PillTone.Pending
                    else -> PillTone.Lose
                },
            )
        }
        // 라벨·필이 자연 폭을 가져간 뒤 남는 폭을 볼들이 나눠 갖는다 (weight=나머지 전부).
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 하한 없음: 큰 글씨·긴 필로 폭이 더 좁아져도 필을 침범하는 대신 볼이 계속 줄어든다
            val gaps = (nums.size - 1).coerceAtLeast(0)
            val ball = ((maxWidth - 6.dp * gaps) / nums.size.coerceAtLeast(1))
                .coerceIn(0.dp, 32.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                nums.forEach { n ->
                    val isMatch = winningNumbers?.contains(n) == true
                    val isBonusMatch = bonusNumber != null && bonusNumber == n
                    LottoBall(
                        n = n,
                        size = ball,
                        dimmed = hasResult && !(isMatch || isBonusMatch),
                        // 맞은 번호는 잉크 할로 링 — 같은 계열 어두운 림은 볼에 묻혀 안 보임(사용자 피드백)
                        matched = hasResult && (isMatch || isBonusMatch),
                    )
                }
            }
        }
    }
}
