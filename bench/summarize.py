"""측정 결과 폴더 하나를 Markdown 표로 정리하고, 측정이 유효했는지 검증한다.

사용법: python3 summarize.py results/20261105-120000

이미 측정한 결과 폴더에도 다시 실행할 수 있다 (원본 JSON 만 읽는다).
"""
import json
import sys
from pathlib import Path

ALL_STRATEGIES = ["db-naive", "db-lock", "db-conditional", "redis-naive", "redis-lua"]
SAFE_STRATEGIES = ["db-lock", "db-conditional", "redis-lua"]
LAYOUT_TYPES = [("denormalized", "반정규화 (구역별 배열)"), ("normalized", "좌석 1개 = 1행")]


def _load(out, name):
    path = out / name
    return json.loads(path.read_text()) if path.exists() else None


def _metric(data, name):
    return data["metrics"].get(name, {})


def _count(data, name):
    return int(_metric(data, name).get("count", 0))


def collect(out):
    """결과 폴더를 읽어 숫자만 담은 dict 로 만든다. (aggregate.py 에서도 사용)"""
    out = Path(out)
    result = {
        "name": out.name,
        "info": _load(out, "info.json"),
        "seed": _load(out, "seed.json"),
        "params": _load(out, "params.json") or {},
        "layout": {},
        "race": {},
        "throughput": {},
    }

    for layout_type, _ in LAYOUT_TYPES:
        d = _load(out, f"layout-{layout_type}.json")
        if not d:
            continue
        duration = _metric(d, "http_req_duration{name:layout}")
        result["layout"][layout_type] = {
            "rps": _metric(d, "http_reqs{name:layout}").get("rate"),
            "p50": duration.get("p(50)"),
            "p95": duration.get("p(95)"),
            "p99": duration.get("p(99)"),
            "requests": _count(d, "http_reqs{name:layout}"),
            # 응답 코드 200 확인에 실패한 요청 (워밍업 요청은 확인하지 않는다)
            "errors": int(_metric(d, "checks").get("fails", 0)),
        }

    for strategy in ALL_STRATEGIES:
        d = _load(out, f"race-{strategy}.json")
        report = _load(out, f"race-{strategy}-report.json")
        if not d or not report or strategy not in report:
            continue
        requests = _count(d, "iterations")  # VU 반복 1회 = 선점 요청 1건
        answered = _count(d, "reserve_success") + _count(d, "reserve_conflict")
        result["race"][strategy] = {
            "requests": requests,
            "successes": report[strategy]["successes"],
            "doubleBookings": report[strategy]["doubleBookings"],
            "errors": requests - answered,
        }

    for strategy in SAFE_STRATEGIES:
        d = _load(out, f"throughput-{strategy}.json")
        if not d:
            continue
        report = _load(out, f"throughput-{strategy}-report.json")
        duration = _metric(d, "reserve_duration")
        result["throughput"][strategy] = {
            "rps": _metric(d, "http_reqs{name:reserve}").get("rate"),
            "p50": duration.get("p(50)"),
            "p95": duration.get("p(95)"),
            "p99": duration.get("p(99)"),
            "requests": _count(d, "http_reqs{name:reserve}"),
            "errors": _count(d, "reserve_error"),
            # 이전 버전 결과에는 서버 집계가 없다
            "doubleBookings": report[strategy]["doubleBookings"] if report and strategy in report else None,
        }
    return result


def validate(result):
    """측정이 유효한지 확인한다. (항목, 통과 여부, 설명) 목록을 돌려준다."""
    checks = []
    rounds = result["params"].get("rounds")

    for layout_type, label in LAYOUT_TYPES:
        r = result["layout"].get(layout_type)
        if r:
            checks.append((f"비교 1 {label}: 오류 없음", r["errors"] == 0,
                           f"요청 {r['requests']:,}건 중 오류 {r['errors']}건"))

    for strategy, r in result["race"].items():
        checks.append((f"비교 2-1 {strategy}: 오류 없음", r["errors"] == 0,
                       f"요청 {r['requests']:,}건 중 오류 {r['errors']}건"))
        if strategy in SAFE_STRATEGIES:
            ok = r["doubleBookings"] == 0 and (rounds is None or r["successes"] == rounds)
            checks.append((f"비교 2-1 {strategy}: 중복 예매 0건, 성공 = 좌석 수", ok,
                           f"성공 {r['successes']}건, 중복 예매 {r['doubleBookings']}건"))

    for strategy, r in result["throughput"].items():
        checks.append((f"비교 2-2 {strategy}: 오류 없음", r["errors"] == 0,
                       f"요청 {r['requests']:,}건 중 오류 {r['errors']}건"))
        if r["doubleBookings"] is None:
            checks.append((f"비교 2-2 {strategy}: 중복 예매 0건", None, "이전 버전 결과라 서버 집계 없음"))
        else:
            checks.append((f"비교 2-2 {strategy}: 중복 예매 0건", r["doubleBookings"] == 0,
                           f"중복 예매 {r['doubleBookings']}건"))
    return checks


