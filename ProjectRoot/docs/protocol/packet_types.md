# Packet Types

아래 표는 코드에 정의된 주요 패킷 타입(`PacketType`)의 역할을 설명합니다.

| 타입 | 분류 | 누가 보내나 | 언제 쓰나 | 핵심 목적 | 코드 위치 |
|---|---|---|---|---|---|
| `ANNOUNCE` | 제어/식별 | 양쪽 앱 | 주기적 브로드캐스트 | 닉네임, 공개키, 배터리, direct 주소, 프로필 요약(성별/생년월일/특이사항) 공유 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt), [IdentityAnnouncementPayload](../../shared/src/main/java/com/example/lifesaivior/protocol/model/IdentityAnnouncementPayload.kt) |
| `LEAVE` | 제어/식별 | 양쪽 앱 | 앱 종료/이탈 시 | 메쉬에서 노드 이탈 알림 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt) |
| `MESSAGE` | 메시징 | 양쪽 앱 | 채팅/프로필 TLV 송신 시 | 일반 텍스트/데이터 전달 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt) |
| `RESCUE_ID` | 식별 | 양쪽 앱 | 식별정보 공유 시 | 이름/생년월일/성별 TLV 전달 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt), [RescueIdPayload](../../shared/src/main/java/com/example/lifesaivior/protocol/model/RescueIdPayload.kt) |
| `CALL_HANDSHAKE` | 제어 | 양쪽 앱 | 통화 연결/종료/응답 시 | 통화 상태 협상, 전송 경로 정보 교환 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt), [CallHandshakePayload](../../shared/src/main/java/com/example/lifesaivior/protocol/model/CallHandshakePayload.kt) |
| `DEVICE_CONTROL` | 제어 | 구조자 앱 | 원격 대응 시 | 화면 켜기, 경고음, 진동, 고주파, 경보 중지 명령 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt), [DeviceControlPayload](../../shared/src/main/java/com/example/lifesaivior/protocol/model/DeviceControlPayload.kt) |
| `REQUEST_SYNC` | 동기화 | 양쪽 앱 | 초기/주기 동기화 시 | 내 보유 패킷 집합을 요약해 누락 데이터 요청 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt), [RequestSyncPayload](../../shared/src/main/java/com/example/lifesaivior/protocol/model/RequestSyncPayload.kt) |
| `FRAGMENT` | 전송 | 공통 엔진 | 페이로드가 클 때 | 조각 단위 전송 후 수신 측 재조립 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt), [FragmentPayload](../../shared/src/main/java/com/example/lifesaivior/protocol/model/FragmentPayload.kt) |
| `FILE_TRANSFER` | 전송 | 양쪽 앱 | 음성/파일 전송 시 | 파일 본문 전달 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt), [FileTransferPayload](../../shared/src/main/java/com/example/lifesaivior/protocol/model/FileTransferPayload.kt) |
| `FILE_ACK` | 전송 | 수신 측 | 파일 수신 확인 시 | 파일 전송 완료 확인 및 재시도 제어 | [PacketType](../../shared/src/main/java/com/example/lifesaivior/protocol/model/PacketType.kt), [FileTransferAckPayload](../../shared/src/main/java/com/example/lifesaivior/protocol/model/FileTransferAckPayload.kt) |

## DEVICE_CONTROL 명령 상세

| 명령 | 의미 | 코드 위치 |
|---|---|---|
| `WAKE_SCREEN` | 화면 켜기 | [DeviceControlCommand](../../shared/src/main/java/com/example/lifesaivior/protocol/model/DeviceControlPayload.kt) |
| `BEEP` | 경고음 재생 | [DeviceControlCommand](../../shared/src/main/java/com/example/lifesaivior/protocol/model/DeviceControlPayload.kt) |
| `VIBRATE` | 진동 실행 | [DeviceControlCommand](../../shared/src/main/java/com/example/lifesaivior/protocol/model/DeviceControlPayload.kt) |
| `HIGH_TONE` | 고주파 톤 재생 | [DeviceControlCommand](../../shared/src/main/java/com/example/lifesaivior/protocol/model/DeviceControlPayload.kt) |
| `STOP_ALERTS` | 실행 중 경보/알림 중지 | [DeviceControlCommand](../../shared/src/main/java/com/example/lifesaivior/protocol/model/DeviceControlPayload.kt) |
| `POWER_SAVE_ON` | 절전 모드 켜기 | [DeviceControlCommand](../../shared/src/main/java/com/example/lifesaivior/protocol/model/DeviceControlPayload.kt) |
| `POWER_SAVE_OFF` | 절전 모드 끄기 | [DeviceControlCommand](../../shared/src/main/java/com/example/lifesaivior/protocol/model/DeviceControlPayload.kt) |

## CALL_HANDSHAKE 액션 상세

| 액션 | 의미 | 코드 위치 |
|---|---|---|
| `START` | 통화 시작 요청 | [CallHandshakeAction](../../shared/src/main/java/com/example/lifesaivior/protocol/model/CallHandshakePayload.kt) |
| `ACK` | 통화 수락/응답 | [CallHandshakeAction](../../shared/src/main/java/com/example/lifesaivior/protocol/model/CallHandshakePayload.kt) |
| `END` | 통화 종료 | [CallHandshakeAction](../../shared/src/main/java/com/example/lifesaivior/protocol/model/CallHandshakePayload.kt) |
| `UWB_SYNC` | UWB 세션 정보 동기화 | [CallHandshakeAction](../../shared/src/main/java/com/example/lifesaivior/protocol/model/CallHandshakePayload.kt) |
