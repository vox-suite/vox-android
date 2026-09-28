package `in`.voxagent.mobile.voice

import android.util.Log
import java.io.PipedInputStream
import java.io.PipedOutputStream
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.BitstreamException
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer

private const val TAG = "Mp3StreamDecoder"
private const val PIPE_BUFFER_SIZE = 256 * 1024

/**
 * Decodes a live MP3 byte stream fed in arbitrary-sized chunks (WebSocket
 * frames, not MP3-frame-aligned). One Bitstream/Decoder pair is kept alive
 * for the whole turn instead of being rebuilt per chunk, so a frame split
 * across two chunks decodes correctly instead of losing its tail.
 */
class Mp3StreamDecoder(private val onPcm: (samples: ShortArray, sampleRate: Int, channels: Int) -> Unit) {
    private var pipeOut: PipedOutputStream? = null
    private var decodeThread: Thread? = null

    @Synchronized
    fun start() {
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
        val out = pipeOut ?: return
        try {
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enqueue MP3 chunk (decoder stopped?): ${e.message}")
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
        val bitstream = Bitstream(input)
        val decoder = Decoder()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val header = try {
                    bitstream.readFrame()
                } catch (e: BitstreamException) {
                    Log.w(TAG, "MP3 decoding warning: ${e.message}")
                    break
                } ?: break

                val output = decoder.decodeFrame(header, bitstream)
                if (output is SampleBuffer && output.bufferLength > 0) {
                    val samples = output.buffer.copyOf(output.bufferLength)
                    onPcm(samples, output.sampleFrequency, output.channelCount)
                }
                bitstream.closeFrame()
            }
        } catch (e: Exception) {
            if (!Thread.currentThread().isInterrupted) {
                Log.w(TAG, "MP3 decode loop stopped: ${e.message}")
            }
        } finally {
            runCatching { bitstream.close() }
        }
    }
}
