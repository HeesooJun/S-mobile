# 보안

## 목적
핵심 구조 패킷의 위변조 여부를 검증해 잘못된 제어/신호 반영을 줄입니다.

## 적용 범위
| 항목 | 내용 | 설정 근거 | 코드 위치 |
|---|---|---|---|
| 서명 알고리즘 | Ed25519 | 모바일 환경에서 짧은 키/서명 길이 대비 검증 성능이 좋아 패킷 단위 무결성 검증에 적합 | [SignatureManager](../../shared/src/main/java/com/example/lifesaiver/protocol/security/SignatureManager.kt) |
| 검증 대상 | `ANNOUNCE`, `MESSAGE`, `FILE_TRANSFER` | 메쉬 식별/메시지/파일 전송의 핵심 데이터만 우선 검증해 보안성과 처리 비용을 균형화 | [SignatureManager.shouldVerify()](../../shared/src/main/java/com/example/lifesaiver/protocol/security/SignatureManager.kt) |

## 처리 방식
1. 송신 시 패킷에 서명을 포함합니다.
2. 수신 시 서명과 공개키를 이용해 무결성을 검증합니다.
3. 검증 실패 패킷은 신뢰 데이터로 반영하지 않습니다.

처리 로직은 [SignatureManager.sign() / verify()](../../shared/src/main/java/com/example/lifesaiver/protocol/security/SignatureManager.kt)에 구현되어 있습니다.

## 키 관리
- 저장 위치: EncryptedSharedPreferences
- 보호 루트: Android Keystore
- 관련 구현: [createEncryptedPrefs()](../../shared/src/main/java/com/example/lifesaiver/protocol/security/SignatureManager.kt), [loadOrCreateSigningKeyPair()](../../shared/src/main/java/com/example/lifesaiver/protocol/security/SignatureManager.kt)

## 현재 범위
- 현재는 무결성 검증 중심
- 종단간 암호화 채널은 확장 영역
