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

// 다크: Lucky Gloss 팔레트를 어둡게 변주(테마 일관성 유지).
val DarkColors = darkColorScheme(
    primary = Color(0xFF62D6D6),
    onPrimary = Color(0xFF00373A),
    primaryContainer = LgTealInk,
    onPrimaryContainer = Color(0xFFB9E7E7),
    secondary = LgGold,
    onSecondary = Color(0xFF3A2E00),
    tertiary = Color(0xFF9BD97A),
    onTertiary = Color(0xFF10300A),
    background = Color(0xFF15161B),
    onBackground = Color(0xFFEDE6D6),
    surface = Color(0xFF15161B),
    onSurface = Color(0xFFEDE6D6),
    surfaceVariant = Color(0xFF3A3A40),
    onSurfaceVariant = Color(0xFFC7C2B6),
    outline = Color(0xFF6E6A5E),
)
