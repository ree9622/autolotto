package com.umicorp.autolotto.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umicorp.autolotto.R
import com.umicorp.autolotto.appContainer
import com.umicorp.autolotto.data.DepositAccount
import com.umicorp.autolotto.ui.theme.LgGreen
import com.umicorp.autolotto.ui.theme.LgTealInk
import com.umicorp.autolotto.ui.util.launchActivitySafely
import com.umicorp.autolotto.ui.vm.DepositUiState
import com.umicorp.autolotto.ui.vm.DepositViewModel

/**
 * 네이티브 동행복권 예치금 충전 화면.
 *
 * 케이뱅크 전용 가상계좌 발급 프로세스를 네이티브로 제공하며,
 * 원클릭 계좌번호 복사, 금융 앱(토스, 카카오뱅크 등) 바로가기,
 * 충전 후 실시간 예치금 잔액 확인을 지원합니다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DepositScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null,
    vm: DepositViewModel = viewModel(factory = LocalContext.current.appContainer.viewModelFactory),
) {
    val context = LocalContext.current
    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val balance by vm.balance.collectAsState()
    val selectedAmount by vm.selectedAmount.collectAsState()
    val customAmount by vm.customAmount.collectAsState()
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.depositTitle),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cdBack),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 현재 예치금 잔액 카드
            ColorDrenchedCard(accent = LgTealInk) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.depositCurrentBalance),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "₩${formatNumber(balance)}",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { vm.refreshBalance() },
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.cdRefresh),
                        )
                    }
                }
            }

            if (!isLoggedIn) {
                // 비로그인 안내
                TonalCard(accent = MaterialTheme.colorScheme.error) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.depositLoginRequired),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.hintLoginRequired),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (onNavigateToSettings != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.buttonLogin))
                        }
                    }
                }
            } else {
                when (val state = uiState) {
                    is DepositUiState.Success -> {
                        // 발급 성공 화면
                        DepositSuccessView(
                            account = state.account,
                            onRefreshBalance = { vm.refreshBalance() },
                            onNewDeposit = { vm.reset() },
                        )
                    }
                    is DepositUiState.Loading -> {
                        // 로딩 화면
                        SectionCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.depositIssuing),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    else -> {
                        // 금액 선택 및 발급 요청 화면
                        if (state is DepositUiState.Error) {
                            TonalCard(accent = MaterialTheme.colorScheme.error) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.depositIssueFailed, state.message),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }

                        // 금액 선택 섹션
                        SectionCard {
                            SectionHeader(stringResource(R.string.depositSelectAmount))
                            Spacer(Modifier.height(8.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                vm.presetAmounts.forEach { amt ->
                                    val isSelected = selectedAmount == amt && customAmount.isEmpty()
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { vm.selectPresetAmount(amt) },
                                        label = {
                                            Text(
                                                "₩${formatNumber(amt)}",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = customAmount,
                                onValueChange = { vm.setCustomAmount(it) },
                                label = { Text(stringResource(R.string.depositCustomAmount)) },
                                placeholder = { Text(stringResource(R.string.depositAmountHint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                prefix = { Text("₩ ", fontWeight = FontWeight.Bold) },
                                suffix = { Text("원") },
                            )
                        }

                        // 유의사항 안내
                        DepositNoticeCard()

                        // 발급 버튼
                        Spacer(Modifier.height(8.dp))
                        CtaButton(
                            onClick = { vm.requestVirtualAccount() },
                            enabled = selectedAmount >= 5_000,
                        ) {
                            Icon(
                                Icons.Rounded.AccountBalance,
                                contentDescription = null,
                                tint = Color.White,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selectedAmount >= 5_000) {
                                    "₩${formatNumber(selectedAmount)} " + stringResource(R.string.depositBtnIssue)
                                } else {
                                    stringResource(R.string.depositBtnIssue)
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 발급 성공 후 가상계좌 정보 및 송금 안내 컴포넌트.
 */
@Composable
private fun DepositSuccessView(
    account: DepositAccount,
    onRefreshBalance: () -> Unit,
    onNewDeposit: () -> Unit,
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 성공 배너
        TonalCard(accent = LgGreen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = LgGreen,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.depositIssueSuccess),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.depositNotice3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 가상계좌 상세 카드
        SectionCard {
            Text(
                stringResource(R.string.depositAccountTitle),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            // 은행 및 계좌번호
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        account.bankName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        account.formattedAccount,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                    )
                }

                FilledTonalButton(
                    onClick = {
                        copyToClipboard(context, account.accountNumber)
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.depositCopyAccount))
                }
            }

            Spacer(Modifier.height(16.dp))

            // 입금 세부정보 항목들
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DepositDetailRow(
                    label = "입금 금액",
                    value = "₩${formatNumber(account.amount)}",
                    valueColor = MaterialTheme.colorScheme.primary,
                    isBold = true,
                )
                if (account.buyerName.isNotBlank()) {
                    DepositDetailRow(
                        label = "입금자명",
                        value = account.buyerName,
                    )
                }
                if (account.expirationDate.isNotBlank()) {
                    DepositDetailRow(
                        label = "입금 기한",
                        value = account.expirationDate,
                    )
                }
            }
        }

        // 빠른 간편 송금 버튼들
        SectionCard {
            SectionHeader("간편 송금 앱으로 바로 입금")
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 토스로 송금
                Button(
                    onClick = {
                        openTossSend(context, account.accountNumber, account.amount)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0064FF),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("토스 송금", fontWeight = FontWeight.Bold)
                }

                // 카카오뱅크 열기
                Button(
                    onClick = {
                        openKakaoBank(context)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFE812),
                        contentColor = Color(0xFF381E1F),
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("카카오뱅크", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 잔액 새로고침 & 다른 금액 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = onRefreshBalance,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.depositRefreshBalance))
            }

            OutlinedButton(
                onClick = onNewDeposit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("다른 금액 충전")
            }
        }

        // 안내문
        DepositNoticeCard()
    }
}

@Composable
private fun DepositDetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * 입금 주의사항 카드.
 */
@Composable
private fun DepositNoticeCard() {
    TonalCard(accent = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "충전 시 꼭 확인해주세요",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "• " + stringResource(R.string.depositNotice1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "• " + stringResource(R.string.depositNotice2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "• " + stringResource(R.string.depositNotice3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 클립보드에 계좌번호 복사 및 토스트 피드백.
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("케이뱅크 가상계좌", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.depositAccountCopied), Toast.LENGTH_SHORT).show()
}

/**
 * 토스 간편 송금 열기 (supertoss 딥링크).
 */
private fun openTossSend(context: Context, accountNo: String, amount: Int) {
    // 토스 송금 딥링크: supertoss://send?bank=케이뱅크&accountNo=...&amount=...
    val uri = Uri.parse("supertoss://send?bank=케이뱅크&accountNo=$accountNo&amount=$amount")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.launchActivitySafely(intent)
    } catch (_: Exception) {
        // 토스 미설치 시 플레이스토어로 연결
        context.launchActivitySafely(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=viva.republica.toss")),
        )
    }
}

/**
 * 카카오뱅크 열기.
 */
private fun openKakaoBank(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.kakaobank.channel")
    if (intent != null) {
        context.launchActivitySafely(intent)
    } else {
        context.launchActivitySafely(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.kakaobank.channel")),
        )
    }
}

private fun formatNumber(n: Int): String = "%,d".format(n)
