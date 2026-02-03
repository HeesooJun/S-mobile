package com.example.lifesaiver.ai.stt

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.lang.Math.sqrt

/**
 * 🚨 EmergencyKeywordDetector
 * 안전한 스레드 동기화가 적용된 버전입니다.
 */
class EmergencyIntentClassifierKorean(context: Context) {

    // [핵심] 동기화를 위한 락 객체
    private val lock = Any()

    // [핵심] 종료 상태 플래그
    @Volatile private var isClosed = false

    private var interpreter: Interpreter? = null
    private var tokenizer: WordPieceTokenizer? = null
    @Volatile private var isReady = false

    private val emergencyAnchors = listOf(
        "살려주세요", "살려줘", "사람 살려",
        "구해주세요", "구해줘", "구조 요청",
        "도와주세요", "도와줘", "아파요",
    )

    private val normalAnchors = listOf(
        "안녕하세요", "반가워요", "식사 하셨나요",
        "날씨 좋다", "배고파", "심심해",
        "이거 뭐야", "전화기", "노래 틀어줘",
        "그러니까", "초야", "춰야", "갑자기",
        "세상", "센서", "토요일","퇴원 중",
        "병원",
        "이거 뭐야", "왜 이래", "누구세요", "뭐지",
        "잘못 켰어", "오작동이야", "고장 났나",
        "무슨 일이야", "어떻게 된 거야", "잠깐만",
        "그러니까", "그래서", "하지만", "그런데", "솔직히",
        "진짜", "정말", "완전", "대박", "헐",
        "갑자기", "그냥", "아니", "음", "어"
    )

    private val anchorVectors = mutableListOf<FloatArray>()
    private val anchorLabels = mutableListOf<Int>()

    init {
        Thread {
            try {
                setupModel(context)
                // 설정이 끝난 후, 닫혀있지 않다면 준비 완료 처리
                synchronized(lock) {
                    if (!isClosed) {
                        isReady = true
                        Log.d("KeywordDetector", "🚀 키워드 감지 모델 준비 완료")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun setupModel(context: Context) {
        val modelBuffer = loadModelFile(context, "bert_kor.tflite")
        val vocab = loadVocab(context, "vocab.txt")

        // [안전 장치] 인터프리터 생성 및 할당 시점 보호
        synchronized(lock) {
            if (isClosed) return // 이미 닫혔다면 중단
            interpreter = Interpreter(modelBuffer)
            tokenizer = WordPieceTokenizer(vocab)
        }

        // 1. 비상 앵커 등록 (getEmbedding 내부에서 동기화 체크함)
        emergencyAnchors.forEach { text ->
            if (isClosed) return // 루프 도중에도 닫히면 즉시 중단
            getEmbedding(text)?.let {
                anchorVectors.add(it)
                anchorLabels.add(1)
            }
        }

        // 2. 일상 앵커 등록
        normalAnchors.forEach { text ->
            if (isClosed) return
            getEmbedding(text)?.let {
                anchorVectors.add(it)
                anchorLabels.add(0)
            }
        }
    }

    fun checkIntent(inputText: String, callback: (Boolean, Double, String) -> Unit) {
        if (!isReady || inputText.length < 2) {
            callback(false, 0.0, "")
            return
        }
        Thread {
            // 스레드 시작 직후 닫혔을 수도 있으므로 내부에서 체크
            val (isDetected, score, match) = detectKeywordSemantics(inputText)

            // 콜백 시점에 서비스가 살아있는지는 Service 쪽에서 처리하겠지만,
            // 여기서도 닫힌 상태면 콜백을 안 부르는 게 안전할 수 있음
            if (!isClosed) {
                callback(isDetected, score, match)
            }
        }.start()
    }

    private fun detectKeywordSemantics(text: String): Triple<Boolean, Double, String> {
        try {
            // 여기서 getEmbedding이 null을 반환하면(닫힘) 바로 종료
            val inputVector = getEmbedding(text) ?: return Triple(false, 0.0, "")

            var maxScore = 0.0
            var bestMatch = ""
            var isEmergency = false

            for (i in anchorVectors.indices) {
                val score = calculateCosineSimilarity(anchorVectors[i], inputVector)
                if (score > maxScore) {
                    maxScore = score
                    bestMatch = if (anchorLabels[i] == 1) emergencyAnchors[i % emergencyAnchors.size]
                    else normalAnchors[i % normalAnchors.size]
                    isEmergency = (anchorLabels[i] == 1)
                }
            }

            val displayScore = if (maxScore > 0.9) (maxScore - 0.9) * 1000.0 else 0.0
            Log.d("KeywordCheck", "입력: '$text' -> 가장 유사: '$bestMatch' (${String.format("%.1f", displayScore)}점)")

            if (isEmergency && maxScore >= 0.92) {
                return Triple(true, displayScore, bestMatch)
            } else {
                return Triple(false, 0.0, bestMatch)
            }

        } catch (e: Exception) {
            return Triple(false, 0.0, "")
        }
    }

    // [핵심 수정] 임베딩 추출 시 자물쇠(lock) 사용
    private fun getEmbedding(text: String): FloatArray? {
        synchronized(lock) {
            // 1. 닫혔거나 초기화 전이면 즉시 리턴
            if (isClosed || tokenizer == null || interpreter == null) return null

            try {
                val inputIdsInt = tokenizer!!.tokenize(text)
                val inputIdsLong = Array(1) { LongArray(128) }
                for (i in inputIdsInt.indices) inputIdsLong[0][i] = inputIdsInt[i].toLong()
                val output = Array(1) { FloatArray(512) }

                // 2. 안전하게 추론 실행
                interpreter?.run(inputIdsLong, output)
                return output[0]
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }

    private fun calculateCosineSimilarity(v1: FloatArray, v2: FloatArray): Double {
        var dotProduct = 0.0; var normA = 0.0; var normB = 0.0
        if (v1.size != v2.size) return 0.0
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun loadVocab(context: Context, fileName: String): Map<String, Int> {
        val vocab = mutableMapOf<String, Int>()
        context.assets.open(fileName).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line -> vocab[line] = index }
        }
        return vocab
    }

    // [핵심 수정] 종료 시 자물쇠를 걸고 안전하게 닫음
    fun close() {
        synchronized(lock) {
            isClosed = true // 깃발 내림
            isReady = false

            try {
                interpreter?.close()
                interpreter = null
                Log.d("EmergencyClassifier", "🛑 모델 리소스 안전 해제 완료")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
