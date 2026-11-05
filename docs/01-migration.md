# 1. NestJS → Spring Boot 마이그레이션

## 한눈에 보기

| 항목 | 전환 전 | 전환 후 |
|---|---|---|
| 언어 / 프레임워크 | TypeScript, NestJS 10 (Express) | Java 21, Spring Boot 3.5 (Spring MVC + 가상 스레드) |
| DB 접근 | TypeORM, `synchronize`로 스키마 자동 변경 | Spring Data JPA + Flyway, JPA는 스키마 검증만(`ddl-auto=validate`) |
| Redis | ioredis, Lua 4개 | Spring Data Redis(Lettuce), Lua 10개(예매) + 2개(세션) |
| 인증 | `SessionAuthGuard(status)` + Redis JSON 세션 | `@RequireStatus` 인터셉터 + Redis Hash 세션 |
| 실시간 | `@Sse` + RxJS `BehaviorSubject` + 이벤트별 `setInterval` | `SseEmitter` + 전역 주기 브로드캐스터 |
| 테스트 | jest (spec 대부분 비어 있음) | JUnit 5 + Testcontainers(MySQL, Redis), 109개 |
| 코드 규모 | 약 5,000줄 (mock 제외) | 약 4,100줄 + 테스트 약 2,200줄 |
| API | 33개 (SSE 2개 포함) | 33개, 경로·응답 형식 그대로 |

프런트엔드 코드는 **한 줄도 바꾸지 않고** 새 백엔드에 연결되는 것을 브라우저 E2E로 확인했다.

## 주요 결정

| 결정 | 선택 | 이유 |
|---|---|---|
| 웹 스택 | **Spring MVC + 가상 스레드** (WebFlux 아님) | JPA는 블로킹이라 WebFlux와 맞지 않음. SSE는 `SseEmitter`(서블릿 비동기)로 연결당 스레드를 점유하지 않음. 디버깅·테스트가 쉬움 |
| DB 접근 | **JPA** | 쿼리가 단순하고 팀에 익숙함 |
| 전환 방식 | **한 번에 전환** (도메인별 점진 전환 아님) | 운영 중인 서비스가 아니라 병행 운영·라우팅 비용이 불필요 |
| 스키마 관리 | **Flyway** + `ddl-auto=validate` | 스키마 변경을 SQL 파일로 버전 관리. TypeORM `synchronize`처럼 컬럼이 조용히 지워지는 위험이 없음 |
| API 계약 | **경로·JSON 필드·에러 형식·SSE 형식·쿠키 유지** | 프런트엔드를 고치지 않기 위함. 에러 응답도 Nest 형식 `{statusCode, message, error}`를 그대로 재현 |
| 세션 저장 | JSON 문자열 → **Redis Hash** | 필드 단위 원자적 갱신으로 동시 요청 간 덮어쓰기 제거 |
| MOCK_MODE | **제거** | 프런트엔드가 mock API를 호출하지 않는 것을 코드로 확인. 운영 서버에 가짜 API가 열려 있던 문제도 함께 해소 |
| 관리자 회원가입 | **설정으로 켜고 끄기** (dev 켜짐, prod 꺼짐) | 인증 없이 누구나 관리자 계정을 만들 수 있던 보안 문제 해결 |
| 테이블 이름 | `User` → `users` 등 snake_case 복수형 | 새 스키마로 시작하므로 관례에 맞춤 |

## 진행 단계

| 단계 | 내용 | 커밋 |
|---|---|---|
| 1. 뼈대 | Gradle, 공통 설정, 세션 인증, Nest 형식 에러 응답, 엔티티 7개, Flyway, Testcontainers | `674eb88`, `2e4dfff` |
| 2. 조회 도메인 | place, program, event API 11개 | `3286b93` |
| 3. 사용자 | 회원가입·로그인·게스트·로그아웃, 게스트 정리 스케줄러 | `f07fb83` |
| 4. 보안 | 관리자 회원가입 설정 토글 | `a6b412a` |
| 5. 예매 핵심 | booking(대기열·입장·좌석·SSE·오픈/마감), reservation | `c9f8cea` |
| 6. 프런트 연동 검증 | 실제 프런트엔드 + Playwright로 예매 전 과정 확인 | (테스트 단계) |
| 7. 교체 | Nest 삭제, `back-java/` → `back/`, Docker prod 프로필, CI에 백엔드 테스트 추가 | `ec83576` |

브랜치: `refactor/backend-java-migration`

## 구조

```
back/src/main/java/com/realticket
├── global
│   ├── auth        세션 인증 (@RequireStatus, @CurrentUser, SessionRepository)
│   ├── exception   ApiException, Nest 형식 에러 응답
│   ├── config      CORS, OpenAPI, BCrypt, 시간대, 스케줄링
│   └── logging     요청 로그
└── domain
    ├── user / place / program / event / reservation
    └── booking
        ├── repository   Redis 저장소 (대기열, 입장, 좌석 선택, 좌석 비트맵)
        ├── service      예매 흐름, 오픈/마감 스케줄, 입장 세션 정리
        └── sse          좌석·대기열 브로드캐스터
back/src/main/resources
├── db/migration/V1__init.sql
└── lua/booking/*.lua
```

## 예매(booking) 설계

### 상태 흐름

```
LOGIN ──입장 요청──▶ ENTERING ──좌석 SSE 연결──▶ SELECTING_SEAT ──예매 확정/SSE 끊김──▶ LOGIN
           └──────▶ WAITING ──빈자리 생김──▶ ENTERING
ENTERING 상태로 일정 시간(prod 2분) 안에 좌석 화면에 오지 않으면 LOGIN 으로 돌아간다.
```

