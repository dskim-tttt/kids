# 개발용 APK · 여행 속도계 0.4.2

- 최신: [travel-speedometer-0.4.2-debug.apk](travel-speedometer-0.4.2-debug.apk)
- 고정 별칭: [travel-speedometer-debug.apk](travel-speedometer-debug.apk)
- 이전: [0.4.1](travel-speedometer-0.4.1-debug.apk), [0.4.0](travel-speedometer-0.4.0-debug.apk)
- 앱 ID `com.example.kidstrip`, versionCode `6`, Android 8.0(API 26) 이상
- 크기: 84,860 bytes
- SHA-256: `5565f67727395a3ef62425c0a60a2fe62800fc346f06fa8bab43ce1c8cbd34a1`

GitHub 파일 화면에서 **Download raw file**로 내려받아 기존 앱 위에 업데이트 설치한다.
기존 버전과 서명 및 앱 ID를 유지했다.

## 화면이 자동으로 꺼지지 않게

- 앱을 앞에서 사용하는 동안 화면을 켜 둔다. 여행·탐험·도장·도시 환영 화면에 동일 적용.
- 다른 앱으로 이동하거나 종료할 때는 유지 플래그를 해제한다.
- 전원 버튼으로 직접 끄는 것은 가능하다. 시스템 전체의 화면 꺼짐 시간은 바꾸지 않는다.
- 추가 권한·백그라운드 WakeLock을 사용하지 않는다.
- 재방문 소리·효과, 중복 없는 도장, 나침반, 카드 등 기존 동작 유지.
- 도시별 별도 로고 원본은 미반영이며, 현재는 앱 자체 도시 이름 배지다.

## 검증

- 기존 자동 테스트 **49개 통과**. 빌드·lint 통과(오류·경고 0).
- 0.4.1과 APK 서명 인증서 일치, versionCode 증가, APK 압축 무결성 확인.
- 실제 기기의 자동 화면 꺼짐 및 업데이트 설치는 미검증이다.

휴대폰에서 자동 화면 꺼짐 시간보다 오래 앱을 조작하지 않고 켜 두어 유지되는지 확인한다.
각 탭·도시 환영 미리보기에서도 확인하고, 홈으로 나간 뒤 기본 시간에 화면이 꺼지는지 확인한다.
