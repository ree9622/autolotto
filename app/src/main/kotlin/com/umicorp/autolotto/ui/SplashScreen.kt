package com.umicorp.autolotto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umicorp.autolotto.AppContainer
import com.umicorp.autolotto.R

/** 로컬 설정이 준비될 때까지 표시한다. 네트워크 로그인은 AppRoot가 담당한다. */
@Composable
fun SplashScreen(container: AppContainer, onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        // 설정 하이드레이션만 대기(로컬, 빠름). autoLogin은 AppRoot 소유 — 네트워크 지연이
        // 스플래시를 붙잡지 않는다(crosscheck R2: OkHttp 블로킹 시퀀스는 withTimeout으로 못 끊음).
        container.ensureHydrated()
        onFinished()
    }
    StartupContent()
}
@Composable
internal fun StartupContent() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(32.dp, 4.dp).background(colors.secondary, RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.appTitle), color = colors.onBackground,
                style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.primary,
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.startupLoading), color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
