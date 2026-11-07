package com.mio.os.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mio.os.core.MioApp
import com.mio.os.core.MioSettings

object MioColors {
    val Bg = Color(0xFF04030A)
    val BgSoft = Color(0xFF0B0716)
    val Surface = Color(0xFF120C22)
    val SurfaceHigh = Color(0xFF1A1230)
    val Text = Color(0xFFF6F3FF)
    val Muted = Color(0xFF9C94B8)
    val Dim = Color(0xFF6B6484)
    val Purple = Color(0xFF7C5CFF)
    val PurpleSoft = Color(0xFFA78BFA)
    val PurpleDeep = Color(0xFF2A1A5E)
    val Blue = Color(0xFF3C7DFF)
    val BlueTile = Color(0xFF2F6BF0)
    val Green = Color(0xFF3DDC97)
    val Pink = Color(0xFFF472B6)
    val Amber = Color(0xFFFFC46B)
    val Red = Color(0xFFFF6B81)
    val Border = Color(0x1FFFFFFF)
    val Glass = Color(0x12FFFFFF)
}

object MioType {
    val clock = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 66.sp,
        letterSpacing = (-2.5).sp,
        color = MioColors.Text,
    )
    val h1 = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
        letterSpacing = (-0.4).sp, color = MioColors.Text,
    )
    val h2 = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
        letterSpacing = (-0.2).sp, color = MioColors.Text,
    )
    val title = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MioColors.Text,
    )
    val body = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 13.sp, color = MioColors.Muted, lineHeight = 18.sp,
    )
    val label = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 11.sp, color = MioColors.Muted, letterSpacing = 0.3.sp,
    )
    val micro = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 10.sp, color = MioColors.Dim, letterSpacing = 0.4.sp,
    )
}

val LocalAccent = staticCompositionLocalOf { MioColors.Purple }
val LocalSettings = staticCompositionLocalOf { MioSettings() }
val LocalApps = staticCompositionLocalOf<List<MioApp>> { emptyList() }

/** Accent-aware helper so every screen uses the single user accent colour. */
val accentColor: Color
    @Composable get() = LocalAccent.current

@Composable
fun MioTheme(settings: MioSettings, content: @Composable () -> Unit) {
    val accent = Color(settings.accent)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = MioColors.PurpleSoft,
        background = MioColors.Bg,
        onBackground = MioColors.Text,
        surface = MioColors.Surface,
        onSurface = MioColors.Text,
        surfaceVariant = MioColors.SurfaceHigh,
        onSurfaceVariant = MioColors.Muted,
        outline = MioColors.Border,
        error = MioColors.Red,
    )
    val typography = Typography(
        headlineLarge = MioType.h1,
        headlineMedium = MioType.h2,
        titleMedium = MioType.title,
        bodyMedium = MioType.body,
        labelMedium = MioType.label,
    )
    CompositionLocalProvider(LocalAccent provides accent, LocalSettings provides settings) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Distinct pastel for each app so letter-avatars look intentional. */
fun hueTint(seed: String): Color {
    val palette = listOf(
        Color(0xFF7C5CFF), Color(0xFF4D8DFF), Color(0xFF34D399), Color(0xFFF472B6),
        Color(0xFFFFB86B), Color(0xFF9F7AEA), Color(0xFF56CCF2), Color(0xFFFF6B81),
    )
    var h = 7
    for (c in seed) h = (h * 31 + c.code) and 0x7FFFFFFF
    return palette[h % palette.size]
}
