package com.yujif.thinklet.realtimecompanion.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AssistantAudioPlayer {
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (audioTrack != null) return
        val minBuffer = AudioTrack.getMinBufferSize(
            MicrophoneStreamer.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(MicrophoneStreamer.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, MicrophoneStreamer.SAMPLE_RATE / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    fun play(bytes: ByteArray) {
        val track = audioTrack ?: return
        track.write(bytes, 0, bytes.size)
    }

    fun stop() {
        audioTrack?.runCatching { stop() }
        audioTrack?.runCatching { release() }
        audioTrack = null
    }
}
