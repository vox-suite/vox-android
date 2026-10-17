package `in`.voxagent.mobile.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.voxagent.mobile.cache.DeviceCache
import `in`.voxagent.mobile.control.LocalAppControl
import `in`.voxagent.mobile.spaces.SpacesColors
import `in`.voxagent.mobile.ui.VoxDarkScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(token: () -> String?, bottomInset: Dp = 88.dp) {
    val application = LocalContext.current.applicationContext
    val latest by rememberUpdatedState(token)
    val cache = remember(application) { DeviceCache(application, "projects") }
    val model: ProjectsViewModel =
        viewModel(
            key = "projects:${cache.identity}:${cache.epoch}",
            factory = viewModelFactory { initializer { ProjectsViewModel({ latest() }, cache) } },
        )
    val ui by model.ui.collectAsState()
    val control = LocalAppControl.current
    val app = control?.context?.collectAsState()?.value
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, model) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.observe() }
    }
    LaunchedEffect(app?.projectId) {
        if (app?.projectId != ui.selectedId) model.open(app?.projectId)
    }
    LaunchedEffect(ui.selectedId, ui.project?.revision) {
        control?.patch {
            it.copy(
                projectId = ui.selectedId,
                resourceDetails =
                    ui.project?.let { p ->
                        kotlinx.serialization.json.buildJsonObject {
                            put("name", kotlinx.serialization.json.JsonPrimitive(p.name))
                            put(
                                "contextNodes",
                                kotlinx.serialization.json.JsonPrimitive(p.data.nodes.size),
                            )
                        }
                    },
            )
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var ideate by rememberSaveable { mutableStateOf(false) }
    var newPlan by rememberSaveable { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ContextNode?>(null) }
    val p = ui.project
    BackHandler(ui.selectedId != null && !ideate && editing == null) { model.open(null) }
    MaterialTheme(colorScheme = SpacesColors) {
        VoxDarkScreen {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomInset)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (p != null)
                            TextButton(onClick = { model.open(null) }, enabled = !ui.busy) { Text("‹ Projects") }
                        Text(
                            p?.name ?: "Projects",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (p == null) "Long-lived context for your next iteration"
                            else
                                "${p.data.sources.size} sources · ${p.data.nodes.size} context nodes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (p != null) TextButton(onClick = { settings = true }, enabled = !ui.busy) { Text("Edit") }
                    TextButton(onClick = { model.reload() }, enabled = !ui.busy) { Text("Refresh") }
                }
                if (ui.error != null)
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(
                            ui.error!!,
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                if (ui.loading || ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (p == null) {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                "Bring an existing project into Vox",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Import folders or up to five files in the desktop app. Your context and decisions appear here; files remain on that device.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            FilledTonalButton(onClick = { creating = true }, enabled = !ui.busy) {
                                Text("Start a new project")
                            }
                        }
                        items(ui.items, key = { it.id }) { project ->
                            OutlinedCard(
                                Modifier.fillMaxWidth().clickable { model.open(project.id) }
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(project.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        project.data.notes.ifBlank {
                                            "Open to ideate and build context"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                    Text(
                                        "${project.data.sources.size} sources · ${project.data.template}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                        if (ui.cursor != null)
                            item {
                                TextButton(onClick = { model.more() }, enabled = !ui.busy) {
                                    Text("Load more projects")
                                }
                            }
                    }
                } else {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = { ideate = true }) { Text("Ideate") }
                        FilledTonalButton(onClick = { newPlan = true }, enabled = !ui.busy) {
                            Text("New plan")
                        }
                        TextButton(onClick = { archive = true }, enabled = !ui.busy) {
                            Text("Archive")
                        }
                    }
                    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
                        listOf("Context", "Plans", "Sources", "Activity").forEachIndexed {
                            index,
                            label ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                text = { Text(label) },
                            )
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (tab) {
                            0 -> {
                                item {
                                    Text(p.data.notes, style = MaterialTheme.typography.bodySmall)
                                    TextButton(
                                        onClick = {
                                            editing =
                                                ContextNode(
                                                    java.util.UUID.randomUUID().toString(),
                                                    "decision",
                                                    "",
                                                    "",
                                                    "user",
                                                )
                                        }
                                    ) {
                                        Text("Add context")
                                    }
                                }
                                if (p.data.nodes.isEmpty())
                                    item {
                                        Text(
                                            "Ideate to explore your vision. Accept useful proposals to build context.",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                items(p.data.nodes, key = { it.id }) { node ->
                                    OutlinedCard(
                                        Modifier.fillMaxWidth().clickable { editing = node }
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(
                                                "${node.kind} · ${node.origin}" +
                                                    if (node.stale) " · Possibly outdated" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color =
                                                    if (node.stale) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                node.title,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                            )
                                            Text(
                                                node.body,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            node.citations.forEach {
                                                Text(
                                                    "${it.path}:${it.start_line}–${it.end_line}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(top = 6.dp),
                                                )
                                            }
                                            if (node.citations.isEmpty())
                                                Text(
                                                    if (node.origin == "user") "User statement"
                                                    else "Assumption · no source cited",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                item {
                                    TextButton(
                                        onClick = {
                                            model.candidates()
                                            attaching = true
                                        },
                                        enabled = !ui.busy,
                                    ) {
                                        Text("Attach an existing Plan")
                                    }
                                }
                                if (ui.plans.isEmpty())
                                    item {
                                        Text(
                                            "No Plans yet. Turn a concrete direction into your next iteration."
                                        )
                                    }
                                items(ui.plans, key = { it.id }) { plan ->
                                    OutlinedCard(
                                        Modifier.fillMaxWidth().clickable {
                                            control?.patch {
                                                it.copy(
                                                    page = "spaces",
                                                    spaceId = plan.id,
                                                    selectedItemId = null,
                                                )
                                            }
                                        }
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(plan.title, fontWeight = FontWeight.Medium)
                                            Text(
                                                plan.intent,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                plan.state,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            if (
                                                plan.state == "planned" || plan.state == "committed"
                                            )
                                                TextButton(
                                                    onClick = {
                                                        model.findings(plan)
                                                        ideate = true
                                                    },
                                                    enabled = !ui.busy,
                                                ) {
                                                    Text("Review findings")
                                                }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                item {
                                    Text(
                                        "Files stay on your desktop",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "Manage folders, inspect code and refresh changed sources in Vox desktop. Android uses the same approved context summaries.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                items(p.data.sources, key = { it.id }) { source ->
                                    OutlinedCard(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(source.label)
                                            Text(
                                                "${source.files} files · ${source.symbols} symbols",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                "Indexed ${source.indexed_at.take(10)} · Local to desktop",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }
                            else ->
                                items(p.data.activity.reversed(), key = { it.id }) { activity ->
                                    Column {
                                        Text(
                                            activity.text,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            activity.at.take(19).replace('T', ' '),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                        }
                    }
                }
            }
            if (attaching)
                AlertDialog(
                    onDismissRequest = { attaching = false },
                    title = { Text("Attach a Plan") },
                    text = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (ui.busy) item { CircularProgressIndicator() }
                            else if (ui.candidates.isEmpty())
                                item {
                                    Text(
                                        "No standalone Plans available. Running Plans must be stopped before attaching."
                                    )
                                }
                            items(ui.candidates, key = { it.id }) { plan ->
                                TextButton(
                                    onClick = {
                                        model.attach(plan)
                                        attaching = false
                                    }
                                ) {
                                    Text(plan.title)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { attaching = false }) { Text("Close") }
                    },
                )
            if (settings && p != null)
                ProjectForm(
                    "Edit project",
                    "Keep your vision current.",
                    "Save project",
                    ui.busy,
                    initialName = p.name,
                    initialNotes = p.data.notes,
                    initialTemplate = p.data.template,
                    onDismiss = { settings = false },
                    onSubmit = { name, notes, template ->
                        model.settings(name, notes, template)
                        settings = false
                    },
                )
            if (creating)
                ProjectForm(
                    "New project",
                    "Create a project to think before starting a Plan.",
                    "Create project",
                    ui.busy,
                    onDismiss = { creating = false },
                    onSubmit = { name, notes, template ->
                        model.create(name, notes, template)
                        creating = false
                    },
                )
            if (newPlan)
                ProjectForm(
                    "New plan",
                    "Uses current approved context from ${p?.name}.",
                    "Start plan",
                    ui.busy,
                    onlyNotes = true,
                    onDismiss = { newPlan = false },
                    onSubmit = { _, intent, _ ->
                        model.plan(intent) { plan ->
                            control?.patch {
                                it.copy(page = "spaces", spaceId = plan.id, selectedItemId = null)
                            }
                        }
                        newPlan = false
                    },
                )
            if (archive)
                AlertDialog(
                    onDismissRequest = { archive = false },
                    title = { Text("Archive ${p?.name}?") },
                    text = {
                        Text("Hide the project. Existing Plans and local files are preserved.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                model.archive()
                                archive = false
                            }
                        ) {
                            Text("Archive")
                        }
                    },
                    dismissButton = { TextButton(onClick = { archive = false }) { Text("Cancel") } },
                )
            editing?.let { node ->
                ContextSheet(
                    node,
                    ui.busy,
                    onDismiss = { editing = null },
                    onSave = {
                        model.save(it)
                        editing = null
                    },
                    onRemove = {
                        model.remove(node.id)
                        editing = null
                    },
                )
            }
            if (ideate && p != null)
                ModalBottomSheet(
                    onDismissRequest = { ideate = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    var draft by rememberSaveable { mutableStateOf("") }
                    Column(
                        Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().padding(16.dp)
                    ) {
                        Text("Ideate · ${p.name}", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(
                            Modifier.weight(1f),
                            reverseLayout = true,
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(p.data.messages.reversed(), key = { it.id }) { message ->
                                Column {
                                    Text(
                                        if (message.role == "user") "You" else "Vox",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(message.body, style = MaterialTheme.typography.bodyMedium)
                                    message.proposals.forEach { proposal ->
                                        OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text(proposal.title, fontWeight = FontWeight.Medium)
                                                Text(
                                                    proposal.body,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Text(
                                                    "Inferred · ${proposal.citations.size} references",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                                Row {
                                                    TextButton(
                                                        onClick = {
                                                            model.accept(message.id, proposal)
                                                        },
                                                        enabled = !ui.busy,
                                                    ) {
                                                        Text("Accept context")
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            model.dismiss(message.id, proposal.id)
                                                        },
                                                        enabled = !ui.busy,
                                                    ) {
                                                        Text("Dismiss")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (p.data.messages.isEmpty())
                                item {
                                    Text(
                                        "What are you building, and what should it make easier? Ideas stay here until you accept them as context."
                                    )
                                }
                        }
                        ui.error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "Context summaries shared · files stay local",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        OutlinedTextField(
                            draft,
                            { if (it.length <= 12000) draft = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("Explore an idea") },
                            maxLines = 4,
                        )
                        Button(
                            onClick = {
                                model.chat(draft.trim())
                                draft = ""
                            },
                            enabled = draft.isNotBlank() && !ui.busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(if (ui.busy) "Thinking…" else "Send")
                        }
                    }
                }
        }
    }
}

@Composable
private fun ProjectForm(
    title: String,
    description: String,
    action: String,
    busy: Boolean,
    onlyNotes: Boolean = false,
    initialName: String = "",
    initialNotes: String = "",
    initialTemplate: String = "engineering",
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var template by rememberSaveable { mutableStateOf(initialTemplate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(description, style = MaterialTheme.typography.bodySmall)
                if (!onlyNotes) {
                    OutlinedTextField(
                        name,
                        { if (it.length <= 160) name = it },
                        label = { Text("Project name") },
                        singleLine = true,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(listOf("engineering", "launch", "3d-print", "general")) { type ->
                            FilterChip(
                                selected = template == type,
                                onClick = { template = type },
                                label = { Text(type, fontSize = 9.sp) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    notes,
                    { if (it.length <= 12000) notes = it },
                    label = { Text(if (onlyNotes) "Plan vision" else "What are you building?") },
                    minLines = 3,
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), notes.trim(), template) },
                enabled =
                    !busy && (onlyNotes && notes.isNotBlank() || !onlyNotes && name.isNotBlank()),
            ) {
                Text(action)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(
    node: ContextNode,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ContextNode) -> Unit,
    onRemove: () -> Unit,
) {
    var title by rememberSaveable(node.id) { mutableStateOf(node.title) }
    var body by rememberSaveable(node.id) { mutableStateOf(node.body) }
    var kind by rememberSaveable(node.id) { mutableStateOf(node.kind) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Edit context", style = MaterialTheme.typography.titleLarge)
                if (node.stale)
                    Text(
                        "Possibly outdated. Review changed source files on desktop before marking this context current.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                OutlinedTextField(
                    title,
                    { if (it.length <= 300) title = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        listOf(
                            "goal",
                            "component",
                            "decision",
                            "constraint",
                            "question",
                            "asset",
                            "milestone",
                        )
                    ) { type ->
                        FilterChip(
                            selected = kind == type,
                            onClick = { kind = type },
                            label = { Text(type) },
                        )
                    }
                }
                OutlinedTextField(
                    body,
                    { if (it.length <= 12000) body = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Context") },
                    minLines = 4,
                    maxLines = 10,
                )
                node.citations.forEach {
                    Text(
                        "${it.path}:${it.start_line}–${it.end_line} · Open on desktop",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Button(
                    onClick = {
                        onSave(
                            node.copy(
                                title = title.trim(),
                                body = body.trim(),
                                kind = kind,
                                origin = "user",
                            )
                        )
                    },
                    enabled = !busy && title.isNotBlank() && body.isNotBlank(),
                ) {
                    Text("Save context")
                }
                TextButton(onClick = onRemove, enabled = !busy) {
                    Text("Remove context", color = MaterialTheme.colorScheme.error)
                }
                if (node.history.isNotEmpty())
                    Text("Earlier versions", style = MaterialTheme.typography.titleMedium)
            }
            items(node.history.reversed()) { version ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(version.title)
                        Text(version.body, style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = {
                                onSave(
                                    node.copy(
                                        title = version.title,
                                        body = version.body,
                                        citations = version.citations,
                                        origin = "user",
                                        stale = true,
                                    )
                                )
                            },
                            enabled = !busy,
                        ) {
                            Text("Use this version")
                        }
                    }
                }
            }
        }
    }
}
