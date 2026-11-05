"""여러 번 측정한 결과를 모아 중앙값(최소~최대)과 배율을 정리한다.

사용법:
  python3 aggregate.py                      # results/ 아래 모든 회차
  python3 aggregate.py results/A results/B  # 지정한 회차만

검증(summarize.py 의 validate)에 실패한 회차는 자동으로 제외한다.
"""
import statistics
import sys
from pathlib import Path

from summarize import ALL_STRATEGIES, LAYOUT_TYPES, SAFE_STRATEGIES, collect, validate


def spread(values, digits=1):
    values = [v for v in values if v is not None]
    if not values:
        return "-"
    median = statistics.median(values)
    if len(values) == 1:
        return f"{median:,.{digits}f}"
    return f"{median:,.{digits}f} ({min(values):,.{digits}f}~{max(values):,.{digits}f})"


def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def main():
    base = Path(__file__).parent / "results"
    dirs = [Path(p) for p in sys.argv[1:]] or sorted(p for p in base.iterdir() if p.is_dir())
    runs, excluded = [], []
    for d in dirs:
        result = collect(d)
        failed = [name for name, ok, _ in validate(result) if ok is False]
        if failed:
            excluded.append((d.name, failed))
        elif result["layout"] or result["race"] or result["throughput"]:
            runs.append(result)

    print(f"# 측정 결과 종합 ({len(runs)}회)\n")
    print("값은 **중앙값 (최소~최대)** 입니다.\n")
    for name, failed in excluded:
        print(f"- 제외한 회차: {name} (검증 실패: {', '.join(failed)})")
    if not runs:
        print("검증을 통과한 회차가 없습니다.")
        return
    if len(runs) < 3:
        print(f"- 참고: 회차가 {len(runs)}번뿐입니다. 3회 이상을 권장합니다.")

    first = runs[0]
    if first["info"]:
        info = first["info"]
        print(f"- 서버: CPU {info.get('availableProcessors')}개, 힙 {info.get('maxHeapMb')}MB, "
              f"DB 커넥션 풀 {info.get('dbPoolSize')}")
    if first["seed"]:
        seed = first["seed"]
        print(f"- 데이터: {seed['totalSeats']:,}석 (구역 {seed['sections']}개 × {seed['seatsPerSection']}석)")
    print(f"- 회차: {', '.join(r['name'] for r in runs)}")

    def values(section, key, field):
        return [r[section][key][field] for r in runs if key in r[section]]

    print("\n## [비교 1] 좌석 배치 조회\n")
    print("| 구조 | 처리량 (req/s) | p50 (ms) | p95 (ms) | p99 (ms) |")
    print("|---|---|---|---|---|")
    for layout_type, label in LAYOUT_TYPES:
        print(f"| {label} | {spread(values('layout', layout_type, 'rps'))} "
              f"| {spread(values('layout', layout_type, 'p50'), 2)} "
              f"| {spread(values('layout', layout_type, 'p95'), 2)} "
              f"| {spread(values('layout', layout_type, 'p99'), 2)} |")

    print("\n## [비교 2-1] 같은 좌석 동시 선점\n")
    print("| 방식 | 성공 | 중복 예매 |")
    print("|---|---|---|")
    for strategy in ALL_STRATEGIES:
        print(f"| {strategy} | {spread(values('race', strategy, 'successes'), 0)} "
              f"| {spread(values('race', strategy, 'doubleBookings'), 0)} |")

    print("\n## [비교 2-2] 좌석 선점 처리량\n")
    print("| 방식 | 선점 처리량 (req/s) | p50 (ms) | p95 (ms) | p99 (ms) |")
    print("|---|---|---|---|---|")
    for strategy in SAFE_STRATEGIES:
        print(f"| {strategy} | {spread(values('throughput', strategy, 'rps'))} "
              f"| {spread(values('throughput', strategy, 'p50'), 2)} "
              f"| {spread(values('throughput', strategy, 'p95'), 2)} "
              f"| {spread(values('throughput', strategy, 'p99'), 2)} |")

    print("\n## 포트폴리오용 비교 (중앙값 기준)\n")
    lines = []

    def ratio(a, b):
        return None if not a or not b else a / b

    def reduction(before, after):
        return None if not before or after is None else (1 - after / before) * 100

    den, nor = "denormalized", "normalized"
    r = ratio(median(values("layout", den, "rps")), median(values("layout", nor, "rps")))
    p = reduction(median(values("layout", nor, "p95")), median(values("layout", den, "p95")))
    if r is not None and p is not None:
        lines.append(f"- 반정규화: 조회 처리량 **{r:.1f}배**, p95 **{p:.0f}% 감소** (좌석 1개 = 1행 대비)")

    for name in ["db-naive", "redis-naive"]:
        doubles = values("race", name, "doubleBookings")
        if doubles:
            lines.append(f"- {name}: 중복 예매 {min(doubles):,}~{max(doubles):,}건 ({len(doubles)}회 모두 발생)"
                         if min(doubles) > 0 else f"- {name}: 중복 예매 {min(doubles):,}~{max(doubles):,}건")
    safe_doubles = [d for s in SAFE_STRATEGIES for d in values("race", s, "doubleBookings")]
    if safe_doubles and max(safe_doubles) == 0:
        lines.append(f"- db-lock, db-conditional, redis-lua: {len(runs)}회 모두 중복 예매 **0건**")

    lua_rps, lua_p95 = median(values("throughput", "redis-lua", "rps")), median(values("throughput", "redis-lua", "p95"))
    for name in ["db-lock", "db-conditional"]:
        r = ratio(lua_rps, median(values("throughput", name, "rps")))
        p = reduction(median(values("throughput", name, "p95")), lua_p95)
        if r is not None and p is not None:
            lines.append(f"- redis-lua: {name} 대비 처리량 **{r:.1f}배**, p95 **{p:.0f}% 감소**")
    print("\n".join(lines) if lines else "(비교할 결과가 부족합니다)")


if __name__ == "__main__":
    main()
