# Lifesaiver for Android

Lifesaiver는 인터넷/기지국 연결이 없는 상황에서도 단말 간 직접 통신으로 구조 신호와 생존자-구조자 간 데이터를 전달하는 Android 멀티모듈 프로젝트입니다.

## 1. 프로젝트 개요
- 동작 환경: 오프라인 재난/고립 상황
- 핵심 기능: SOS, 음성 통신(PTT/Call), 텍스트 채팅, 프로필 공유
- 앱 구성: 생존자 앱(`:lifesaiver`), 구조자 앱(`:rescuer`), 공통 엔진(`:shared`)

## 2. 모듈 구성
### 2.1 `:lifesaiver` (생존자 앱)
- 프로필, 대기/비상, 채팅, PTT 화면 제공
- 고립 감지 후 음성/센서 기반 자동 트리거 지원
- 구조 요청 송신 및 상태 공유 중심 UI

### 2.2 `:rescuer` (구조자 앱)
- 생존자 목록/상태 조회, 메쉬 맵 시각화
- 대상 생존자 선택 후 통화/채팅/원격 제어
- 구조 활동에 필요한 탐색/대응 흐름 제공

### 2.3 `:shared` (공통 로직)
- 프로토콜 코덱, 패킷 파이프라인, 멀티홉 릴레이
- 동기화(RequestSync), 스토어-앤-포워드, 서명 검증
- BLE/Wi-Fi/UWB 기반 통신 및 거리 추정 공통 로직

## 3. 기능 요약
### 3.1 생존자 측
- 고립 감지(`IsolationDetector`): 인터넷 + 셀룰러 단절 감지
- 음성 감지(`VoiceService`) + STT + 의도 분류
- 충격 감지(`SensorService`) 기반 자동 구조 시나리오
- SOS 송출/유지, 구조 채팅, PTT/통화
- 프로필 생성/수정 및 네트워크 전파

### 3.2 구조자 측
- 생존자 목록(DB) 기반 상태 확인(배터리/신호 포함)
- 메쉬 맵에서 직접/멀티홉 피어 현황 확인
- 대상 선택 후 통화 시도, 직접 채팅
- 디바이스 제어 패킷(진동/고음/중지 등) 처리

### 3.3 공통 엔진(`:shared`)
- `protocol/core`: 송수신, 릴레이, 파일 ACK/재시도, store-and-forward
- `protocol/codec`: `BinaryPacketCodec`(버전, flags, 압축, 패딩, 서명 필드)
- `protocol/pipeline`: 분할/재조립, 중복 제거
- `protocol/mesh`: 피어/그래프 레지스트리, gossip TLV
- `protocol/sync`: RequestSync 기반 누락 데이터 동기화
- `protocol/security`: 서명 생성/검증, 키 저장, 검증 로그
- `core/ble`: 광고/스캔/연결/전송, RSSI 모니터링, blocklist
- `core/call`, `core/audio`: 실시간 오디오 스트리밍, 통화 매니저
- `core/location`: RSSI/RTT/UWB 결합 거리 추정

## 4. 네트워크와 메쉬 동작
- 기본 데이터 전달은 BLE(`BleTransport`) + 멀티홉 릴레이
- 통화 경로는 Wi-Fi Aware 우선, 실패 시 Wi-Fi Direct 전환
- 거리 추정은 RSSI(Direct), RTT(Aware), UWB 중 가용 소스 선택
- 배터리 정보는 ANNOUNCE payload(TLV)로 피어 간 공유
- 수신 불가 대상 패킷은 임시 저장 후 재전달(store-and-forward)

### RequestSync(GCS) 설명
- 목적: 메쉬에서 누락된 메시지/ANNOUNCE를 보완 동기화
- 방식:
  - 수신 측이 "이미 받은 패킷 ID 집합"을 GCS 필터로 압축해 전송
  - 송신 측이 누락된 패킷만 선별 재전송
- 참고: 메쉬 그래프를 직접 계산하는 기능이 아니라 누락 복구 메커니즘

## 5. 바이너리 프로토콜
- 기본 구조: `PacketHeader + Payload`
- Header: 송신자, 타입, TTL, 타임스탬프, 옵션(압축/서명/경로 등)
- Payload: 텍스트, 음성, 파일, 제어 데이터 등 실제 본문
- 큰 데이터는 `FRAGMENT`로 분할 후 수신 측에서 재조립

주요 패킷 타입:
- `ANNOUNCE`, `MESSAGE`, `LEAVE`
- `FRAGMENT`, `REQUEST_SYNC`, `FILE_TRANSFER`, `FILE_ACK`
- `RESCUE_ID`, `CALL_HANDSHAKE`, `DEVICE_CONTROL`

신뢰성 처리:
- TTL 기반 릴레이
- 중복 제거(Dedup)
- 파일 전송 ACK/재시도
- 압축/패딩/서명 플래그 처리

## 6. 보안
- 현재 적용:
  - Ed25519 기반 패킷 서명/검증
  - 검증 대상: `ANNOUNCE`, `MESSAGE`, `FILE_TRANSFER`
  - 키 저장: EncryptedSharedPreferences/Android Keystore
- 현재 범위:
  - 종단간 암호화 데이터 채널은 아직 미적용
  - `NOISE_HANDSHAKE`, `NOISE_ENCRYPTED` 타입은 예약 상태(확장 예정)

## 7. 빌드 및 테스트
### 요구 환경
- Android Studio 최신 안정 버전 권장
- JDK 8 이상
- Android SDK 34
- minSdk 29 / targetSdk 34

### 빌드 (Windows PowerShell)
```powershell
.\gradlew.bat :lifesaiver:assembleDebug
.\gradlew.bat :rescuer:assembleDebug
```

### 테스트 (Windows PowerShell)
```powershell
.\gradlew.bat :lifesaiver:testDebugUnitTest
.\gradlew.bat :rescuer:testDebugUnitTest
```

## 8. 용어
- BLE: 저전력 블루투스 통신
- Mesh: 단말이 서로 중계해 통신 범위를 확장하는 구조
- PTT: 버튼을 누르는 동안만 송신하는 음성 통신 방식
- TTL: 패킷이 전달될 수 있는 최대 홉 수
- ACK: 수신 확인 응답
- GCS 필터: 수신한 데이터 집합을 압축 표현해 누락 동기화에 쓰는 필터
