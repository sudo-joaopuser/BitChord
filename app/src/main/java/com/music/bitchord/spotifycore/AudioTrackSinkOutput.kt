package com.music.bitchord.spotifycore

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import xyz.gianlu.librespot.player.mixing.output.OutputAudioFormat
import xyz.gianlu.librespot.player.mixing.output.SinkOutput
import java.io.IOException

/**
 * librespot [SinkOutput] backed by Android's [AudioTrack].
 *
 * librespot-java has no Android sink: its stock outputs are Java Sound
 * (absent on Android), a pipe and stdout. This one is instantiated by
 * librespot's AudioSink through [PlayerConfiguration] CUSTOM output,
 * which is why it needs a public no-arg constructor — AudioSink builds
 * it by reflection with no parameters.
 *
 * librespot calls [start] with the decoder's PCM format, [write] with
 * raw PCM, [stop] on pause and [start] again on resume, so [start]
 * resumes an existing track when the format did not change and only
 * rebuilds it when it did.
 */
class AudioTrackSinkOutput : SinkOutput {
    private var track: AudioTrack? = null
    private var currentFormat: OutputAudioFormat? = null
    private var bigEndian16 = false
    private var volume = 1f

    override fun start(format: OutputAudioFormat): Boolean {
        val existing = track
        if (existing != null && sameFormat(format)) {
            existing.play()
            return true
        }
        releaseTrack()
        val channelConfig = when (format.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> return false
        }
        val encoding = when (format.sampleSizeInBits) {
            16 -> AudioFormat.ENCODING_PCM_16BIT
            8 -> AudioFormat.ENCODING_PCM_8BIT
            else -> return false
        }
        val sampleRate = format.sampleRate.toInt()
        if (sampleRate <= 0) return false
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuf <= 0) return false
        bigEndian16 = format.isBigEndian && format.sampleSizeInBits == 16
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(encoding)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            return false
        }
        created.setVolume(volume)
        created.play()
        track = created
        currentFormat = format
        return true
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        val t = track ?: throw IOException("AudioTrack not started")
        var data = buffer
        var off = offset
        if (bigEndian16) {
            // AudioTrack is little-endian; swap the slice in a copy.
            val swapped = buffer.copyOfRange(offset, offset + len)
            var i = 0
            while (i + 1 < swapped.size) {
                val b = swapped[i]
                swapped[i] = swapped[i + 1]
                swapped[i + 1] = b
                i += 2
            }
            data = swapped
            off = 0
        }
        var written = 0
        while (written < len) {
            val n = t.write(data, off + written, len - written, AudioTrack.WRITE_BLOCKING)
            if (n <= 0) throw IOException("AudioTrack write failed: $n")
            written += n
        }
    }

    override fun setVolume(volume: Float): Boolean {
        this.volume = volume.coerceIn(0f, 1f)
        track?.setVolume(this.volume)
        return true
    }

    override fun stop() {
        track?.pause()
    }

    override fun flush() {
        track?.flush()
    }

    override fun drain() {
        // AudioTrack has no drain; written buffers are small and the
        // blocking writes already pace the sink, so nothing to wait for.
    }

    override fun release() {
        releaseTrack()
    }

    // SinkOutput extends Closeable: librespot never calls this (it
    // uses release()), but the interface requires it.
    override fun close() {
        releaseTrack()
    }

    private fun releaseTrack() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        currentFormat = null
    }

    private fun sameFormat(format: OutputAudioFormat): Boolean {
        val cur = currentFormat ?: return false
        return cur.sampleRate == format.sampleRate &&
            cur.sampleSizeInBits == format.sampleSizeInBits &&
            cur.channels == format.channels &&
            cur.isBigEndian == format.isBigEndian
    }
}
