package `in`.voxagent.mobile.voice

import java.io.PipedInputStream
import java.io.PipedOutputStream
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.BitstreamException
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import timber.log.Timber

private const val TAG = "Mp3StreamDecoder"
private const val PIPE_BUFFER_SIZE = 256 * 1024

class Mp3StreamDecoder(
    private val onPcm: (samples: ShortArray, sampleRate: Int, channels: Int) -> Unit
) {
    private var pipeOut: PipedOutputStream? = null
    private var decodeThread: Thread? = null

    @Synchronized
    fun start() {
        Timber.tag(TAG).d("start")
        stop()
        val input = PipedInputStream(PIPE_BUFFER_SIZE)
        val output = PipedOutputStream(input)
        pipeOut = output

        val thread = Thread({ decodeLoop(input) }, "mp3-stream-decoder")
        thread.isDaemon = true
        decodeThread = thread
        thread.start()
    }

    @Synchronized
    fun enqueueChunk(bytes: ByteArray) {
        Timber.tag(TAG).d("enqueueChunk: ${bytes.size} bytes")
        val out =
            pipeOut
                ?: run {
                    Timber.tag(TAG).w("enqueueChunk: no pipe open, dropping ${bytes.size} bytes")
                    return
                }
        try {
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to enqueue MP3 chunk (decoder stopped?): ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        pipeOut?.let { runCatching { it.close() } }
        pipeOut = null
        decodeThread?.interrupt()
        decodeThread = null
    }

    private fun decodeLoop(input: PipedInputStream) {
        Timber.tag(TAG).d("decodeLoop: started")
        val bitstream = Bitstream(input)
        val decoder = Decoder()
        var frameCount = 0
        try {
            while (!Thread.currentThread().isInterrupted) {
                val header =
                    try {
                        bitstream.readFrame()
                    } catch (e: BitstreamException) {
                        Timber.tag(TAG).w("MP3 decoding warning: ${e.message}")
                        break
                    } ?: break

                val output = decoder.decodeFrame(header, bitstream)
                if (output is SampleBuffer && output.bufferLength > 0) {
                    frameCount++
                    val samples = output.buffer.copyOf(output.bufferLength)
                    Timber.tag(TAG)
                        .d(
                            "decodeLoop: frame #$frameCount bufferLength=${output.bufferLength} " +
                                "sampleRate=${output.sampleFrequency} channels=${output.channelCount}"
                        )
                    onPcm(samples, output.sampleFrequency, output.channelCount)
                } else {
                    Timber.tag(TAG).d("decodeLoop: frame decoded to non-audio output: $output")
                }
                bitstream.closeFrame()
            }
        } catch (e: Exception) {
            if (!Thread.currentThread().isInterrupted) {
                Timber.tag(TAG).w("MP3 decode loop stopped: ${e.message}")
            }
        } finally {
            Timber.tag(TAG).d("decodeLoop: exiting after $frameCount frames")
            runCatching { bitstream.close() }
        }
    }
}
