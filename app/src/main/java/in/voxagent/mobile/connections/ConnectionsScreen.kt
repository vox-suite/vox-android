package `in`.voxagent.mobile.connections

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.ui.VoxDarkScreen
import `in`.voxagent.mobile.ui.VoxPrimaryButton
import `in`.voxagent.mobile.ui.VoxStatusPill
import `in`.voxagent.mobile.ui.VoxStatusTone
import `in`.voxagent.mobile.ui.VoxTextButton
import `in`.voxagent.mobile.ui.kit.VoxChip
import `in`.voxagent.mobile.ui.kit.VoxEmpty
import `in`.voxagent.mobile.ui.kit.VoxErrorBar
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.PureWhite
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.theme.SuccessGreen
import `in`.voxagent.mobile.ui.theme.VoidBlack
import `in`.voxagent.mobile.ui.theme.VoxSpaceGroteskFontFamily
import `in`.voxagent.mobile.ui.voxFieldColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ConnectionsScreen(
    token: () -> String?,
    bottomInset: Dp = 88.dp,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: ConnectionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ConnectionsViewModel(app, token)
            }
        }
    )
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    val openApp = ui.connectors.find { it.id == ui.openConnectorId }
    val openConnection = openApp?.let { appDesc ->
        ui.connections.find { it.connector_id == appDesc.id }
    }

    VoxDarkScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = bottomInset),
        ) {
            // Header
            ConnectionsHeader(
                onBack = onBack,
                onRefresh = vm::reload,
                isRefreshing = ui.busy,
            )

            // Search bar
            SearchBar(
                query = ui.searchQuery,
                onQueryChange = vm::setSearchQuery,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            // Filter Tabs
            FilterChips(
                filter = ui.filter,
                counts = ui.counts,
                onFilterSelect = vm::setFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            // Pending Setup / Action Notification Bar
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

            // Error Bar
            if (ui.error != null) {
                VoxErrorBar(
                    message = ui.error ?: "Unable to load connections",
                    onRetry = vm::reload,
                )
            }

            // Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (ui.loading && ui.connectors.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = CoralPulse,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                } else if (ui.filteredConnectors.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        VoxEmpty(
                            title = if (ui.searchQuery.isNotBlank()) "No apps matching \"${ui.searchQuery}\"" else "No apps available",
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            items = ui.filteredConnectors,
                            key = { it.id },
                        ) { connector ->
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

        // Details & Connection Modal Bottom Sheet
        if (openApp != null) {
            val brand = getBrandMeta(openApp.id)
            ConnectionDetailSheet(
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
                onRefresh = {
                    openConnection?.let { vm.refreshConnection(it.id) }
                },
                onDisconnect = {
                    openConnection?.let { vm.disconnect(it.id) }
                },
                onOpenExternal = openUrl,
            )
        }
    }
}

@Composable
private fun ConnectionsHeader(
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Obsidian)
                        .border(BorderStroke(1.dp, BorderSubtle), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", color = Mist, fontSize = 24.sp, fontWeight = FontWeight.Light)
                }
            }
            Column {
                Text(
                    text = "Connected Apps",
                    color = Mist,
                    fontFamily = VoxSpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                )
                Text(
                    text = "Sync accounts with your Vox timeline",
                    color = GraphiteDark,
                    fontSize = 12.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Obsidian)
                .border(BorderStroke(1.dp, BorderSubtle), CircleShape)
                .clickable(enabled = !isRefreshing, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    color = Mist,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    tint = Smoke,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian)
            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = SmokeDark,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search apps & connectors…",
                        color = SmokeDark,
                        fontSize = 14.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Mist,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Default,
                    ),
                    cursorBrush = SolidColor(Mist),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = SmokeDark, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    filter: ConnectionFilter,
    counts: Triple<Int, Int, Int>,
    onFilterSelect: (ConnectionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (allCount, connCount, availCount) = counts
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoxChip(
            text = "All ($allCount)",
            selected = filter == ConnectionFilter.ALL,
            onClick = { onFilterSelect(ConnectionFilter.ALL) },
        )
        VoxChip(
            text = "Connected ($connCount)",
            selected = filter == ConnectionFilter.CONNECTED,
            onClick = { onFilterSelect(ConnectionFilter.CONNECTED) },
        )
        VoxChip(
            text = "Available ($availCount)",
            selected = filter == ConnectionFilter.AVAILABLE,
            onClick = { onFilterSelect(ConnectionFilter.AVAILABLE) },
        )
    }
}

@Composable
private fun PendingSetupBanner(
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1712))
            .border(BorderStroke(1.dp, Color(0xFF5A3D22)), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                color = Color(0xFFFFB366),
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = message,
                color = Color(0xFFFFE0B2),
                fontSize = 12.sp,
            )
        }
        Text(
            text = "Cancel",
            color = CoralPulse,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatusToastBar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian)
            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            color = Mist,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "✕",
            color = SmokeDark,
            fontSize = 12.sp,
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(start = 8.dp),
        )
    }
}

