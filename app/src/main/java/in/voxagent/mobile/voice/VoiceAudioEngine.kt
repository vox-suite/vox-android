package `in`.voxagent.mobile.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import `in`.voxagent.mobile.logging.RemoteLog
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import timber.log.Timber

private const val TAG = "VoiceAudioEngine"
private const val CAPTURE_SAMPLE_RATE = 16000
private const val PLAYBACK_SAMPLE_RATE = 44100

class VoiceAudioEngine(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedMode = AudioManager.MODE_NORMAL
    private var savedSpeakerphone = false
    private var savedVoiceVolume = 0
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
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
        val minBuf =
            AudioRecord.getMinBufferSize(
                CAPTURE_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBuf <= 0) {
            throw IllegalStateException("Device does not support 16kHz mono audio capture")
        }

        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalStateException("Microphone permission not granted")
        }

        val record =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                CAPTURE_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Failed to initialize AudioRecord")
        }

        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler =
                AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor =
                NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }
        }
        RemoteLog.i(
            TAG,
            "startCapture: aec_available=${AcousticEchoCanceler.isAvailable()} aec=${echoCanceler?.enabled} ns_available=${NoiseSuppressor.isAvailable()} ns=${noiseSuppressor?.enabled} source=VOICE_COMMUNICATION rate=$CAPTURE_SAMPLE_RATE minBuf=$minBuf",
        )

        audioRecord = record
        capturing = true
        record.startRecording()
        Timber.tag(TAG).d("startCapture: recordingState=${record.recordingState} minBuf=$minBuf")

        val thread =
            Thread(
                {
                    val buffer = ShortArray(minBuf / 2)
                    var readCount = 0
                    var lastLog = 0L
                    while (capturing) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            readCount++
                            val now = System.currentTimeMillis()
                            if (now - lastLog >= 1000) {
                                Timber.tag(TAG).d("capture: read $read shorts (call #$readCount)")
                                lastLog = now
                            }
                            onSamples(buffer.copyOf(read))
                        } else if (read < 0) {
                            RemoteLog.w(TAG, "capture: AudioRecord.read returned error code $read")
                        }
                    }
                    Timber.tag(TAG).d("capture: loop exiting after $readCount successful reads")
                },
                "voice-mic-capture",
            )
        thread.isDaemon = true
        captureThread = thread
        thread.start()
    }

    fun stopCapture() {
        capturing = false
        captureThread?.join(500)
        captureThread = null
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null
    }

    private fun enterCommunicationMode() {
        savedMode = audioManager.mode
        savedSpeakerphone = audioManager.isSpeakerphoneOn
        savedVoiceVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
            0,
        )
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val devices = audioManager.availableCommunicationDevices
            val headset =
                devices.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
            if (!headset) {
                devices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audioManager.setCommunicationDevice(it) }
            }
        } else {
            @Suppress("DEPRECATION")
            if (!audioManager.isWiredHeadsetOn && !audioManager.isBluetoothScoOn) {
                audioManager.isSpeakerphoneOn = true
            }
        }
    }

    private fun exitCommunicationMode() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = savedSpeakerphone
        }
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVoiceVolume, 0)
        audioManager.mode = savedMode
    }

    fun startPlayback() {
        enterCommunicationMode()
        val minBuf =
            AudioTrack.getMinBufferSize(
                PLAYBACK_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        Timber.tag(TAG).d("startPlayback: minBuf=$minBuf")
        val track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(PLAYBACK_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

        RemoteLog.i(
            TAG,
            "startPlayback: AudioTrack state=${track.state} playState=${track.playState} rate=$PLAYBACK_SAMPLE_RATE minBuf=$minBuf bufferFrames=${track.bufferSizeInFrames} perfMode=${track.performanceMode} route=${track.routedDevice?.productName}/${track.routedDevice?.type} audioMode=${audioManager.mode} speakerphone=${audioManager.isSpeakerphoneOn}",
        )
        audioTrack = track
        playing = true
        track.play()
        Timber.tag(TAG).d("startPlayback: after play() playState=${track.playState}")

        val thread =
            Thread(
                {
                    Timber.tag(TAG).d("playback thread started")
                    while (playing) {
                        val chunk = pcmQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                        val epochAtEnqueue = chunk.first
                        if (epochAtEnqueue != playbackEpoch.get()) {
                            Timber.tag(TAG)
                                .d(
                                    "playback: dropping stale chunk (epoch $epochAtEnqueue != ${playbackEpoch.get()})"
                                )
                            queuedChunks.decrementAndGet()
                            continue
                        }
                        val written = track.write(chunk.second, 0, chunk.second.size)
                        Timber.tag(TAG)
                            .d(
                                "playback: wrote $written/${chunk.second.size} shorts, playState=${track.playState}, queued=${queuedChunks.get()}"
                            )
                        queuedChunks.decrementAndGet()
                    }
                    Timber.tag(TAG).d("playback thread exiting")
                },
                "voice-playback",
            )
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
        exitCommunicationMode()
    }

    fun isPlaying(): Boolean = queuedChunks.get() > 0

    fun queuedChunkCount(): Int = queuedChunks.get()

    fun underrunCount(): Int = audioTrack?.underrunCount ?: 0

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
        Timber.tag(TAG)
            .d(
                "enqueuePlayback: ${samples.size} samples, sampleRate=$sampleRate, channels=$channels"
            )
        if (sampleRate != PLAYBACK_SAMPLE_RATE) {
            RemoteLog.w(
                TAG,
                "Skipping MP3 frame at unexpected sample rate $sampleRate (expected $PLAYBACK_SAMPLE_RATE)",
            )
            return
        }
        val mono =
            if (channels <= 1) {
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
