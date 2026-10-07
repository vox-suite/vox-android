package `in`.voxagent.mobile.sms

import kotlinx.serialization.Serializable

@Serializable data class SmsMessage(val sender: String, val body: String, val received_at: String)
