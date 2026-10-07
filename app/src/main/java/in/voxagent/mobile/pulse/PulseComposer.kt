package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*

@Composable
internal fun ColumnScope.PulseComposer(ui: PulseUiState, viewModel: PulseViewModel) {
    val goal = ui.mode == PulseMode.Goal
    var draft by rememberSaveable(ui.mode) { mutableStateOf("") }
    val messages = if (goal) ui.goalMessages else ui.messages
    val list = rememberLazyListState()
    LazyColumn(
        state = list,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (messages.isEmpty())
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (goal) "What do you want to achieve?" else "Build a chart by chatting",
                        color = Mist,
                        fontSize = 22.sp,
                    )
                    Text(
                        if (goal)
                            "Tell me a goal. I’ll track it from your data, or let you log progress yourself for things like savings."
                        else
                            "Describe it, then refine: change the time range, switch to weeks, make it a line. Your chart updates below.",
                        color = SmokeDark,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                    val examples =
                        if (goal)
                            listOf(
                                "Save ₹80,000 for a bike by December",
                                "Play under 10 hours of games a week",
                                "Spend less than ₹20k on food this month",
                            )
                        else
                            listOf(
                                "Hours I game each week",
                                "My top artists this month",
                                "Daily Spotify listening, last 30 days",
                            )
                    examples.forEach { example ->
                        OutlinedButton(
                            onClick = { viewModel.send(example) {} },
                            enabled = !ui.busy,
                        ) {
                            Text(example, fontSize = 12.sp)
                        }
                    }
                }
            }
        items(messages.size) { i ->
            val message = messages[i]
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (message.role == "user") Color(0xFF18352A) else Obsidian,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(start = if (message.role == "user") 20.dp else 0.dp),
            ) {
                Text(
                    message.content,
                    color = Mist,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (ui.busy) item { Text("Working on it…", color = PulseMint, fontSize = 12.sp) }
        item {
            if (goal && ui.goalPreview != null)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PulseGoalCard(ui.goalPreview)
                    Button(
                        onClick = viewModel::createGoal,
                        enabled = !ui.busy && ui.goalDraft != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create goal")
                    }
                }
            else if (!goal && ui.definition != null && ui.preview != null)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PulseRangeControls(ui.definition, ui.measurement?.buckets.orEmpty(), !ui.busy) {
                        viewModel.change(it)
                    }
                    PulseChartCard(ui.title, ui.definition, ui.preview) {
                        Button(
                            onClick = { viewModel.save() },
                            enabled = !ui.busy && ui.title.isNotBlank() && ui.preview.canSave,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add to Pulse")
                        }
                    }
                }
            else if (!ui.busy)
                Text(
                    if (goal) "Your goal will appear here" else "Your chart will appear here",
                    color = SmokeDark,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
        }
    }
    HorizontalDivider(color = BorderSubtle)
    Row(
        Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            draft,
            { draft = it.take(500) },
            placeholder = {
                Text(
                    if (goal) "Describe your goal" else "Describe the chart you want",
                    fontSize = 12.sp,
                )
            },
            modifier = Modifier.weight(1f),
            enabled = !ui.busy,
            maxLines = 4,
        )
        IconButton(
            onClick = { viewModel.send(draft) { draft = "" } },
            enabled = !ui.busy && draft.isNotBlank(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send to Pulse", tint = PulseMint)
        }
    }
}
