package com.example.lifesaiver.ai.stt

class WordPieceTokenizer(private val vocab: Map<String, Int>) {
    fun tokenize(text: String, maxLen: Int = 128): IntArray {
        val tokens = mutableListOf<Int>()
        tokens.add(vocab["[CLS]"] ?: 2) // 시작 토큰

        // 간단한 전처리 (특수문자 제거 등은 필요시 추가)
        val cleanText = text.trim()

        cleanText.split(" ").forEach { word ->
            var subword = word
            while (subword.isNotEmpty()) {
                var found = false
                // 가장 긴 매칭되는 단어 찾기 (Max Match)
                for (i in subword.length downTo 1) {
                    val candidate = if (found) "##" + subword.substring(0, i) else subword.substring(0, i)
                    if (vocab.containsKey(candidate)) {
                        tokens.add(vocab[candidate]!!)
                        subword = subword.substring(i)
                        found = true
                        break
                    }
                }
                if (!found) {
                    tokens.add(vocab["[UNK]"] ?: 1) // 모르는 단어 처리
                    break // 해당 어절 건너뜀
                }
            }
        }

        // Padding 채우기 (나머지는 0)
        val result = IntArray(maxLen) { 0 }
        for (i in tokens.indices) {
            if (i < maxLen) result[i] = tokens[i]
        }
        return result
    }
}