def fmt(value, digits=2):
    return "-" if value is None else f"{value:,.{digits}f}"


def main():
    if len(sys.argv) != 2:
        print("사용법: python3 summarize.py results/<측정 시각>")
        sys.exit(1)
    result = collect(sys.argv[1])
    info, seed, params = result["info"], result["seed"], result["params"]

    print(f"# 측정 결과 ({result['name']})\n")
    if info:
        print(f"- 서버: CPU {info.get('availableProcessors')}개, 힙 {info.get('maxHeapMb')}MB, "
              f"Java {info.get('javaVersion')}, DB 커넥션 풀 {info.get('dbPoolSize')}")
    if seed:
        print(f"- 데이터: 구역 {seed['sections']}개 × 좌석 {seed['seatsPerSection']}개 = {seed['totalSeats']}석")
    if params:
        print(f"- 부하: 동시 사용자 {params.get('vus')}명 × {params.get('duration')} "
              f"(동시 선점: 좌석 {params.get('rounds')}개 × 각 {params.get('raceVus')}명)")

    print("\n## [비교 1] 좌석 배치 조회\n")
    print("| 구조 | 처리량 (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | 오류 |")
    print("|---|---|---|---|---|---|")
    for layout_type, label in LAYOUT_TYPES:
        r = result["layout"].get(layout_type)
        if r:
            print(f"| {label} | {fmt(r['rps'], 1)} | {fmt(r['p50'])} | {fmt(r['p95'])} | {fmt(r['p99'])} "
                  f"| {r['errors']} |")

    print("\n## [비교 2-1] 같은 좌석 동시 선점\n")
    rounds = params.get("rounds", "?")
    print(f"좌석 {rounds}개를 각각 동시 {params.get('raceVus', '?')}명이 노림. "
          f"정상이라면 성공 {rounds}건, 중복 예매 0건.\n")
    print("| 방식 | 성공 | 중복 예매 | 오류 |")
    print("|---|---|---|---|")
    for strategy in ALL_STRATEGIES:
        r = result["race"].get(strategy)
        if r:
            print(f"| {strategy} | {r['successes']:,} | {r['doubleBookings']:,} | {r['errors']} |")

    print("\n## [비교 2-2] 좌석 선점 처리량\n")
    print("무작위 좌석을 잡고, 성공하면 바로 놓는 사이클을 반복한 기준. 응답 시간은 선점 요청만 집계.\n")
    print("| 방식 | 선점 처리량 (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | 중복 예매 | 오류 |")
    print("|---|---|---|---|---|---|---|")
    for strategy in SAFE_STRATEGIES:
        r = result["throughput"].get(strategy)
        if r:
            doubles = "-" if r["doubleBookings"] is None else r["doubleBookings"]
            print(f"| {strategy} | {fmt(r['rps'], 1)} | {fmt(r['p50'])} | {fmt(r['p95'])} | {fmt(r['p99'])} "
                  f"| {doubles} | {r['errors']} |")

    checks = validate(result)
    print("\n## 검증\n")
    for name, ok, detail in checks:
        mark = "✓" if ok else ("?" if ok is None else "✗")
        print(f"- {mark} {name} ({detail})")
    failed = [name for name, ok, _ in checks if ok is False]
    print()
    if failed:
        print(f"**✗ 확인이 필요한 항목이 {len(failed)}개 있습니다. 이 회차 결과는 사용하지 마세요.**")
    else:
        print("**✓ 모든 검증을 통과했습니다.**")


if __name__ == "__main__":
    main()
