// [비교 2-1] 같은 좌석 동시 선점: 중복 예매가 생기는가
// VUS 명이 좌석 0번을 동시에 잡고, 다음 반복에서 좌석 1번을 동시에 잡고 ... ROUNDS 번 반복한다.
// 정상이라면 좌석마다 성공은 1명뿐이다. 결과는 서버 집계(doubleBookings)로 확인한다.
//   k6 run -e EVENT_ID=1 -e STRATEGY=redis-lua -e VUS=100 -e ROUNDS=100 k6/race.js
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = Number(__ENV.EVENT_ID || 1);
const STRATEGY = __ENV.STRATEGY || 'redis-lua';
const VUS = Number(__ENV.VUS || 100);
const ROUNDS = Number(__ENV.ROUNDS || 100);
const SEATS_PER_SECTION = Number(__ENV.SEATS_PER_SECTION || 300);

const successes = new Counter('reserve_success');
const conflicts = new Counter('reserve_conflict');
const errors = new Counter('reserve_error'); // 200(성공), 409(이미 예약) 이외의 응답

export const options = {
  scenarios: {
    race: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ROUNDS,
      maxDuration: '5m',
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  tags: { strategy: STRATEGY },
};

const params = { headers: { 'Content-Type': 'application/json' }, tags: { name: 'reserve' } };

export function setup() {
  http.post(`${BASE_URL}/bench/seat/reset?eventId=${EVENT_ID}`);
}

export default function () {
  const seat = __ITER; // 모든 VU 가 같은 반복에서 같은 좌석을 노린다
  const body = JSON.stringify({
    eventId: EVENT_ID,
    sectionIndex: Math.floor(seat / SEATS_PER_SECTION),
    seatIndex: seat % SEATS_PER_SECTION,
    userId: `vu-${__VU}`,
  });
  const res = http.post(`${BASE_URL}/bench/seat/${STRATEGY}/reserve`, body, params);
  if (res.status === 200) successes.add(1);
  else if (res.status === 409) conflicts.add(1);
  else errors.add(1);
}

export function teardown() {
  const report = JSON.parse(http.get(`${BASE_URL}/bench/seat/report`).body)[STRATEGY];
  console.log(`[${STRATEGY}] 좌석 ${ROUNDS}개 × 동시 ${VUS}명 → 성공 ${report.successes}건, `
    + `중복 예매 ${report.doubleBookings}건 (정상: 성공 ${ROUNDS}건, 중복 0건)`);
}
