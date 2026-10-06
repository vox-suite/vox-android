package `in`.voxagent.mobile.connections

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

enum class ConnectionFilter {
    ALL,
    CONNECTED,
    AVAILABLE,
}

data class ConnectionsUiState(
    val connectors: List<ConnectorDescriptor> = emptyList(),
    val connections: List<ConnectionItem> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,
    val searchQuery: String = "",
    val filter: ConnectionFilter = ConnectionFilter.ALL,
    val pendingSetupId: String? = null,
    val openConnectorId: String? = null,
) {
    val filteredConnectors: List<ConnectorDescriptor>
        get() {
            val q = searchQuery.trim().lowercase()
            val isConn: (String) -> Boolean = { id -> connections.any { it.connector_id == id } }
            return connectors
                .filter { c ->
                    q.isEmpty() || c.name.lowercase().contains(q) || c.description.lowercase().contains(q)
                }
                .filter { c ->
                    when (filter) {
                        ConnectionFilter.ALL -> true
                        ConnectionFilter.CONNECTED -> isConn(c.id)
                        ConnectionFilter.AVAILABLE -> !isConn(c.id)
                    }
                }
                .sortedWith(
                    compareByDescending<ConnectorDescriptor> { isConn(it.id) }
                        .thenBy { it.name }
                )
        }

    val counts: Triple<Int, Int, Int>
        get() {
            val q = searchQuery.trim().lowercase()
            val matches = connectors.filter { c ->
                q.isEmpty() || c.name.lowercase().contains(q) || c.description.lowercase().contains(q)
            }
            val allCount = matches.size
            val connCount = matches.count { c -> connections.any { it.connector_id == c.id } }
            val availCount = matches.count { c -> connections.none { it.connector_id == c.id } }
            return Triple(allCount, connCount, availCount)
        }
}

class ConnectionsViewModel(
    application: Application,
    private val tokenProvider: () -> String?,
) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "vox_connections_prefs"
        private const val KEY_PENDING_SETUP = "vox.pending-connection-setup"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(
        ConnectionsUiState(
            pendingSetupId = prefs.getString(KEY_PENDING_SETUP, null),
        )
    )
    val ui: StateFlow<ConnectionsUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null

    init {
        reload()
        if (_ui.value.pendingSetupId != null) {
            startPolling()
        }
    }

    fun setFilter(filter: ConnectionFilter) {
        _ui.update { it.copy(filter = filter) }
    }

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
    }

    fun setOpenConnector(connectorId: String?) {
        _ui.update { it.copy(openConnectorId = connectorId, statusMessage = null) }
    }

    fun clearStatusMessage() {
        _ui.update { it.copy(statusMessage = null) }
    }

    fun reload() {
        val token = tokenProvider() ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.connectors.isEmpty(), error = null) }
            try {
                val (connectors, connections) = coroutineScope {
                    val c = async { ConnectionsApi.listConnectors(token) }
                    val n = async { ConnectionsApi.listConnections(token) }
                    c.await() to n.await()
                }
                _ui.update {
                    it.copy(
                        connectors = connectors.filter { c -> c.auth_type != "import" },
                        connections = connections,
                        loading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to load connections")
                _ui.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Failed to load connections",
                    )
                }
            }
        }
    }

    fun connect(
        connectorId: String,
        consent: Boolean,
        npsso: String? = null,
        onOpenUrl: (String) -> Unit,
    ) {
        val token = tokenProvider() ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, statusMessage = null, error = null) }
            try {
                val resp = ConnectionsApi.startConnection(
                    token,
                    StartConnectionRequest(
                        connector_id = connectorId,
                        consent = consent,
                        npsso = npsso?.ifBlank { null },
                    )
                )

                if (resp.setup_id != null && resp.authorization_url != null) {
                    savePendingSetup(resp.setup_id)
                    _ui.update {
                        it.copy(
                            pendingSetupId = resp.setup_id,
                            busy = false,
                            statusMessage = "Waiting for authorization in browser...",
                        )
                    }
                    startPolling()
                    onOpenUrl(resp.authorization_url)
                } else {
                    _ui.update {
                        it.copy(
                            busy = false,
                            statusMessage = "Account connected successfully.",
                        )
                    }
                    reload()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Connect failed")
                _ui.update {
                    it.copy(
                        busy = false,
                        statusMessage = "The connection request failed. Please try again.",
                    )
                }
            }
        }
    }

    fun cancelPending() {
        val setupId = _ui.value.pendingSetupId ?: return
        val token = tokenProvider() ?: return
        viewModelScope.launch {
            runCatching { ConnectionsApi.cancelSetup(token, setupId) }
            clearPendingSetup()
            _ui.update {
                it.copy(
                    pendingSetupId = null,
                    statusMessage = "Setup cancelled.",
                )
            }
        }
    }

    fun onResumeCheck() {
        if (_ui.value.pendingSetupId != null) {
            checkPendingStatus()
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && _ui.value.pendingSetupId != null) {
                checkPendingStatus()
                delay(3000)
            }
        }
    }

    private fun checkPendingStatus() {
        val setupId = _ui.value.pendingSetupId ?: return
        val token = tokenProvider() ?: return
        viewModelScope.launch {
            try {
                val status = ConnectionsApi.getSetupStatus(token, setupId)
                if (status.status !in listOf("pending", "exchanging")) {
                    clearPendingSetup()
                    pollJob?.cancel()
                    pollJob = null
                    _ui.update {
                        it.copy(
                            pendingSetupId = null,
                            statusMessage = if (status.status == "authorized") {
                                "Account connected successfully."
                            } else {
                                "Setup ended. Start again to connect your account."
                            },
                        )
                    }
                    reload()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e.message?.contains("404") == true) {
                    clearPendingSetup()
                    pollJob?.cancel()
                    pollJob = null
                    _ui.update {
                        it.copy(
                            pendingSetupId = null,
                            statusMessage = "Setup session expired. Please start again.",
                        )
                    }
                }
            }
        }
    }

    fun updatePreferences(connectionId: String, syncTimeline: Boolean? = null, assistantRead: Boolean? = null) {
        val token = tokenProvider() ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                ConnectionsApi.updatePreferences(
                    token,
                    connectionId,
                    PreferencesRequest(sync_timeline = syncTimeline, assistant_read = assistantRead)
                )
                reload()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Update preferences failed")
                _ui.update { it.copy(statusMessage = "Failed to update preferences.") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    fun refreshConnection(connectionId: String) {
        val token = tokenProvider() ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, statusMessage = null) }
            try {
                ConnectionsApi.refreshConnection(token, connectionId)
                _ui.update { it.copy(statusMessage = "Timeline refreshed.") }
                reload()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Refresh connection failed")
                _ui.update { it.copy(statusMessage = "Failed to refresh timeline.") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    fun disconnect(connectionId: String) {
        val token = tokenProvider() ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, statusMessage = null) }
            try {
                ConnectionsApi.disconnectConnection(token, connectionId)
                _ui.update {
                    it.copy(
                        statusMessage = "Disconnected. Imported spans are retained.",
                        openConnectorId = null,
                    )
                }
                reload()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Disconnect failed")
                _ui.update { it.copy(statusMessage = "Failed to disconnect.") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun savePendingSetup(setupId: String) {
        prefs.edit().putString(KEY_PENDING_SETUP, setupId).apply()
    }

    private fun clearPendingSetup() {
        prefs.edit().remove(KEY_PENDING_SETUP).apply()
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
