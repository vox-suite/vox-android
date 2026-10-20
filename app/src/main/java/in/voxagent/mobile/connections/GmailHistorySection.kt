package `in`.voxagent.mobile.connections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Smoke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun GmailHistorySection(token: () -> String?) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Backfill?>(null) }
    var failure by remember { mutableStateOf("") }
    val running = status?.state == "running"
    LaunchedEffect(running) {
        do {
            try { status = GmailHistory.backfill(token() ?: return@LaunchedEffect, start = false) }
            catch (cause: CancellationException) { throw cause }
            catch (_: Exception) {}
            if (running) delay(5000)
        } while (running)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Previous emails", color = Mist)
        Text("New emails sync automatically from when you connected. Sync the 3 months before that to add past orders, bills and bookings to your timeline.", color = Smoke)
        OutlinedButton({
            scope.launch {
                failure = ""
                try { status = GmailHistory.backfill(token() ?: error("Sign in first"), start = true) }
                catch (cause: CancellationException) { throw cause }
                catch (cause: Exception) { failure = cause.message ?: "Could not start sync" }
            }
        }, enabled = !running) { Text(if (running) "Syncing…" else "Sync last 3 months") }
        status?.takeIf { it.state != null }?.let {
            Text(
                if (running) "Checked ${it.processed} emails so far, ${it.imported} added."
                else "Done. Checked ${it.processed} emails, ${it.imported} added" + (if (it.skipped > 0) ", ${it.skipped} skipped." else "."),
                color = Smoke,
            )
        }
        if (failure.isNotBlank()) Text(failure, color = Smoke)
    }
}
