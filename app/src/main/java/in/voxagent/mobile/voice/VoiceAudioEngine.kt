package `in`.voxagent.mobile.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
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
private const val PLAYBACK_SAMPLE_RATE = 8000

class VoiceAudioEngine(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedMode = AudioManager.MODE_NORMAL
    private var savedSpeakerphone = false
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false

    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    @Volatile private var playing = false
    private val pcmQueue = LinkedBlockingQueue<Pair<Long, ShortArray>>(500)
    private val queuedChunks = AtomicInteger(0)
    private val playbackEpoch = AtomicLong(0)

    private val playbackLock = Any()
    private val progress = PlaybackProgress()
    private var playbackBase = 0L
    private var communicationModeEntered = false
    private var focusRequest: AudioFocusRequest? = null

    fun startCapture(onError: () -> Unit = {}, onSamples: (ShortArray) -> Unit) {
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

        audioRecord = record
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
                        val read =
                            runCatching { record.read(buffer, 0, buffer.size) }
                                .getOrDefault(AudioRecord.ERROR_DEAD_OBJECT)
                        if (read > 0) {
                            readCount++
                            val now = System.currentTimeMillis()
                            if (now - lastLog >= 1000) {
                                Timber.tag(TAG).d("capture: read $read shorts (call #$readCount)")
                                lastLog = now
                            }
                            onSamples(buffer.copyOf(read))
                        } else if (read < 0) {
                            if (capturing) {
                                capturing = false
                                onError()
                            }
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
        audioRecord?.let { runCatching { it.stop() } }
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
        communicationModeEntered = true
        savedMode = audioManager.mode
        savedSpeakerphone = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
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
        if (!communicationModeEntered) return
        communicationModeEntered = false
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = savedSpeakerphone
        }
        audioManager.mode = savedMode
    }

    fun startPlayback(onFocusLost: () -> Unit = {}) {
        val focus =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    if (
                        change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    )
                        onFocusLost()
                }
                .build()
        focusRequest = focus
        check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Audio is in use by another call"
        }
        enterCommunicationMode()
        val minBuf =
            AudioTrack.getMinBufferSize(
                PLAYBACK_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        check(minBuf > 0) { "Device does not support voice playback" }
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
        playbackBase = track.playbackHeadPosition.toLong() and 0xffffffffL
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
                        synchronized(playbackLock) {
                            if (epochAtEnqueue == playbackEpoch.get()) {
                                var offset = 0
                                while (playing && offset < chunk.second.size) {
                                    val count =
                                        runCatching {
                                                track.write(
                                                    chunk.second,
                                                    offset,
                                                    chunk.second.size - offset,
                                                )
                                            }
                                            .getOrDefault(AudioTrack.ERROR_DEAD_OBJECT)
                                    if (count <= 0) {
                                        playing = false
                                        break
                                    }
                                    offset += count
                                }
                                queuedChunks.decrementAndGet()
                            }
                        }
                    }
                    Timber.tag(TAG).d("playback thread exiting")
                },
                "voice-playback",
            )
        thread.isDaemon = true
        playbackThread = thread
        thread.start()
    }

    fun stopPlayback() {
        playing = false

        audioTrack?.let { runCatching { it.pause() } }
        playbackThread?.join(500)
        playbackThread = null
        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null
        pcmQueue.clear()
        queuedChunks.set(0)
        progress.clear()
        exitCommunicationMode()
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    fun isPlaying(): Boolean = synchronized(playbackLock) { progress.remaining(playedFrames()) > 0 }

    fun playbackPosition(): Long = synchronized(playbackLock) { playedFrames() }

    fun bufferedMilliseconds(): Long =
        synchronized(playbackLock) { progress.remaining(playedFrames()) / 8 }

    fun queuedChunkCount(): Int = queuedChunks.get()

    fun underrunCount(): Int = audioTrack?.underrunCount ?: 0

    fun clearPlayback() =
        synchronized(playbackLock) {
            playbackEpoch.incrementAndGet()
            pcmQueue.clear()
            queuedChunks.set(0)
            audioTrack?.let { track ->
                track.pause()
                track.flush()
                playbackBase = track.playbackHeadPosition.toLong() and 0xffffffffL
                track.play()
            }
            progress.clear()
        }

    fun enqueueMuLaw(bytes: ByteArray) =
        synchronized(playbackLock) {
            check(playing) { "Playback is unavailable" }
            check(bytes.size <= 8192 && progress.remaining(playedFrames()) + bytes.size <= 80000) {
                "Voice playback stalled"
            }
            for (offset in bytes.indices step 160) {
                val samples =
                    ShortArray(minOf(160, bytes.size - offset)) { MuLaw.decode(bytes[offset + it]) }
                check(pcmQueue.offer(playbackEpoch.get() to samples)) {
                    "Voice playback queue full"
                }
                progress.enqueue(samples.size)
                queuedChunks.incrementAndGet()
            }
        }

    fun mark(name: String) = synchronized(playbackLock) { progress.mark(name) }

    fun completedMarks(): List<String> =
        synchronized(playbackLock) {
            check(playing) { "Voice playback stopped" }
            progress.drain(playedFrames())
        }

    private fun playedFrames(): Long =
        ((audioTrack?.playbackHeadPosition?.toLong() ?: 0L) - playbackBase) and 0xffffffffL

    fun release() {
        stopCapture()
        stopPlayback()
    }
}
