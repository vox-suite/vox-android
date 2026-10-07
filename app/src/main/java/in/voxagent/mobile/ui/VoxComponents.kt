package `in`.voxagent.mobile.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.EmberHush
import `in`.voxagent.mobile.ui.theme.Ink
import `in`.voxagent.mobile.ui.theme.Iron
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.theme.SuccessGreen
import `in`.voxagent.mobile.ui.theme.VoidBlack

enum class VoxStatusTone {
    Success,
    Neutral,
    Warning,
    Danger,
}

@Composable
fun VoxStatusPill(text: String, tone: VoxStatusTone) {
    val (bg, fg) =
        when (tone) {
            VoxStatusTone.Success -> SuccessGreen.copy(alpha = 0.15f) to SuccessGreen
            VoxStatusTone.Danger -> EmberHush to CoralPulse
            VoxStatusTone.Warning -> CoralPulse.copy(alpha = 0.15f) to CoralPulse
            VoxStatusTone.Neutral ->
                MaterialTheme.colorScheme.surfaceVariant to
                    MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.lowercase(),
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
fun VoxPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.size(10.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun VoxTextButton(
    text: String,
    modifier: Modifier = Modifier,
    tone: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = tone ?: Smoke, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun VoxSettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Ink)
                .border(
                    BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.5f)),
                    RoundedCornerShape(18.dp),
                ),
        content = content,
    )
}

@Composable
fun VoxSettingsRow(
    label: String,
    trailingText: String? = null,
    trailingTone: VoxStatusTone = VoxStatusTone.Neutral,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClick,
                            )
                        } else Modifier
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = Mist, fontSize = 15.sp, fontWeight = FontWeight.Normal)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (trailingText != null) VoxStatusPill(trailingText, trailingTone)
                if (onClick != null) {
                    Text(text = "›", color = Smoke, fontSize = 18.sp)
                }
            }
        }
        if (showDivider) {
            Box(
                modifier =
                    Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
fun VoxDarkScreen(content: @Composable BoxScope.() -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller =
            (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (controller != null && previous != null)
                controller.isAppearanceLightStatusBars = previous
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(VoidBlack), content = content)
}

@Composable
fun voxFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = Mist,
        unfocusedTextColor = Mist,
        disabledTextColor = SmokeDark,
        focusedBorderColor = Mist,
        unfocusedBorderColor = Iron,
        disabledBorderColor = Iron,
        cursorColor = Mist,
        focusedPlaceholderColor = SmokeDark,
        unfocusedPlaceholderColor = SmokeDark,
    )
