package `in`.voxagent.mobile.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
internal fun SpaceNodeSheet(
    node: SpaceNode,
    editable: Boolean,
    busy: Boolean,
    error: String?,
    onClose: () -> Unit,
    viewModel: SpacesViewModel,
) {
    var title by rememberSaveable(node.id) { mutableStateOf(node.title) }
    var body by rememberSaveable(node.id) { mutableStateOf(node.body) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Obsidian,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Eyebrow("SELECTED / ${node.kind.uppercase()}")
                StatusLabel(node.state)
            }
            Text(node.title, color = Mist, fontSize = 22.sp)
            ErrorBanner(error)
            if (editable) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
                OutlinedTextField(
                    body,
                    { body = it },
                    label = { Text("Body") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10,
                    enabled = !busy,
                )
                if (title != node.title || body != node.body)
                    Button(
                        onClick = {
                            viewModel.update(
                                node,
                                NodePatch(title = title.trim(), body = body.trim()),
                            )
                        },
                        enabled = !busy && title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Saving…" else "Save changes")
                    }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.update(
                                node,
                                NodePatch(
                                    state = if (node.state == "rejected") "done" else "rejected"
                                ),
                            )
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (node.state == "rejected") "Restore" else "Reject",
                            color = if (node.state == "rejected") Mist else CoralPulse,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.send(
                                "Dig deeper on \"${node.title}\". Research more details, pros, cons, and budget implications."
                            ) {
                                onClose()
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Dig deeper")
                    }
                }
            } else {
                Text(
                    node.body.ifBlank { "No additional details." },
                    color = Mist,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
                Text("This space is read-only.", color = SmokeDark, fontSize = 12.sp)
            }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
        }
    }
}
