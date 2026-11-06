@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.umicorp.autolotto.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.umicorp.autolotto.R
import com.umicorp.autolotto.data.FallbackPolicy
import com.umicorp.autolotto.data.NumberConfig720
import com.umicorp.autolotto.data.Rank720
import com.umicorp.autolotto.data.Slot720
import com.umicorp.autolotto.data.Ticket720
import com.umicorp.autolotto.data.WinningNumbers720
import com.umicorp.autolotto.data.lumpPrize
import com.umicorp.autolotto.data.matchedDigits
import com.umicorp.autolotto.dhlottery.PensionFailure
import com.umicorp.autolotto.dhlottery.PensionOrder
import com.umicorp.autolotto.dhlottery.PurchaseResult720
import com.umicorp.autolotto.dhlottery.Round720
import com.umicorp.autolotto.dhlottery.classifyPensionFailure
import com.umicorp.autolotto.ui.appViewModel
import com.umicorp.autolotto.ui.theme.LgAmber
import com.umicorp.autolotto.ui.util.formatNumber
import com.umicorp.autolotto.ui.util.localizedDayNames
import com.umicorp.autolotto.ui.vm.PensionBuyState
import com.umicorp.autolotto.ui.vm.PensionRoundGroup
import com.umicorp.autolotto.ui.vm.PensionViewModel
import com.umicorp.autolotto.ui.vm.pensionHistoryWindows
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 합성 자료로도 띄울 수 있도록 화면 입력을 한곳에 모은다. */
internal data class PensionUiState(
    val isLoggedIn: Boolean,
    val balance: Int,
    val config: NumberConfig720,
    val configLoaded: Boolean,
    val upcomingRound: Int,
    val saleOpen: Boolean,
    val purchasedRound: Int,
    val winning: WinningNumbers720?,
    val winningLoading: Boolean,
    val autoEnabled: Boolean,
    val autoDay: Int,
    val autoHour: Int,
    val autoMinute: Int,
    val tickets: List<Ticket720>,
    val winningByRound: Map<Int, WinningNumbers720>,
    val historyLoading: Boolean,
    val historyFailed: Boolean,
    val canLoadMore: Boolean,
    val saveFailed: Boolean = false,
    val windowsLoaded: Int = 0,
    val historyProgress: Int = 0,
    val loadingAll: Boolean = false,
    val loadedFrom: java.time.LocalDate? = null,
    val loadedTo: java.time.LocalDate? = null,
)

internal data class PensionActions(
    val onRetryWinning: () -> Unit = {},
    val onSaveConfig: (List<Slot720>, FallbackPolicy, Boolean) -> Unit = { _, _, _ -> },
    val onConsumeSaveFailed: () -> Unit = {},
    val onBuy: () -> Unit = {},
    val onConfirmFirst: () -> Unit = {},
    val onConfirmExtraAuto: (Int) -> Unit = {},
    val onConfirmExtraSet: () -> Unit = {},
    val onDismissBuy: () -> Unit = {},
    val onSetAutoEnabled: (Boolean) -> Unit = {},
    val onSetAutoDay: (Int) -> Boolean = { true },
    val onSetAutoTime: (Int, Int) -> Boolean = { _, _ -> true },
    val onRefreshHistory: () -> Unit = {},
    val onLoadMoreHistory: () -> Unit = {},
    val onLoadAllHistory: () -> Unit = {},
    val onNavigateToSettings: () -> Unit = {},
    val onNavigateToDeposit: () -> Unit = {},
    val onNavigateToNumbers: () -> Unit = {},
    val onNavigateToHistory: () -> Unit = {},
)

/** 연금 화면은 로또와 같은 홈·번호·내역 세 장으로 나뉜다. 위쪽 복권 전환에서 연금을 고르면 각 탭에 들어간다. */
internal enum class PensionPage { HOME, NUMBERS, HISTORY }

internal class PensionUi(val state: PensionUiState, val buyState: PensionBuyState, val actions: PensionActions)

/**
 * 연금 뷰모델 값을 한 번만 모아 세 장이 함께 쓴다. 안전 판단은
 * [com.umicorp.autolotto.dhlottery.PensionPurchaseManager]가 하고 화면은 표시만 한다.
 */
