package `in`.voxagent.mobile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

private data class AppVersion(val status: String, val latest: String?, val url: String?)

private suspend fun checkVersion(): AppVersion? = withContext(Dispatchers.IO) {
    try {
        val version = URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8")
        val json = VoxJson.parseToJsonElement(VoxHttp.requestJson("GET", "/v1/app/version?platform=android&version=$version")).jsonObject
        fun field(key: String) = json[key]?.jsonPrimitive?.contentOrNull
        AppVersion(field("status") ?: "ok", field("latest_version"), field("download_url"))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

@Composable
fun UpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf<AppVersion?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            checkVersion()?.let { version = it }
            delay(60 * 60 * 1000L)
        }
    }
    val current = version
    val latest = current?.latest?.let { " $it" } ?: ""
    val download = {
        current?.url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        Unit
    }
    if (current?.status == "update_required") {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Update required", style = MaterialTheme.typography.headlineSmall)
            Text(
                "This version of Vox (${BuildConfig.VERSION_NAME}) is no longer supported. Install Vox$latest to keep using it.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current.url != null) Button(download) { Text("Download update") }
            OutlinedButton({ scope.launch { checkVersion()?.let { version = it } } }) { Text("Check again") }
        }
        return
    }
    content()
    if (current?.status == "update_available" && !dismissed) {
        AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text("Update available") },
            text = { Text("Vox$latest is available.") },
            confirmButton = { if (current.url != null) TextButton({ dismissed = true; download() }) { Text("Download") } },
            dismissButton = { TextButton({ dismissed = true }) { Text("Later") } },
        )
    }
}
