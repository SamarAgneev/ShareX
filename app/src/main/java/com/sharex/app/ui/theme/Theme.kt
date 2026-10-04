package com.sharex.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sharex.app.data.ThemeMode

@Immutable
data class ShareXColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentAlt: Color,
    val onAccent: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    /** Fill of the selected bottom-navigation pill, chips and other accent containers. */
    val accentContainer: Color,
    val online: Color,
    val offline: Color,
    val bubbleOut: Color,
    val bubbleIn: Color,
    val isDark: Boolean,
) {
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accent, accentAlt))
    val softAccentBrush: Brush get() = Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), accentAlt.copy(alpha = 0.12f)))
}

private val Dark = ShareXColors(
    background = Color(0xFF080B10),
    surface = Color(0xFF10151C),
    surfaceHigh = Color(0xFF1A212B),
    outline = Color(0xFF232C39),
    text = Color(0xFFF4F6FB),
    textMuted = Color(0xFF9AA6B6),
    accent = Color(0xFF19E3D0),
    accentAlt = Color(0xFF5AF0E2),
    onAccent = Color(0xFF04201D),
    success = Color(0xFF22DD77),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFB547),
    accentContainer = Color(0xFF0D3F3B),
    online = Color(0xFF22DD77),
    offline = Color(0xFF7D8797),
    bubbleOut = Color(0xFF0B5A52),
    bubbleIn = Color(0xFF1A212B),
    isDark = true,
)

private val Light = ShareXColors(
    background = Color(0xFFF5F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEEF0F6),
    outline = Color(0xFFDDE1EA),
    text = Color(0xFF0E1116),
    textMuted = Color(0xFF5C6573),
    accent = Color(0xFF0E766E),
    accentAlt = Color(0xFF0F9D91),
    onAccent = Color.White,
    success = Color(0xFF0FA77F),
    danger = Color(0xFFE5484D),
    warning = Color(0xFFD98A00),
    accentContainer = Color(0xFFD2F1ED),
    online = Color(0xFF0FA77F),
    offline = Color(0xFF8A94A3),
    bubbleOut = Color(0xFFCBEDE8),
    bubbleIn = Color(0xFFFFFFFF),
    isDark = false,
)

val LocalShareXColors = staticCompositionLocalOf { Dark }

object ShareX {
    val colors: ShareXColors
        @Composable get() = LocalShareXColors.current
}

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun ShareXTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentAlt,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textMuted,
            surfaceContainerHigh = colors.surfaceHigh,
            surfaceContainer = colors.surface,
            surfaceContainerLow = colors.surface,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentAlt,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textMuted,
            surfaceContainerHigh = colors.surfaceHigh,
            surfaceContainer = colors.surface,
            surfaceContainerLow = colors.surface,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalShareXColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