@Composable
internal fun rememberPensionUi(
    onNavigateToSettings: () -> Unit,
    onNavigateToDeposit: () -> Unit,
    onNavigateToNumbers: () -> Unit,
    onNavigateToHistory: () -> Unit,
): PensionUi {
    val vm: PensionViewModel = appViewModel()
    val context = LocalContext.current
    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val balance by vm.balance.collectAsState()
    val config by vm.config.collectAsState()
    val configLoaded by vm.configLoaded.collectAsState()
    val purchasedRound by vm.purchasedRound.collectAsState()
    val winning by vm.winning.collectAsState()
    val winningLoading by vm.winningLoading.collectAsState()
    val autoEnabled by vm.autoEnabled.collectAsState()
    val autoDay by vm.autoDay.collectAsState()
    val autoHour by vm.autoHour.collectAsState()
    val autoMinute by vm.autoMinute.collectAsState()
    val tickets by vm.tickets.collectAsState()
    val winningByRound by vm.winningByRound.collectAsState()
    val historyLoading by vm.historyLoading.collectAsState()
    val historyFailed by vm.historyFailed.collectAsState()
    val canLoadMore by vm.canLoadMore.collectAsState()
    val saveFailed by vm.saveFailed.collectAsState()
    val buyState by vm.buyState.collectAsState()
    val windowsLoaded by vm.windowsLoaded.collectAsState()
    val historyProgress by vm.historyProgress.collectAsState()
    val loadingAll by vm.loadingAll.collectAsState()
    val historyToday by vm.historyToday.collectAsState()
    val loadedRange = historyToday?.takeIf { windowsLoaded > 0 }?.let { today ->
        pensionHistoryWindows(today)[windowsLoaded - 1].first to today
    }

    // 회차와 판매 시간은 시간이 지나면 바뀐다. 화면이 보이는 동안만 다시 읽고, 결제 직전 검사는 관리자가 또 한다.
    var round by remember { mutableIntStateOf(vm.upcomingRound) }
    var saleOpen by remember { mutableStateOf(vm.isSaleOpenNow()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var tick = 0
            while (true) {
                round = vm.upcomingRound
                saleOpen = vm.isSaleOpenNow()
                // 추첨 뒤 새 당첨번호가 올라오거나 앞선 조회가 실패했을 때 5분마다 다시 받는다.
                val latest = vm.winning.value
                if (tick % 10 == 0 && !vm.winningLoading.value && (latest == null || latest.round < round - 1)) {
                    vm.loadWinning()
                }
                tick++
                delay(30_000)
            }
        }
    }

    return PensionUi(
        state = PensionUiState(
            isLoggedIn, balance, config, configLoaded, round, saleOpen, purchasedRound, winning, winningLoading,
            autoEnabled, autoDay, autoHour, autoMinute, tickets, winningByRound, historyLoading, historyFailed,
            canLoadMore, saveFailed, windowsLoaded, historyProgress, loadingAll,
            loadedRange?.first, loadedRange?.second,
        ),
        buyState = buyState,
        actions = PensionActions(
            onRetryWinning = vm::loadWinning,
            onSaveConfig = vm::saveConfig,
            onConsumeSaveFailed = vm::consumeSaveFailed,
            onBuy = vm::onBuyTap,
            onConfirmFirst = vm::confirmFirst,
            onConfirmExtraAuto = vm::confirmExtraAuto,
            onConfirmExtraSet = vm::confirmExtraSet,
            onDismissBuy = vm::dismissBuy,
            onSetAutoEnabled = { enabled ->
                vm.setAutoEnabled(enabled)
                // 6/45 자동구매와 같게: 켤 때 배터리 최적화 제외를 안내해 예약 구매를 놓치지 않게 한다.
                if (enabled && !isIgnoringBatteryOptimizations(context)) requestIgnoreBatteryOptimizations(context)
            },
            onSetAutoDay = vm::setAutoDay,
            onSetAutoTime = vm::setAutoTime,
            onRefreshHistory = vm::loadHistory,
            onLoadMoreHistory = vm::loadMoreHistory,
            onLoadAllHistory = vm::loadAllHistory,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToDeposit = onNavigateToDeposit,
            onNavigateToNumbers = onNavigateToNumbers,
            onNavigateToHistory = onNavigateToHistory,
        ),
    )
}

