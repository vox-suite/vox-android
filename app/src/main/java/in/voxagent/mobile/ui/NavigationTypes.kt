package `in`.voxagent.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class Destination {
    Home,
    Timeline,
    Spaces,
    Pulse,
    Connections,
}

enum class TalkState {
    Idle,
    Connecting,
    Active,
    Error,
}
