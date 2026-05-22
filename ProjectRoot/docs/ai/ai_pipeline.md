# AI 기술

## 1) 목적
재난/고립 상황에서 사용자가 버튼을 누르기 어려운 경우에도 음성 기반으로 긴급 호출을 자동 실행합니다.

## 2) 구성

### STT (Speech-to-Text)
- Android `SpeechRecognizer`로 음성을 텍스트로 변환
- 고립 상황에서 백그라운드 감시 흐름과 연동
- 인식기 오류가 발생하면 재시작해 감시를 이어가도록 구성
- 구현: [VoiceTriggerDetector](../../Lifesaivior/src/main/java/com/example/lifesaivior/ai/stt/VoiceTriggerDetector.kt)

### 의도 분류 (NLU)
- `bert_kor.tflite` + `vocab.txt` 기반 온디바이스 분류
- STT 텍스트를 비상/일상 의도로 구분
- 네트워크 없이도 단말 내부에서 분류 수행
- 구현: [EmergencyIntentClassifierKorean](../../Lifesaivior/src/main/java/com/example/lifesaivior/ai/stt/EmergencyIntentClassifierKorean.kt), [WordPieceTokenizer](../../Lifesaivior/src/main/java/com/example/lifesaivior/ai/stt/WordPieceTokenizer.kt)

## 3) 입력/출력
- 입력: 사용자 음성 발화
- 중간 결과: STT 텍스트
- 출력: `비상` 또는 `일상` 분류 결과
- 후속 동작: 비상으로 분류되면 긴급 모드/구조 신호 실행

## 4) 동작 기준
- 음성 입력만으로 즉시 구조 신호를 보내지 않고, 의도 분류 단계를 거친 뒤 실행
- 같은 발화라도 문맥에 따라 비상/일상으로 달라질 수 있어, 분류 결과를 기준으로 자동 호출 여부를 결정
