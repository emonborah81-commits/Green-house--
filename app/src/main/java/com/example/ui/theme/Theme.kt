package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val GreenHouseLightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = PureWhite,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = ForestDeep,
    secondary = EmeraldMuted,
    onSecondary = PureWhite,
    secondaryContainer = IvorySurfaceVariant,
    onSecondaryContainer = ForestDeep,
    tertiary = CrestGoldDeep,
    onTertiary = PureWhite,
    tertiaryContainer = CrestGoldWash,
    onTertiaryContainer = CrestGoldDeep,
    background = IvoryCanvas,
    onBackground = InkPrimary,
    surface = PureWhite,
    onSurface = InkPrimary,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = EmeraldBorder,
    outlineVariant = ColorTokens.SubtleDivider,
    error = CrimsonError,
    onError = PureWhite,
    errorContainer = CrimsonWash,
    onErrorContainer = CrimsonError
)

private val GreenHouseDarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = NightInk,
    secondary = EmeraldMuted,
    onSecondary = PureWhite,
    secondaryContainer = NightSurfaceElevated,
    onSecondaryContainer = NightInk,
    tertiary = NightGold,
    onTertiary = NightOnPrimary,
    tertiaryContainer = NightGoldContainer,
    onTertiaryContainer = NightGold,
    background = NightCanvas,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceElevated,
    onSurfaceVariant = NightInkMuted,
    outline = EmeraldMuted,
    outlineVariant = NightPrimaryContainer,
    error = CrimsonError,
    onError = PureWhite,
    errorContainer = CrimsonWash,
    onErrorContainer = CrimsonError
)

private object ColorTokens {
    val SubtleDivider = androidx.compose.ui.graphics.Color(0xFFE4E1D6)
}

val GreenHouseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GreenHouseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GreenHouseDarkColorScheme else GreenHouseLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GreenHouseShapes,
        content = content
    )
}
