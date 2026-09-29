package `in`.voxagent.mobile.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "VoiceAudioEngine"
private const val CAPTURE_SAMPLE_RATE = 16000
private const val PLAYBACK_SAMPLE_RATE = 44100

/**
 * Mic capture (16kHz mono PCM16, streamed to the server for transcription)
 * and MP3-decoded playback (44.1kHz mono, matching ElevenLabs' output
 * format) for the direct voice session. Android's AudioRecord/AudioTrack
 * accept these rates directly, so unlike the desktop client (cpal,
 * device-native rate only) no manual resampling is needed on either side.
 */
class VoiceAudioEngine {
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false

    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    @Volatile private var playing = false
    private val pcmQueue = LinkedBlockingQueue<Pair<Long, ShortArray>>()
    private val queuedChunks = AtomicInteger(0)
    private val playbackEpoch = AtomicLong(0)

    val mp3Decoder = Mp3StreamDecoder { samples, sampleRate, channels ->
        enqueuePlayback(samples, sampleRate, channels)
    }

    fun startCapture(onSamples: (ShortArray) -> Unit) {
        val minBuf = AudioRecord.getMinBufferSize(
            CAPTURE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            throw IllegalStateException("Device does not support 16kHz mono audio capture")
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            CAPTURE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Failed to initialize AudioRecord")
        }

        audioRecord = record
        capturing = true
        record.startRecording()
        Log.d(TAG, "startCapture: recordingState=${record.recordingState} minBuf=$minBuf")

        val thread = Thread({
            val buffer = ShortArray(minBuf / 2)
            var readCount = 0
            var lastLog = 0L
            while (capturing) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    readCount++
                    val now = System.currentTimeMillis()
                    if (now - lastLog >= 1000) {
                        Log.d(TAG, "capture: read $read shorts (call #$readCount)")
                        lastLog = now
                    }
                    onSamples(buffer.copyOf(read))
                } else if (read < 0) {
                    Log.w(TAG, "capture: AudioRecord.read returned error code $read")
                }
            }
            Log.d(TAG, "capture: loop exiting after $readCount successful reads")
        }, "voice-mic-capture")
        thread.isDaemon = true
        captureThread = thread
        thread.start()
    }

    fun stopCapture() {
        capturing = false
        captureThread?.join(500)
        captureThread = null
        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null
    }

    fun startPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(
            PLAYBACK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        Log.d(TAG, "startPlayback: minBuf=$minBuf")
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_VOICE_COMMUNICATION routes to the in-call/earpiece stream and
                    // follows in-call volume, which is silent or barely audible without an
                    // active telephony call forcing speakerphone. USAGE_MEDIA plays on the
                    // normal media stream through the loudspeaker like any other app audio.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(PLAYBACK_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        Log.d(TAG, "startPlayback: AudioTrack state=${track.state} playState=${track.playState} sessionId=${track.audioSessionId}")
        audioTrack = track
        playing = true
        track.play()
        Log.d(TAG, "startPlayback: after play() playState=${track.playState}")

        val thread = Thread({
            Log.d(TAG, "playback thread started")
            while (playing) {
                val chunk = pcmQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val epochAtEnqueue = chunk.first
                if (epochAtEnqueue != playbackEpoch.get()) {
                    Log.d(TAG, "playback: dropping stale chunk (epoch $epochAtEnqueue != ${playbackEpoch.get()})")
                    queuedChunks.decrementAndGet()
                    continue
                }
                val written = track.write(chunk.second, 0, chunk.second.size)
                Log.d(TAG, "playback: wrote $written/${chunk.second.size} shorts, playState=${track.playState}, queued=${queuedChunks.get()}")
                queuedChunks.decrementAndGet()
            }
            Log.d(TAG, "playback thread exiting")
        }, "voice-playback")
        thread.isDaemon = true
        playbackThread = thread
        thread.start()

        mp3Decoder.start()
    }

    fun stopPlayback() {
        playing = false
        mp3Decoder.stop()
        playbackThread?.join(500)
        playbackThread = null
        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null
        pcmQueue.clear()
        queuedChunks.set(0)
    }

    fun isPlaying(): Boolean = queuedChunks.get() > 0

    fun clearPlayback() {
        playbackEpoch.incrementAndGet()
        pcmQueue.clear()
        queuedChunks.set(0)
        audioTrack?.let {
            runCatching {
                it.pause()
                it.flush()
                it.play()
            }
        }
        mp3Decoder.start()
    }

    private fun enqueuePlayback(samples: ShortArray, sampleRate: Int, channels: Int) {
        Log.d(TAG, "enqueuePlayback: ${samples.size} samples, sampleRate=$sampleRate, channels=$channels")
        if (sampleRate != PLAYBACK_SAMPLE_RATE) {
            Log.w(TAG, "Skipping MP3 frame at unexpected sample rate $sampleRate (expected $PLAYBACK_SAMPLE_RATE)")
            return
        }
        val mono = if (channels <= 1) {
            samples
        } else {
            val frames = samples.size / channels
            ShortArray(frames) { i ->
                var sum = 0
                for (c in 0 until channels) sum += samples[i * channels + c]
                (sum / channels).toShort()
            }
        }
        queuedChunks.incrementAndGet()
        pcmQueue.put(playbackEpoch.get() to mono)
    }

    fun release() {
        stopCapture()
        stopPlayback()
    }
}