/** 연금 한 장. 구매 대화상자와 저장 실패 안내는 구매가 일어나는 번호 장에서만 띄운다(세 장이 함께 떠 있어도 한 번만). */
@Composable
internal fun PensionPageContent(
    page: PensionPage,
    state: PensionUiState,
    buyState: PensionBuyState,
    actions: PensionActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    if (page == PensionPage.NUMBERS) {
        LaunchedEffect(state.saveFailed) {
            if (state.saveFailed) {
                actions.onConsumeSaveFailed()
                snackbar.showSnackbar(resources.getString(R.string.pensionSaveFailed))
            }
        }
    }

    Scaffold(
        modifier = modifier.creamPageBackground(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (page) {
                                PensionPage.HOME -> R.string.pensionTitle
                                PensionPage.NUMBERS -> R.string.numberSetupTitle
                                PensionPage.HISTORY -> R.string.historyTitle
                            },
                        ),
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (page == PensionPage.HISTORY && state.isLoggedIn) {
                        IconButton(
                            onClick = actions.onRefreshHistory,
                            enabled = !state.historyLoading,
                            modifier = Modifier.testTag("pension-history-refresh"),
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.cdRefresh),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (page) {
                PensionPage.HOME -> PensionHomePane(state, actions)
                PensionPage.NUMBERS -> PensionNumbersPane(
                    state = state,
                    actions = actions,
                    onScheduleBlocked = {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.pensionScheduleBlocked)) }
                    },
                )
                PensionPage.HISTORY -> PensionHistoryPane(state, actions, onGoBuy = actions.onNavigateToNumbers)
            }
        }
    }

    if (page == PensionPage.NUMBERS) {
        PensionBuyDialogs(
            state = buyState,
            actions = actions,
            onShowHistory = {
                actions.onDismissBuy()
                actions.onNavigateToHistory()
            },
        )
    }
}

internal val PENSION_MAX_WIDTH = 720.dp

// ===== 홈 =====

