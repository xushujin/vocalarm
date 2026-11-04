package com.vocalarm.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One neumorphic colour scheme. Both the surface tones and the two light sources are derived from
 * six base colours so every screen keeps a single, coherent lighting model.
 */
@Immutable
data class VocalarmPalette(
    val id: String,
    @param:StringRes val nameRes: Int,
    val isDark: Boolean,
    val background: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val muted: Color,
    val danger: Color,
    val track: Color,
    val lightShadowAlpha: Float,
    val darkShadowAlpha: Float,
) {

    /** Cards sit a touch above the page, so depth survives even on dim screens. */
    val surface: Color
        get() = lerp(background, Color.White, if (isDark) 0.05f else 0.32f)

    /** Chips and small inset areas that need one more step of separation. */
    val surfaceHigh: Color
        get() = lerp(background, Color.White, if (isDark) 0.09f else 0.46f)

    /** A solid tint used behind icons instead of translucent overlays. */
    val accentTint: Color
        get() = lerp(background, accent, if (isDark) 0.24f else 0.13f)

    /** End colour for primary-action gradients. */
    val accentDeep: Color
        get() = lerp(accent, Color.Black, if (isDark) 0.10f else 0.24f)

    /** Hairline used to stop light surfaces from dissolving into each other. */
    val hairline: Color
        get() = lerp(darkShadow, Color.White, 0.22f).copy(alpha = if (isDark) 0.42f else 0.34f)

    val accentGradient: Brush
        get() = Brush.linearGradient(listOf(accent, accentDeep))

    val softGradient: Brush
        get() = Brush.linearGradient(listOf(surfaceHigh, surface))
}

object Palettes {

    val Mist = VocalarmPalette(
        id = "mist",
        nameRes = com.vocalarm.app.R.string.palette_mist,
        isDark = false,
        background = Color(0xFFE8EEF6),
        lightShadow = Color(0xFFFFFFFF),
        darkShadow = Color(0xFFB8C6DA),
        accent = Color(0xFF4A77DE),
        onAccent = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF1D2A3A),
        textSecondary = Color(0xFF5E7086),
        muted = Color(0xFF8B9AAD),
        danger = Color(0xFFC64B43),
        track = Color(0xFFD6E0EC),
        lightShadowAlpha = 0.95f,
        darkShadowAlpha = 0.55f,
    )

    val Sand = VocalarmPalette(
        id = "sand",
        nameRes = com.vocalarm.app.R.string.palette_sand,
        isDark = false,
        background = Color(0xFFF1E7DA),
        lightShadow = Color(0xFFFFFBF5),
        darkShadow = Color(0xFFCBB79F),
        accent = Color(0xFFB26B3F),
        onAccent = Color(0xFFFFF8F2),
        textPrimary = Color(0xFF33281E),
        textSecondary = Color(0xFF74614F),
        muted = Color(0xFF9C8773),
        danger = Color(0xFFB5463F),
        track = Color(0xFFDFCEB9),
        lightShadowAlpha = 0.95f,
        darkShadowAlpha = 0.48f,
    )

    val Moss = VocalarmPalette(
        id = "moss",
        nameRes = com.vocalarm.app.R.string.palette_moss,
        isDark = false,
        background = Color(0xFFE7EEE4),
        lightShadow = Color(0xFFFDFFF9),
        darkShadow = Color(0xFFB6C4B6),
        accent = Color(0xFF4A7A5E),
        onAccent = Color(0xFFF7FFF9),
        textPrimary = Color(0xFF25342A),
        textSecondary = Color(0xFF5F7265),
        muted = Color(0xFF8C9F90),
        danger = Color(0xFFB5463F),
        track = Color(0xFFCFDBCC),
        lightShadowAlpha = 0.95f,
        darkShadowAlpha = 0.48f,
    )

    val Blossom = VocalarmPalette(
        id = "blossom",
        nameRes = com.vocalarm.app.R.string.palette_blossom,
        isDark = false,
        background = Color(0xFFF4E8ED),
        lightShadow = Color(0xFFFFFAFC),
        darkShadow = Color(0xFFD2BCC5),
        accent = Color(0xFFB2526F),
        onAccent = Color(0xFFFFF8FA),
        textPrimary = Color(0xFF37262D),
        textSecondary = Color(0xFF7A626D),
        muted = Color(0xFFA18A95),
        danger = Color(0xFFB5464C),
        track = Color(0xFFE3CDD5),
        lightShadowAlpha = 0.95f,
        darkShadowAlpha = 0.48f,
    )

    val Ink = VocalarmPalette(
        id = "ink",
        nameRes = com.vocalarm.app.R.string.palette_ink,
        isDark = true,
        background = Color(0xFF1C2028),
        lightShadow = Color(0xFF303845),
        darkShadow = Color(0xFF0C0F14),
        accent = Color(0xFF8FB4FF),
        onAccent = Color(0xFF0A1220),
        textPrimary = Color(0xFFEDF2FA),
        textSecondary = Color(0xFFA6B3C6),
        muted = Color(0xFF7C8A9E),
        danger = Color(0xFFFF8F86),
        track = Color(0xFF2A303A),
        lightShadowAlpha = 0.62f,
        darkShadowAlpha = 0.78f,
    )

    val all: List<VocalarmPalette> = listOf(Mist, Sand, Moss, Blossom, Ink)

    val default: VocalarmPalette = Mist

    fun byId(id: String?): VocalarmPalette = all.firstOrNull { it.id == id } ?: default
}

/**
 * Shadow geometry shared by every surface. Slightly asymmetric (less side light, more vertical
 * depth) so cards read as pressed paper instead of glowing halos.
 */
@Immutable
data class NeuMetrics(
    val lightOffset: Dp = (-4).dp,
    val darkOffset: Dp = 6.dp,
    val blur: Dp = 15.dp,
    val layers: Int = 12,
)

val LocalPalette = staticCompositionLocalOf { Palettes.default }
val LocalNeuMetrics = staticCompositionLocalOf { NeuMetrics() }
