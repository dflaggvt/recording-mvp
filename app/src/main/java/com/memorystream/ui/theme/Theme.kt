package com.memorystream.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.memorystream.R.array.com_google_android_gms_fonts_certs
)

private val interFont = GoogleFont("Inter")

private val InterFamily = FontFamily(
    Font(googleFont = interFont, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = interFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = interFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = interFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = interFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

object CalmColors {
    val GradientTop = Color(0xFF1A3040)
    val GradientBottom = Color(0xFF122535)
    val GradientMid = Color(0xFF162B3D)
    val GlassFill = Color.White.copy(alpha = 0.10f)
    val GlassBorder = Color.White.copy(alpha = 0.15f)
    val GlassHighFill = Color.White.copy(alpha = 0.14f)
    val Periwinkle = Color(0xFF5B9BD5)
    val Lavender = Color(0xFF8B9DC3)
    val SoftTeal = Color(0xFF4ECDC4)
    val MutedCoral = Color(0xFFD4726A)
    val SoftAmber = Color(0xFFD4A574)
    val SageGreen = Color(0xFF8FA67A)

    val ScreenGradient: Brush
        get() = Brush.verticalGradient(listOf(GradientTop, GradientMid, GradientBottom))

    val NavBarBackground = Color(0xFF0E1E2E).copy(alpha = 0.95f)
}

private val DarkColorScheme = darkColorScheme(
    primary = CalmColors.Periwinkle,
    secondary = CalmColors.Lavender,
    tertiary = CalmColors.SoftTeal,
    background = CalmColors.GradientBottom,
    surface = CalmColors.GlassFill,
    surfaceVariant = CalmColors.GlassHighFill,
    onPrimary = Color(0xFF0A1A2A),
    onSecondary = Color(0xFF0A1A2A),
    onBackground = Color.White.copy(alpha = 0.90f),
    onSurface = Color.White.copy(alpha = 0.90f),
    onSurfaceVariant = Color.White.copy(alpha = 0.50f),
    primaryContainer = CalmColors.Periwinkle.copy(alpha = 0.15f),
    onPrimaryContainer = Color.White.copy(alpha = 0.90f),
    tertiaryContainer = CalmColors.Lavender.copy(alpha = 0.12f),
    onTertiaryContainer = Color.White.copy(alpha = 0.85f),
    error = CalmColors.MutedCoral
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A5FA0),
    secondary = Color(0xFF6B5FA0),
    tertiary = Color(0xFF3A9990),
    background = Color(0xFFF5F3FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEECF5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1830),
    onSurface = Color(0xFF1A1830),
    onSurfaceVariant = Color(0xFF605880),
    primaryContainer = Color(0xFFE0DDFA),
    onPrimaryContainer = Color(0xFF1A1830),
    tertiaryContainer = Color(0xFFE5E0F0),
    onTertiaryContainer = Color(0xFF1A1830),
    error = Color(0xFFC45040)
)

val LocalCalmColors = staticCompositionLocalOf { CalmColors }

@Composable
fun MemoryStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalCalmColors provides CalmColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
