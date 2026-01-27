# Lifesaiver

오프라인 환경에서 구조 신호(SOS), 음성(Push-to-Talk), 텍스트 채팅을 제공하는 Android 앱이다.  
BLE 기반 연결을 사용하며, 사용자/구조자 흐름을 분리한 UI를 제공한다.

## 개발 중인 기능(초기 화면 구성)
- 모드 선택(사용자/구조자)
- 대기 상태 안내 + 센서 상태 점검
- SOS 비콘 화면
- PTT 음성 연결 UI
- 구조 채팅(텍스트)
- 권한 안내 화면

## 아키텍처
- MVVM 기반 (ViewModel/UiState 중심)
- 패키지 분리 기준은 “도메인 축” 단위로만 확장

현재 패키지 구조(요약):
```
com.example.lifesaiver
├─ ui/                # Compose 화면
├─ presentation/      # ViewModel, UiState, 화면 로직
│  ├─ screen/
│  └─ sensor/
├─ protocol/          # 패킷 모델, 코어, 코덱 스켈레톤
└─ core/              # BLE, 오디오 등 핵심 기능
```
