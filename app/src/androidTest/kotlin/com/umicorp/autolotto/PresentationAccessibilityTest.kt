package com.umicorp.autolotto

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.umicorp.autolotto.ui.FloatingPillNav
import com.umicorp.autolotto.ui.LocalizedApp
import com.umicorp.autolotto.ui.StartupContent
import com.umicorp.autolotto.ui.TabItem
import com.umicorp.autolotto.ui.screen.CtaButton
import com.umicorp.autolotto.ui.theme.AutoLottoTheme
import com.umicorp.autolotto.ui.theme.DarkColors
import com.umicorp.autolotto.ui.theme.LightColors
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PresentationAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationAnnouncesSelectionAndChangesItOnClick() {
        val selected = mutableStateOf(0)
        compose.setContent {
            AutoLottoTheme { LocalizedApp("ko") {
                FloatingPillNav(listOf(TabItem(R.string.nav_home, Icons.Rounded.Home),
                    TabItem(R.string.nav_settings, Icons.Rounded.Settings)), selected.value,
                    { selected.value = it })
            } }
        }
        compose.onNodeWithText("홈").assertIsSelected()
        compose.onNodeWithText("설정").assertIsNotSelected().performClick()
        compose.onNodeWithText("설정").assertIsSelected()
        compose.onNodeWithText("홈").assertIsNotSelected()
    }

    @Test fun disabledButtonBlocksClickAndUsesReadableThemeText() {
        var clicks = 0
        var foreground = Color.Unspecified
        compose.setContent { AutoLottoTheme(darkTheme = true) {
            CtaButton(onClick = { clicks++ }, enabled = false) {
                foreground = LocalContentColor.current
                Text("사용 불가")
            }
        } }
        compose.onNodeWithText("사용 불가").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(0, clicks)
            assertTrue(contrast(foreground, DarkColors.surfaceContainerHighest) >= 4.5)
        }
    }

    @Test fun themePairsMeetBodyTextContrast() {
        for (colors in listOf(LightColors, DarkColors)) {
            for ((text, background) in listOf(
                colors.onSurface to colors.surfaceContainerLowest,
                colors.onSurfaceVariant to colors.surfaceContainerHighest,
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer,
            )) assertTrue("Contrast must be at least 4.5", contrast(text, background) >= 4.5)
        }
    }

    @Test fun startupLightUsesLocalizedName() = startup(false, "startup-light")
    @Test fun startupDarkUsesLocalizedName() = startup(true, "startup-dark")

    private fun startup(dark: Boolean, name: String) {
        compose.setContent { AutoLottoTheme(darkTheme = dark) {
            LocalizedApp("ko") { StartupContent() }
        } }
        compose.onNodeWithText("로또루틴").assertIsDisplayed()
        compose.onNodeWithText("AutoLotto").assertDoesNotExist()
        compose.onNodeWithText("설정을 불러오고 있습니다").assertIsDisplayed()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("ui-proof")!!
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
