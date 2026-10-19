package com.umicorp.autolotto.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 로또 용지의 흰 면, 인쇄 잉크, 표시용 노랑에서 가져온 고정 색 체계.
 * 로또 공 색은 여기 없다 — 항상 com.umicorp.autolotto.ui.util.ballColor(n) 재사용.
 */

// 캔버스 / 표면
val LgCream = Color(0xFFF4F6F8)          // 종이 바깥의 중립 배경
val LgSurface = Color(0xFFFFFFFF)        // 번호표 표면
val LgSurfaceLow = Color(0xFFF8F9FA)     // 낮은 표면
val LgSurfaceVariant = Color(0xFFE9EDF1) // 중립 표면(미선택 공 등)

// 브랜드
val LgTeal = Color(0xFF1F4E79)           // 번호표의 파란 인쇄색
val LgTealInk = Color(0xFF17324D)        // 짙은 잉크
val LgGold = Color(0xFFF4C542)           // 표시용 노랑
val LgGreen = Color(0xFF2E7D62)          // 완료 상태
val LgAmber = Color(0xFFB56716)          // 경고

// 잉크 / 텍스트
val LgInk = Color(0xFF17212B)            // 본문 잉크
val LgMuted = Color(0xFF59636E)          // 보조 텍스트
val LgOutline = Color(0xFFD6DBE1)        // 용지 구분선
val LgError = Color(0xFFBA1A1A)

// CTA 딥네이비 그라디언트 (execBuy 등 핵심 버튼)
val LgCtaNavyStart = LgTealInk
val LgCtaNavyEnd = LgTealInk

/** 딥네이비 CTA 그라디언트 브러시 (좌상→우하). onPrimary=흰색. */
fun ctaGradient(): Brush = Brush.linearGradient(listOf(LgCtaNavyStart, LgCtaNavyEnd))

/** 홈 카운트다운 히어로용 liquid 그라디언트 (blue→teal→orange). */
fun heroGradient(): Brush = Brush.linearGradient(listOf(LgTealInk, LgTealInk))

val LightColors = lightColorScheme(
    primary = LgTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE7F0),
    onPrimaryContainer = Color(0xFF102A43),
    secondary = LgGold,
    onSecondary = Color(0xFF3A2E00),
    secondaryContainer = Color(0xFFFFF1B8),
    onSecondaryContainer = Color(0xFF3A2E00),
    tertiary = LgGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEFE8),
    onTertiaryContainer = Color(0xFF20330A),
    error = LgError,
    onError = Color.White,
    background = LgCream,
    onBackground = LgInk,
    surface = LgCream,
    onSurface = LgInk,
    surfaceVariant = LgSurfaceVariant,
    onSurfaceVariant = LgMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LgSurfaceLow,
    surfaceContainer = Color(0xFFF0F2F4),
    surfaceContainerHigh = Color(0xFFE9EDF1),
    surfaceContainerHighest = Color(0xFFE1E6EA),
    outline = LgOutline,
    outlineVariant = Color(0xFFE1E5E9),
)

// 밝은 화면과 같은 남색·중립색 체계. 모든 표면을 명시한다.
val DarkColors = darkColorScheme(
    primary = Color(0xFFAACBEA),
    onPrimary = Color(0xFF102A43),
    primaryContainer = LgTealInk,
    onPrimaryContainer = Color(0xFFDDE7F0),
    secondary = LgGold,
    onSecondary = Color(0xFF3A2E00),
    secondaryContainer = Color(0xFF4A3C10),
    onSecondaryContainer = Color(0xFFFFE7A0),
    tertiary = Color(0xFF9BD5BF),
    onTertiary = Color(0xFF08382A),
    tertiaryContainer = Color(0xFF174D3C),
    onTertiaryContainer = Color(0xFFC2EFDD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10161D),
    onBackground = Color(0xFFE3E9EF),
    surface = Color(0xFF10161D),
    onSurface = Color(0xFFE3E9EF),
    surfaceVariant = Color(0xFF303D49),
    onSurfaceVariant = Color(0xFFB7C3CF),
    surfaceContainerLowest = Color(0xFF151D26),
    surfaceContainerLow = Color(0xFF19232D),
    surfaceContainer = Color(0xFF1E2934),
    surfaceContainerHigh = Color(0xFF26323E),
    surfaceContainerHighest = Color(0xFF303D49),
    outline = Color(0xFF8593A1),
    outlineVariant = Color(0xFF3C4A57),
)
