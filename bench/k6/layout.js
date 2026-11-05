// [비교 1] 좌석 배치 조회: 반정규화(운영) vs 좌석 1개=1행
//   k6 run -e PLACE_ID=1 -e TYPE=denormalized k6/layout.js
//   k6 run -e PLACE_ID=1 -e TYPE=normalized   k6/layout.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PLACE_ID = __ENV.PLACE_ID || '1';
const TYPE = __ENV.TYPE || 'denormalized';

export const options = {
  scenarios: {
    layout: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '30s',
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  // 워밍업 요청을 뺀 측정 요청만 따로 집계되도록 지정 (조건 자체는 항상 통과)
  thresholds: {
    'http_req_duration{name:layout}': ['max>=0'],
    'http_reqs{name:layout}': ['count>=0'],
  },
  tags: { type: TYPE },
};

export function setup() {
  // 워밍업: JIT, 커넥션 풀, DB 버퍼를 데운다 (측정에 포함하지 않음)
  for (let i = 0; i < 200; i++) {
    http.get(`${BASE_URL}/bench/layout/${PLACE_ID}?type=${TYPE}`, { tags: { name: 'warmup' } });
  }
}

export default function () {
  const res = http.get(`${BASE_URL}/bench/layout/${PLACE_ID}?type=${TYPE}`, { tags: { name: 'layout' } });
  check(res, { 'status 200': (r) => r.status === 200 });
}