/** 로또 홈과 같은 순서: 당첨번호 → 예치금 → 자동 구매 상태 → 번호 설정으로. */
@Composable
private fun PensionHomePane(state: PensionUiState, actions: PensionActions) {
    Column(
        Modifier
            .widthIn(max = PENSION_MAX_WIDTH)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("pension-home"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WinningCard(state, actions.onRetryWinning)
        if (state.purchasedRound >= state.upcomingRound) {
            StatusPill(stringResource(R.string.pensionPurchasedPill), PillTone.Win)
        }
        BalanceCard(state.balance, alertEnabled = false, threshold = 0, onNavigateToDeposit = actions.onNavigateToDeposit)
        AutoStatusCard(
            state.autoEnabled, state.autoDay, state.autoHour, state.autoMinute,
            offHint = stringResource(R.string.pensionAutoOffHint),
        )
        CtaButton(onClick = actions.onNavigateToNumbers, modifier = Modifier.testTag("pension-home-cta")) {
            Text(
                stringResource(R.string.buttonSetupNumbers),
                color = LocalContentColor.current,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ===== 번호·구매 =====

@Composable
private fun PensionNumbersPane(state: PensionUiState, actions: PensionActions, onScheduleBlocked: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = PENSION_MAX_WIDTH)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("pension-buy"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GamesCard(state, actions)
        BuyCta(state, actions)
        AutoCard(state, actions, onScheduleBlocked)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WinningCard(state: PensionUiState, onRetry: () -> Unit) {
    SectionCard(contentPadding = PaddingValues(16.dp)) {
        val winning = state.winning
        when {
            winning != null -> {
                Text(
                    stringResource(R.string.pensionLatestWinning, winning.round),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    winning.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PensionNumberRow(stringResource(R.string.pensionGroupLabel, winning.jo), winning.number)
                Spacer(Modifier.height(8.dp))
                PensionNumberRow(stringResource(R.string.pensionBonusLabel), winning.bonusNumber)
            }
            state.winningLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.pensionWinningLoading))
            }
            else -> {
                Text(stringResource(R.string.pensionWinningFailed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.pensionRetry)) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(
            stringResource(
                R.string.pensionNextDraw,
                state.upcomingRound,
                Round720.getDrawDate(state.upcomingRound).toString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GamesCard(state: PensionUiState, actions: PensionActions) {
    var editingSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    val config = state.config
    SectionCard(contentPadding = PaddingValues(vertical = 8.dp)) {
        SectionHeader(
            stringResource(R.string.pensionGamesTitle),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        BuyModeSelector(
            setMode = config.setMode,
            enabled = state.configLoaded,
            onChange = { actions.onSaveConfig(config.slots, config.fallback, it) },
        )
        if (config.setMode) {
            SetPreview()
        } else {
            HorizontalDivider()
            config.slots.forEachIndexed { i, slot ->
                SlotRow(
                    index = i,
                    slot = slot,
                    enabled = state.configLoaded,
                    onClick = { editingSlot = i },
                )
            }
            if (config.slots.any { it is Slot720.Manual }) {
                HorizontalDivider()
                FallbackSelector(config.fallback) { actions.onSaveConfig(config.slots, it, config.setMode) }
            }
        }
    }
    editingSlot?.let { i ->
        SlotEditDialog(
            index = i,
            initial = config.slots[i],
            onDismiss = { editingSlot = null },
            onConfirm = { slot ->
                editingSlot = null
                actions.onSaveConfig(config.slots.toMutableList().also { it[i] = slot }, config.fallback, config.setMode)
            },
        )
    }
}

/** 구매 방식: 게임마다 따로 고르기 / 같은 번호로 1~5조. 고른 쪽의 결과를 바로 아래에 풀어 쓴다. */
@Composable
private fun BuyModeSelector(setMode: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to R.string.pensionModeSeparate, true to R.string.pensionModeSet)
                .forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = setMode == value,
                        onClick = { if (setMode != value) onChange(value) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        modifier = Modifier.testTag(if (value) "pension-mode-set" else "pension-mode-separate"),
                    ) { Text(stringResource(label)) }
                }
        }
        Text(
            stringResource(if (setMode) R.string.pensionModeSetBody else R.string.pensionModeSeparateBody),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 세트 미리보기: 1~5조에 같은 번호 하나가 들어간다는 것을 그림으로 보여 준다. */
@Composable
private fun SetPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant)
            .padding(12.dp)
            .testTag("pension-set-preview"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..5).forEach { jo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pensionSetPreviewGroup, jo), Modifier.width(44.dp), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pensionSetPreviewNumber), letterSpacing = 2.sp, color = scheme.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.pensionSetPreviewCaption),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SlotRow(index: Int, slot: Slot720, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pension-slot-$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val configured = slot != Slot720.Unset
        val scheme = MaterialTheme.colorScheme
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (configured) scheme.primaryContainer else scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                ('A' + index).toString(),
                color = if (configured) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            slotLabel(slot),
            modifier = Modifier.weight(1f),
            color = if (configured) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (configured) FontWeight.SemiBold else FontWeight.Normal,
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun slotLabel(slot: Slot720): String = when (slot) {
    Slot720.Unset -> stringResource(R.string.pensionSlotUnset)
    Slot720.FullAuto -> stringResource(R.string.pensionSlotAuto)
    is Slot720.SemiAuto -> stringResource(R.string.pensionSlotSemi, slot.group)
    is Slot720.Manual -> stringResource(R.string.pensionSlotManual, slot.group, slot.digits.joinToString(""))
}

@Composable
private fun FallbackSelector(selected: FallbackPolicy, onSelect: (FallbackPolicy) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).selectableGroup()) {
        Text(
            stringResource(R.string.pensionFallbackTitle),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        listOf(
            FallbackPolicy.REASSIGN_ALL to R.string.pensionFallbackAll,
            FallbackPolicy.KEEP_GROUP_RANDOM to R.string.pensionFallbackKeepGroup,
            FallbackPolicy.GIVE_UP to R.string.pensionFallbackGiveUp,
        ).forEach { (policy, label) ->
            ChoiceRow(stringResource(label), selected == policy) { onSelect(policy) }
        }
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f))
    }
}

/** 슬롯 편집 대화상자의 선택지. "조 자동 + 번호 지정"은 [Slot720]과 같이 고를 수 없다. */
internal enum class SlotEditMode { NONE, AUTO, GROUP, MANUAL }

/** 입력을 슬롯으로 바꾼다. 번호 지정이 6자리가 아니면 null이고, 그동안 확인 버튼이 잠긴다. */
internal fun buildPensionSlot(mode: SlotEditMode, group: Int, digits: String): Slot720? = when (mode) {
    SlotEditMode.NONE -> Slot720.Unset
    SlotEditMode.AUTO -> Slot720.FullAuto
    SlotEditMode.GROUP -> Slot720.SemiAuto(group)
    SlotEditMode.MANUAL ->
        if (digits.length == 6 && digits.all { it in '0'..'9' }) Slot720.Manual(group, digits.map { it - '0' }) else null
}

@Composable
private fun SlotEditDialog(index: Int, initial: Slot720, onDismiss: () -> Unit, onConfirm: (Slot720) -> Unit) {
    var mode by rememberSaveable {
        mutableStateOf(
            when (initial) {
                Slot720.Unset -> SlotEditMode.NONE
                Slot720.FullAuto -> SlotEditMode.AUTO
                is Slot720.SemiAuto -> SlotEditMode.GROUP
                is Slot720.Manual -> SlotEditMode.MANUAL
            },
        )
    }
    var group by rememberSaveable {
        mutableIntStateOf(
            when (initial) {
                is Slot720.SemiAuto -> initial.group
                is Slot720.Manual -> initial.group
                else -> 1
            },
        )
    }
    var digits by rememberSaveable { mutableStateOf((initial as? Slot720.Manual)?.digits?.joinToString("") ?: "") }
    val result = buildPensionSlot(mode, group, digits)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pensionSlotEditTitle, ('A' + index).toString())) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        SlotEditMode.NONE to R.string.pensionSlotUnset,
                        SlotEditMode.AUTO to R.string.pensionSlotAuto,
                        SlotEditMode.GROUP to R.string.pensionModeGroup,
                        SlotEditMode.MANUAL to R.string.pensionModeManual,
                    ).forEach { (value, label) ->
                        ChoiceRow(stringResource(label), mode == value) { mode = value }
                    }
                }
                if (mode == SlotEditMode.GROUP || mode == SlotEditMode.MANUAL) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pensionPickGroup), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { g ->
                            FilterChip(
                                selected = group == g,
                                onClick = { group = g },
                                label = { Text(stringResource(R.string.pensionGroupLabel, g)) },
                            )
                        }
                    }
                }
                if (mode == SlotEditMode.MANUAL) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = digits,
                        onValueChange = { input -> digits = input.filter { it in '0'..'9' }.take(6) },
                        label = { Text(stringResource(R.string.pensionDigitsLabel)) },
                        supportingText = { Text("${digits.length}/6") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("pension-digits"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = result != null,
                onClick = { result?.let(onConfirm) },
                modifier = Modifier.testTag("pension-slot-confirm"),
            ) { Text(stringResource(R.string.buttonConfirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.buttonCancel)) }
        },
    )
}

