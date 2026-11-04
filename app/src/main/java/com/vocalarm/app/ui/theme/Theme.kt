package com.vocalarm.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val VocalarmTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 60.sp,
        lineHeight = 64.sp,
        letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)

/** Tabular figures keep the large clock from jittering while the minute changes. */
val TimeDisplayStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Light,
    fontSize = 62.sp,
    lineHeight = 68.sp,
    letterSpacing = (-2).sp,
    fontFeatureSettings = "tnum",
)

fun VocalarmPalette.toColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = textSecondary,
        onSecondary = background,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = track,
        onSurfaceVariant = textSecondary,
        error = danger,
        onError = Color.White,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = textSecondary,
        onSecondary = background,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = track,
        onSurfaceVariant = textSecondary,
        error = danger,
        onError = Color.White,
    )
}

@Composable
fun VocalarmTheme(
    palette: VocalarmPalette = Palettes.default,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val colorScheme = remember(palette) { palette.toColorScheme() }

    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !palette.isDark
            controller.isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalNeuMetrics provides NeuMetrics(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VocalarmTypography,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
