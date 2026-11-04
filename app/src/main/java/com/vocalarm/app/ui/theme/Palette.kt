package com.vocalarm.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single neumorphic colour scheme. Every palette carries both the surface colour and the two
 * shadow tints, because neumorphism is defined by the light direction rather than by Material roles.
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
)

object Palettes {

    val Mist = VocalarmPalette(
        id = "mist",
        nameRes = com.vocalarm.app.R.string.palette_mist,
        isDark = false,
        background = Color(0xFFE7EDF4),
        lightShadow = Color(0xFFFFFFFF),
        darkShadow = Color(0xFFBCC7D8),
        accent = Color(0xFF3F6FD1),
        onAccent = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF22303F),
        textSecondary = Color(0xFF6C7C8F),
        muted = Color(0xFF93A1B2),
        danger = Color(0xFFC0473F),
        track = Color(0xFFD3DDEA),
        lightShadowAlpha = 0.95f,
        darkShadowAlpha = 0.50f,
    )

    val Sand = VocalarmPalette(
        id = "sand",
        nameRes = com.vocalarm.app.R.string.palette_sand,
        isDark = false,
        background = Color(0xFFEFE5D8),
        lightShadow = Color(0xFFFFFBF4),
        darkShadow = Color(0xFFC7B59E),
        accent = Color(0xFFB06A3B),
        onAccent = Color(0xFFFFF8F1),
        textPrimary = Color(0xFF3A2F25),
        textSecondary = Color(0xFF7C6B5B),
        muted = Color(0xFFA2917F),
        danger = Color(0xFFB0453F),
        track = Color(0xFFDCCDB9),
        lightShadowAlpha = 0.95f,
        darkShadowAlpha = 0.45f,
    )

    val Moss = VocalarmPalette(
        id = "moss",
        nameRes = com.vocalarm.app.R.string.palette_moss,
        isDark = false,
        background = Color(0xFFE4EBE1),
        lightShadow = Color(0xFFFCFFF8),
        darkShadow = Color(0xFFB7C5B7),
        accent = Color(0xFF47765A),
        onAccent = Color(0xFFF7FFF9),
        textPrimary = Color(0xFF2A382E),
        textSecondary = Color(0xFF67796B),
        muted = Color(0xFF93A595),
        danger = Color(0xFFB0453F),
        track = Color(0xFFCFDACD),
        lightShadowAlpha = 0.95f,
        darkShadowAlpha = 0.45f,
    )

    val Blossom = VocalarmPalette(
        id = "blossom",
        nameRes = com.vocalarm.app.R.string.palette_blossom,
        isDark = false,
        background = Color(0xFFF2E5E9),
        lightShadow = Color(0xFFFFFAFC),
        darkShadow = Color(0xFFCDB9C1),
        accent = Color(0xFFAC4E6A),
        onAccent = Color(0xFFFFF7FA),
        textPrimary = Color(0xFF3B2A30),
        textSecondary = Color(0xFF7C6570),
        muted = Color(0xFFA48D97),
        danger = Color(0xFFB4404A),
        track = Color(0xFFE0CBD2),
        lightShadowAlpha = 0.95f,
        darkShadowAlpha = 0.45f,
    )

    val Ink = VocalarmPalette(
        id = "ink",
        nameRes = com.vocalarm.app.R.string.palette_ink,
        isDark = true,
        background = Color(0xFF22262D),
        lightShadow = Color(0xFF353C47),
        darkShadow = Color(0xFF111419),
        accent = Color(0xFF7FA8FF),
        onAccent = Color(0xFF0B1220),
        textPrimary = Color(0xFFE9EEF7),
        textSecondary = Color(0xFF9BA8BA),
        muted = Color(0xFF7A8798),
        danger = Color(0xFFFF8B80),
        track = Color(0xFF2C323A),
        lightShadowAlpha = 0.55f,
        darkShadowAlpha = 0.70f,
    )

    val all: List<VocalarmPalette> = listOf(Mist, Sand, Moss, Blossom, Ink)

    val default: VocalarmPalette = Mist

    fun byId(id: String?): VocalarmPalette = all.firstOrNull { it.id == id } ?: default
}

/** Shadow geometry shared by every neumorphic surface so the whole app has one light source. */
@Immutable
data class NeuMetrics(
    val lightOffset: Dp = (-5).dp,
    val darkOffset: Dp = 5.dp,
    val blur: Dp = 16.dp,
    val layers: Int = 12,
)

val LocalPalette = staticCompositionLocalOf { Palettes.default }
val LocalNeuMetrics = staticCompositionLocalOf { NeuMetrics() }