@Composable
private fun BuyCta(state: PensionUiState, actions: PensionActions) {
    val games = state.config.gameCount
    val amount = games * PensionOrder.UNIT_PRICE
    val purchased = state.purchasedRound >= state.upcomingRound
    val blockedReason = when {
        !state.isLoggedIn -> stringResource(R.string.hintLoginRequired)
        !state.saleOpen -> stringResource(R.string.pensionSaleClosed)
        !state.configLoaded -> stringResource(R.string.pensionWinningLoading)
        !purchased && games == 0 -> stringResource(R.string.pensionNeedsGames)
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (purchased) StatusPill(stringResource(R.string.pensionPurchasedPill), PillTone.Win)
        CtaButton(
            onClick = actions.onBuy,
            enabled = blockedReason == null,
            modifier = Modifier.testTag("pension-buy-cta"),
        ) {
            Text(
                blockedReason ?: if (purchased) {
                    stringResource(R.string.pensionBuyExtra)
                } else {
                    stringResource(R.string.pensionBuyAmount, formatNumber(amount))
                },
                color = LocalContentColor.current,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.isLoggedIn) {
            val short = !purchased && state.balance < amount
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (short) R.string.pensionBalanceShort else R.string.pensionBalance,
                        formatNumber(state.balance),
                    ),
                    modifier = Modifier.weight(1f),
                    color = if (short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = actions.onNavigateToDeposit) {
                    Text(stringResource(R.string.pensionGoDeposit))
                }
            }
        } else {
            TextButton(onClick = actions.onNavigateToSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pensionGoLogin))
            }
        }
    }
}

