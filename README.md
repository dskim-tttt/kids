# 여행 속도계 · Kids Trip Speedometer

아이와 보호자가 이동 중 속도와 현재 도시를 함께 보는 Android 앱의 초기 버전.
Android 8.0(API 26) 이상, Java 17 소스, Android Gradle Plugin 8.9.1, Gradle 8.11.1, SDK 35.

## 구현 범위

- GPS 속도를 km/h로 변환해 큰 글씨로 표시한다.
- 최근 10초 이내 GPS 관측만 사용한다. 속도 없음/정확도 낮음은 0 대신 ‘—’로 표시한다.
- Android Geocoder로 도시를 조회한다. 도시가 없으면 시군 또는 광역 행정명으로 대체한다.
- 역지오코딩은 최소 60초 간격, 이전 성공 위치와 1km 미만이면 최대 5분 캐시한다.
- 권한 거부, 대략적 위치만 허용, GPS 꺼짐, GPS 미지원, 신호 단절, 도시 조회 실패를 안내한다.
- 화면을 벗어나면 GPS 구독을 해제한다. 로그인, 광고, 분석 SDK, 서버, 위치 기록 저장은 없다.
- Geocoder는 기기에 구현된 시스템 서비스를 이용한다. 도시 조회에는 네트워크 및 서비스 제공자 처리가 발생할 수 있고, 오프라인 도시명은 보장하지 않는다.

## 빌드

Android Studio에서 이 폴더를 열고 SDK 35를 설치한 다음 실행한다.
커맨드라인에서는 JDK 17 또는 21, Android SDK가 필요하다.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Windows PowerShell에서는 `./gradlew.bat testDebugUnitTest assembleDebug lintDebug`.
APK: `app/build/outputs/apk/debug/app-debug.apk` (개발용 서명).
실제 위치·속도 검증은 Android 기기에서 위치 권한을 허용하고 수행한다.
클라우드는 빌드와 단위 테스트를 담당하며 실제 GPS 수신을 검증하지 못한다.

## 수동 검증

1. 첫 실행: 위치 사용 버튼으로 정확한 위치 허용. GPS 수신 후 속도·도시 표시 확인.
2. 권한 거부 또는 대략적 위치만 허용: 정확한 위치 안내, 충돌 없음 확인.
3. GPS 끄기/터널 진입: 10초 내 속도가 ‘—’로 바뀌고 오래된 도시를 현 위치처럼 표시하지 않는지 확인.
4. 네트워크 끄기: 속도 측정은 지속, 도시 조회 실패 안내 확인(시스템 캐시 결과가 나올 수도 있음).
5. 홈 버튼/다른 앱 이동: GPS 구독 해제, 복귀 시 재수신 확인.
6. 회전·큰 글꼴·작은 화면·Android 15 시스템 바 겹침 확인.
7. 도시 경계 통과, 정지, 이동, 장시간 이동, 배터리 소모는 실제 동승 테스트 필요.

추가 놀이 요소 검토: [docs/playful-learning.md](docs/playful-learning.md).

## 저장소와 APK 다운로드

GitHub 저장소: https://github.com/dskim-tttt/kids

개발용 APK: [downloads/travel-speedometer-debug.apk](downloads/travel-speedometer-debug.apk).
GitHub에서 파일을 열고 **Download raw file** 버튼으로 내려받는다. 저장소가 비공개이면 권한이 있는 계정으로 로그인해야 한다.
Android 8.0 이상에서 설치하며 정확한 위치를 ‘앱 사용 중’ 허용한다. 실제 GPS/도시 표시 확인은 동승자가 수행한다.
이 APK는 기본 속도·도시 화면만 포함하고, 탐험 카드·여행 도장은 디자인 제안이다.

## 이번 클라우드 검증 결과

- 디버그 APK 빌드 성공.
- 속도 변환/정지/누락·부정확·오래된 관측 처리 단위 테스트 3개 통과.
- Android lint: 오류 0, 경고 0.
- `scripts/cloud-setup.sh` 재실행 성공. 위치 서비스가 있는 휴대폰에서의 실제 동작은 미검증.

화면 디자인 제안: [docs/design.md](docs/design.md). 시안의 탐험·도장 화면은 미구현 확장 제안이다.
