package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CryptoPayDarkColorScheme = darkColorScheme(
    primary = BinanceGold,
    onPrimary = ObsidianDeep,
    primaryContainer = BinanceGoldSoft,
    onPrimaryContainer = BinanceGoldBright,
    secondary = CyberCyan,
    onSecondary = ObsidianDeep,
    secondaryContainer = CyberCyanSoft,
    onSecondaryContainer = CyberCyan,
    tertiary = CryptoGreen,
    onTertiary = ObsidianDeep,
    tertiaryContainer = CryptoGreenSoft,
    onTertiaryContainer = CryptoGreen,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardElevated,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    outlineVariant = Color(0xFF1F242D),
    error = CryptoRed,
    onError = Color.White,
    errorContainer = CryptoRedSoft,
    onErrorContainer = CryptoRed
)

val CryptoPayShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CryptoPayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CryptoPayDarkColorScheme,
        typography = CryptoPayTypography,
        shapes = CryptoPayShapes,
        content = content
    )
}