@Composable
private fun AutoCard(state: PensionUiState, actions: PensionActions, onScheduleBlocked: () -> Unit) {
    var showTimePicker by remember { mutableStateOf(false) }
    val games = state.config.gameCount
    SectionCard(contentPadding = PaddingValues(vertical = 8.dp)) {
        SectionHeader(
            stringResource(R.string.pensionAutoTitle),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val hint = when {
            !state.isLoggedIn -> stringResource(R.string.hintLoginRequired)
            games == 0 -> stringResource(R.string.pensionNeedsGames)
            else -> null
        }
        SwitchRow(
            title = stringResource(R.string.pensionAutoSwitch),
            subtitle = hint?.let { text -> { Text(text, color = MaterialTheme.colorScheme.error) } },
            checked = state.autoEnabled,
            // 이미 켜져 있으면 게임이 0개여도 끌 수 있어야 한다.
            enabled = state.autoEnabled || hint == null,
            onChange = actions.onSetAutoEnabled,
        )
        if (state.autoEnabled) {
            HorizontalDivider()
            val dayNames = localizedDayNames()
            ListItem(
                colors = transparentListColors(),
                headlineContent = { Text(stringResource(R.string.settingPurchaseDay), fontWeight = FontWeight.Bold) },
                trailingContent = {
                    InlineDropdown(
                        selected = state.autoDay,
                        options = (1..7).toList(),
                        label = { stringResource(R.string.dayFormat, dayNames[it - 1]) },
                        onSelect = { day -> if (!actions.onSetAutoDay(day)) onScheduleBlocked() },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                colors = transparentListColors(),
                headlineContent = { Text(stringResource(R.string.settingPurchaseTime), fontWeight = FontWeight.Bold) },
                trailingContent = {
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(
                            "%02d:%02d".format(state.autoHour, state.autoMinute),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
            )
        }
    }
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = state.autoHour,
            initialMinute = state.autoMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                showTimePicker = false
                if (!actions.onSetAutoTime(hour, minute)) onScheduleBlocked()
            },
        )
    }
}

// ===== 구매 대화상자 =====

@Composable
private fun PensionBuyDialogs(state: PensionBuyState, actions: PensionActions, onShowHistory: () -> Unit) {
    val dismiss = actions.onDismissBuy
    when (state) {
        PensionBuyState.Idle -> Unit
        PensionBuyState.NeedsSetup -> NoticeDialog(R.string.pensionBuyTitle, stringResource(R.string.pensionNeedsGames), dismiss)
        is PensionBuyState.ConfirmingFirst -> ConfirmFirstDialog(state, actions)
        is PensionBuyState.PickingExtra -> PickExtraDialog(state, actions)
        PensionBuyState.InProgress -> AlertDialog(
            onDismissRequest = {}, // 진행 중 닫기 금지
            title = { Text(stringResource(R.string.pensionBuyTitle)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.pensionBuying))
                }
            },
            confirmButton = {},
        )
        is PensionBuyState.Success -> SuccessDialog(state, dismiss)
        PensionBuyState.AlreadyPurchased -> NoticeDialog(R.string.pensionBuyTitle, stringResource(R.string.pensionAlreadyPurchased), dismiss)
        PensionBuyState.SaleClosed -> NoticeDialog(R.string.pensionBuyTitle, stringResource(R.string.pensionSaleClosedBody), dismiss)
        PensionBuyState.RoundChanged -> NoticeDialog(R.string.pensionBuyTitle, stringResource(R.string.pensionRoundChanged), dismiss)
        PensionBuyState.NothingBought -> NoticeDialog(R.string.pensionBuyTitle, stringResource(R.string.pensionNothingBought), dismiss)
        PensionBuyState.NotLoggedIn -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.pensionBuyTitle)) },
            text = { Text(stringResource(R.string.pensionNotLoggedIn)) },
            confirmButton = {
                TextButton(onClick = { dismiss(); actions.onNavigateToSettings() }) {
                    Text(stringResource(R.string.pensionGoLogin))
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.buttonCancel)) } },
        )
        // 결제됐을 수 있으므로 다시 시도하는 버튼을 두지 않는다.
        PensionBuyState.ResultUnknown -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.pensionUnknownTitle)) },
            text = { Text(stringResource(R.string.pensionUnknownBody)) },
            confirmButton = {
                TextButton(onClick = onShowHistory, modifier = Modifier.testTag("pension-unknown-history")) {
                    Text(stringResource(R.string.pensionShowHistory))
                }
            },
        )
        is PensionBuyState.Failed -> {
            val needsDeposit = state.message?.let { "예치금" in it || "잔액" in it } == true
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.pensionFailedTitle)) },
                text = { Text(state.message ?: stringResource(R.string.pensionFailedBody)) },
                confirmButton = {
                    if (needsDeposit) {
                        TextButton(onClick = { dismiss(); actions.onNavigateToDeposit() }) {
                            Text(stringResource(R.string.pensionGoDeposit))
                        }
                    } else {
                        TextButton(onClick = dismiss) { Text(stringResource(R.string.buttonConfirm)) }
                    }
                },
                dismissButton = if (needsDeposit) {
                    { TextButton(onClick = dismiss) { Text(stringResource(R.string.buttonCancel)) } }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun NoticeDialog(titleRes: Int, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.buttonConfirm)) } },
    )
}

