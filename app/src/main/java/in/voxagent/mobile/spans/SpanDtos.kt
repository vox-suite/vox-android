package `in`.voxagent.mobile.spans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewSpanPayload(
    val title: String,
    val notes: String = "",
    val category: String = "general",
    val status: String = "planned",
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("execution_type") val executionType: String? = null,
    @SerialName("collection_ids") val collectionIds: List<String> = emptyList(),
)

@Serializable
data class SpanPatch(
    val title: String? = null,
    val notes: String? = null,
    val category: String? = null,
    val status: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
)
