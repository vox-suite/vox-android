package `in`.voxagent.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun androidx.compose.ui.text.TextStyle.withInter() = copy(fontFamily = VoxInterFontFamily)

private val VoxTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.withInter(),
        displayMedium = base.displayMedium.withInter(),
        displaySmall = base.displaySmall.withInter(),
        headlineLarge = base.headlineLarge.withInter(),
        headlineMedium = base.headlineMedium.withInter(),
        headlineSmall = base.headlineSmall.withInter(),
        titleLarge = base.titleLarge.withInter(),
        titleMedium = base.titleMedium.withInter(),
        titleSmall = base.titleSmall.withInter(),
        bodyLarge = base.bodyLarge.withInter(),
        bodyMedium = base.bodyMedium.withInter(),
        bodySmall = base.bodySmall.withInter(),
        labelLarge = base.labelLarge.withInter(),
        labelMedium = base.labelMedium.withInter(),
        labelSmall = base.labelSmall.withInter(),
    )
}

val VoxShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

private val VoxColorScheme = darkColorScheme(
    background = VoidBlack,
    onBackground = PureWhite,
    surface = Ink,
    onSurface = PureWhite,
    surfaceVariant = Obsidian,
    onSurfaceVariant = Ash,
    primary = Mist,
    onPrimary = Iron,
    secondary = Obsidian,
    onSecondary = Mist,
    tertiary = CoralPulse,
    onTertiary = PureWhite,
    error = EmberHush,
    onError = CoralPulse,
    outline = Slate,
    outlineVariant = BorderSubtle,
    scrim = Color.Black,
)

@Composable
fun VoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VoxColorScheme,
        shapes = VoxShapes,
        typography = VoxTypography,
        content = content,
    )
}
