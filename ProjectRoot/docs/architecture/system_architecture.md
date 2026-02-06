# 아키텍처

## 1) 런타임 처리 흐름

1. 피구조자 앱에서 SOS/음성/센서/타이머 트리거가 발생하면 패킷 생성 요청이 시작됩니다.
2. AppViewModel/서비스에서 패킷을 생성하고 `ProtocolCore`로 전달합니다. `ProtocolCore`는 파이프라인에서 TTL, 중복 처리, 조각화를 적용합니다.
3. BLE 메쉬가 패킷을 주변 노드로 전달하고, 필요 시 멀티홉으로 릴레이합니다.
4. 구조자 앱은 수신 패킷을 기반으로 피구조자 상태를 갱신하고 대응 대상을 선택합니다.
5. 구조자 앱은 `DEVICE_CONTROL`, `CALL_HANDSHAKE`, `MESSAGE`를 송신해 원격 대응과 통신을 이어갑니다.

## 2) 통신 경로 정책

| 구분 | 기본 경로 | 보조/대체 경로 | 용도 |
|---|---|---|---|
| 일반 데이터 | BLE 메쉬 | 없음 | 신호/텍스트/프로필/제어 명령 전달 |
| 통화 경로 | Wi-Fi Aware 우선 | Wi-Fi Direct fallback(현재 코드 기본값은 비활성화) | 실시간 음성 통신 |
| 거리 추정 | UWB 우선(가용 시) | RTT/RSSI 보조 | 탐색 동선 판단 |

## 3) 앱 아키텍처(MVVM)와 폴더 구조

### MVVM 책임 분리
- `UI`: 화면 렌더링, 사용자 입력 처리
- `ViewModel`: 상태(StateFlow), 이벤트, 유스케이스 조합
- `Domain/Engine`: 통신, 동기화, 보안, 거리 추정 등 공통 로직

### 폴더 구조(핵심)

```text
ProjectRoot/
├── Lifesaivior/src/main/java/com/example/lifesaivior/
│   ├── ai/
│   ├── ui/
│   ├── presentation/
│   ├── wakeup/
│   └── core/
├── Rescuer/src/main/java/com/example/lifesaivior/
│   ├── ai/
│   ├── ui/
│   ├── presentation/
│   ├── wakeup/
│   └── core/
└── shared/src/main/java/com/example/lifesaivior/
    ├── protocol/
    ├── presentation/
    └── core/
```

## 4) 주요 컴포넌트

| 영역 | 컴포넌트 | 역할 | 파일 |
|---|---|---|---|
| 앱 오케스트레이션 | AppViewModel | 화면 상태, 패킷 송수신, 호출/제어 이벤트 제어 | [Rescuer AppViewModel](../../Rescuer/src/main/java/com/example/lifesaivior/presentation/AppViewModel.kt) |
| 앱 오케스트레이션 | AppViewModel | 피구조자 UI 상태, 호출 흐름, 패킷 처리 제어 | [Lifesaivior AppViewModel](../../Lifesaivior/src/main/java/com/example/lifesaivior/presentation/AppViewModel.kt) |
| 프로토콜 코어 | ProtocolCore | 패킷 생성/전파/수신 진입점 | [ProtocolCore](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolCore.kt) |
| 파이프라인 | PacketPipeline | 중복 제거, 조각화/재조립 처리 | [PacketPipeline](../../shared/src/main/java/com/example/lifesaivior/protocol/pipeline/PacketPipeline.kt) |
| 동기화 | GossipSyncManager | RequestSync + GCS 기반 누락 복구 | [GossipSyncManager](../../shared/src/main/java/com/example/lifesaivior/protocol/sync/GossipSyncManager.kt) |
| 보안 | SignatureManager | Ed25519 서명/검증 및 키 로드 | [SignatureManager](../../shared/src/main/java/com/example/lifesaivior/protocol/security/SignatureManager.kt) |
| BLE 전송 | BleManager | 광고/스캔/연결 및 메쉬 중계 | [BleManager](../../shared/src/main/java/com/example/lifesaivior/core/ble/BleManager.kt) |
| 거리 추정 | HybridDistanceManager | UWB 우선 + RTT/RSSI 보조 거리 계산 | [HybridDistanceManager](../../shared/src/main/java/com/example/lifesaivior/core/location/HybridDistanceManager.kt) |
| UWB | UwbRanger | UWB 세션 생성 및 거리 측정 처리 | [UwbRanger](../../shared/src/main/java/com/example/lifesaivior/core/uwb/UwbRanger.kt) |
| 통화 경로 | WifiAwareRanger / WifiDirectRanger | 실시간 통화 데이터 경로 선택/유지 | [WifiAwareRanger](../../shared/src/main/java/com/example/lifesaivior/core/wifi/WifiAwareRanger.kt) / [WifiDirectRanger](../../shared/src/main/java/com/example/lifesaivior/core/wifi/WifiDirectRanger.kt) |
| 음성 인식 | VoiceTriggerDetector | STT 입력 수집 및 자동 호출 연계 | [VoiceTriggerDetector](../../Lifesaivior/src/main/java/com/example/lifesaivior/ai/stt/VoiceTriggerDetector.kt) |
| 의도 분류 | EmergencyIntentClassifierKorean | `bert_kor.tflite` 기반 비상/일상 분류 | [EmergencyIntentClassifierKorean](../../Lifesaivior/src/main/java/com/example/lifesaivior/ai/stt/EmergencyIntentClassifierKorean.kt) |

## 5) 의존성

| 분류 | 사용 기술 | 목적 |
|---|---|---|
| UI | Jetpack Compose, Material3, Navigation Compose | 화면 구성, 상태 기반 UI, 라우팅 |
| 아키텍처/비동기 | Lifecycle ViewModel, Kotlin Coroutines | 상태 관리, 비동기 처리 |
| 저장소 | Room(SQLite), DataStore | 메시지/프로필 저장, 설정 관리 |
| 통신/거리 | BLE, AndroidX Core UWB, Wi-Fi Aware, Wi-Fi Direct | 오프라인 전파, 거리 추정, 통화 경로 |
| AI | Android SpeechRecognizer, TensorFlow Lite(`bert_kor.tflite`) | 음성 인식, 의도 분류 |
| 보안 | BouncyCastle(Ed25519), AndroidX Security Crypto | 서명 검증, 로컬 키 보호 |

## 6) 상태/저장소

| 데이터 | 저장/관리 위치 | 목적 |
|---|---|---|
| 메시지/프로필 DB | Room(SQLite) | 로컬 조회 및 동기화 기준 |
| 사용자 설정 | DataStore | 앱 설정 유지 |
| 서명 키 | EncryptedSharedPreferences + Keystore | 패킷 무결성 검증 |

## 7) 장애 대응 설계
- 메쉬 전파 제어: TTL + Dedup
- 큰 패킷 대응: Fragmentation/Reassembly
- 전달 실패 보완: ACK 재시도 + Store-and-Forward
- 누락 복구: RequestSync + GCS 필터 기반 선택 재전송
