package `in`.voxagent.mobile.voice

import org.junit.Assert.*
import org.junit.Test

class BridgeAudioTest {
    @Test
    fun muLawPreservesSilenceAndSignedSpeech() {
        assertEquals(0, MuLaw.decode(0xff.toByte()).toInt())
        for (sample in listOf(-30000, -10000, -1000, 0, 1000, 10000, 30000)) {
            val result = MuLaw.decode(MuLaw.encode(sample.toShort())).toInt()
            assertTrue("$sample -> $result", kotlin.math.abs(result - sample) <= 1000)
        }
    }

    @Test
    fun captureFramesSurviveOddBatchBoundaries() {
        val packetizer = MuLawPacketizer()
        assertTrue(packetizer.accept(ShortArray(159)).isEmpty())
        val frames = packetizer.accept(ShortArray(481))
        assertEquals(2, frames.size)
        assertTrue(frames.all { it.size == 160 })
    }

    @Test
    fun marksWaitForHardwarePlaybackAndClearDropsOldMarks() {
        val progress = PlaybackProgress()
        progress.enqueue(160)
        progress.mark("one")
        assertTrue(progress.drain(159).isEmpty())
        assertEquals(listOf("one"), progress.drain(160))
        progress.enqueue(160)
        progress.mark("old")
        progress.clear()
        assertTrue(progress.drain(320).isEmpty())
    }
}