### Redis 키 (모두 이벤트 단위)

| 키 | 타입 | 용도 |
|---|---|---|
| `open-booking:opened` | Set | 오픈된 이벤트 |
| `waiting-queue:{e}`, `:order`, `:orders` | List, String, Hash | 대기열, 마지막 번호, sid별 번호 |
| `entering:{e}`, `:amounts` | ZSet, Hash | 입장 허가(허가 시각), 미리 고른 인원 수 |
| `in-booking:{e}:sessions`, `:session:{sid}`, `:session:{sid}:seats` | Set, Hash, Set | 좌석 선택 중인 사용자, 인원 수·저장 여부, 선점 좌석 |
| `event:{e}:section:{i}:seats` | Bitmap | 좌석 상태 (1 예약 가능, 0 예약됨) |

모든 키가 이벤트 단위라, 마감할 때 `KEYS` 명령 없이 정확한 키만 지운다.

### 원자적 처리 (Lua)

"확인 후 변경"이 필요한 동작은 모두 Lua 스크립트 하나로 처리해 중간에 다른 요청이 끼어들 수 없게 했다.

| 스크립트 | 하는 일 |
|---|---|
| `try_enter` | 자리가 있고 대기열이 비었으면 입장, 아니면 대기 번호 발급 (중복 요청 시 같은 번호) |
| `admit_waiting` | 빈자리만큼 대기열 앞에서 입장 |
| `move_to_in_booking` | 입장 → 좌석 선택 중 (이미 좌석 선택 중이면 그대로) |
| `reserve_seat` | 인원 수 확인 + 좌석 상태 확인 + 선점 기록 |
| `release_seat` | 내가 선점한 좌석만 취소 |
| `reset_booking_amount` | 인원 변경 시 선점 좌석 모두 해제 |
| `leave_in_booking` | 화면을 떠나면 (예매 전이라면) 좌석 해제 후 정리 |
| `mark_saved` | 예매 저장 시작 표시 (중복 예매 저장 방지) |
| `expire_entering` | 만료된 입장 세션 정리 |
| `init_section` | 오픈 시 구역 비트맵 생성 |

### SSE 브로드캐스트

- 이벤트별 `setInterval` 대신 **전역 주기 하나**: 좌석이 바뀐 이벤트만 0.5초마다 다시 보내고, 30초마다 전체 상태를 보낸다. 마감 후 interval이 남는 문제가 구조적으로 없다.
- **직렬화는 한 번**: 구독자 수와 상관없이 JSON을 한 번 만들어 모두에게 보낸다 (Nest 버전의 브로드캐스팅 최적화 유지).
- **가상 스레드로 전송**: 느린 클라이언트 하나가 전체 전송을 붙잡지 않는다.
- **5초 heartbeat**: Tomcat은 데이터를 보내다 실패해야 끊긴 연결을 알 수 있어, 주기적으로 SSE 주석을 보내 끊긴 연결을 감지하고 선점 좌석을 풀어 준다.
- **사용자당 연결 하나**: 같은 사용자가 다시 연결하면 이전 연결을 닫되, 이것을 "떠남"으로 처리하지 않는다.
- SSE 종료 시 비동기 재진입(async dispatch)에서는 인증 인터셉터를 건너뛴다 (그 사이 세션 상태가 바뀌어 오류가 나는 것을 방지).

### 스케줄링

- 서버 시작 시, 매시 59분, 관리자 요청 시 예약 마감 전 이벤트를 확인해 오픈/마감을 `TaskScheduler`로 예약한다.
- 입장 세션 정리는 주기 작업으로 돌고, 정리 후 대기자를 입장시킨다.
- **서버 한 대 전제** (Nest 버전과 동일). 여러 대로 늘리려면 스케줄러 분산 락(ShedLock 등)과 서버 간 좌석 변경 알림(Redis Pub/Sub 등)이 필요하다.

## Nest와 달라진 동작

자세한 버그 수정 목록은 [02-bug-fixes.md](./02-bug-fixes.md) 참고.

| 동작 | 변경 | 프런트 영향 |
|---|---|---|
| 경로의 id가 숫자가 아닐 때 | 일부 406 → 모두 400 | 없음 (406을 따로 처리하지 않음) |
| SSE 연결 끊김 감지 | 즉시 → 최대 5~10초 | 좌석이 그만큼 늦게 풀림 |
| 좌석 SSE·좌석 요청 | 입장 요청한 이벤트에만 허용 | 없음 (항상 같은 이벤트 번호 사용) |
| 대기열 | 대기자가 있으면 새 사용자는 빈자리가 있어도 뒤로 | 없음 |
| dev 프로필 예매 설정 | Nest 주석에 적힌 원래 의도대로 (최대 2명, 입장 유효 30초) | 로컬에서 대기열 확인 가능 |

## 배포 시 필요한 작업

- **DB 새로 만들기**: 테이블 이름이 바뀌었고 Flyway가 스키마를 관리한다. 기존 Nest 테이블이 있는 DB에서는 Flyway가 시작을 거부한다.
- **배포 스크립트 확인**: CI가 실행하는 서버의 `./nest/start.sh`는 저장소 밖에 있다. 환경 변수 이름(`DATABASE_*`, `REDIS_*`, `FRONT_URL`, `PORT`)은 Nest와 같다.
- **프록시 SSE 버퍼링**: `/api` 프록시에서 `proxy_buffering off` 확인.
