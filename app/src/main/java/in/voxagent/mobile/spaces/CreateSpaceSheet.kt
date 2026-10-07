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
    onCreate: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
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
            Eyebrow("NEW SPACE")
            Text("Create a Space", color = Mist, fontSize = 24.sp)
            Text(
                "Describe what you are thinking of doing. Vox will research options using your available data and build a visual plan.",
                color = SmokeDark,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            ErrorBanner(error)
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("Title") },
                placeholder = { Text("Weekend getaway near Bangalore") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                intent,
                { intent = it },
                label = { Text("Vision & intent") },
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
                    onClick = { onCreate(title, intent) },
                    enabled = !busy && title.isNotBlank() && intent.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (busy) "Creating…" else "Create space")
                }
            }
        }
    }
}
