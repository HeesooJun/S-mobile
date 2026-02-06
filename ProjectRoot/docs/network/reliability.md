# 신뢰성

## 목적
오프라인/불안정 링크 환경에서도 패킷 전달 성공률과 일관성을 유지합니다.

## 적용 메커니즘
| 항목 | 설정값 | 역할 | 설정 근거 | 코드 위치 |
|---|---|---|---|---|
| 메시지 TTL | `7 hops` | 전파 범위 제어 | 구조 신호가 인접 영역을 넘겨 충분히 확산되도록 hop 상한 설정 | [ProtocolConstants.MESSAGE_TTL_HOPS](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| 동기화 TTL | `0` | 동기화 패킷 과확산 방지 | 동기화 요청/응답은 대상 노드 간 교환으로 제한해 망 혼잡 최소화 | [ProtocolConstants.SYNC_TTL_HOPS](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| Dedup 윈도우 | `300000ms` | 중복 패킷 처리 방지 | 메쉬 재중계 지연을 흡수하면서 중복 저장 비용을 제한 | [ProtocolConstants.Dedup.MESSAGE_TIMEOUT_MS](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| Dedup 최대 이력 | `10000` | 중복 판별 메모리 상한 관리 | 피크 구간 중복 판별 정확도를 유지하면서 메모리 폭주를 방지 | [ProtocolConstants.Dedup.MAX_PROCESSED_MESSAGES](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| 조각화 임계치 | `512 bytes` | MTU 초과 시 조각 전송 시작 | BLE 페이로드 한계를 고려해 과대 패킷을 안정적으로 분할 전송 | [ProtocolConstants.Fragmentation.FRAGMENT_SIZE_THRESHOLD](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| 최대 fragment 크기 | `469 bytes` | 단편 전송 크기 제한 | 헤더 오버헤드를 제외한 안전 전송 크기를 보장 | [ProtocolConstants.Fragmentation.MAX_FRAGMENT_SIZE](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| 재조립 타임아웃 | `30000ms` | 끊긴 조각 세션 정리 | 손실 조각을 무기한 대기하지 않고 세션 정리를 통해 리소스 회수 | [ProtocolConstants.Fragmentation.FRAGMENT_TIMEOUT_MS](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| ACK 대기 시간 | `5000ms` | 파일 수신 확인 대기 | 모바일 P2P 환경의 평균 응답 지연을 고려한 재시도 전 대기 시간 | [ProtocolConstants.FileTransfer.ACK_TIMEOUT_MS](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| 파일 최대 재시도 | `3` | 실패 전송 복구 | 일시적 전파 손실은 복구하되 무한 재시도로 인한 혼잡을 방지 | [ProtocolConstants.FileTransfer.MAX_RETRY_ATTEMPTS](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| 캐시 보관 시간 | `43200000ms` | Store-and-Forward 보관 | 반일(12시간) 범위에서 지연 전달 가능성을 보존하고 캐시 누적을 억제 | [ProtocolConstants.StoreForward.MESSAGE_CACHE_TIMEOUT_MS](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| 캐시 최대 수 | `100` | 캐시 폭주 방지 | 단말 메모리 사용량을 통제하면서 재전달 후보를 유지 | [ProtocolConstants.StoreForward.MAX_CACHED_MESSAGES](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |
| 캐시 정리 주기 | `600000ms` | 만료 데이터 주기 정리 | 청소 비용과 만료 데이터 잔존 시간을 균형 있게 유지 | [ProtocolConstants.StoreForward.CLEANUP_INTERVAL_MS](../../shared/src/main/java/com/example/lifesaivior/protocol/core/ProtocolConstants.kt) |

## 기대 효과
- 중복/과전파/단편 손실을 제어해 전달 안정성 유지
- 일시 단절 상황에서도 재전송으로 복구 가능
