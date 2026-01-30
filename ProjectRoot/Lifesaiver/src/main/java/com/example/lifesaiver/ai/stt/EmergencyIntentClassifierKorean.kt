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
 * "살려주세요", "구해주세요" 등의 핵심 의미가 담겨 있으면
 * 앞에 "제발", "빨리" 같은 수식어가 붙어도 찰떡같이 잡아냅니다.
 */
class EmergencyIntentClassifierKorean(context: Context) {

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var tokenizer: WordPieceTokenizer? = null
    @Volatile private var isReady = false

    // ✅ 핵심 앵커 (이것들과 뉘앙스가 비슷하면 잡힘)
    // 앞에 '제발', '빨리'가 붙어도 BERT가 알아서 이쪽으로 분류합니다.
    private val emergencyAnchors = listOf(
        "살려주세요", "살려줘", "사람 살려",
        "구해주세요", "구해줘", "구조 요청",
        "도와주세요", "도와줘", "아파요",
    )

    // ✅ 비교군 (오작동 방지용)
    // 살려달라는 말이 아닌 평범한 말들을 넣어둡니다.
    private val normalAnchors = listOf(
        "안녕하세요", "반가워요", "식사 하셨나요",
        "날씨 좋다", "배고파", "심심해",
        "이거 뭐야", "전화기", "노래 틀어줘",
        "그러니까", "초야", "춰야", "갑자기",
        "세상", "센서", "토요일","퇴원 중",
        "병원",

        // 2. [의문 및 당황] (앱이 갑자기 켜졌을 때 반응)
        "이거 뭐야", "왜 이래", "누구세요", "뭐지",
        "잘못 켰어", "오작동이야", "고장 났나",
        "무슨 일이야", "어떻게 된 거야", "잠깐만",

        // 3. [접속사 및 추임새] (문장 중간에 들어가는 말들)
        "그러니까", "그래서", "하지만", "그런데", "솔직히",
        "진짜", "정말", "완전", "대박", "헐",
        "갑자기", "그냥", "아니", "음", "어"
    )

    private val anchorVectors = mutableListOf<FloatArray>()
    private val anchorLabels = mutableListOf<Int>() // 1: 비상, 0: 일상

    init {
        Thread {
            try {
                setupModel(context)
                isReady = true
                Log.d("KeywordDetector", "🚀 키워드 감지 모델 준비 완료")
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun setupModel(context: Context) {
        val modelBuffer = loadModelFile(context, "bert_kor.tflite")
        interpreter = Interpreter(modelBuffer)
        val vocab = loadVocab(context, "vocab.txt")
        tokenizer = WordPieceTokenizer(vocab)

        // 1. 비상 앵커 등록
        emergencyAnchors.forEach { text ->
            getEmbedding(text)?.let {
                anchorVectors.add(it)
                anchorLabels.add(1) // Label 1: 비상
            }
        }

        // 2. 일상 앵커 등록
        normalAnchors.forEach { text ->
            getEmbedding(text)?.let {
                anchorVectors.add(it)
                anchorLabels.add(0) // Label 0: 일상
            }
        }
    }

    fun checkIntent(inputText: String, callback: (Boolean, Double, String) -> Unit) {
        if (!isReady || inputText.length < 2) {
            callback(false, 0.0, "")
            return
        }
        Thread {
            val (isDetected, score, match) = detectKeywordSemantics(inputText)
            callback(isDetected, score, match)
        }.start()
    }

    /**
     * 🕵️‍♀️ [의미 기반 키워드 탐지]
     * 단순 글자 매칭이 아니라, 벡터 유사도를 봅니다.
     */
    private fun detectKeywordSemantics(text: String): Triple<Boolean, Double, String> {
        try {
            val inputVector = getEmbedding(text) ?: return Triple(false, 0.0, "")

            var maxScore = 0.0
            var bestMatch = ""
            var isEmergency = false

            // 모든 앵커(비상 + 일상) 중에서 가장 비슷한 녀석 하나를 뽑습니다.
            for (i in anchorVectors.indices) {
                val score = calculateCosineSimilarity(anchorVectors[i], inputVector)

                if (score > maxScore) {
                    maxScore = score
                    bestMatch = if (anchorLabels[i] == 1) emergencyAnchors[i % emergencyAnchors.size]
                    else normalAnchors[i % normalAnchors.size]

                    // 1등이 비상 앵커면 True, 아니면 False
                    isEmergency = (anchorLabels[i] == 1)
                }
            }

            // 점수 변환 (0.90 ~ 1.0 구간을 0~100점으로 보기 좋게)
            val displayScore = if (maxScore > 0.9) (maxScore - 0.9) * 1000.0 else 0.0

            Log.d("KeywordCheck", "입력: '$text' -> 가장 유사: '$bestMatch' (${String.format("%.1f", displayScore)}점)")

            // 🎯 판정 로직
            // 1. 가장 비슷한 앵커가 '비상' 그룹이어야 함.
            // 2. 유사도가 최소 0.92 (92%) 이상이어야 함. (엉뚱한 단어 방지)

            if (isEmergency && maxScore >= 0.92) {
                return Triple(true, displayScore, bestMatch)
            } else {
                return Triple(false, 0.0, bestMatch)
            }

        } catch (e: Exception) {
            return Triple(false, 0.0, "")
        }
    }

    // --- (이하 유틸리티 함수: 기존과 동일) ---
    private fun getEmbedding(text: String): FloatArray? {
        if (tokenizer == null || interpreter == null) return null
        val inputIdsInt = tokenizer!!.tokenize(text)
        val inputIdsLong = Array(1) { LongArray(128) }
        for (i in inputIdsInt.indices) inputIdsLong[0][i] = inputIdsInt[i].toLong()
        val output = Array(1) { FloatArray(512) }
        interpreter?.run(inputIdsLong, output)
        return output[0]
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

    fun close() { interpreter?.close() }
}
