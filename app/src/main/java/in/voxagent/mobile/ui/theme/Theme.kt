package `in`.voxagent.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
        content = content,
    )
}
