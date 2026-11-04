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
└── domain          user / place / program / event / reservation (/ booking 예정)
```

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
- **`GET /event/:id`의 `name`**: Nest 버전은 비어 있었지만, 이제 프로그램 이름을 내려줍니다 (프런트엔드가 화면에 표시하는 값).

## 전환 진행 상황

- [x] 뼈대: 빌드, 설정, 세션 인증, 에러 응답 형식, 엔티티, Flyway, 테스트 기반
- [x] place / program / event
- [x] user (회원가입, 로그인, 게스트, 로그아웃, 게스트 정리 스케줄러)
- [ ] reservation
- [ ] booking (대기열, 입장, 좌석 선점, SSE, 예매 오픈/마감 스케줄)
- [ ] 프런트 연동 검증, `back/` 삭제, Docker/CI 교체
