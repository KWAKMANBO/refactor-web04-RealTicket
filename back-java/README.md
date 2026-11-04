# RealTicket Backend (Java)

NestJS 백엔드(`../back`)를 Spring Boot로 옮기는 중인 새 백엔드입니다.

- Java 21, Spring Boot 3.5 (Spring MVC + 가상 스레드)
- MySQL + Spring Data JPA + Flyway
- Redis (Spring Data Redis / Lettuce)

## 실행

환경 변수는 Nest 버전과 같은 이름을 씁니다.

| 변수 | 기본값 |
|---|---|
| `DATABASE_HOST` / `DATABASE_PORT` / `DATABASE_SCHEMA` | `localhost` / `3306` / `real_ticket` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `root` / `root` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / (없음) |
| `FRONT_URL` | `http://localhost:3000` |
| `PORT` | `8080` |
| `ADMIN_SIGNUP_ENABLED` | dev: `true`, prod: `false` |
| `IN_BOOKING_DEFAULT_MAX_SIZE` | dev: `2`, prod: `100` (좌석 선택 화면 최대 인원) |

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

테스트는 Testcontainers로 MySQL과 Redis를 띄우므로 Docker가 필요합니다.

```bash
./gradlew test
```

API 문서: `http://localhost:8080/api-docs`

## 구조

```
com.realticket
├── global
│   ├── auth        세션 인증 (@RequireStatus, @CurrentUser, SessionRepository)
│   ├── exception   ApiException, Nest 형식 에러 응답
│   ├── config      CORS, OpenAPI, BCrypt, 스케줄링
│   └── logging     요청 로그
└── domain          user / place / program / event / reservation / booking
```

## 예매(booking) 구조

```
LOGIN ──입장 요청──▶ ENTERING ──좌석 SSE 연결──▶ SELECTING_SEAT ──예매 확정/SSE 끊김──▶ LOGIN
           └──────▶ WAITING ──빈자리 생김──▶ ENTERING
```

- Redis 키 구조는 `BookingKeys`, 원자적으로 처리해야 하는 동작은 `src/main/resources/lua/booking/*.lua`에 있습니다.
- 좌석 SSE: 바뀐 이벤트만 0.5초마다 보내고, 30초마다 전체 상태, 5초마다 heartbeat를 보냅니다. heartbeat 전송이 실패하면 연결이 끊긴 것으로 보고 선점 좌석을 풀어 줍니다.
- 예매 오픈/마감, 입장 세션 정리는 서버 메모리의 스케줄러로 동작합니다. **서버 한 대**를 전제로 합니다. 여러 대로 늘리려면 스케줄러에 분산 락(ShedLock 등)이 필요하고, 좌석 변경 알림도 Redis Pub/Sub 등으로 서버 간에 공유해야 합니다.

## Nest 버전과 달라진 점

- **세션 저장 형식**: `user:{sid}`를 JSON 문자열 대신 Redis Hash로 저장합니다. 필드 단위로 원자적으로 갱신하므로 동시 요청 간 덮어쓰기가 없습니다. 만료된 세션을 갱신 과정에서 TTL 없이 다시 만들던 문제도 없습니다.
- **테이블 이름**: `users`, `places`, `sections`, `programs`, `events`, `reservations`, `reserved_seats` (snake_case 복수형). 스키마는 Flyway(`src/main/resources/db/migration`)로 관리하고, JPA는 `ddl-auto=validate`로 검증만 합니다.
- **MOCK_MODE 제거**: `/mock/**` API는 옮기지 않습니다.
- **날짜 형식**: DB에는 한국 시간으로 저장하고, API에서는 UTC ISO-8601(`2024-12-01T06:00:00Z`)로 주고받습니다. Nest 버전과 같은 방식입니다.
- **경로 변수 숫자 파싱 실패**: Nest는 일부 API에서 406을 돌려줬지만 모두 400으로 통일했습니다. 프런트엔드는 406을 따로 처리하지 않습니다.
- **에러 처리 정리**: Nest 버전은 컨트롤러의 try/catch 조건 오류(`error instanceof A || B`)로 의도와 다른 상태 코드가 나가거나, 없는 장소 조회가 500이 되는 경우가 있었습니다. 이제 의도한 400/404/409가 나갑니다.
- **관리자 회원가입 제한**: `POST /user/signup/admin`은 인증 없이 누구나 관리자를 만들 수 있었습니다. 이제 `ADMIN_SIGNUP_ENABLED=true`일 때만 동작하고, 꺼져 있으면 404를 돌려줍니다. dev 프로필은 켜져 있고 prod 프로필은 꺼져 있습니다. 운영에서 관리자가 필요하면 잠시 켜고 계정을 만든 뒤 다시 끕니다.
- **로그인 실패 시 기존 세션 유지**: Nest 버전은 비밀번호가 틀려도 그 계정의 기존 세션을 먼저 끊었습니다. 이제 로그인에 성공했을 때만 기존 세션을 끊습니다.
- **로그아웃 시 쿠키 만료**: `SID` 쿠키도 함께 만료시킵니다.
- **게스트 정리**: soft delete 된 예약까지 함께 지웁니다 (Nest 버전은 외래 키 때문에 실패할 수 있었습니다). `DELETE /user/guest`는 삭제 건수를 돌려줍니다.
- **예매 동시성**: 인원 확인과 상태 변경 사이에 다른 요청이 끼어들 수 있던 동작을 Lua로 원자적으로 처리합니다. 정원 초과 입장, 설정 인원보다 많은 좌석 선점, 같은 예매 두 번 저장을 테스트로 확인합니다.
- **예매 버그 수정**:
  - 오픈 시각 전인 이벤트도 열리던 문제 (`validateOpeningEvent` 반환값 무시)
  - 한 이벤트를 마감하면 다른 이벤트의 입장 대기자까지 지워지던 문제 (`entering:*` 삭제)
  - 마감 후에도 좌석/대기열 브로드캐스트 interval이 계속 돌던 문제
  - `NODE_ENV` 조건이 항상 참이라 개발 환경 설정값이 쓰이지 않던 문제 → 프로필별 설정으로 분리
  - 서버 재시작 후 이미 열린 이벤트의 SSE 구독이 실패하던 문제 (구독 채널을 필요할 때 만듦)
  - 예매 확정 후 좌석을 더 선점하고 나가면 그 좌석이 풀리지 않던 문제 (확정 후 좌석 변경 차단)
  - 좌석 SSE에 다시 연결하면 기존 선점 정보가 초기화되던 문제
- **대기열 공정성**: 대기자가 있으면 새로 온 사용자는 빈자리가 있어도 대기열 뒤로 갑니다. 대기 중 다시 요청해도 번호가 바뀌지 않습니다.
- **Redis `KEYS` 미사용**: 모든 키를 이벤트 단위로 나눠 마감 시 정확한 키만 지웁니다.
- **`GET /event/:id`의 `name`**: Nest 버전은 비어 있었지만, 이제 프로그램 이름을 내려줍니다 (프런트엔드가 화면에 표시하는 값).

## 전환 진행 상황

- [x] 뼈대: 빌드, 설정, 세션 인증, 에러 응답 형식, 엔티티, Flyway, 테스트 기반
- [x] place / program / event
- [x] user (회원가입, 로그인, 게스트, 로그아웃, 게스트 정리 스케줄러)
- [x] reservation
- [x] booking (대기열, 입장, 좌석 선점, SSE, 예매 오픈/마감 스케줄)
- [ ] 프런트 연동 검증, `back/` 삭제, Docker/CI 교체
