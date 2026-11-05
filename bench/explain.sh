#!/usr/bin/env bash
# [비교 1 보충] 좌석 배치 조회 시 DB 가 실제로 읽는 행 수와 저장 공간을 확인한다.
# 사용법: ./explain.sh               (가장 최근 측정의 공연장)
#        PLACE_ID=3 ./explain.sh    (공연장 지정)
set -euo pipefail
cd "$(dirname "$0")"

BASE_URL=${BASE_URL:-http://localhost:8080}

mysql_exec() {
  docker compose exec -T mysql mysql -r -uroot -proot real_ticket "$@" 2>/dev/null
}

PLACE_ID=${PLACE_ID:-}
if [ -z "${PLACE_ID}" ]; then
  LATEST=$(ls -d results/*/ 2>/dev/null | tail -1 || true)
  if [ -n "${LATEST}" ] && [ -f "${LATEST}seed.json" ]; then
    PLACE_ID=$(sed -E 's/.*"placeId":([0-9]+).*/\1/' "${LATEST}seed.json")
  fi
fi

# DB 를 초기화(docker compose down -v)했다면 측정 데이터가 없으므로 새로 만든다
EXISTS=0
if [ -n "${PLACE_ID}" ]; then
  EXISTS=$(echo "SELECT COUNT(*) FROM places WHERE id = ${PLACE_ID};" | mysql_exec -N || echo 0)
fi
if [ "${EXISTS}" != "1" ]; then
  echo "측정 데이터가 없어 새로 만듭니다 (구역 10개 × 좌석 300개)"
  if ! SEED=$(curl -sf -XPOST "${BASE_URL}/bench/seed?sections=10&seatsPerSection=300"); then
    echo "✗ ${BASE_URL} 서버에 연결할 수 없습니다. 'docker compose up -d' 후 다시 실행하세요."
    exit 1
  fi
  PLACE_ID=$(echo "${SEED}" | sed -E 's/.*"placeId":([0-9]+).*/\1/')
fi

echo "공연장 id: ${PLACE_ID}"
sed "s/SET @place_id = 1;/SET @place_id = ${PLACE_ID};/" sql/layout-explain.sql | mysql_exec
