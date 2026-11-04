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

## 전환 진행 상황

- [x] 뼈대: 빌드, 설정, 세션 인증, 에러 응답 형식, 엔티티, Flyway, 테스트 기반
- [ ] place / program / event
- [ ] user (회원가입, 로그인, 게스트, 로그아웃)
- [ ] reservation
- [ ] booking (대기열, 입장, 좌석 선점, SSE, 예매 오픈/마감 스케줄)
- [ ] 프런트 연동 검증, `back/` 삭제, Docker/CI 교체