@Composable
private fun ConfirmFirstDialog(state: PensionBuyState.ConfirmingFirst, actions: PensionActions) {
    val config = state.config
    val amount = config.gameCount * PensionOrder.UNIT_PRICE
    AlertDialog(
        onDismissRequest = actions.onDismissBuy,
        title = { Text(stringResource(R.string.pensionBuyTitle)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.pensionConfirmSummary, state.round, config.gameCount, formatNumber(amount)),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                if (config.setMode) {
                    Text(stringResource(R.string.pensionSetConfirm))
                } else {
                    config.slots.forEachIndexed { i, slot ->
                        if (slot != Slot720.Unset) Text("${'A' + i}  ${slotLabel(slot)}")
                    }
                    if (config.slots.any { it is Slot720.Manual }) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                R.string.pensionConfirmFallback,
                                stringResource(
                                    when (config.fallback) {
                                        FallbackPolicy.REASSIGN_ALL -> R.string.pensionFallbackAll
                                        FallbackPolicy.KEEP_GROUP_RANDOM -> R.string.pensionFallbackKeepGroup
                                        FallbackPolicy.GIVE_UP -> R.string.pensionFallbackGiveUp
                                    },
                                ),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmFirst, modifier = Modifier.testTag("pension-confirm-pay")) {
                Text(stringResource(R.string.pensionPayAmount, formatNumber(amount)), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = actions.onDismissBuy) { Text(stringResource(R.string.buttonCancel)) } },
    )
}

/** 추가 구매 선택. 0은 세트를 뜻한다. */
@Composable
private fun PickExtraDialog(state: PensionBuyState.PickingExtra, actions: PensionActions) {
    var choice by rememberSaveable { mutableIntStateOf(1) }
    val isSet = choice == 0
    val games = if (isSet) PensionOrder.SET_GAMES else choice
    val amount = games * PensionOrder.UNIT_PRICE
    AlertDialog(
        onDismissRequest = actions.onDismissBuy,
        title = { Text(stringResource(R.string.pensionExtraTitle)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pensionExtraBody))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        FilterChip(
                            selected = choice == n,
                            onClick = { choice = n },
                            label = { Text(stringResource(R.string.pensionGamesCount, n)) },
                        )
                    }
                    FilterChip(
                        selected = isSet,
                        onClick = { choice = 0 },
                        label = { Text(stringResource(R.string.pensionModeSet)) },
                        modifier = Modifier.testTag("pension-extra-set"),
                    )
                }
                if (isSet) {
                    Text(
                        stringResource(R.string.pensionModeSetBody),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.pensionConfirmSummary, state.round, games, formatNumber(amount)),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (isSet) actions.onConfirmExtraSet() else actions.onConfirmExtraAuto(choice) }) {
                Text(stringResource(R.string.pensionPayAmount, formatNumber(amount)), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = actions.onDismissBuy) { Text(stringResource(R.string.buttonCancel)) } },
    )
}

