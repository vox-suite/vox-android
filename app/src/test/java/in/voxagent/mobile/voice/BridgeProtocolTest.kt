package `in`.voxagent.mobile.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class BridgeProtocolTest {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    @Test
    fun ticketHandshakeNeverCarriesUserIdentityOrBearerToken() {
        val message =
            json
                .parseToJsonElement(
                    json.encodeToString(
                        VoiceClientMessage.serializer(),
                        VoiceClientMessage.Start("single-use-ticket"),
                    )
                )
                .jsonObject
        assertEquals(setOf("type", "ticket"), message.keys)
        assertEquals("start", message.getValue("type").jsonPrimitive.content)
        assertEquals(
            "wss://bridge.example/bridge/desktop/voice",
            bridgeVoiceUrl("https://bridge.example/"),
        )
    }

    @Test
    fun connectedFrameMustMatchTheBridgeAudioContract() {
        val connected =
            json.decodeFromString(
                VoiceServerMessage.serializer(),
                """{"type":"connected","session_id":"session","device_id":null,"audio_format":"mulaw","sample_rate":8000,"input_mode":"stream"}""",
            ) as VoiceServerMessage.Connected
        assertTrue(connected.isSupported())
        assertFalse(connected.copy(audio_format = "mp3").isSupported())
        assertFalse(connected.copy(sample_rate = 16000).isSupported())
    }

    @Test
    fun playbackAcknowledgmentUsesBridgeMarkName() {
        val mark =
            json.decodeFromString(
                VoiceServerMessage.serializer(),
                """{"type":"mark","name":"response-1","generation":3}""",
            ) as VoiceServerMessage.Mark
        val response =
            json
                .parseToJsonElement(
                    json.encodeToString(
                        VoiceClientMessage.serializer(),
                        VoiceClientMessage.PlaybackFinished(mark.name),
                    )
                )
                .jsonObject
        assertEquals("playback_finished", response.getValue("type").jsonPrimitive.content)
        assertEquals("response-1", response.getValue("name").jsonPrimitive.content)
    }
}
