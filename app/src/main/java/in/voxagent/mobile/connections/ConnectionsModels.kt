package `in`.voxagent.mobile.connections

import androidx.compose.ui.graphics.Color
import `in`.voxagent.mobile.R
import kotlinx.serialization.Serializable

@Serializable
data class ConnectorDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val supported_features: List<String> = emptyList(),
    val auth_type: String,
    val available: Boolean = true,
)

@Serializable
data class ConnectionItem(
    val id: String,
    val connector_id: String,
    val account_display_id: String? = null,
    val authorization_state: String,
    val sync_timeline: Boolean = true,
    val assistant_read: Boolean = true,
    val last_synced_at: String? = null,
    val failure_code: String? = null,
    val created_at: String? = null,
)

@Serializable
data class StartConnectionRequest(
    val connector_id: String,
    val consent: Boolean,
    val npsso: String? = null,
)

@Serializable
data class StartConnectionResponse(
    val setup_id: String? = null,
    val connection_id: String? = null,
    val authorization_url: String? = null,
    val status: String,
)

@Serializable
data class SetupStatusResponse(
    val status: String,
    val connection_id: String? = null,
    val error: String? = null,
)

@Serializable
data class PreferencesRequest(
    val sync_timeline: Boolean? = null,
    val assistant_read: Boolean? = null,
)

@Serializable
data class RefreshResponse(
    val refreshed: Boolean,
    val spans_created: Int = 0,
)

data class BrandMeta(
    val color: Color,
    val tagline: String,
    val iconRes: Int,
)

val BRAND_CONFIGS = mapOf(
    "google_calendar" to BrandMeta(
        color = Color(0xFF3C90FF),
        tagline = "Calendar events on your timeline",
        iconRes = R.drawable.ic_google_calendar,
    ),
    "playstation" to BrandMeta(
        color = Color(0xFF0070D1),
        tagline = "Gaming sessions and playtime",
        iconRes = R.drawable.ic_playstation,
    ),
)

val FALLBACK_BRAND = BrandMeta(
    color = Color(0xFFA78BFA),
    tagline = "Connected account",
    iconRes = R.drawable.ic_plug,
)

fun getBrandMeta(connectorId: String): BrandMeta =
    BRAND_CONFIGS[connectorId] ?: FALLBACK_BRAND
