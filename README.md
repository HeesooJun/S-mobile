# Lifesaivior Android (Offline Rescue Support)

<strong><em>가장 위급한 순간, 가장 가까운 곳에.</em></strong>

Lifesaivior는 재난 상황에서 인터넷과 기지국이 끊겨도 구조 신호를 전달할 수 있는 오프라인 구조 지원 앱입니다.
피구조자가 보낸 구조 신호는 BLE로 연결된 주변 단말을 거쳐 다음 단말로 전달되며, 통신 범위를 점차 넓혀갑니다.

구조자는 앱에서 피구조자 목록을 확인하고, 필요한 대상을 선택해 상태 정보를 본 뒤 어디를 먼저 찾아갈지 우선순위를 정할 수 있습니다.
필요하면 피구조자 단말에 화면, 소리, 진동 반응을 원격으로 유도하고, 채팅, PTT, 통화로 안내를 계속 이어갈 수 있습니다.

## 서비스 특징
- 다중 트리거 자동 호출: 터치, 음성, 센서, 무응답 타이머를 결합해 긴급 모드 자동 실행
- 오프라인 전달: BLE 멀티홉 메쉬를 통해 구조 신호/메시지를 주변 노드로 릴레이
- 탐색 지원: 구조자 앱에서 UWB + BLE RSSI 기반 거리 추정 제공
- 현장 대응: 구조자 앱에서 피구조자 단말 화면/소리/진동/고주파/경보중지 원격 제어
- 통신 지원: PTT/통화/채팅을 통한 양방향 의사소통
- 신뢰성/보안: TTL, Dedup, 조각화/재조립, ACK 재시도, RequestSync+GCS, Ed25519 무결성 검증

## 아키텍처
- 앱 아키텍처: MVVM 기반(`UI -> ViewModel -> Domain/Engine`)으로 화면 상태와 통신 상태를 분리 관리
- 시스템 아키텍처: `:shared`의 `ProtocolCore`를 중심으로 패킷 생성/중계/검증/동기화 처리
- 통신 아키텍처: 일반 데이터는 BLE 메쉬, 통화는 Wi-Fi Aware 우선
- 거리 추정 아키텍처: UWB 우선, 가용하지 않으면 RTT/RSSI 보조 소스 사용
- 저장 아키텍처: Room(SQLite), DataStore, EncryptedSharedPreferences/Keystore 병행

### 폴더 구조(요약)
```text
ProjectRoot/
├── Lifesaivior/
│   └── src/main/java/com/example/lifesaivior/
│       ├── ai/            # 음성 인식/의도 분류
│       ├── ui/            # 화면(Compose), 네비게이션, 컴포넌트
│       ├── presentation/  # ViewModel, 상태, 화면 로직
│       ├── wakeup/        # 음성/센서/고립 감지 기반 자동 호출
│       └── core/          # 앱 내부 서비스/DB/프로필
├── Rescuer/
│   └── src/main/java/com/example/lifesaivior/
│       ├── ai/            # 음성 인식/의도 분류
│       ├── ui/            # 구조자 화면, 탐색/대응 UI
│       ├── presentation/  # 구조자 ViewModel, 상태/이벤트 관리
│       ├── wakeup/        # 음성/센서/고립 감지 기반 자동 호출
│       └── core/          # 구조자 서비스/DB/로컬 처리
└── shared/
    └── src/main/java/com/example/lifesaivior/
        ├── protocol/      # 패킷, 코덱, 파이프라인, 동기화, 보안
        ├── presentation/  # 공통 화면/상태 관리
        └── core/          # BLE/UWB/Wi-Fi/오디오/거리 추정 공통 계층
```

아키텍처 흐름도와 상세 설명은 [`docs/architecture/system_architecture.md`](ProjectRoot/docs/architecture/system_architecture.md)에 정리했습니다.

## 모듈 구성
- `:lifesaivior`: 피구조자 앱(긴급 모드, 자동 호출, 구조 통신 UI)
- `:rescuer`: 구조자 앱(탐색, 대응, 원격 제어 UI)
- `:shared`: 공통 엔진(프로토콜, 전송 파이프라인, 동기화, 보안)

## 주요 기능
1. 통신 음영 지역 감지 후 자동 호출 흐름 전환
2. 대기 화면 30초 무응답 시 자동 구조 신호 발신
3. 피구조자-구조자 UI 분리 및 역할별 흐름 제공
4. RequestSync + GCS 필터 기반 누락 데이터 선택 동기화
5. Ed25519 서명 검증 기반 핵심 패킷 무결성 보호

## 기능 상세 (역할별)
### 피구조자
- 긴급 트리거: 터치, 음성, 센서, 타이머
- 구조 신호 송출 후 채팅/PTT/통화 연계
- 프로필 정보 생성/공유

### 구조자
- 생존자 상태 목록 및 메쉬 상황 파악
- 거리 추정 기반 탐색 동선 판단(UWB + BLE RSSI)
- 원격 제어 신호 송출로 단말 반응 유도

### 시스템
- 메쉬 릴레이, TTL, Dedup, Fragmentation/Reassembly
- RequestSync + GCS 필터 기반 누락 동기화
- Ed25519 기반 핵심 패킷 서명 검증

## 유저 플로우
- 피구조자 앱: `:lifesaivior`
- 구조자 앱: `:rescuer`

