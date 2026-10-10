package `in`.voxagent.mobile.connections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Smoke
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

@Composable
internal fun TakeoutImportSection(token: () -> String?, onOpenExternal:(String)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            busy=true;message="Uploading Takeout…"
            try {
                val result=withContext(Dispatchers.IO) {
                    val file=File.createTempFile("takeout-",".zip",context.cacheDir)
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                            val buffer=ByteArray(65536);var total=0L
                            while(true) { ensureActive();val count=input.read(buffer);if(count<0) break;total+=count;require(total<=100*1024*1024){"Takeout exceeds 100 MiB; export only YouTube or Timeline"};output.write(buffer,0,count) }
                            require(total>0){"Empty Takeout file"}
                        }} ?: error("Cannot open Takeout")
                        val bearer=token() ?: error("Sign in first")
                        val request=Request.Builder().url(VoxHttp.url("/v1/connectors/google/takeout/upload")).header("Authorization","Bearer $bearer")
                            .post(file.asRequestBody("application/octet-stream".toMediaType())).build()
                        VoxHttp.client.newBuilder().readTimeout(180,TimeUnit.SECONDS).writeTimeout(180,TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                            check(response.isSuccessful){"Takeout import failed (${response.code})"}
                            VoxJson.parseToJsonElement(response.body?.string() ?: error("Empty import response")).jsonObject
                        }
                    } finally { file.delete() }
                }
                message="${result["total_events_created"] ?: 0} events imported. ${result["notes"] ?: ""}"
            } catch(error:CancellationException){throw error}
            catch(error:Exception){message=error.message ?: "Takeout import failed"}
            finally{busy=false}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Import Google Takeout",color=Mist)
        Text("Export YouTube watch history or actual Maps Timeline visits as JSON in a ZIP. Settings and encrypted backup notices do not contain usable location history.",color=Smoke)
        OutlinedButton({onOpenExternal("https://takeout.google.com/")},enabled=!busy){Text("Open Google Takeout")}
        Row {Checkbox(consent,{consent=it},enabled=!busy);Text("I agree to send this export to Vox for timeline parsing.",color=Mist)}
        Button({picker.launch(arrayOf("application/zip","application/json","application/octet-stream"))},enabled=consent && !busy,modifier=Modifier.fillMaxWidth()){Text(if(busy) "Importing…" else "Choose ZIP or JSON")}
        if(message.isNotBlank()) Text(message,color=Smoke)
    }
}
