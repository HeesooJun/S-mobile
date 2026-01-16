# 센서 데이터 수집(CSV) - Life Savior

## 목표
- 근접 + 조도 (+ 필요하면 가속도) 데이터를 CSV로 저장해서
  - 임계값 튜닝
  - 트리거 로직 검증
  - (선택) AI 학습 데이터셋 기반 확보

## 저장 포맷(권장)
- prox/light:
  - timestamp_ms, prox_raw, prox_near, lux, label

- accel까지 포함 시:
  - ts_ms, prox_near, lux, ax, ay, az, acc_mag, label

## 저장 위치(권장)
- context.getExternalFilesDir(null)
- 경로 예:
  - /sdcard/Android/data/<패키지명>/files/prox_light_*.csv

## 수집 방법
1) 디버그 화면에서 Start Logging 버튼
2) 라벨 입력(예: pocket / hand / table / walk / impact)
3) 상황별로 행동하고 Stop Logging
4) adb pull로 PC로 가져오기

## ADB pull
- 목록 확인:
  - adb shell ls /sdcard/Android/data/<패키지명>/files/
- 가져오기:
  - adb pull /sdcard/Android/data/<패키지명>/files/prox_light_*.csv .

## 수집 시나리오(최소)
- pocket(주머니): 30초
- hand(손): 30초
- table(책상 정지): 30초
- walk(걷기): 30초
- impact(충격): 10회 정도 (안전하게)
