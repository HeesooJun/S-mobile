# 1. 빌드 및 배포 문서

## 제출 항목 중 해당 없음 (기획적 사유)
`웹서버/WAS`  
이 서비스는 재난 상황에서도 동작해야 하므로 서버 의존을 제거한 오프라인 설계를 선택했습니다.  
중앙 서버 장애나 네트워크 단절이 발생해도 BLE 메쉬만으로 구조 신호가 전달되도록 한 방향입니다.  
그래서 운영/배포 인프라가 필요하지 않아 웹서버/WAS 항목은 문서화 대상에서 제외했습니다.

`빌드 환경 변수`  
누구나 바로 빌드할 수 있도록 환경 변수 의존을 최소화했습니다.  
Android Studio/Gradle 표준 설정만으로 재현 가능하도록 했고, SDK 경로는 Studio가 관리합니다.  

`서버 DB 계정/프로퍼티 파일`  
구조자 앱은 피구조자 프로필을 로컬 Room DB에 저장해 현장에서 정보를 이어받습니다.  
구조자가 현장에 혼자 있어도 이전에 수집된 생존자 정보를 즉시 확인할 수 있게 하려는 목적입니다.  
중앙 서버 DB를 두지 않는 설계이므로 서버 DB 계정/프로퍼티 파일은 존재하지 않습니다.

## 먼저 설치해야 할 것
1. Android Studio 설치
2. Android SDK 설치
3. JDK 17 이상

## APK 빠른 설치 (이미 준비된 파일 사용)
이미 프로젝트 루트에 APK가 준비되어 있습니다.
- [lifesaivior.apk](../../lifesaivior.apk)
- [rescuer.apk](../../rescuer.apk)

Android Studio를 열어둔 상태에서 **프로젝트 루트** 기준으로 아래만 실행하면 됩니다.
```powershell
adb devices
adb install -r .\lifesaivior.apk
adb install -r .\rescuer.apk
```

ADB가 인식되지 않으면 Android SDK의 `platform-tools` 경로에서 실행:
```powershell
# Windows (기본 경로)
$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe devices

# 환경 변수 사용 시
$env:ANDROID_HOME\platform-tools\adb.exe devices
# 또는
$env:ANDROID_SDK_ROOT\platform-tools\adb.exe devices
```

## 빌드 환경 및 버전 (프로젝트 기준)
- Kotlin 2.0.21
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Android SDK: compileSdk 36 / targetSdk 34 / minSdk 31
- JDK 17+

## Android SDK 설치 방법(Android Studio)
1. Android Studio 실행
2. `More Actions > SDK Manager` 열기
3. `SDK Platforms`에서 `Android SDK Platform 36` 설치
4. `SDK Tools`에서 `Android SDK Platform-Tools` 설치
5. 적용 후 저장

## 프로젝트 열기
1. Android Studio에서 `ProjectRoot` 폴더 열기
2. Gradle Sync 완료 대기
3. Sync 중 SDK 경로 요청이 뜨면 Android SDK 경로를 지정

## 빌드 방법(디버그 APK)
프로젝트 루트 `ProjectRoot`에서 실행:
```powershell
.\gradlew.bat :lifesaivior:assembleDebug
.\gradlew.bat :rescuer:assembleDebug
```

## APK 산출물 위치
- `ProjectRoot/Lifesaivior/build/outputs/apk/debug/lifesaivior-debug.apk`
- `ProjectRoot/Rescuer/build/outputs/apk/debug/rescuer-debug.apk`

## 기기 연결 방법(USB 디버깅)
1. 휴대폰 개발자 옵션 활성화
2. USB 디버깅 허용
3. USB 케이블로 PC 연결
4. 연결 확인:
```powershell
adb devices
```
`device` 상태가 표시되면 정상

## APK 설치 방법(ADB)
```powershell
adb install -r .\ProjectRoot\Lifesaivior\build\outputs\apk\debug\lifesaivior-debug.apk
adb install -r .\ProjectRoot\Rescuer\build\outputs\apk\debug\rescuer-debug.apk
```

## 설치 확인/실행
```powershell
adb shell monkey -p com.example.lifesaivior -c android.intent.category.LAUNCHER 1
adb shell monkey -p com.example.lifesaivior.rescuer -c android.intent.category.LAUNCHER 1
```

## 설치 실패 시(서명 충돌)
```powershell
adb uninstall com.example.lifesaivior
adb uninstall com.example.lifesaivior.rescuer
```
삭제 후 다시 설치

## 배포 특이사항
- 오프라인 전제
- UWB/BLE/Wi-Fi Aware는 기기 하드웨어 및 권한에 따라 동작