@Composable
private fun ConnectionCard(
    connector: ConnectorDescriptor,
    connection: ConnectionItem?,
    brand: BrandMeta,
    onClick: () -> Unit,
) {
    val isConnected = connection != null
    val needsAttention = connection != null &&
        (!connection.failure_code.isNullOrBlank() || connection.authorization_state != "authorized")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Obsidian)
            .border(BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.7f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        // Subtle ambient radial glow in top right
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(brand.color.copy(alpha = 0.16f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Row: Name & Tagline on left, Brand Icon on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = connector.name,
                        color = Mist,
                        fontFamily = VoxSpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = brand.tagline,
                        color = SmokeDark,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // Brand Icon Container
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brand.color.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, brand.color.copy(alpha = 0.35f)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(brand.iconRes),
                        contentDescription = connector.name,
                        tint = if (brand.iconRes == R.drawable.ic_playstation) Color.Unspecified else brand.color,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Status Pill
            Row {
                when {
                    needsAttention -> {
                        VoxStatusPill(text = "needs attention", tone = VoxStatusTone.Warning)
                    }
                    isConnected -> {
                        VoxStatusPill(text = "connected", tone = VoxStatusTone.Success)
                    }
                    else -> {
                        VoxStatusPill(text = "not connected", tone = VoxStatusTone.Neutral)
                    }
                }
            }

            // Description
            Text(
                text = connector.description,
                color = Smoke,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            // Bottom Action Button
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) BorderSubtle.copy(alpha = 0.5f) else brand.color.copy(alpha = 0.18f),
                    contentColor = if (isConnected) Mist else brand.color,
                ),
                border = BorderStroke(1.dp, if (isConnected) BorderSubtle else brand.color.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(if (isConnected) R.drawable.ic_settings else R.drawable.ic_plug),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (isConnected) Mist else brand.color,
                    )
                    Text(
                        text = if (isConnected) "Configure" else "Connect",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionDetailSheet(
    connector: ConnectorDescriptor,
    connection: ConnectionItem?,
    brand: BrandMeta,
    busy: Boolean,
    pendingSetup: Boolean,
    statusMessage: String?,
    onDismiss: () -> Unit,
    onConnect: (consent: Boolean, npsso: String?) -> Unit,
    onCancelPending: () -> Unit,
    onTogglePreference: (key: String, value: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    var consent by remember { mutableStateOf(false) }
    var npssoToken by remember { mutableStateOf("") }
    var npssoVisible by remember { mutableStateOf(false) }
    var showHelpAccordion by remember { mutableStateOf(false) }

    val isConnected = connection != null
    val needsAttention = connection != null &&
        (!connection.failure_code.isNullOrBlank() || connection.authorization_state != "authorized")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Obsidian,
        contentColor = Mist,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Header: Brand Icon, Name, and Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(brand.color.copy(alpha = 0.16f))
                        .border(BorderStroke(1.dp, brand.color.copy(alpha = 0.4f)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(brand.iconRes),
                        contentDescription = connector.name,
                        tint = if (brand.iconRes == R.drawable.ic_playstation) Color.Unspecified else brand.color,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connector.name,
                        color = Mist,
                        fontFamily = VoxSpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        when {
                            needsAttention -> VoxStatusPill(text = "needs attention", tone = VoxStatusTone.Warning)
                            isConnected -> VoxStatusPill(text = "connected", tone = VoxStatusTone.Success)
                            else -> VoxStatusPill(text = "not connected", tone = VoxStatusTone.Neutral)
                        }
                    }
                }
            }

            // Description
            Text(
                text = connector.description,
                color = Smoke,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )

            // Status message / feedback
            if (!statusMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E22))
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(text = statusMessage, color = Mist, fontSize = 12.sp)
                }
            }

            // Connected Account Section
            if (connection != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VoidBlack)
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Account Display ID
                    Column {
                        Text(
                            text = "ACCOUNT",
                            color = SmokeDark,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = connection.account_display_id ?: "Connected account",
                            color = Mist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    // Last Synced
                    Column {
                        Text(
                            text = "LAST SYNCED",
                            color = SmokeDark,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = formatSyncDate(connection.last_synced_at),
                            color = Mist,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                // Attention/failure banner
                if (!connection.failure_code.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2C2016))
                            .border(BorderStroke(1.dp, Color(0xFF6B4826)), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = if (connection.failure_code == "reconnect_required" || connection.failure_code == "consent_required") {
                                "Reconnect this account to restore sync access."
                            } else {
                                "The last sync failed. Try refreshing."
                            },
                            color = Color(0xFFFFD4A3),
                            fontSize = 12.sp,
                        )
                    }
                }

                // Sync Preferences Switches
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VoidBlack)
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(14.dp)),
                ) {
                    PreferenceToggleRow(
                        title = "Sync to timeline",
                        description = "Synchronize events & sessions to your daily timeline",
                        checked = connection.sync_timeline,
                        disabled = busy,
                        onCheckedChange = { onTogglePreference("sync_timeline", it) },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderSubtle.copy(alpha = 0.5f)),
                    )
                    PreferenceToggleRow(
                        title = "Allow assistant reads",
                        description = "Permit Vox assistant to access activity context",
                        checked = connection.assistant_read,
                        disabled = busy,
                        onCheckedChange = { onTogglePreference("assistant_read", it) },
                    )
                }

                // Actions: Refresh now & Disconnect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onRefresh,
                        enabled = !busy && connection.authorization_state == "authorized",
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BorderSubtle.copy(alpha = 0.5f),
                            contentColor = Mist,
                        ),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text("Refresh now", fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = onDisconnect,
                        enabled = !busy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF261214),
                            contentColor = CoralPulse,
                        ),
                        border = BorderStroke(1.dp, Color(0xFF5E272B)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_unplug),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text("Disconnect", fontSize = 13.sp)
                        }
                    }
                }
            }

            // PlayStation Custom NPSSO Token Input (if PlayStation connector)
            if (connector.id == "playstation") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PlayStation NPSSO Token",
                        color = Mist,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    OutlinedTextField(
                        value = npssoToken,
                        onValueChange = { npssoToken = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Paste your 64-char account token", color = SmokeDark, fontSize = 13.sp) },
                        visualTransformation = if (npssoVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = voxFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            Text(
                                text = if (npssoVisible) "Hide" else "Show",
                                color = Smoke,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { npssoVisible = !npssoVisible }
                                    .padding(horizontal = 12.dp),
                            )
                        },
                    )

                    // Expandable Help Accordion
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VoidBlack)
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp)),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showHelpAccordion = !showHelpAccordion }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "How do I get this token?",
                                    color = Mist,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = if (showHelpAccordion) "−" else "+",
                                    color = SmokeDark,
                                    fontSize = 16.sp,
                                )
                            }

                            AnimatedVisibility(
                                visible = showHelpAccordion,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "1. Sign in to your PlayStation account at playstation.com in your browser.",
                                        color = Smoke,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                    Text(
                                        text = "2. Open the Sony token page below. It displays a short JSON response.",
                                        color = Smoke,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                    Text(
                                        text = "3. Copy the 64-character value after \"npsso\" and paste it above.",
                                        color = Smoke,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )

                                    Button(
                                        onClick = { onOpenExternal("https://ca.account.sony.com/api/v1/ssocookie") },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BorderSubtle,
                                            contentColor = Mist,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Open token page in browser", fontSize = 12.sp)
                                    }

                                    Text(
                                        text = "Keep this token private. It is not an official Sony feature and may need re-entry periodically.",
                                        color = SmokeDark,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Consent Agreement Checkbox (required for connection)
            val consentBorderColor by animateColorAsState(
                targetValue = if (consent) SuccessGreen.copy(alpha = 0.4f) else BorderSubtle,
                label = "consentBorder",
            )
            val consentBgColor by animateColorAsState(
                targetValue = if (consent) SuccessGreen.copy(alpha = 0.08f) else VoidBlack,
                label = "consentBg",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(consentBgColor)
                    .border(BorderStroke(1.dp, consentBorderColor), RoundedCornerShape(14.dp))
                    .clickable { consent = !consent }
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_check),
                    contentDescription = null,
                    tint = if (consent) SuccessGreen else SmokeDark,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "I allow Vox to sync activity to my timeline and read connected account data when helping me. I can turn either use off independently.",
                    color = Mist,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f),
                )
                Checkbox(
                    checked = consent,
                    onCheckedChange = { consent = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = SuccessGreen,
                        checkmarkColor = VoidBlack,
                        uncheckedColor = SmokeDark,
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Connect Button (or Pending Status)
            if (pendingSetup) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onCancelPending,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralPulse.copy(alpha = 0.15f),
                            contentColor = CoralPulse,
                        ),
                        border = BorderStroke(1.dp, CoralPulse.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("Cancel Pending Setup", fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = "Complete sign-in in your browser, then return to Vox.",
                        color = SmokeDark,
                        fontSize = 11.sp,
                    )
                }
            } else {
                val canConnect = consent &&
                    (connector.id != "playstation" || npssoToken.trim().isNotEmpty()) &&
                    !busy

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VoxPrimaryButton(
                        text = if (isConnected) "Reconnect ${connector.name}" else "Connect ${connector.name}",
                        onClick = { onConnect(consent, npssoToken.trim().ifEmpty { null }) },
                        icon = {
                            if (busy) {
                                CircularProgressIndicator(
                                    color = VoidBlack,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_plug),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    )
                    if (!consent) {
                        Text(
                            text = "Accept the consent above to connect.",
                            color = SmokeDark,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    disabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = Mist,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = SmokeDark,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = !disabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = SuccessGreen,
                uncheckedThumbColor = SmokeDark,
                uncheckedTrackColor = Obsidian,
                uncheckedBorderColor = BorderSubtle,
            ),
        )
    }
}

private fun formatSyncDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "Not yet synced"
    return try {
        val instant = Instant.parse(dateStr)
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        dateStr
    }
}
