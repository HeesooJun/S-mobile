package com.example.lifesaiver.core.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log

@SuppressLint("MissingPermission")
class AudioEngine {
    private val sampleRate = 8000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val format = AudioFormat.ENCODING_PCM_16BIT

    private val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, format)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false

    init {
        if (minBufSize != AudioRecord.ERROR && minBufSize != AudioRecord.ERROR_BAD_VALUE) {
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(format)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelOut)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack?.play()
            } catch (e: Exception) {
                Log.e("AudioEngine", "Speaker init failed", e)
            }
        }
    }

    fun startRecording(onData: (ByteArray) -> Unit) {
        if (isRecording || minBufSize <= 0) return
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelIn,
                format,
                minBufSize
            )
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                isRecording = true
                audioRecord?.startRecording()
                Thread {
                    val buffer = ByteArray(160)
                    while (isRecording) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) onData(buffer.copyOf())
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Mic error", e)
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
        }
    }

    fun playAudio(data: ByteArray) {
        try {
            audioTrack?.write(data, 0, data.size)
        } catch (e: Exception) {
        }
    }
}
