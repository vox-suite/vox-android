package `in`.voxagent.mobile.voice

enum class VoiceStatus {
    IDLE,
    CONNECTING,
    ACTIVE,
    ERROR,
}

sealed class VoiceEvent {
    data class UserTranscript(val text: String) : VoiceEvent()

    data class Delta(val text: String) : VoiceEvent()

    data object Thinking : VoiceEvent()

    data object Done : VoiceEvent()

    data object Interrupted : VoiceEvent()

    data class Error(val message: String) : VoiceEvent()
}
