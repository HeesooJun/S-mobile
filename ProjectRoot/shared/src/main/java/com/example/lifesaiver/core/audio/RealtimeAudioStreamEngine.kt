package com.example.lifesaiver.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.apply
import kotlin.code
import kotlin.collections.copyOfRange
import kotlin.math.sqrt
import kotlin.ranges.rangeTo
import kotlin.run
import kotlin.text.format
import kotlin.text.toByteArray

/**
 * [실시간 오디오 스트리밍 엔진]
 * - Wi-Fi Aware 실시간 통화용 (UDP 전송 최적화)
 * - Echo Cancellation (AEC) 및 Noise Suppression (NS) 적용
 * - 통화 모드(VOICE_COMMUNICATION) 사용
 */
@SuppressLint("MissingPermission")
class RealtimeAudioStreamEngine(private val context: Context) {
    private val sampleRate = 48000
    private val channelCount = 1
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val opusMime = MediaFormat.MIMETYPE_AUDIO_OPUS
    private val opusBitrate = 16_000
    private val opusReferenceSampleRate = 48_000
    private val opusPreSkipSamples = 312
    private val opusSeekPreRollSamples = 3_840
    private val frameDurationMs = 20
    private val pcmBytesPerFrame = sampleRate / 1000 * frameDurationMs * 2

    // 지연 시간 최소화를 위한 버퍼 크기 산정
    private val minBufSizeIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private val minBufSizeOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    // 음향 효과 (에코 캔슬링, 노이즈 억제)
    private var echoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var opusEncoder: MediaCodec? = null
    private var opusDecoder: MediaCodec? = null
    private var preferOpus = true
    private var preferSpeakerphone = true
    private var useOpus = true
    private val encoderBufferInfo = MediaCodec.BufferInfo()
    private val decoderBufferInfo = MediaCodec.BufferInfo()

    private var isStreaming = false
    private var isCapturing = false
    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var opusPcmBuffer = ByteArray(pcmBytesPerFrame)
    private var opusPcmOffset = 0
    private var lastCaptureLogAt = 0L
    private var lastEncodeLogAt = 0L
    private var lastDecodeLogAt = 0L
    private var lastPlayLogAt = 0L
    private var lastNetInLogAt = 0L
    private var lastDropLogAt = 0L
    private var lastDecodeSkipLogAt = 0L
    private var lastGateLogAt = 0L
    private var lastEchoLogAt = 0L
    private var lastPlaybackRms = 0.0
    private var lastPlaybackAt = 0L
    private val debugLogIntervalMs = 1000L
    private val silenceGateEnabled = false
    private val silenceGateRmsThreshold = 3.0
    private val silenceGateWarmupFrames = 50
    private val transmitGain = 0.7
    private val echoMitigationEnabled = true
    private val echoMitigationWindowMs = 240L
    private val echoMitigationPlaybackRmsThresholdSpeaker = 700.0
    private val echoMitigationPlaybackRmsThresholdEarpiece = 280.0
    private val echoMitigationMaxRatioSpeaker = 1.25
    private val echoMitigationMaxRatioEarpiece = 1.60
    private var captureFrameCount = 0

    private val _debugStats = MutableStateFlow(AudioDebugStats())
    val debugStats = _debugStats.asStateFlow()

