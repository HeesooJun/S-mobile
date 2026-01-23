package com.example.lifesaiver.core.audio

import android.media.MediaRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceRecorder(private val outputDir: File) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File? {
        stopInternal()
        return try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val name = "voice_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".m4a"
            val file = File(outputDir, name)

            val rec = MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioSamplingRate(16000)
            rec.setAudioEncodingBitRate(20_000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()

            recorder = rec
            outputFile = file
            file
        } catch (_: Exception) {
            stopInternal()
            null
        }
    }

    fun stop(): File? {
        val file = outputFile
        stopInternal()
        return file
    }

    private fun stopInternal() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile = null
    }
}
