package com.realticket.bench;

import java.util.Arrays;

import com.realticket.global.exception.ApiException;

/**
 * [비교 2] 좌석 선점의 동시성 처리 방식.
 * 모든 방식이 "좌석이 비어 있으면 예약으로 바꾼다"는 같은 일을 한다.
 */
public enum SeatLockStrategy {
    /** DB: 읽고(SELECT) 비어 있으면 쓰기(UPDATE). 사이에 다른 요청이 끼어들 수 있다. */
    DB_NAIVE("db-naive"),
    /** DB: 트랜잭션 안에서 SELECT ... FOR UPDATE 로 행을 잠근 뒤 쓰기 (비관적 락) */
    DB_LOCK("db-lock"),
    /** DB: UPDATE ... WHERE reserved = FALSE 한 번으로 확인과 쓰기를 함께 (조건부 갱신) */
    DB_CONDITIONAL("db-conditional"),
    /** Redis: GETBIT 로 읽고 비어 있으면 SETBIT. Redis 로 옮기기만 해서는 해결되지 않음을 보여 준다. */
    REDIS_NAIVE("redis-naive"),
    /** Redis: Lua 스크립트로 확인과 쓰기를 원자적으로 (운영 방식) */
    REDIS_LUA("redis-lua");

    private final String value;

    SeatLockStrategy(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SeatLockStrategy from(String value) {
        return Arrays.stream(values())
                .filter(strategy -> strategy.value.equals(value))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        "strategy 는 db-naive, db-lock, db-conditional, redis-naive, redis-lua 중 하나입니다."));
    }
}
