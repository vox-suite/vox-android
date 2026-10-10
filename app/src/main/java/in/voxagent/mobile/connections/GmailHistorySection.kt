package `in`.voxagent.mobile.connections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Smoke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate

@Composable
internal fun GmailHistorySection(token: () -> String?) {
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(LocalDate.now().minusYears(1).toString()) }
    var end by remember { mutableStateOf(LocalDate.now().toString()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<HistoryPage?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    fun scan(next: String?) {
        scope.launch {
            busy=true;message="Reading and classifying up to 3 emails on the server…"
            try {
                val result = GmailHistory.scan(token() ?: error("Sign in first"),start,end,next)
                page=result;selected=emptySet()
                message="${result.excluded} emails excluded by the server. Review the remaining messages."
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message=error.message ?: "Server classification unavailable" }
            finally { busy=false }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Import previous emails",color=Mist)
        Text("Live sync starts at connection time. Requested history is read and classified on the server. Select messages to add them to your timeline.",color=Smoke)
        OutlinedTextField(start,{start=it},label={Text("From (YYYY-MM-DD)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(end,{end=it},label={Text("Before (YYYY-MM-DD)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedButton({scan(null)},enabled=!busy) { Text("Review historical emails") }
        if (message.isNotBlank()) Text(message,color=Smoke)
        page?.candidates?.forEach { candidate ->
            val id=candidate["message_id"]!!.jsonPrimitive.content
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row {
                    Checkbox(id in selected,{checked -> selected=if(checked) selected+id else selected-id},enabled=!busy)
                    Text(candidate["subject"]?.jsonPrimitive?.contentOrNull ?: "Email",color=Mist)
                }
                Text(candidate["reason"]?.jsonPrimitive?.contentOrNull ?: "Review extracted facts",color=Smoke)
                if(candidate["uncertainty"]?.jsonPrimitive?.booleanOrNull == true) Text("Uncertain facts stay out of the timeline. Import to surface a review notice in Updates.",color=Smoke)
                Text(candidate["events"].toString(),color=Smoke)
            }
        }
        if(page!=null) {
            Button(onClick={scope.launch {
                busy=true;var imported=0
                try {
                    for(candidate in page?.candidates.orEmpty().filter { it["message_id"]!!.jsonPrimitive.content in selected }) {
                        val id=candidate["message_id"]!!.jsonPrimitive.content
                        imported += GmailHistory.importSelected(token() ?: error("Sign in first"),candidate)
                        page=page?.copy(candidates=page!!.candidates.filter { it["message_id"]!!.jsonPrimitive.content != id })
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
