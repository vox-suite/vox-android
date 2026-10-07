package `in`.voxagent.mobile.spaces

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpaceChatSheet(ui: SpacesUiState, onClose: () -> Unit, viewModel: SpacesViewModel) {
    val graph = ui.graph ?: return
    val space = graph.space
    var draft by rememberSaveable(space.id) { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(ui.messages.size, space.run_state) {
        if (ui.messages.isNotEmpty()) list.animateScrollToItem(ui.messages.size)
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Obsidian,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding()) {
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Eyebrow("SPACE CHAT")
                Text(space.title, color = Mist, fontSize = 20.sp)
                Text(space.intent, color = SmokeDark, fontSize = 12.sp, maxLines = 3)
            }
            ErrorBanner(ui.error)
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "mission") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        space.mission
                            ?.takeIf { it.isNotBlank() }
                            ?.let { mission ->
                                Surface(
                                    color = VoidBlack,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Eyebrow("MISSION")
                                        Text(
                                            mission,
                                            color = Mist,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                        space.run_error?.let { error ->
                            ErrorBanner(error)
                            OutlinedButton(
                                onClick = {
                                    viewModel.send(
                                        ui.messages.lastOrNull { it.role == "user" }?.text
                                            ?: space.intent
                                    )
                                },
                                enabled = !ui.busy && space.editable,
                            ) {
                                Text("Retry research")
                            }
                        }
                        val stale = graph.nodes.filter { it.state == "stale" }
                        if (stale.isNotEmpty())
                            OutlinedButton(
                                onClick = {
                                    viewModel.send(
                                        "Please refresh and re-evaluate the following stale nodes: ${stale.joinToString(", ") { "\"${it.title}\"" }}"
                                    )
                                },
                                enabled = !ui.busy && space.editable,
                            ) {
                                Text("Re-run stale (${stale.size})")
                            }
                        if (ui.messages.isEmpty())
                            Text(
                                "No messages yet. Steer the plan below.",
                                color = SmokeDark,
                                fontSize = 12.sp,
                            )
                    }
                }
                items(ui.messages, key = { it.id }) { message ->
                    Surface(
                        color = if (message.role == "user") EmberHush else VoidBlack,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    start = if (message.role == "user") 20.dp else 0.dp,
                                    end = if (message.role == "user") 0.dp else 20.dp,
                                ),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Eyebrow(message.role.uppercase())
                                Text(
                                    spaceDate(message.created_at, time = true),
                                    color = SmokeDark,
                                    fontSize = 10.sp,
                                )
                            }
                            Text(
                                message.text,
                                color = if (message.role == "system") CoralPulse else Mist,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
                if (space.run_state == "running")
                    item(key = "working") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Mist,
                            )
                            Text(
                                "Working… researching and generating cards",
                                fontSize = 12.sp,
                                color = Mist,
                            )
                        }
                    }
            }
            HorizontalDivider(color = BorderSubtle)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    draft,
                    { draft = it },
                    placeholder = {
                        Text(
                            if (space.editable) "Steer the plan…"
                            else "Committed space (read-only)",
                            fontSize = 12.sp,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = space.editable && !ui.busy,
                    maxLines = 4,
                )
                IconButton(
                    onClick = { viewModel.send(draft) { draft = "" } },
                    enabled = space.editable && !ui.busy && draft.isNotBlank(),
                ) {
                    if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, "Send message", tint = Mist)
                }
            }
        }
    }
}
