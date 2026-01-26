# Framing 방식 - 구분자 기반

모든 프레임이 바이트 혹은 문자의 모음으로 간주된다.

Byte Oriented Protocols
BISYNC - Binary Synchronous Communication Protocol
PPP - Point-to-Point Protocol
DDCMP - Digital Data Communication Message Protocol


## BISYNC 
데이터 링크 계층 프로토콜이며 센티널 접근 방식을 따른다.
IBM에서 개발했고, BSC라고 불리기도 함.

1번째 SYN 필드가 8비트
2번째 SYN 필드가 8비트
3번째 SOH 필드가 8비트
4번째 헤더
5번째 STX 필드 8비트
6번째 바디 가변길이
7번째 ETX 필드 8비트
8번쨰 CRC 필드 16비트 - 에러 디텍팅을 위한 필드

프레임 전송은 가장 왼쪽 필드부터 진행됨.

시작 기준은 special한 SYN(synchronize) character를 전송 함으로써 시작됨.

헤더시작은 SOH필드

바디의 시작기준은 STX필드, 끝 기준은 ETX필드

DLE은 DataLink 탈출.

## 문제점

만약에 본문에 STX필드나 ETX필드가 나타나게 된다면?
수신자는 그것을 시작필드인지 끝필드인지 알 수 없다.

이를 해결하기 위해 문자 스터핑으로 처리한다.

문자 스터핑(Character Stuffing)
- 문자 프레임 전송과정에서 제어 문자를 추가하는 기능
- 송신 호스트: 데이터에 DLE 문자가 있으면 강제로 DLE 문자 추가
- 수신 호스트: 데이터에 DLE 문자가 두 번 연속 있으면 DLE 문자 삭제