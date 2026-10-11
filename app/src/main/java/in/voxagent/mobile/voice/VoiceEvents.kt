package `in`.voxagent.mobile.voice

enum class VoiceStatus {
    IDLE,
    CONNECTING,
    ACTIVE,
    ERROR,
}

sealed class VoiceEvent {
    data class Error(val message: String) : VoiceEvent()
}
