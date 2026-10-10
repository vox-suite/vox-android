package `in`.voxagent.mobile.updates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.contracts.UpdateItem
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import `in`.voxagent.mobile.ui.VoxDarkScreen
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.CoralPulse
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.util.UUID

@Composable
fun UpdatesScreen(token: () -> String?, bottomInset: Dp = 88.dp) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<UpdateItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var unlock by remember { mutableStateOf<UpdateItem?>(null) }
    var password by remember { mutableStateOf("") }
    var hasMore by remember { mutableStateOf(false) }
    suspend fun load(more:Boolean=false) {
        val bearer = token() ?: return
        loading = true
        try {
            val body = buildJsonObject { put("status","active"); put("limit",100); if (kind.isNotEmpty()) put("kind",kind); if(more) items.lastOrNull()?.let { put("before",it.published_at);put("before_id",it.id) } }
            val result:List<UpdateItem> = VoxJson.decodeFromString(VoxHttp.postJson("/v1/updates/list",body.toString(),bearer))
            items=if(more) items+result else result;hasMore=result.size==100
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Could not load updates" }
        finally { loading = false }
    }
    fun action(path: String, body: JsonObject = JsonObject(emptyMap())) {
        if (loading) return
        scope.launch {
            loading = true; error = ""
            try { VoxHttp.postJson(path,body.toString(),token()); load() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not update" }
            finally { loading = false }
        }
    }
    LaunchedEffect(kind) { error = ""; load() }
    LaunchedEffect(kind) {
        val live = `in`.voxagent.mobile.net.LiveHub.get(token)
        live.start()
        live.events.collect { event ->
            if (event.type == "updates_updated" || event.type == "live_reconnected") {
                kotlinx.coroutines.delay(350)
                if (!loading) load()
            }
        }
    }
    VoxDarkScreen {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=bottomInset)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Updates",color=Mist); TextButton(onClick={ scope.launch { load() } },enabled=!loading) { Text("Refresh") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("" to "All","briefing" to "Briefings","email_notice" to "Email","processing_issue" to "Needs attention","connection_status" to "Connections","daily_plan" to "Plans").forEach { (value,label) ->
                    FilterChip(selected=kind==value,onClick={kind=value},label={Text(label)})
                }
            }
            if (error.isNotEmpty()) Text(error,color=CoralPulse,modifier=Modifier.padding(16.dp))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if (!loading && items.isEmpty() && error.isEmpty()) item { Text("You're up to date.",color=Mist) }
                items(items,key={it.id}) { item ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(item.title,color=Mist); item.summary?.let { Text(it) }; Text(item.category,style=MaterialTheme.typography.labelSmall)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                if (item.read_at==null) TextButton(onClick={action("/v1/updates/${item.id}/read")},enabled=!loading) { Text("Mark read") }
                                if ("retry" in item.available_actions && item.source_job_id!=null) TextButton(onClick={action("/v1/updates/jobs/${item.source_job_id}/retry",buildJsonObject { put("idempotency_key",UUID.randomUUID().toString()) })},enabled=!loading) { Text("Retry") }
                                if ("provide_input" in item.available_actions && item.source_job_id!=null) TextButton(onClick={password="";unlock=item},enabled=!loading) { Text("Unlock attachment") }
                                TextButton(onClick={action("/v1/updates/${item.id}/dismiss")},enabled=!loading) { Text("Dismiss") }
                            }
                        }
                    }
                }
                if(hasMore) item { TextButton({scope.launch {load(true)}},enabled=!loading){Text("Load more updates")} }
            }
        }
    }
    unlock?.let { item ->
        AlertDialog(onDismissRequest={unlock=null;password=""},title={Text("Unlock attachment")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("The password is encrypted for this job and expires after 15 minutes.")
                OutlinedTextField(value=password,onValueChange={password=it},label={Text("Attachment password")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            }
        },confirmButton={TextButton(enabled=password.isNotEmpty()&&!loading,onClick={
            val secret=password; password="";unlock=null
            action("/v1/updates/jobs/${item.source_job_id}/input",buildJsonObject { put("input_type","password");put("data",buildJsonObject { put("password",secret) }) })
        }) { Text("Submit password") }},dismissButton={TextButton(onClick={unlock=null;password=""}) { Text("Cancel") }})
    }
}
