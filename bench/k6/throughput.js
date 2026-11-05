// [비교 2-2] 좌석 선점 처리량: 동시 사용자가 무작위 좌석을 잡았다 놓기를 반복한다.
// 잡는 데 성공하면 바로 놓아서 좌석을 순환시킨다. 측정 지표는 선점 요청(reserve)의 처리량과 응답 시간.
//   k6 run -e EVENT_ID=1 -e STRATEGY=redis-lua -e TOTAL_SEATS=3000 k6/throughput.js
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = Number(__ENV.EVENT_ID || 1);
const STRATEGY = __ENV.STRATEGY || 'redis-lua';
const TOTAL_SEATS = Number(__ENV.TOTAL_SEATS || 3000);
const SEATS_PER_SECTION = Number(__ENV.SEATS_PER_SECTION || 300);

const reserveDuration = new Trend('reserve_duration', true);
const successes = new Counter('reserve_success');
const conflicts = new Counter('reserve_conflict');
const errors = new Counter('reserve_error');

export const options = {
  scenarios: {
    throughput: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || '30s',
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  // 선점 요청만 따로 집계되도록 지정 (조건 자체는 항상 통과)
  thresholds: {
    'http_reqs{name:reserve}': ['count>=0'],
  },
  tags: { strategy: STRATEGY },
};

const params = { headers: { 'Content-Type': 'application/json' } };

export function setup() {
  http.post(`${BASE_URL}/bench/seat/reset?eventId=${EVENT_ID}`);
}

export default function () {
  const seat = Math.floor(Math.random() * TOTAL_SEATS);
  const body = JSON.stringify({
    eventId: EVENT_ID,
    sectionIndex: Math.floor(seat / SEATS_PER_SECTION),
    seatIndex: seat % SEATS_PER_SECTION,
    userId: `vu-${__VU}`,
  });
  const res = http.post(`${BASE_URL}/bench/seat/${STRATEGY}/reserve`, body,
    Object.assign({ tags: { name: 'reserve' } }, params));
  reserveDuration.add(res.timings.duration);
  if (res.status === 200) {
    successes.add(1);
    http.post(`${BASE_URL}/bench/seat/${STRATEGY}/release`, body, Object.assign({ tags: { name: 'release' } }, params));
  } else if (res.status === 409) {
    conflicts.add(1);
  } else {
    errors.add(1);
  }
}

export function teardown() {
  const report = JSON.parse(http.get(`${BASE_URL}/bench/seat/report`).body)[STRATEGY];
  console.log(`[${STRATEGY}] 성공 ${report.successes}, 충돌 ${report.conflicts}, 중복 예매 ${report.doubleBookings}`);
}
