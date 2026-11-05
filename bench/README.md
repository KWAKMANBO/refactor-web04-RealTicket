# 좌석 저장 구조 · 동시성 처리 성능 측정

포트폴리오용 수치를 직접 측정하기 위한 환경입니다. 운영 코드는 그대로 두고, `bench` 프로필에서만 비교용 테이블과 API가 열립니다.

| 비교 | 질문 | 비교 대상 |
|---|---|---|
| 1. 좌석 배치 조회 | 반정규화로 조회 비용이 얼마나 줄었나? | 구역별 JSON 배열(운영) vs 좌석 1개 = 1행 |
| 2-1. 같은 좌석 동시 선점 | 어떤 방식에서 중복 예매가 생기나? | DB 단순 처리 / DB 비관적 락 / DB 조건부 갱신 / Redis 단순 처리 / Redis + Lua(운영) |
| 2-2. 좌석 선점 처리량 | 안전한 방식들 중 무엇이 빠른가? | DB 비관적 락 / DB 조건부 갱신 / Redis + Lua(운영) |

## 준비물

- Docker
- [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) (`brew install k6`, Windows는 `winget install k6`)
- Python 3 (결과 요약 표 생성)

## 측정 순서

### 1. 실행 환경 띄우기

`bench` 폴더에서 실행합니다. MySQL, Redis, 백엔드(bench 프로필)가 CPU·메모리 제한이 걸린 컨테이너로 뜹니다.

```bash
docker compose up -d --build
```

```bash
curl http://localhost:8080/bench/info
```

`/bench/info`에 서버 CPU 수, 힙 크기, DB 커넥션 풀 크기가 나오면 준비된 것입니다.

### 2. 전체 시나리오 실행

```bash
./run-all.sh
```

데이터 생성(기본 10구역 × 300석 = 3,000석)부터 세 가지 비교까지 순서대로 실행하고, `results/<시각>/summary.md`에 표를 만들어 줍니다. 조건은 환경 변수로 바꿀 수 있습니다.

```bash
SECTIONS=20 SEATS_PER_SECTION=500 VUS=100 DURATION=60s ./run-all.sh
```

| 변수 | 기본값 | 의미 |
|---|---|---|
| `SECTIONS`, `SEATS_PER_SECTION`, `COL_LEN` | 10, 300, 20 | 공연장 크기 |
| `VUS`, `DURATION` | 50, 30s | 비교 1, 2-2의 동시 사용자 수와 시간 |
| `RACE_VUS`, `ROUNDS` | 100, 100 | 비교 2-1: 좌석 하나에 동시에 몰리는 인원과 반복할 좌석 수 |
| `DB_POOL_SIZE` | 10 | DB 커넥션 풀 크기 (`docker compose up` 시 지정) |

### 3. DB가 읽는 행 수 확인 (비교 1 보충)

`sql/layout-explain.sql`의 `@place_id`를 `results/<시각>/seed.json`의 `placeId`로 바꾸고 실행합니다.

```bash
docker compose exec -T mysql mysql -uroot -proot real_ticket < sql/layout-explain.sql
```

`EXPLAIN ANALYZE` 결과에서 두 쿼리가 실제로 읽은 행 수(`rows=`)와 테이블 크기를 비교할 수 있습니다.

### 4. 정리

```bash
docker compose down -v
```

## 시나리오별로 보여 주는 것

- **비교 1**: 같은 공연장을 두 구조로 저장하고, 같은 응답(JSON)을 만드는 API를 각각 호출합니다. 두 응답이 완전히 같다는 것은 테스트(`BenchApiTest`)로 확인합니다. 좌석 1개 = 1행 구조는 인덱스와 JOIN 한 번으로 정상 최적화한 상태로 비교합니다.
- **비교 2-1**: 동시 사용자 전원이 좌석 0번을 노리고, 다음 반복에서 좌석 1번을 노리는 식으로 `ROUNDS`번 경쟁시킵니다. 정상이라면 성공은 정확히 `ROUNDS`건, 중복 예매는 0건입니다. 서버가 "좌석이 이미 점유된 상태에서 또 성공한 경우"를 세어 `doubleBookings`로 알려 줍니다.
  - `redis-naive`는 **Redis로 옮기기만 해서는 동시성 문제가 해결되지 않는다**는 것을 보여 주기 위한 대조군입니다.
- **비교 2-2**: 사용자들이 무작위 좌석을 잡았다가 바로 놓기를 반복하며, 선점 요청의 처리량과 응답 시간을 잽니다. 중복 예매가 생기는 방식은 비교할 의미가 없어 제외합니다.

## 결과 기록 양식

```markdown
### 측정 환경
- PC: (CPU 모델, 코어 수, 메모리)
- 컨테이너 제한: app 2 CPU / 2GB, MySQL 2 CPU / 2GB, Redis 1 CPU / 512MB
- 데이터: 구역 10개 × 좌석 300개 = 3,000석, DB 커넥션 풀 10
- 부하: 동시 사용자 50명, 30초, 3회 반복 후 중앙값

### 1. 좌석 배치 조회
| 구조 | 처리량 (req/s) | p95 (ms) | DB가 읽은 행 수 | 저장 공간 |
|---|---|---|---|---|
| 좌석 1개 = 1행 | | | | |
| 반정규화 | | | | |

### 2-1. 같은 좌석 동시 선점 (좌석 100개 × 동시 100명)
| 방식 | 성공 | 중복 예매 |
|---|---|---|

### 2-2. 좌석 선점 처리량
| 방식 | 처리량 (req/s) | p95 (ms) | p99 (ms) |
|---|---|---|---|
```

## 공정한 측정을 위한 주의점

- **같은 조건에서 3회 이상 반복**하고 중앙값을 쓰세요. 첫 실행은 JIT 컴파일 등으로 느릴 수 있습니다 (비교 1은 시작 전에 워밍업 요청을 보냅니다).
- **평균보다 p95/p99**를 쓰세요. 사용자가 체감하는 느린 구간은 평균에 묻힙니다.
- **측정 중에는 다른 무거운 프로그램을 끄세요.** 노트북이라면 전원을 연결하세요.
- **DB 커넥션 풀 크기는 DB 방식 결과에 큰 영향을 줍니다.** 값을 바꿔 측정했다면 함께 적으세요.
- **측정 범위**: `/bench/seat/*`는 "좌석 확인 + 예약"만 비교하기 위해 로그인 세션·인원 수 확인을 뺐습니다. 운영 API(`POST /booking`)는 여기에 세션 조회가 더해집니다.
- **역사 순서**: 좌석 1개 = 1행 구조와 단순 처리 방식은 비교를 위해 **재현한 것**입니다. 포트폴리오에는 "이전 구조를 재현해 같은 조건에서 비교했다"고 밝히세요.

## 구성

```
bench/
├── docker-compose.yml        MySQL, Redis, 백엔드(bench 프로필) + 자원 제한
├── run-all.sh                전체 측정 실행
├── summarize.py              k6 결과 → Markdown 표
├── k6/
│   ├── layout.js             비교 1
│   ├── race.js               비교 2-1
│   └── throughput.js         비교 2-2
├── sql/layout-explain.sql    비교 1의 DB 실행 계획
└── results/                  측정 결과 (실행 시 생성)

back/src/main/java/com/realticket/bench/        측정용 API (bench 프로필에서만 등록)
back/src/main/resources/db/bench/               측정용 테이블 (bench 프로필에서만 적용)
back/src/main/resources/application-bench.yml   bench 프로필 설정
```
