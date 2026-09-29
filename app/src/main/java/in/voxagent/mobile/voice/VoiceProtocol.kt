package `in`.voxagent.mobile.voice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class VoiceClientMessage {
    @Serializable
    @SerialName("turn")
    data class Turn(
        val conversation_id: String? = null,
    ) : VoiceClientMessage()

    @Serializable
    @SerialName("interrupt")
    object Interrupt : VoiceClientMessage()

    @Serializable
    @SerialName("ping")
    object Ping : VoiceClientMessage()
}

@Serializable
sealed class VoiceServerMessage {
    @Serializable
    @SerialName("connected")
    data class Connected(val format: String, val sample_rate: Int) : VoiceServerMessage()

    @Serializable
    @SerialName("user_transcript")
    data class UserTranscript(val turn_id: String, val text: String) : VoiceServerMessage()

    @Serializable
    @SerialName("thinking")
    data class Thinking(val turn_id: String) : VoiceServerMessage()

    @Serializable
    @SerialName("text_delta")
    data class TextDelta(val turn_id: String, val delta: String) : VoiceServerMessage()

    @Serializable
    @SerialName("done")
    data class Done(val turn_id: String) : VoiceServerMessage()

    @Serializable
    @SerialName("interrupted")
    object Interrupted : VoiceServerMessage()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : VoiceServerMessage()

    @Serializable
    @SerialName("pong")
    object Pong : VoiceServerMessage()
}
