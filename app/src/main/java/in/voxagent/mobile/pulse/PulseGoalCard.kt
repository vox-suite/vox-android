package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*
import kotlin.math.*

@Composable
internal fun PulseGoalCard(
    goal: GoalView,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onEntry: ((String, () -> Unit) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    var amount by rememberSaveable(goal.id) { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val color =
        when (goal.status) {
            "behind" -> Color(0xFFE5A04C)
            "over_limit" -> CoralPulse
            "unavailable",
            "in_progress" -> SmokeDark
            else -> PulseMint
        }
    Surface(
        modifier.fillMaxWidth(),
        color = Color(0xFF151515),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.title, color = Mist, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (onRemove != null)
                    TextButton(onClick = { confirm = true }, enabled = !busy) {
                        Text("Delete", color = SmokeDark, fontSize = 11.sp)
                    }
            }
            Text(goal.status.replace('_', ' '), color = color, fontSize = 12.sp)
            Text(
                "${formatPulse(if (goal.status == "unavailable") null else goal.current, goal.unit)} of ${formatPulse(goal.target, goal.unit)}",
                color = Mist,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
            )
            LinearProgressIndicator(
                progress = { (goal.percent / 100).toFloat().coerceIn(0f, 1f) },
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        contentDescription = "${goal.title} progress"
                    },
                color = color,
                trackColor = BorderSubtle,
            )
            Text(
                goal.error
                    ?: listOfNotNull(
                            if (goal.status == "over_limit")
                                "${formatPulse((goal.current - goal.target).coerceAtLeast(0.0), goal.unit)} over target"
                            else
                                "${formatPulse(goal.remaining, goal.unit)} ${if (goal.direction == "at_most") "left" else "to go"}",
                            goal.period?.let { "this $it" },
                            goal.deadline?.let { "by $it" },
                            goal.days_left?.let { "$it days left" },
                            goal.per_week_needed?.let {
                                "need ${formatPulse(it, goal.unit)} a week"
                            },
                            goal.projected_on?.let { "on pace for $it" },
                        )
                        .joinToString(" · "),
                color = SmokeDark,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
            if (goal.kind == "saving" && onEntry != null)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        amount,
                        { amount = it },
                        label = { Text("Add ${goal.unit}", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        singleLine = true,
                    )
                    Button(
                        onClick = { onEntry(amount) { amount = "" } },
                        enabled = !busy && validEntry(amount) != null,
                    ) {
                        Text("Add")
                    }
                }
        }
    }
    if (confirm)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete goal?") },
            text = { Text(goal.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        onRemove?.invoke()
                    },
                    enabled = !busy,
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
}
