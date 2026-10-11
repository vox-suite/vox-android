package `in`.voxagent.mobile.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateSpaceSheet(
    busy: Boolean,
    error: String?,
    onClose: () -> Unit,
    onCreate: (String, String, String?) -> Unit,
    loadProjects: suspend () -> List<`in`.voxagent.mobile.projects.Project>,
) {
    var projects by remember {
        mutableStateOf<List<`in`.voxagent.mobile.projects.Project>>(emptyList())
    }
    var project by rememberSaveable { mutableStateOf<String?>(null) }
    var projectError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            projects = loadProjects()
        } catch (e: Exception) {
            projectError = e.message
        }
    }
    var intent by rememberSaveable { mutableStateOf("") }
    val currentBusy by rememberUpdatedState(busy)
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() },
        sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { !currentBusy || it != SheetValue.Hidden },
            ),
        containerColor = Obsidian,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Eyebrow("NEW PLAN")
            Text("Create a Plan", color = Mist, fontSize = 24.sp)
            Text(
                "Describe what you are thinking of doing. Vox will research options using your available data and build a visual plan.",
                color = SmokeDark,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            ErrorBanner(error)
            Text("Attach to project (optional)", color = SmokeDark, fontSize = 12.sp)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = project == null,
                        onClick = { project = null },
                        label = { Text("Standalone") },
                    )
                }
                items(projects.size) { index ->
                    val p = projects[index]
                    FilterChip(
                        selected = project == p.id,
                        onClick = { project = p.id },
                        label = { Text(p.name) },
                    )
                }
            }
            ErrorBanner(projectError)
            OutlinedTextField(
                intent,
                { intent = it },
                label = { Text("What are you thinking about?") },
                placeholder = {
                    Text("Plan a short trip. Compare places, driving time and a realistic budget.")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                minLines = 4,
                maxLines = 8,
            )
            if (busy)
                Text("Preparing your research workspace…", color = SmokeDark, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onCreate(
                            intent.trim().split(Regex("\\s+")).take(5).joinToString(" "),
                            intent,
                            project,
                        )
                    },
                    enabled = !busy && intent.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (busy) "Creating…" else "Create plan")
                }
            }
        }
    }
}
