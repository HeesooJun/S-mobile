package com.example.lifesaivior.ai.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceTriggerDetector(
    private val context: Context,
    private val onStateChange: (String) -> Unit,
    private val onDetected: (String) -> Unit,
    private val onErrorOccurred: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionIntent: Intent? = null
    private var isListening = false

    init {
        setupRecognizer()
    }

    private fun setupRecognizer() {
        // 기존에 찌꺼기가 있다면 확실히 제거
        if (speechRecognizer != null) {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }

        // 새 인식기 생성
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onStateChange("👂 듣는 중...")
            }

            override fun onBeginningOfSpeech() {
                onStateChange("📝 말소리 감지됨...")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                onStateChange("⏳ 분석 중...")
            }

            override fun onError(error: Int) {
                isListening = false
                val message = getErrorText(error)
                Log.e("VoiceDetector", "에러 발생: $message (코드: $error)")

                // ★ [핵심] 5번 에러(Client Error)가 나면 인식기를 재부팅함
                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    onStateChange("⚠️ 인식기 재설정 중...")
                    // 잠깐 쉬었다가 에러 전파 (MainActivity가 재시작하도록)
                    onErrorOccurred("시스템 재정비 중 ($message)")
                    setupRecognizer() // 인식기 강제 리셋
                } else {
                    onErrorOccurred(message)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onDetected(matches[0]) // 가장 정확한 결과 전달
                } else {
                    onErrorOccurred("인식된 내용 없음")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // 음성 인식 설정 (한국어)
        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    fun startListening() {
        if (isListening) return // 이미 듣고 있으면 패스

        // 인식기가 죽어있으면 살려냄
        if (speechRecognizer == null) setupRecognizer()

        try {
            // 메인 스레드에서 실행 보장
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                speechRecognizer?.startListening(recognitionIntent)
                isListening = true
                onStateChange("🎙️ 마이크 켜짐")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isListening = false
            onErrorOccurred("시작 실패: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            isListening = false
            speechRecognizer?.stopListening()
            onStateChange("⚪ 대기 중")
        } catch (e: Exception) {
            // 무시
        }
    }

    // 에러 코드 번역기
    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "오디오 에러"
            SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러(5) - 재시작 필요"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한 없음"
            SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃"
            SpeechRecognizer.ERROR_NO_MATCH -> "목소리를 못 찾음"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "과부하 걸림(8)"
            SpeechRecognizer.ERROR_SERVER -> "서버 에러"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말하는 시간 초과"
            else -> "알 수 없는 오류($errorCode)"
        }
    }
}