1. 피구조자는 SOS 버튼을 누르거나 음성/센서/무응답 타이머 조건으로 긴급 모드를 실행합니다.
2. 피구조자 단말은 구조 신호를 주변 단말로 전송하고, 주변 단말은 신호를 메쉬 방식으로 중계합니다.
3. 구조자는 앱에서 생존자 목록과 거리 정보를 확인하고, 우선순위를 정해 탐색 동선을 결정합니다.
4. 구조자는 필요 시 원격 제어(화면/소리/진동)로 피구조자 반응을 유도하고, 채팅/PTT/통화로 안내합니다.
5. 구조 완료 후 상태를 갱신하고 다음 대상 탐색 또는 상황 종료 절차를 진행합니다.

## 프로토콜

### 패킷 구조
- 기본 구조: `PacketHeader + Payload`
- 주요 헤더: `type`, `ttl`, `timestamp`, `senderId`, `recipientId`, `signature`
- 대용량 데이터는 `FRAGMENT`로 분할 전송 후 수신 측 재조립

### 패킷 타입과 역할
- `ANNOUNCE`: 노드 상태(닉네임, 키, 배터리, 주소) 주기 공유
- `LEAVE`: 노드 이탈 알림
- `MESSAGE`: 일반 텍스트/프로필 데이터 전달
- `RESCUE_ID`: 식별정보(이름, 생년월일, 성별) 전달
- `CALL_HANDSHAKE`: 통화 시작/응답/종료 및 경로 협상
- `DEVICE_CONTROL`: 원격 화면/소리/진동/고주파/중지 명령 전달
- `REQUEST_SYNC`: 누락 데이터 동기화 요청
- `FILE_TRANSFER`, `FILE_ACK`: 파일 전송 및 수신 확인

패킷별 송신자/수신자/필드 상세는 [`docs/protocol/packet_types.md`](ProjectRoot/docs/protocol/packet_types.md)에 정리했습니다.

## 동기화 / 신뢰성 / 보안
- RequestSync + GCS(골롬-라이스) 필터로 누락 데이터만 선택 재전송
- TTL 기반 릴레이 전파 제어와 Dedup(중복 제거) 적용
- Fragmentation/Reassembly + ACK/재시도로 전송 안정성 확보
- Store-and-Forward 캐시로 오프라인 구간 재전달 보완
- Ed25519 기반 서명 검증으로 핵심 패킷 위변조 방지

동기화/신뢰성/보안 정책 상세는 [`docs/network/README.md`](ProjectRoot/docs/network/README.md)에 정리했습니다.

## AI 기술
- `SpeechRecognizer`로 음성을 텍스트로 변환
- `bert_kor.tflite`로 비상/일상 의도 분류
- 비상으로 분류된 발화를 자동 호출 흐름과 연동

AI 파이프라인 상세는 [`docs/ai/ai_pipeline.md`](ProjectRoot/docs/ai/ai_pipeline.md)에 정리했습니다.

## 의존성 (Dependencies)

### 빌드 환경
- Kotlin `2.0.21`, Android Gradle Plugin `8.13.2`, Gradle `8.13`
- Android SDK: `compileSdk 36` / `targetSdk 34` / `minSdk 31`

### 주요 의존성
| 분류 | 라이브러리/기술 | 버전 |
|---|---|---|
| UI | Jetpack Compose BOM, Material3, Navigation Compose | `2024.09.00`, `2.7.7` |
| 아키텍처/비동기 | Lifecycle ViewModel KTX, Kotlin Coroutines | `2.6.1`, `1.8.1` |
| 저장소 | Room, DataStore Preferences | `2.6.1`, `1.2.0` |
| 통신/거리 | AndroidX Core UWB, BLE/Wi-Fi Aware/Wi-Fi Direct(플랫폼 API) | `1.0.0-alpha11` |
| 보안 | BouncyCastle(`bcprov-jdk15on`), AndroidX Security Crypto | `1.70`, `1.1.0` |
| AI | TensorFlow Lite, Android SpeechRecognizer | `2.14.0`, Android Framework |
| 품질 도구 | ktlint, detekt | `12.1.1`, `1.23.6` |

## 실행 방법
### Requirements
- Android Studio
- JDK 17+
- Android SDK (`compileSdk 36`, `targetSdk 34`, `minSdk 31`)

### APK 버전 안내 (루트 배포 파일 기준)
- `0.3.0`: 내부 기능 검증용 빌드 (비시연)
- `0.6.0`: 시연 기준 빌드 (망 안정화 개선 반영)

### Build
```powershell
.\ProjectRoot\gradlew.bat -p .\ProjectRoot :lifesaivior:assembleDebug
.\ProjectRoot\gradlew.bat -p .\ProjectRoot :rescuer:assembleDebug
```

### Test
```powershell
.\ProjectRoot\gradlew.bat -p .\ProjectRoot :lifesaivior:testDebugUnitTest
.\ProjectRoot\gradlew.bat -p .\ProjectRoot :rescuer:testDebugUnitTest
```

## 지원 범위 / 제약
- UWB는 단말 하드웨어 지원 + 권한 허용 시에만 동작
- 기본 전파 경로는 BLE 메쉬, 통화 경로는 Wi-Fi Aware 우선
- 오프라인 환경에서 동기화는 RequestSync + GCS 기반 누락 복구 방식으로 수행

