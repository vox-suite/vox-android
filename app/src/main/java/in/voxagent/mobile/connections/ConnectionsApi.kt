package `in`.voxagent.mobile.connections

import `in`.voxagent.mobile.net.ApiException
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxHttpException
import `in`.voxagent.mobile.net.VoxJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ConnectionsApi {

    suspend fun listConnectors(token: String): List<ConnectorDescriptor> {
        val json = post("/v1/me/connectors/list", "{}", token)
        return VoxJson.decodeFromString(ListSerializer(ConnectorDescriptor.serializer()), json)
    }

    suspend fun listConnections(token: String): List<ConnectionItem> {
        val json = post("/v1/me/connections/list", "{}", token)
        return VoxJson.decodeFromString(ListSerializer(ConnectionItem.serializer()), json)
    }

    suspend fun startConnection(
        token: String,
        req: StartConnectionRequest,
    ): StartConnectionResponse {
        val body = VoxJson.encodeToString(req)
        val json = post("/v1/me/connections/start", body, token)
        return VoxJson.decodeFromString(StartConnectionResponse.serializer(), json)
    }

    suspend fun getSetupStatus(token: String, setupId: String): SetupStatusResponse {
        val json = post("/v1/me/connections/setup/$setupId/status", "{}", token)
        return VoxJson.decodeFromString(SetupStatusResponse.serializer(), json)
    }

    suspend fun cancelSetup(token: String, setupId: String) {
        post("/v1/me/connections/setup/$setupId/cancel", "{}", token)
    }

    suspend fun updatePreferences(
        token: String,
        connectionId: String,
        req: PreferencesRequest,
    ): ConnectionItem {
        val body = VoxJson.encodeToString(req)
        val json = post("/v1/me/connections/$connectionId/preferences", body, token)
        return VoxJson.decodeFromString(ConnectionItem.serializer(), json)
    }

    suspend fun refreshConnection(token: String, connectionId: String): RefreshResponse {
        val json = post("/v1/me/connections/$connectionId/refresh", "{}", token)
        return VoxJson.decodeFromString(RefreshResponse.serializer(), json)
    }

    suspend fun disconnectConnection(token: String, connectionId: String) {
        post("/v1/me/connections/$connectionId/disconnect", "{}", token)
    }

    private suspend fun post(path: String, body: String, token: String): String =
        try {
            VoxHttp.postJson(path, body, token)
        } catch (e: VoxHttpException) {
            val msg =
                runCatching {
                        val obj = VoxJson.parseToJsonElement(e.responseBody).jsonObject
                        (obj["error"] ?: obj["message"])?.jsonPrimitive?.content
                    }
                    .getOrNull() ?: "Connection request failed (${e.statusCode})"
            throw ApiException(e.statusCode, msg)
        }
}
