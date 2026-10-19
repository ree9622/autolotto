package com.umicorp.autolotto.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umicorp.autolotto.ui.theme.LgAmber
import com.umicorp.autolotto.ui.theme.LgGold
import com.umicorp.autolotto.ui.theme.MotionSpecs
import com.umicorp.autolotto.ui.theme.ctaGradient
import com.umicorp.autolotto.ui.theme.heroGradient
import com.umicorp.autolotto.ui.util.ballColor

/**
 * 로또 번호 공 (홈·번호·내역 공유). 실제 추첨표처럼 평평한 색과 선명한 숫자를 쓴다.
 * 공개 API는 기존 그대로 (n, size, selected, dimmed, bordered, onClick) + matched 추가.
 *
 * - [selected]=false: 미선택(번호 그리드) — 중립 톤(surfaceVariant), 흐린 광택.
 * - [dimmed]=true: 추첨 후 비당첨(내역) — ballColor 흐림.
 * - [bordered]=true: 보너스 — 강조 림.
 * - [matched]=true: 당첨 매치(내역) — 할로 링(볼‥카드색 갭‥링). 같은 계열 어두운 림(bordered)은
 *   볼 색에 묻힌다는 사용자 피드백 → 갭이 볼 색과 링을 분리해 5색 볼 전부에서 읽힌다.
 *   링 색: 라이트=앰버, 다크=크림(onSurface) — 둘 다 사용자 피드백 반영. 볼 본체는 링 안쪽
 *   크기로 스케일해 그려 캔디 그라디언트의 진한 가장자리를 보존한다(잘라내면 물빠져 보임).
 * - onClick != null: 탭 시 프레스 스프링(0.92 → overshoot 1.12 → settle), 선택 시 elevation 상승.
 *
 * ponytail: 공 숫자색 흰색은 ballColor 브랜드 처리의 일부(토큰 예외 = ballColor와 한 묶음).
 */
@Composable
fun LottoBall(
    n: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    selected: Boolean = true,
    dimmed: Boolean = false,
    bordered: Boolean = false,
    matched: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val base by animateColorAsState(
        targetValue = when {
            !selected -> neutral
            dimmed -> lerp(ballColor(n), neutral, 0.55f)
            else -> ballColor(n)
        },
        animationSpec = MotionSpecs.gentle(),
        label = "ballBase",
    )
    val fg = when {
        !selected -> MaterialTheme.colorScheme.onSurfaceVariant
        dimmed -> Color.White.copy(alpha = 0.7f)
        else -> Color.White
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 프레스 = 0.92로 눌림 → 놓으면 bouncy 스프링이 1.0으로 돌며 overshoot(≈1.12) 발생.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = MotionSpecs.bouncy(),
        label = "ballScale",
    )
    val rim = lerp(base, Color.Black, if (selected && !dimmed) 0.18f else 0.08f)
    val bonusRim = lerp(ballColor(n), Color.Black, 0.38f)
    // 당첨 매치 할로: 갭=카드색(SectionCard surfaceContainerLowest), 링=라이트 앰버/다크 크림(사용자 피드백).
    // 라이트 앰버(골드보다 한 톤 진함 — 흰 카드·노랑 볼과 분리)는 3dp, 다크 크림은 2dp 유지.
    val matchGap = MaterialTheme.colorScheme.surfaceContainerLowest
    val matchRing = LgAmber
    val matchRingWidth = 3.dp

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .drawBehind {
                val w = this.size.width
                val radius = w / 2 - if (matched) 4.dp.toPx() else 0f
                drawCircle(base, radius = radius)
                drawCircle(rim, radius = radius, style = Stroke(1.dp.toPx()))
                if (matched) {
                    // 갭(R-4..R-2, 그림자 가림용 불투명)·링(외곽 R 고정) — 갭을 살짝 넓혀 AA 이음새를 덮는다.
                    val ringPx = matchRingWidth.toPx()
                    drawCircle(matchGap, radius = w / 2 - 3.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                    drawCircle(matchRing, radius = w / 2 - ringPx / 2, style = Stroke(ringPx))
                }
            }
            .then(
                when {
                    bordered -> Modifier.border(2.5.dp, bonusRim, CircleShape)
                    !selected -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    else -> Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 숫자: 볼 비례 + 소형 볼 하한 11sp(미니 볼 가독성 피드백 2회 반영). lineHeight를 fontSize에
        // 맞추고 폰트 패딩을 없애야 상속 lineHeight(24sp) 탓에 글리프가 우하단으로 밀리는 치우침이 사라진다.
        val fs = (size.value * 0.36f).coerceAtLeast(11f).sp
        Text(
            text = n.toString(),
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = fs,
            lineHeight = fs,
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

/** 얇은 인쇄선으로 구분한 공용 작업 면. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * 홈 카운트다운용 번호표 머리말. 단색 인쇄면과 노란 기준선만 사용한다.
 * [title]은 상단 라벨(예: "제 1232회 추첨까지"), [content]에 큰 숫자 슬롯을 넣는다. 텍스트는 흰색 기준.
 */
@Composable
fun GradientBlobHero(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(heroGradient())
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).background(LgGold, RoundedCornerShape(2.dp)))
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

/**
 * 상태 강조 카드. [accent] 단색으로 채우며 장식성 그라데이션과 그림자는 쓰지 않는다.
 * [onClick] 지정 시 클릭 가능(예: 예치금 카드 → 상세).
 */
@Composable
fun ColorDrenchedCard(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Column(content = content)
    }
}

/** 톤얼 카드 — [accent]를 옅게 깐 부드러운 컨테이너(색 드렌치 대신 은은한 강조). 텍스트는 onSurface. */
@Composable
fun TonalCard(
    accent: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(lerp(accent, MaterialTheme.colorScheme.surface, 0.90f))
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            .padding(contentPadding),
        content = content,
    )
}

/** 설정 행의 절제된 아이콘 타일. */
@Composable
fun GlossyIconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** StatusPill 톤: 확인 대기 / 낙첨 / 당첨. */
enum class PillTone { Pending, Lose, Win, Neutral }

/** 상태 토널 필. [tone]에 따라 색을 고른다. 텍스트는 스킴 파생. */
@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        PillTone.Win -> scheme.secondaryContainer to scheme.onSecondaryContainer
        PillTone.Lose -> scheme.surfaceVariant to scheme.onSurfaceVariant
        PillTone.Pending -> scheme.primaryContainer to scheme.onPrimaryContainer
        PillTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 볼드 섹션 헤더 (이모지 헤더 대체). 화면이 넘기는 이모지도 그대로 표시. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

/** 단색 주 행동 버튼. */
@Composable
fun CtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 중립 작업 배경. 기존 호출부 이름은 호환을 위해 유지한다. */
@Composable
fun Modifier.creamPageBackground(): Modifier {
    val scheme = MaterialTheme.colorScheme
    return this.background(scheme.background)
}
