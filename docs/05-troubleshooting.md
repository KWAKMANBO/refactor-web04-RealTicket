# 5. 문제 해결 기록

개발·측정 과정에서 만난 문제와 해결 방법이다. 코드 버그는 [02-bug-fixes.md](./02-bug-fixes.md)에 따로 정리했다.

## 빌드·테스트 환경

| 문제 | 원인 | 해결 |
|---|---|---|
| Gradle 의존성 다운로드 실패 | 클라우드 개발 환경의 네트워크 정책이 Maven Central·Gradle 저장소를 막음 | 환경 설정에서 해당 호스트 허용 |
| 의존성 다운로드 중 `429 Too Many Requests` | 공유 IP가 Maven Central 요청 제한에 걸림 | 병렬 다운로드를 줄여(`--max-workers=1`) 재시도 |
| Testcontainers가 Docker를 찾지 못함 | Docker Engine 29가 API 1.40 미만 요청을 거부하는데, Testcontainers 1.21의 기본값이 1.32 | 테스트 JVM에 `api.version=1.44` 지정 (`build.gradle.kts`) |
| 한글 이름의 중첩 테스트 클래스가 컴파일되지 않음 | 일부 환경에서 한글 `.class` 파일 이름을 만들지 못함 | 클래스 이름은 영어, `@DisplayName`으로 한글 표시 (메서드 이름은 한글 유지) |
| 테스트에서만 "오픈 시각 전 이벤트가 열림" | 앱은 `main()`에서 기본 시간대를 Asia/Seoul로 바꾸지만, 테스트는 `main()`을 거치지 않아 UTC | 테스트 JVM에 `user.timezone=Asia/Seoul` 지정 |
| 테스트 코드에서 Lombok 인식 실패 | Lombok이 메인 코드에만 설정됨 | `testCompileOnly`·`testAnnotationProcessor` 추가 |

## 구현

| 문제 | 원인 | 해결 |
|---|---|---|
| 앱 시작 실패: `Ambiguous @ExceptionHandler` | 부모 클래스(`ResponseEntityExceptionHandler`)가 이미 처리하는 예외를 중복 등록 | 중복 핸들러 제거 |
| SSE 요청이 실패할 때 에러 JSON이 내려가지 않음 | `Accept: text/event-stream` 요청에 JSON 응답을 쓸 수 없음 | 에러 응답에 `Content-Type: application/json`을 명시 |
| SSE 종료 시 인증 오류 | SSE가 끝날 때의 비동기 재진입에서 인터셉터가 다시 실행되는데, 그 사이 세션 상태가 바뀜 | 비동기 재진입(`DispatcherType.ASYNC`)에서는 검사 생략 |
| 좌석 비트맵이 깨져서 읽힘 | `StringRedisTemplate`이 바이너리 값을 문자열로 변환 | 원시 커넥션으로 파이프라인 읽기 |
| 새 구독자가 오래된 좌석 상태를 받음 | 마지막 전송 값을 캐시해 재사용 | 구독 시 항상 Redis에서 새로 읽음 |

## 프런트엔드 연동 확인

| 문제 | 원인 | 해결 |
|---|---|---|
| 캡차 때문에 자동 테스트 불가 | 이미지 문자 입력 | 테스트 브라우저에서만 캡차 모듈 응답을 바꿈 (Vite가 `export const`를 `var`로 바꿔 패턴 조정) |
| 탭을 닫았는데 대기자가 30초 뒤에야 입장 | 버그가 아님. 프런트는 **구역을 클릭해야** 좌석 SSE를 연다. 구역을 고르기 전에 닫으면 입장 유효 시간(dev 30초)이 지나야 자리가 풀림 | 설계대로 동작함을 확인, 문서화 |
| 백엔드 주소로 접속하니 "페이지를 찾을 수 없습니다" | `VITE_API_URL`(8080)은 API 주소, 화면은 5173 | 접속 주소 안내 |
| `npm ci`의 ERESOLVE 경고·취약점 알림 | 캡차 라이브러리의 오래된 의존성 (React 16 이하 요구) | 동작에 문제 없음. `npm audit fix`는 실행하지 않음 |

## 저장소·배포

| 문제 | 원인 | 해결 |
|---|---|---|
| 측정용 `docker-compose.yml`이 원격에 없음 | 루트 `.gitignore`가 모든 `docker-compose.yml`을 무시 | 배포용 규칙은 유지하고 측정용 파일만 예외(`!bench/docker-compose.yml`) |
| 원격 브랜치 이름 변경 시 이전 브랜치 삭제 실패 | 개발 환경에서 원격 브랜치 삭제가 막힘 | 새 이름으로 push 후, 이전 브랜치는 GitHub에서 직접 삭제 |
| Docker 빌드 컨텍스트가 큼 | 로컬 빌드 산출물(jar 70MB 이상)까지 전송 | `back/.dockerignore` 추가 |
| Python 캐시 폴더가 변경 사항으로 보임 | `aggregate.py`가 `summarize.py`를 불러올 때 생성 | `bench/.gitignore` 추가 |

## 측정 실행

| 문제 | 원인 | 해결 |
|---|---|---|
| `run-all.sh: SECTIONS?: unbound variable` | macOS 기본 bash 3.2가 한국어 환경에서 `$SECTIONS개`의 한글 일부를 변수 이름으로 읽음 | 모든 변수를 `${VAR}`로 감쌈 |
| `docker compose up` 후 컨테이너가 `Created`에서 멈춤 | 이전에 띄운 Redis 컨테이너가 6379 포트 사용 중 | 기존 컨테이너 삭제 |
| 결과 표만으로 측정 유효성 판단 불가 | 일부 비교의 오류 수를 표시하지 않음 | 오류 열과 회차 검증 항목 추가 |
| 처리량 측정의 p99가 방식 간 비슷함 | 각 방식을 한계 부하에서 측정해 CPU 포화 영향이 섞임 | p99는 주장하지 않고 처리량·p95로 비교 |
| 실행 계획이 한 줄로 출력됨 | mysql 클라이언트가 줄바꿈을 `\n`으로 표시 | `mysql -r` 옵션 |
