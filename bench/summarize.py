"""results/<시각>/ 의 k6 --summary-export 결과를 표(Markdown)로 정리한다.
사용법: python3 summarize.py results/20261105-120000
"""
import json
import sys
from pathlib import Path

out = Path(sys.argv[1])


def load(name):
    path = out / name
    return json.loads(path.read_text()) if path.exists() else None


def trend(data, metric):
    m = data["metrics"].get(metric, {})
    return m.get("p(50)"), m.get("p(95)"), m.get("p(99)")


def rate(data, metric):
    return data["metrics"].get(metric, {}).get("rate")


def fmt(v, digits=2):
    return "-" if v is None else f"{v:.{digits}f}"


info = load("info.json") if (out / "info.json").exists() else None
seed = load("seed.json")
print(f"# 측정 결과 ({out.name})\n")
if info:
    print(f"- 서버: CPU {info.get('availableProcessors')}개, 힙 {info.get('maxHeapMb')}MB, "
          f"Java {info.get('javaVersion')}, DB 커넥션 풀 {info.get('dbPoolSize')}")
if seed:
    print(f"- 데이터: 구역 {seed['sections']}개 × 좌석 {seed['seatsPerSection']}개 = {seed['totalSeats']}석\n")

print("## [비교 1] 좌석 배치 조회\n")
print("| 구조 | 처리량 (req/s) | p50 (ms) | p95 (ms) | p99 (ms) |")
print("|---|---|---|---|---|")
for t, label in [("denormalized", "반정규화 (구역별 배열)"), ("normalized", "좌석 1개 = 1행")]:
    d = load(f"layout-{t}.json")
    if d:
        p50, p95, p99 = trend(d, "http_req_duration{name:layout}")
        print(f"| {label} | {fmt(rate(d, 'http_reqs{name:layout}'), 1)} | {fmt(p50)} | {fmt(p95)} | {fmt(p99)} |")

params = load("params.json") or {}
print("\n## [비교 2-1] 같은 좌석 동시 선점\n")
print(f"좌석 {params.get('rounds', '?')}개를 각각 동시 {params.get('raceVus', '?')}명이 노림. "
      f"정상이라면 성공 {params.get('rounds', '?')}건, 중복 예매 0건.\n")
print("| 방식 | 성공 | 중복 예매 |")
print("|---|---|---|")
for s in ["db-naive", "db-lock", "db-conditional", "redis-naive", "redis-lua"]:
    report = load(f"race-{s}-report.json")
    if report and s in report:
        print(f"| {s} | {report[s]['successes']} | {report[s]['doubleBookings']} |")

print("\n## [비교 2-2] 좌석 선점 처리량\n")
print("무작위 좌석을 잡고, 성공하면 바로 놓는 사이클을 반복한 기준. 응답 시간은 선점 요청만 집계.\n")
print("| 방식 | 선점 처리량 (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | 오류 |")
print("|---|---|---|---|---|---|")
for s in ["db-lock", "db-conditional", "redis-lua"]:
    d = load(f"throughput-{s}.json")
    if d:
        p50, p95, p99 = trend(d, "reserve_duration")
        reserves = rate(d, "http_reqs{name:reserve}")
        errors = d["metrics"].get("reserve_error", {}).get("count", 0)
        print(f"| {s} | {fmt(reserves, 1)} | {fmt(p50)} | {fmt(p95)} | {fmt(p99)} | {errors} |")