    /**
     * 통화 시작 (마이크 입력 -> 콜백으로 PCM 데이터 전달)
     * @param onAudioDataAvailable: 네트워크로 전송할 PCM 바이트 배열
     */
    @Synchronized
    fun startStreaming(onAudioDataAvailable: (ByteArray) -> Unit) {
        if (isStreaming) return

        try {
            Log.d("AudioEngine", "Starting stream...")

            // 1. 오디오 매니저 설정 (통화 모드 + 라우팅)
            applyAudioModeAndRoute()

            // 2. AudioTrack 초기화 (수신 음성 재생용)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // 통화용 출력
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfigOut)
                        .build()
                )
                .setBufferSizeInBytes(minBufSizeOut)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                throw kotlin.IllegalStateException("audioTrack init failed")
            }
            Log.d("AudioEngine", "AudioTrack initialized buffer=$minBufSizeOut")

            useOpus = preferOpus && setupOpusCodec()
            _debugStats.update { stats ->
                stats.copy(
                    isStreaming = true,
                    useOpus = useOpus,
                    speakerphoneEnabled = preferSpeakerphone,
                    lastStartError = null
                )
            }

            // 3. AudioRecord 초기화 (VOICE_COMMUNICATION 소스 사용 - AEC 필수)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfigIn,
                audioFormat,
                minBufSizeIn
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                val sessionId = audioRecord!!.audioSessionId
                val disableVoiceFx = shouldDisableVoiceEffectsForDevice()
                if (disableVoiceFx) {
                    Log.w("AudioEngine", "Disable AEC/NS for device compatibility: ${Build.MANUFACTURER} ${Build.MODEL}")
                }
                if (!disableVoiceFx && AcousticEchoCanceler.isAvailable()) {
                    val aec = AcousticEchoCanceler.create(sessionId)
                    if (aec != null) {
                        echoCanceler = aec
                        try {
                            aec.enabled = true
                            Log.d("AudioEngine", "AEC Enabled")
                        } catch (e: Exception) {
                            Log.w("AudioEngine", "AEC enable failed: ${e.message}")
                        }
                    } else {
                        Log.w("AudioEngine", "AEC create returned null")
                    }
                }
                if (AutomaticGainControl.isAvailable()) {
                    val agc = AutomaticGainControl.create(sessionId)
                    if (agc != null) {
                        automaticGainControl = agc
                        try {
                            agc.enabled = true
                            Log.d("AudioEngine", "AGC Enabled")
                        } catch (e: Exception) {
                            Log.w("AudioEngine", "AGC enable failed: ${e.message}")
                        }
                    }
                }
                if (!disableVoiceFx && NoiseSuppressor.isAvailable()) {
                    val ns = NoiseSuppressor.create(sessionId)
                    if (ns != null) {
                        noiseSuppressor = ns
                        try {
                            ns.enabled = true
                            Log.d("AudioEngine", "NS Enabled")
                        } catch (e: Exception) {
                            Log.w("AudioEngine", "NS enable failed: ${e.message}")
                        }
                    }
                }
            } else {
                audioRecord?.release()
                audioRecord = null
                _debugStats.update { stats ->
                    stats.copy(lastStartError = "audioRecord init failed")
                }
            }

            // 4. 하드웨어 시작
            audioTrack?.play()
            isStreaming = true
            if (audioRecord != null) {
                try {
                    audioRecord?.startRecording()
                    isCapturing = true
                } catch (e: Exception) {
                    Log.e("AudioEngine", "AudioRecord start failed", e)
                    _debugStats.update { stats ->
                        stats.copy(lastStartError = e.message ?: "audioRecord start failed")
                    }
                    isCapturing = false
                }
            }

            // 5. 녹음 루프 실행
            if (isCapturing) {
                opusPcmOffset = 0
                captureFrameCount = 0
                streamJob = scope.launch {
                    val buffer = ByteArray(pcmBytesPerFrame)

                    while (isActive && isStreaming && isCapturing) {
                        val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readResult > 0) {
                            val rms = calcRmsPcm16(buffer, readResult)
                            val inWarmup = captureFrameCount < silenceGateWarmupFrames
                            captureFrameCount++
                            maybeLogCapture(readResult, rms)
                            if (silenceGateEnabled && rms < silenceGateRmsThreshold) {
                                if (inWarmup) {
                                    buffer.fill(0, 0, readResult)
                                } else {
                                    maybeLogGate(rms, readResult)
                                    continue
                                }
                            }
                            val effectiveGain = resolveTransmitGain(rms)
                            if (effectiveGain in 0.0..0.999) {
                                applyGainPcm16(buffer, readResult, effectiveGain)
                            }
                            if (useOpus) {
                                var offset = 0
                                while (offset < readResult) {
                                    val remaining = pcmBytesPerFrame - opusPcmOffset
                                    val toCopy =
                                        kotlin.comparisons.minOf(remaining, readResult - offset)
                                    System.arraycopy(buffer, offset, opusPcmBuffer, opusPcmOffset, toCopy)
                                    opusPcmOffset += toCopy
                                    offset += toCopy
                                    if (opusPcmOffset == pcmBytesPerFrame) {
                                        encodeAndSendOpus(opusPcmBuffer, pcmBytesPerFrame, onAudioDataAvailable)
                                        opusPcmOffset = 0
                                    }
                                }
                            } else {
                                maybeLogPcmSend(readResult)
                                onAudioDataAvailable(buffer.copyOfRange(0, readResult))
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("AudioEngine", "Error starting stream", e)
            _debugStats.update { stats ->
                stats.copy(
                    isStreaming = false,
                    lastStartError = e.message ?: "start error"
                )
            }
            stopStreaming()
        }
    }

    private fun shouldDisableVoiceEffectsForDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: return false
        val model = Build.MODEL ?: return false
        if (!manufacturer.equals("samsung", ignoreCase = true)) return false
        // Galaxy Note9 family (SM-N960*)
        return model.startsWith("SM-N960", ignoreCase = true) ||
            model.contains("Note9", ignoreCase = true)
    }

    /**
     * 네트워크에서 받은 오디오 데이터 재생
     */
    fun playReceivedAudio(data: ByteArray) {
        if (!isStreaming || audioTrack == null) {
            val reason = if (!isStreaming) "not_streaming" else "track_null"
            maybeLogDrop(reason, data.size)
            return
        }
        if (useOpus && opusDecoder == null) {
            maybeLogDrop("decoder_null", data.size)
            return
        }
        try {
            maybeLogNetIn(data.size)
            if (useOpus) {
                decodeAndPlayOpus(data)
            } else {
                updatePlaybackMonitor(calcRmsPcm16(data, data.size))
                val written = audioTrack?.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING) ?: 0
                maybeLogPlay(data.size, written)
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error playing audio", e)
            _debugStats.update { stats ->
                stats.copy(
                    playFailCount = stats.playFailCount + 1,
                    lastPlayError = e.message ?: "play error"
                )
            }
        }
    }

    /**
     * 스트리밍 종료 및 자원 해제
     */
    @Synchronized
    fun stopStreaming() {
        if (!isStreaming) return

        Log.d("AudioEngine", "Stopping stream...")
        isStreaming = false
        isCapturing = false
        streamJob?.cancel()
        _debugStats.update { it.copy(isStreaming = false) }

        try {
            // 녹음기 해제
            audioRecord?.stop()
            audioRecord?.release()

            // 효과 해제
            echoCanceler?.release()
            automaticGainControl?.release()
            noiseSuppressor?.release()

            // 재생기 해제
            audioTrack?.stop()
            audioTrack?.release()
            releaseOpusCodec()

        } catch (e: Exception) {
            Log.e("AudioEngine", "Error closing resources", e)
        } finally {
            audioRecord = null
            audioTrack = null
            echoCanceler = null
            automaticGainControl = null
            noiseSuppressor = null
            opusPcmOffset = 0
            captureFrameCount = 0
            lastPlaybackRms = 0.0
            lastPlaybackAt = 0L

            // 오디오 모드 원상복구
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { audioManager.clearCommunicationDevice() }
            }
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    fun setPreferredOpus(enabled: Boolean) {
        preferOpus = enabled
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        preferSpeakerphone = enabled
        if (isStreaming) {
            applyAudioModeAndRoute()
        }
        _debugStats.update { stats ->
            stats.copy(speakerphoneEnabled = enabled)
        }
    }

    fun isSpeakerphoneEnabled(): Boolean = preferSpeakerphone

    fun isOpusSupported(): Boolean {
        return try {
            val encoderName = findPreferredCodecName(opusMime, isEncoder = true) ?: return false
            val decoderName = findPreferredCodecName(opusMime, isEncoder = false) ?: return false
            MediaCodec.createByCodecName(encoderName).release()
            MediaCodec.createByCodecName(decoderName).release()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun setupOpusCodec(): Boolean {
        return try {
            val encoderName = findPreferredCodecName(opusMime, isEncoder = true)
                ?: return false
            val decoderName = findPreferredCodecName(opusMime, isEncoder = false)
                ?: return false
            val encoder = MediaCodec.createByCodecName(encoderName)
            val encoderFormat = MediaFormat.createAudioFormat(opusMime, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, opusBitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, pcmBytesPerFrame)
            }
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val decoder = MediaCodec.createByCodecName(decoderName)
            val decoderFormat = MediaFormat.createAudioFormat(opusMime, sampleRate, channelCount).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(buildOpusHead(sampleRate, channelCount)))
                // For audio/opus, csd-1/csd-2 must be codec delay/seek preroll in ns.
                val codecDelayNs =
                    opusPreSkipSamples * 1_000_000_000L / opusReferenceSampleRate
                val seekPreRollNs =
                    opusSeekPreRollSamples * 1_000_000_000L / opusReferenceSampleRate
                setByteBuffer("csd-1", ByteBuffer.wrap(buildNativeOrderLong(codecDelayNs)))
                setByteBuffer("csd-2", ByteBuffer.wrap(buildNativeOrderLong(seekPreRollNs)))
            }
            decoder.configure(decoderFormat, null, null, 0)
            decoder.start()

            opusEncoder = encoder
            opusDecoder = decoder
            Log.d(
                "AudioEngine",
                "Opus codec enabled (enc=$encoderName, dec=$decoderName)"
            )
            true
        } catch (e: Exception) {
            Log.e("AudioEngine", "Opus codec setup failed", e)
            _debugStats.update { stats ->
                stats.copy(
                    lastStartError = e.message ?: "opus setup failed"
                )
            }
            releaseOpusCodec()
            false
        }
    }

    private fun applyAudioModeAndRoute() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Some devices ignore direct transition to IN_COMMUNICATION unless mode is toggled.
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        var speakerRouted = false
        var communicationDeviceType: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val preferredType = if (preferSpeakerphone) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val available = audioManager.availableCommunicationDevices
            val preferredDevice = available.firstOrNull { it.type == preferredType }
            val targetDevice = preferredDevice ?: available.firstOrNull()
            if (targetDevice != null) {
                runCatching { audioManager.setCommunicationDevice(targetDevice) }
            } else {
                runCatching { audioManager.clearCommunicationDevice() }
            }
            communicationDeviceType = audioManager.communicationDevice?.type
            speakerRouted = communicationDeviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            if (preferSpeakerphone && !speakerRouted) {
                // Fallback for vendor stacks that do not honor setCommunicationDevice.
                runCatching { audioManager.isSpeakerphoneOn = true }
                speakerRouted = audioManager.isSpeakerphoneOn
            } else if (!preferSpeakerphone && speakerRouted) {
                // Some vendor stacks stay on speaker unless explicitly forced off.
                val earpiece = available.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (earpiece != null) {
                    runCatching { audioManager.setCommunicationDevice(earpiece) }
                } else {
                    runCatching { audioManager.clearCommunicationDevice() }
                }
                runCatching { audioManager.isSpeakerphoneOn = false }
                communicationDeviceType = audioManager.communicationDevice?.type
                speakerRouted = communicationDeviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || audioManager.isSpeakerphoneOn
            }
        } else {
            audioManager.isSpeakerphoneOn = preferSpeakerphone
            speakerRouted = audioManager.isSpeakerphoneOn
        }
        // Re-assert after route selection in case policy daemon changed the mode.
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        val modeOk = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
        if (!modeOk || (preferSpeakerphone && !speakerRouted) || (!preferSpeakerphone && speakerRouted)) {
            Log.w(
                "AudioEngine",
                "Speakerphone routing failed target=$preferSpeakerphone actual=$speakerRouted mode=${audioManager.mode} commType=$communicationDeviceType"
            )
        }
        Log.d(
            "AudioEngine",
            "Audio mode=${audioManager.mode}, speakerOn=${audioManager.isSpeakerphoneOn}, commType=$communicationDeviceType"
        )
    }

    private fun findPreferredCodecName(mime: String, isEncoder: Boolean): String? {
        return findSoftwareCodecName(mime, isEncoder) ?: findAnyCodecName(mime, isEncoder)
    }

    private fun findAnyCodecName(mime: String, isEncoder: Boolean): String? {
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        return codecInfos.firstOrNull { info ->
            info.isEncoder == isEncoder &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }?.name
    }

    private fun findSoftwareCodecName(mime: String, isEncoder: Boolean): String? {
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        return codecInfos.firstOrNull { info ->
            info.isEncoder == isEncoder &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                isSoftwareCodec(info)
        }?.name
    }

    private fun isSoftwareCodec(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isSoftwareOnly
        } else {
            val name = info.name.lowercase()
            name.startsWith("omx.google.") || name.startsWith("c2.android.")
        }
    }

    private fun releaseOpusCodec() {
        try {
            opusEncoder?.stop()
            opusEncoder?.release()
        } catch (_: Exception) {
        }
        try {
            opusDecoder?.stop()
            opusDecoder?.release()
        } catch (_: Exception) {
        }
        opusEncoder = null
        opusDecoder = null
    }

    private fun encodeAndSendOpus(
        pcm: ByteArray,
        size: Int,
        onAudioDataAvailable: (ByteArray) -> Unit
    ) {
        val encoder = opusEncoder ?: return
        val inputIndex = encoder.dequeueInputBuffer(0)
        if (inputIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(pcm, 0, size)
            val ptsUs = System.nanoTime() / 1000
            encoder.queueInputBuffer(inputIndex, 0, size, ptsUs, 0)
        }
        try {
            drainEncoder(onAudioDataAvailable)
        } catch (e: Exception) {
            _debugStats.update { stats ->
                stats.copy(
                    encodeFailCount = stats.encodeFailCount + 1,
                    lastEncodeAt = System.currentTimeMillis(),
                    lastEncodeError = e.message ?: "encode error"
                )
            }
        }
    }

    private fun drainEncoder(onAudioDataAvailable: (ByteArray) -> Unit) {
        val encoder = opusEncoder ?: return
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, 0)
            when {
                outIndex >= 0 -> {
                    if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        encoder.releaseOutputBuffer(outIndex, false)
                    } else {
                        val outBuffer = encoder.getOutputBuffer(outIndex)
                        if (outBuffer == null) {
                            encoder.releaseOutputBuffer(outIndex, false)
                        } else {
                            val size = encoderBufferInfo.size
                            if (size > 0) {
                                outBuffer.position(encoderBufferInfo.offset)
                                outBuffer.limit(encoderBufferInfo.offset + size)
                                val data = ByteArray(size)
                                outBuffer.get(data)
                                maybeLogEncode(size)
                                onAudioDataAvailable(data)
                                _debugStats.update { stats ->
                                    stats.copy(
                                        encodedFrames = stats.encodedFrames + 1,
                                        lastEncodeAt = System.currentTimeMillis(),
                                        lastEncodeSize = data.size,
                                        lastEncodeError = null
                                    )
                                }
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                        }
                    }
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> return
            }
        }
    }

    private fun decodeAndPlayOpus(data: ByteArray) {
        val decoder = opusDecoder ?: run {
            maybeLogDecodeSkip("decoder_null", data.size)
            return
        }
        val inputIndex = decoder.dequeueInputBuffer(5_000)
        if (inputIndex < 0) {
            maybeLogDecodeSkip("input_unavailable", data.size)
            return
        }
        val inputBuffer = decoder.getInputBuffer(inputIndex) ?: run {
            maybeLogDecodeSkip("input_buffer_null", data.size)
            return
        }
        inputBuffer.clear()
        inputBuffer.put(data)
        val ptsUs = System.nanoTime() / 1000
        decoder.queueInputBuffer(inputIndex, 0, data.size, ptsUs, 0)
        var produced = false
        while (true) {
            val outIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, 5_000)
            when {
                outIndex >= 0 -> {
                    val outBuffer = decoder.getOutputBuffer(outIndex)
                    if (outBuffer == null) {
                        decoder.releaseOutputBuffer(outIndex, false)
                    } else {
                        val size = decoderBufferInfo.size
                        if (size > 0) {
                            outBuffer.position(decoderBufferInfo.offset)
                            outBuffer.limit(decoderBufferInfo.offset + size)
                            val pcm = ByteArray(size)
                            outBuffer.get(pcm)
                            updatePlaybackMonitor(calcRmsPcm16(pcm, pcm.size))
                            val written = audioTrack?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) ?: 0
                            maybeLogDecode(size)
                            maybeLogPlay(size, written)
                            _debugStats.update { stats ->
                                stats.copy(
                                    decodedFrames = stats.decodedFrames + 1,
                                    lastDecodeAt = System.currentTimeMillis(),
                                    lastDecodeSize = pcm.size,
                                    lastDecodeError = null
                                )
                            }
                            produced = true
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                    }
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!produced) {
                        maybeLogDecodeSkip("no_output", data.size)
                    }
                    return
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> return
            }
        }
    }

    private fun buildOpusHead(sampleRate: Int, channels: Int): ByteArray {
        val head = ByteArray(19)
        val signature = byteArrayOf(
            'O'.code.toByte(),
            'p'.code.toByte(),
            'u'.code.toByte(),
            's'.code.toByte(),
            'H'.code.toByte(),
            'e'.code.toByte(),
            'a'.code.toByte(),
            'd'.code.toByte()
        )
        System.arraycopy(signature, 0, head, 0, signature.size)
        head[8] = 0x01 // version
        head[9] = channels.toByte()
        head[10] = (opusPreSkipSamples and 0xFF).toByte() // pre-skip (LSB)
        head[11] = ((opusPreSkipSamples shr 8) and 0xFF).toByte() // pre-skip (MSB)
        head[12] = (sampleRate and 0xFF).toByte()
        head[13] = ((sampleRate shr 8) and 0xFF).toByte()
        head[14] = ((sampleRate shr 16) and 0xFF).toByte()
        head[15] = ((sampleRate shr 24) and 0xFF).toByte()
        head[16] = 0x00 // output gain (LSB)
        head[17] = 0x00 // output gain (MSB)
        head[18] = 0x00 // channel mapping
        return head
    }

    private fun buildNativeOrderLong(value: Long): ByteArray {
        return ByteBuffer.allocate(8)
            .order(ByteOrder.nativeOrder())
            .putLong(value)
            .array()
    }

    private fun shouldLog(now: Long, last: Long): Boolean {
        return now - last >= debugLogIntervalMs
    }

    private fun maybeLogCapture(buffer: ByteArray, size: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastCaptureLogAt)) return
        lastCaptureLogAt = now
        val rms = calcRmsPcm16(buffer, size)
        Log.d("AudioPipe", "CAPTURE size=$size rms=%.2f useOpus=$useOpus".format(rms))
    }

    private fun maybeLogCapture(size: Int, rms: Double) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastCaptureLogAt)) return
        lastCaptureLogAt = now
        Log.d("AudioPipe", "CAPTURE size=$size rms=%.2f useOpus=$useOpus".format(rms))
    }

    private fun maybeLogGate(rms: Double, size: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastGateLogAt)) return
        lastGateLogAt = now
        Log.d("AudioPipe", "GATE drop size=$size rms=%.2f threshold=%.2f".format(rms, silenceGateRmsThreshold))
    }

    private fun maybeLogEchoSuppression(captureRms: Double, playbackRms: Double, gain: Double) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastEchoLogAt)) return
        lastEchoLogAt = now
        Log.d(
            "AudioPipe",
            "ECHO_SUPPRESS capture=%.1f playback=%.1f gain=%.2f speaker=%s".format(
                captureRms,
                playbackRms,
                gain,
                preferSpeakerphone
            )
        )
    }

    private fun maybeLogPcmSend(size: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastEncodeLogAt)) return
        lastEncodeLogAt = now
        Log.d("AudioPipe", "SEND_PCM size=$size")
    }

    private fun maybeLogEncode(size: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastEncodeLogAt)) return
        lastEncodeLogAt = now
        Log.d("AudioPipe", "ENCODE_OPUS size=$size")
    }

    private fun maybeLogNetIn(size: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastNetInLogAt)) return
        lastNetInLogAt = now
        Log.d("AudioPipe", "NET_IN size=$size useOpus=$useOpus")
    }

    private fun maybeLogDecode(size: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastDecodeLogAt)) return
        lastDecodeLogAt = now
        Log.d("AudioPipe", "DECODE_OPUS size=$size")
    }

    private fun maybeLogPlay(size: Int, written: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastPlayLogAt)) return
        lastPlayLogAt = now
        Log.d("AudioPipe", "PLAY_PCM size=$size written=$written")
    }

    private fun maybeLogDrop(reason: String, size: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastDropLogAt)) return
        lastDropLogAt = now
        val trackState = audioTrack?.state ?: -1
        val playState = audioTrack?.playState ?: -1
        Log.w(
            "AudioPipe",
            "DROP reason=$reason size=$size streaming=$isStreaming trackState=$trackState playState=$playState useOpus=$useOpus"
        )
    }

    private fun maybeLogDecodeSkip(reason: String, size: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastDecodeSkipLogAt)) return
        lastDecodeSkipLogAt = now
        Log.w("AudioPipe", "DECODE_SKIP reason=$reason size=$size useOpus=$useOpus")
    }

    private fun calcRmsPcm16(buffer: ByteArray, size: Int): Double {
        if (size < 2) return 0.0
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < size) {
            val lo = buffer[i].toInt() and 0xFF
            val hi = buffer[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val s = sample.toShort().toInt()
            sum += (s * s).toDouble()
            samples++
            i += 2
        }
        if (samples == 0) return 0.0
        return sqrt(sum / samples)
    }

    private fun updatePlaybackMonitor(rms: Double) {
        if (rms <= 0.0) return
        lastPlaybackRms = if (lastPlaybackRms <= 0.0) {
            rms
        } else {
            (lastPlaybackRms * 0.65) + (rms * 0.35)
        }
        lastPlaybackAt = System.currentTimeMillis()
    }

    private fun resolveTransmitGain(captureRms: Double): Double {
        var gain = if (preferSpeakerphone) transmitGain else (transmitGain * 0.8)
        if (!echoMitigationEnabled) return gain
        val now = System.currentTimeMillis()
        if (now - lastPlaybackAt > echoMitigationWindowMs) return gain
        val playbackRms = lastPlaybackRms
        val threshold = if (preferSpeakerphone) {
            echoMitigationPlaybackRmsThresholdSpeaker
        } else {
            echoMitigationPlaybackRmsThresholdEarpiece
        }
        if (playbackRms < threshold) return gain
        val ratio = if (playbackRms <= 1.0) Double.MAX_VALUE else captureRms / playbackRms
        val maxRatio = if (preferSpeakerphone) {
            echoMitigationMaxRatioSpeaker
        } else {
            echoMitigationMaxRatioEarpiece
        }
        if (ratio <= maxRatio) {
            gain *= when {
                ratio < 0.70 -> 0.0
                ratio < 0.95 -> if (preferSpeakerphone) 0.22 else 0.16
                ratio < 1.10 -> if (preferSpeakerphone) 0.38 else 0.26
                ratio < 1.30 -> if (preferSpeakerphone) 0.52 else 0.38
                else -> if (preferSpeakerphone) 0.66 else 0.48
            }
            maybeLogEchoSuppression(captureRms, playbackRms, gain)
        }
        return gain
    }

    private fun applyGainPcm16(buffer: ByteArray, size: Int, gain: Double) {
        if (size < 2) return
        var i = 0
        while (i + 1 < size) {
            val lo = buffer[i].toInt() and 0xFF
            val hi = buffer[i + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            var scaled = (sample * gain).toInt()
            if (scaled > Short.MAX_VALUE.toInt()) scaled = Short.MAX_VALUE.toInt()
            if (scaled < Short.MIN_VALUE.toInt()) scaled = Short.MIN_VALUE.toInt()
            buffer[i] = (scaled and 0xFF).toByte()
            buffer[i + 1] = ((scaled shr 8) and 0xFF).toByte()
            i += 2
        }
    }
}
