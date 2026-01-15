
🎙️ 온디바이스 음성 인식 (On-Device AI Voice Recognition)

📌 기술 개요
본 프로젝트는 별도의 커스텀 모델 학습 없이, 안드로이드 내장에 탑재된 고성능 Pre-trained STT(Speech-to-Text) 모델을 활용합니다.

"소리를 듣고 글자로 바꿔줘"

단순한 명령만으로 수백만 명의 목소리 데이터로 학습된 삼성/구글의 AI 엔진을 호출하여, "살려주세요", "도와주세요" 등의 구조 요청 키워드를 즉각적으로 감지합니다.

🚀 핵심 기술: 오프라인 모드 (Offline First)
재난 상황에서는 기지국 파괴로 인한 **통신 두절(Network Blackout)**이 빈번하게 발생합니다. 본 앱은 클라우드 의존도를 0%로 낮춰 생존성을 극대화했습니다.

1. EXTRA_PREFER_OFFLINE 적용
안드로이드 SpeechRecognizer 인텐트에 오프라인 우선 플래그를 적용하여, 외부 서버와의 통신 없이 폰 내부의 NPU/CPU 만으로 추론(Inference)을 수행합니다.

Java

intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

2. 기대 효과 (Efficiency & Survival)
이 아키텍처는 **"학습 비용 0, 서버 비용 0, 오프라인 생존성 100%"**를 달성하는 가장 효율적인 방식입니다.

Zero Latency: 네트워크 대기 시간 없이 즉각 반응.

Zero Cost: 별도의 음성 인식 API 서버 비용 발생 안 함.

High Availability: 인터넷이 끊긴 고립된 상황에서도 100% 동작.

⚠️ 필수 요구 사항 (Prerequisite)
'한국어 오프라인 언어 팩' 설치가 필수적입니다. 대부분의 최신 갤럭시 스마트폰에는 기본 탑재되어 있으나, 확실한 작동을 위해 사용자 설정 확인이 필요합니다.

설정 경로: 설정 > 일반 > 텍스트 음성 변환 > 기본 엔진 설정 > 음성 데이터 설치

체크리스트: 앱 최초 실행 시, 해당 언어 팩 설치 여부를 확인하고 미설치 시 다운로드 페이지로 안내하는 로직이 포함되어야 합니다.