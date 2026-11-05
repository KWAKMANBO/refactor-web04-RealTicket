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
OUT="results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

echo "▶ 측정 환경"
curl -s "$BASE_URL/bench/info" | tee "$OUT/info.json"; echo

echo "▶ 데이터 생성: 구역 $SECTIONS개 × 좌석 $SEATS_PER_SECTION개"
SEED=$(curl -s -XPOST "$BASE_URL/bench/seed?sections=$SECTIONS&seatsPerSection=$SEATS_PER_SECTION&colLen=$COL_LEN")
echo "$SEED" | tee "$OUT/seed.json"; echo
PLACE_ID=$(echo "$SEED" | sed -E 's/.*"placeId":([0-9]+).*/\1/')
EVENT_ID=$(echo "$SEED" | sed -E 's/.*"eventId":([0-9]+).*/\1/')
TOTAL_SEATS=$(echo "$SEED" | sed -E 's/.*"totalSeats":([0-9]+).*/\1/')

for TYPE in denormalized normalized; do
  echo "▶ [비교 1] 좌석 배치 조회: $TYPE"
  k6 run --quiet -e BASE_URL="$BASE_URL" -e PLACE_ID="$PLACE_ID" -e TYPE="$TYPE" -e VUS="$VUS" -e DURATION="$DURATION" \
    --summary-export "$OUT/layout-$TYPE.json" k6/layout.js | tee "$OUT/layout-$TYPE.txt"
done

for STRATEGY in db-naive db-lock db-conditional redis-naive redis-lua; do
  echo "▶ [비교 2-1] 같은 좌석 동시 선점: $STRATEGY"
  k6 run --quiet -e BASE_URL="$BASE_URL" -e EVENT_ID="$EVENT_ID" -e STRATEGY="$STRATEGY" -e VUS="$RACE_VUS" \
    -e ROUNDS="$ROUNDS" -e SEATS_PER_SECTION="$SEATS_PER_SECTION" \
    --summary-export "$OUT/race-$STRATEGY.json" k6/race.js 2>&1 | tee "$OUT/race-$STRATEGY.txt"
done

for STRATEGY in db-lock db-conditional redis-lua; do
  echo "▶ [비교 2-2] 좌석 선점 처리량: $STRATEGY"
  k6 run --quiet -e BASE_URL="$BASE_URL" -e EVENT_ID="$EVENT_ID" -e STRATEGY="$STRATEGY" -e VUS="$VUS" \
    -e DURATION="$DURATION" -e TOTAL_SEATS="$TOTAL_SEATS" -e SEATS_PER_SECTION="$SEATS_PER_SECTION" \
    --summary-export "$OUT/throughput-$STRATEGY.json" k6/throughput.js 2>&1 | tee "$OUT/throughput-$STRATEGY.txt"
done

echo "▶ 요약"
python3 summarize.py "$OUT" | tee "$OUT/summary.md"
