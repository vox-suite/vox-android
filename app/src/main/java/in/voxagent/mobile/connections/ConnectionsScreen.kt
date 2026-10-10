package `in`.voxagent.mobile.connections

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.voxagent.mobile.ui.VoxDarkScreen
import `in`.voxagent.mobile.ui.kit.VoxEmpty
import `in`.voxagent.mobile.ui.kit.VoxErrorBar
import `in`.voxagent.mobile.ui.theme.CoralPulse

@Composable
fun ConnectionsScreen(token: () -> String?, bottomInset: Dp = 88.dp, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: ConnectionsViewModel =
        viewModel(factory = viewModelFactory { initializer { ConnectionsViewModel(app, token) } })
    val ui by vm.ui.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onResumeCheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openUrl: (String) -> Unit = { url ->
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    val openApp = ui.connectors.find { it.id == ui.openConnectorId }
    val openConnection =
        openApp?.let { appDesc -> ui.connections.find { it.connector_id == appDesc.id } }

    VoxDarkScreen {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomInset)
        ) {
            ConnectionsHeader(onBack = onBack, onRefresh = vm::reload, isRefreshing = ui.busy)

            SearchBar(
                query = ui.searchQuery,
                onQueryChange = vm::setSearchQuery,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            FilterChips(
                filter = ui.filter,
                counts = ui.counts,
                onFilterSelect = vm::setFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (ui.pendingSetupId != null) {
                PendingSetupBanner(
                    message = ui.statusMessage ?: "Waiting for authorization in browser...",
                    onCancel = vm::cancelPending,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else if (!ui.statusMessage.isNullOrBlank()) {
                StatusToastBar(
                    message = ui.statusMessage ?: "",
                    onDismiss = vm::clearStatusMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            if (ui.error != null) {
                VoxErrorBar(
                    message = ui.error ?: "Unable to load connections",
                    onRetry = vm::reload,
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (ui.loading && ui.connectors.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = CoralPulse,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                } else if (ui.filteredConnectors.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        VoxEmpty(
                            title =
                                if (ui.searchQuery.isNotBlank())
                                    "No apps matching \"${ui.searchQuery}\""
                                else "No apps available"
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(items = ui.filteredConnectors, key = { it.id }) { connector ->
                            val conn = ui.connections.find { it.connector_id == connector.id }
                            val brand = getBrandMeta(connector.id)
                            ConnectionCard(
                                connector = connector,
                                connection = conn,
                                brand = brand,
                                onClick = { vm.setOpenConnector(connector.id) },
                            )
                        }
                    }
                }
            }
        }

        if (openApp != null) {
            val brand = getBrandMeta(openApp.id)
            ConnectionDetailSheet(
                token = token,
                connector = openApp,
                connection = openConnection,
                brand = brand,
                busy = ui.busy,
                pendingSetup = ui.pendingSetupId != null,
                statusMessage = ui.statusMessage,
                onDismiss = { vm.setOpenConnector(null) },
                onConnect = { consent, npsso ->
                    vm.connect(
                        connectorId = openApp.id,
                        consent = consent,
                        npsso = npsso,
                        onOpenUrl = openUrl,
                    )
                },
                onCancelPending = vm::cancelPending,
                onTogglePreference = { key, value ->
                    openConnection?.let { c ->
                        if (key == "sync_timeline") {
                            vm.updatePreferences(c.id, syncTimeline = value)
                        } else if (key == "assistant_read") {
                            vm.updatePreferences(c.id, assistantRead = value)
                        }
                    }
                },
                onRefresh = { openConnection?.let { vm.refreshConnection(it.id) } },
                onDisconnect = { openConnection?.let { vm.disconnect(it.id) } },
                onOpenExternal = openUrl,
            )
        }
    }
}
