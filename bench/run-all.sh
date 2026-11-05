#!/usr/bin/env bash
# 측정 시나리오를 한 번에 실행하고 결과를 results/ 에 저장한다.
# 사용법: ./run-all.sh            (서버가 bench 프로필로 떠 있어야 한다)
# 환경 변수: BASE_URL, SECTIONS, SEATS_PER_SECTION, COL_LEN, VUS, DURATION, RACE_VUS, ROUNDS
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
SECTIONS=${SECTIONS:-10}
SEATS_PER_SECTION=${SEATS_PER_SECTION:-300}
COL_LEN=${COL_LEN:-20}
VUS=${VUS:-50}
DURATION=${DURATION:-30s}
RACE_VUS=${RACE_VUS:-100}
ROUNDS=${ROUNDS:-100}

cd "$(dirname "$0")"

# 실행 전 확인
for cmd in k6 curl python3; do
  command -v "${cmd}" >/dev/null || { echo "✗ ${cmd} 가 설치되어 있지 않습니다."; exit 1; }
done
if ! curl -sf "${BASE_URL}/bench/info" >/dev/null; then
  echo "✗ ${BASE_URL} 에서 bench 프로필 서버를 찾을 수 없습니다."
  echo "  bench 폴더에서 'docker compose up -d --build' 로 띄운 뒤 다시 실행하세요."
  exit 1
fi

OUT="results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "${OUT}"
printf '{"vus":%s,"duration":"%s","raceVus":%s,"rounds":%s}\n' "${VUS}" "${DURATION}" "${RACE_VUS}" "${ROUNDS}" > "${OUT}/params.json"

echo "▶ 측정 환경"
curl -s "${BASE_URL}/bench/info" | tee "${OUT}/info.json"; echo

echo "▶ 데이터 생성: 구역 ${SECTIONS}개 × 좌석 ${SEATS_PER_SECTION}개"
SEED=$(curl -s -XPOST "${BASE_URL}/bench/seed?sections=${SECTIONS}&seatsPerSection=${SEATS_PER_SECTION}&colLen=${COL_LEN}")
echo "${SEED}" | tee "${OUT}/seed.json"; echo
PLACE_ID=$(echo "${SEED}" | sed -E 's/.*"placeId":([0-9]+).*/\1/')
EVENT_ID=$(echo "${SEED}" | sed -E 's/.*"eventId":([0-9]+).*/\1/')
TOTAL_SEATS=$(echo "${SEED}" | sed -E 's/.*"totalSeats":([0-9]+).*/\1/')
if [ "${ROUNDS}" -gt "${TOTAL_SEATS}" ]; then
  echo "✗ ROUNDS(${ROUNDS})는 전체 좌석 수(${TOTAL_SEATS}) 이하여야 합니다."
  exit 1
fi

for TYPE in denormalized normalized; do
  echo "▶ [비교 1] 좌석 배치 조회: ${TYPE}"
  k6 run --quiet -e BASE_URL="${BASE_URL}" -e PLACE_ID="${PLACE_ID}" -e TYPE="${TYPE}" -e VUS="${VUS}" -e DURATION="${DURATION}" \
    --summary-export "${OUT}/layout-${TYPE}.json" k6/layout.js | tee "${OUT}/layout-${TYPE}.txt"
done

for STRATEGY in db-naive db-lock db-conditional redis-naive redis-lua; do
  echo "▶ [비교 2-1] 같은 좌석 동시 선점: ${STRATEGY}"
  k6 run --quiet -e BASE_URL="${BASE_URL}" -e EVENT_ID="${EVENT_ID}" -e STRATEGY="${STRATEGY}" -e VUS="${RACE_VUS}" \
    -e ROUNDS="${ROUNDS}" -e SEATS_PER_SECTION="${SEATS_PER_SECTION}" \
    --summary-export "${OUT}/race-${STRATEGY}.json" k6/race.js 2>&1 | tee "${OUT}/race-${STRATEGY}.txt"
  # 중복 예매 판정은 서버 집계를 기준으로 한다
  curl -s "${BASE_URL}/bench/seat/report" > "${OUT}/race-${STRATEGY}-report.json"
done

for STRATEGY in db-lock db-conditional redis-lua; do
  echo "▶ [비교 2-2] 좌석 선점 처리량: ${STRATEGY}"
  k6 run --quiet -e BASE_URL="${BASE_URL}" -e EVENT_ID="${EVENT_ID}" -e STRATEGY="${STRATEGY}" -e VUS="${VUS}" \
    -e DURATION="${DURATION}" -e TOTAL_SEATS="${TOTAL_SEATS}" -e SEATS_PER_SECTION="${SEATS_PER_SECTION}" \
    --summary-export "${OUT}/throughput-${STRATEGY}.json" k6/throughput.js 2>&1 | tee "${OUT}/throughput-${STRATEGY}.txt"
  curl -s "${BASE_URL}/bench/seat/report" > "${OUT}/throughput-${STRATEGY}-report.json"
done

echo "▶ 요약"
python3 summarize.py "${OUT}" | tee "${OUT}/summary.md"
