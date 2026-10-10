package `in`.voxagent.mobile.connections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.net.VoxJson
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Smoke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate

@Composable
internal fun GmailHistorySection(token: () -> String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(LocalDate.now().minusYears(1).toString()) }
    var end by remember { mutableStateOf(LocalDate.now().toString()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<HistoryPage?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var drafts by remember { mutableStateOf(mapOf<String,String>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { GmailHistory.installModel(context,uri);message="Local model ready" }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { message=error.message ?: "Cannot load model" }
            finally { busy=false }
        }
    }
    fun scan(next: String?) {
        scope.launch {
            busy=true;message="Reading and classifying 10 emails on this device…"
            try {
                val result = GmailHistory.scan(context,token() ?: error("Sign in first"),start,end,next)
                page=result;selected=emptySet();drafts=result.candidates.associate { it["message_id"]!!.jsonPrimitive.content to (it["proposed_event"] ?: JsonNull).toString() }
                message="${result.excluded} emails excluded locally. Review the remaining messages."
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message=error.message ?: "Local classification unavailable" }
            finally { busy=false }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Import previous emails",color=Mist)
        Text("Live sync starts at connection time. History is classified on this device. Only selected messages are sent to Vox.",color=Smoke)
        OutlinedTextField(start,{start=it},label={Text("From (YYYY-MM-DD)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(end,{end=it},label={Text("Before (YYYY-MM-DD)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedButton({picker.launch(arrayOf("application/octet-stream","*/*"))},enabled=!busy) { Text("Choose local .litertlm model") }
        OutlinedButton({scan(null)},enabled=!busy && GmailHistory.modelFile(context).isFile) { Text("Review historical emails") }
        if (message.isNotBlank()) Text(message,color=Smoke)
        page?.candidates?.forEach { candidate ->
            val id=candidate["message_id"]!!.jsonPrimitive.content
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row {
                    Checkbox(id in selected,{checked -> selected=if(checked) selected+id else selected-id},enabled=!busy)
                    Text(candidate["subject"]?.jsonPrimitive?.contentOrNull ?: "Email",color=Mist)
                }
                Text(candidate["reason"]?.jsonPrimitive?.contentOrNull ?: "Review extracted facts",color=Smoke)
                if(candidate["uncertainty"]?.jsonPrimitive?.booleanOrNull == true) Text("Needs correction. Selection confirms you checked the facts.",color=Smoke)
                OutlinedTextField(drafts[id].orEmpty(),{drafts=drafts+(id to it)},label={Text("Extracted event JSON or null")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            }
        }
        if(page!=null) {
            Button(onClick={scope.launch {
                busy=true;var imported=0
                try {
                    for(candidate in page?.candidates.orEmpty().filter { it["message_id"]!!.jsonPrimitive.content in selected }) {
                        val id=candidate["message_id"]!!.jsonPrimitive.content
                        val event=VoxJson.parseToJsonElement(drafts[id].orEmpty())
                        require(event is JsonObject || event==JsonNull) { "Event must be an object or null" }
                        GmailHistory.importSelected(token() ?: error("Sign in first"),candidate,event)
                        imported++;page=page?.copy(candidates=page!!.candidates.filter { it["message_id"]!!.jsonPrimitive.content != id })
                    }
                    selected=emptySet();message="$imported reviewed emails imported. Check Updates for attachment input."
                } catch(error: CancellationException) { throw error }
                catch(error: Exception) { message="$imported imported. ${error.message}" }
                finally { busy=false }
            }},enabled=!busy && selected.isNotEmpty()) { Text("Import selected (${selected.size})") }
            OutlinedButton({scan(page?.next)},enabled=!busy && selected.isEmpty() && page?.next!=null) { Text("Next emails") }
        }
    }
}
