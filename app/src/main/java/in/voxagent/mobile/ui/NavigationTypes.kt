package `in`.voxagent.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class Destination {
    Home,
    Timeline,
    Spaces,
    Pulse,
    Connections,
    Updates,
}

enum class TalkState {
    Idle,
    Connecting,
    Active,
    Error,
}
