package `in`.voxagent.mobile.voice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class VoiceClientMessage {
    @Serializable @SerialName("start") data class Start(val ticket: String) : VoiceClientMessage()

    @Serializable
    @SerialName("playback_finished")
    data class PlaybackFinished(val name: String) : VoiceClientMessage()

    @Serializable @SerialName("text") data class Text(val text: String) : VoiceClientMessage()

    @Serializable @SerialName("stop") data object Stop : VoiceClientMessage()
}

@Serializable
sealed class VoiceServerMessage {
    @Serializable
    @SerialName("connected")
    data class Connected(
        val session_id: String,
        val audio_format: String,
        val sample_rate: Int,
        val input_mode: String = "",
    ) : VoiceServerMessage() {
        fun isSupported() =
            audio_format == "mulaw" &&
                sample_rate == 8000 &&
                input_mode == "stream" &&
                session_id.isNotBlank()
    }

    @Serializable
    @SerialName("mark")
    data class Mark(val name: String, val generation: Long) : VoiceServerMessage()

    @Serializable
    @SerialName("clear")
    data class Clear(val generation: Long) : VoiceServerMessage()

    @Serializable
    @SerialName("error")
    data class Error(val message: String = "Voice session failed") : VoiceServerMessage()
}
