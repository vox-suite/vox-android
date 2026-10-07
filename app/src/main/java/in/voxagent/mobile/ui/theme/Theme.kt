package `in`.voxagent.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun androidx.compose.ui.text.TextStyle.withInter() = copy(fontFamily = VoxInterFontFamily)

private val VoxTypography =
    Typography().let { base ->
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

val VoxShapes =
    androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(100.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(9999.dp),
        extraLarge = RoundedCornerShape(40.dp),
    )

val VoxLightColorScheme =
    lightColorScheme(
        background = Parchment,
        onBackground = OffBlack,
        surface = Parchment,
        onSurface = OffBlack,
        surfaceVariant = PeriwinkleMist,
        onSurfaceVariant = Graphite,
        primary = LakeBlue,
        onPrimary = PureWhite,
        secondary = OffBlack,
        onSecondary = Parchment,
        tertiary = Coral,
        onTertiary = PureWhite,
        error = Crimson,
        onError = PureWhite,
        outline = Ash,
        outlineVariant = Ash.copy(alpha = 0.5f),
        scrim = Color.Black,
    )

val VoxDarkColorScheme =
    darkColorScheme(
        background = ParchmentDark,
        onBackground = OffBlackInverted,
        surface = CardDark,
        onSurface = OffBlackInverted,
        surfaceVariant = PeriwinkleDark,
        onSurfaceVariant = GraphiteDark,
        primary = LakeBlueDark,
        onPrimary = PureWhite,
        secondary = OffBlackInverted,
        onSecondary = ParchmentDark,
        tertiary = Coral,
        onTertiary = PureWhite,
        error = Crimson,
        onError = PureWhite,
        outline = AshDark,
        outlineVariant = AshDark.copy(alpha = 0.5f),
        scrim = Color.Black,
    )

@Composable
fun VoxTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) VoxDarkColorScheme else VoxLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = VoxShapes,
        typography = VoxTypography,
        content = content,
    )
}