@Composable
private fun SuccessDialog(state: PensionBuyState.Success, onDismiss: () -> Unit) {
    val result: PurchaseResult720 = state.result
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pensionSuccessTitle)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (result.confirmedByHistory) R.string.pensionSuccessHistory else R.string.pensionSuccessBody,
                        result.round,
                        result.tickets.size,
                    ),
                )
                result.tickets.forEach { ticket ->
                    PensionNumberRow(
                        stringResource(R.string.pensionGroupLabel, ticket.jo),
                        ticket.number,
                        maxBall = 28.dp,
                    )
                }
                result.partialFailure?.let { failure ->
                    val unknown = classifyPensionFailure(failure.cause) is PensionFailure.Unknown
                    Text(
                        stringResource(
                            if (unknown) R.string.pensionPartialUnknown else R.string.pensionPartialRejected,
                            failure.failedGames,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.recordFailed) {
                    Text(stringResource(R.string.pensionRecordFailed), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.buttonConfirm)) } },
    )
}

// ===== 자릿수 공 =====

/** 자리별 공 색. 모두 흰 글자와 명암비 4.5:1 이상이다(PensionDigitColorsTest). */
internal val PensionDigitColors = listOf(
    Color(0xFFC62828),
    Color(0xFFC2410C),
    Color(0xFF8A6D00),
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
    Color(0xFF6A1B9A),
)

/**
 * "3조" 표식과 자릿수 공 6개. 화면 낭독기에는 행 전체가 "3조 123456"으로 읽힌다.
 * [matchedDigits]가 null이면 추첨 전이고, 숫자면 뒤에서 그만큼의 자리만 색을 남겨 맞은 자리를 보여 준다.
 */
@Composable
internal fun PensionNumberRow(
    label: String,
    number: String,
    modifier: Modifier = Modifier,
    matchedDigits: Int? = null,
    groupMatched: Boolean = false,
    maxBall: Dp = 36.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val rowDescription = "$label $number"
    val labelChip: @Composable () -> Unit = {
        Box(
            Modifier
                .widthIn(min = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (groupMatched) scheme.primaryContainer else scheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (groupMatched) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    val balls: @Composable (Dp) -> Unit = { width ->
        val gap = 4.dp
        val ball = ((width - gap * 5) / 6).coerceIn(0.dp, maxBall)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            number.forEachIndexed { i, digit ->
                PensionDigitBall(
                    digit = digit,
                    color = PensionDigitColors[i % PensionDigitColors.size],
                    size = ball,
                    matched = matchedDigits != null && i >= number.length - matchedDigits,
                    muted = matchedDigits != null && i < number.length - matchedDigits,
                )
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = rowDescription }) {
        // 표식 폭은 글자 크기에 따라 변한다. 옆에 두면 공이 너무 작아질 때(큰 글자·긴 표식)만 위 줄로 올린다.
        val labelWidth = with(LocalDensity.current) {
            (MaterialTheme.typography.labelLarge.fontSize.toDp() * (label.length * 0.9f + 1f)) + 16.dp
        }.coerceAtLeast(44.dp)
        val fullWidth = maxWidth
        val besideWidth = fullWidth - labelWidth - 8.dp
        if ((besideWidth - 4.dp * 5) / 6 >= MIN_INLINE_BALL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                labelChip()
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { balls(besideWidth) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labelChip()
                balls(fullWidth)
            }
        }
    }
}

private val MIN_INLINE_BALL = 30.dp

@Composable
private fun PensionDigitBall(digit: Char, color: Color, size: Dp, matched: Boolean, muted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (muted) scheme.surfaceVariant else color)
            .then(if (matched) Modifier.border(2.dp, LgAmber, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // 공 크기에 맞춘 글자 크기라 큰 글자 설정에서도 공 밖으로 넘치지 않는다.
        val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
        Text(
            digit.toString(),
            color = if (muted) scheme.onSurfaceVariant else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            lineHeight = fontSize,
            style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
    }
}
