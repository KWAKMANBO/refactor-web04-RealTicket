package com.realticket.domain.booking.repository;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** resources/lua/booking/*.lua 스크립트 */
final class BookingScripts {

    static final RedisScript<Long> TRY_ENTER = longScript("try_enter");
    @SuppressWarnings({"unchecked", "rawtypes"})
    static final RedisScript<List<String>> ADMIT_WAITING = (RedisScript) listScript("admit_waiting");
    @SuppressWarnings({"unchecked", "rawtypes"})
    static final RedisScript<List<String>> EXPIRE_ENTERING = (RedisScript) listScript("expire_entering");
    static final RedisScript<Long> MOVE_TO_IN_BOOKING = longScript("move_to_in_booking");
    static final RedisScript<Long> RESERVE_SEAT = longScript("reserve_seat");
    static final RedisScript<Long> RELEASE_SEAT = longScript("release_seat");
    static final RedisScript<Long> RESET_BOOKING_AMOUNT = longScript("reset_booking_amount");
    static final RedisScript<Long> LEAVE_IN_BOOKING = longScript("leave_in_booking");
    static final RedisScript<Long> MARK_SAVED = longScript("mark_saved");
    static final RedisScript<Long> INIT_SECTION = longScript("init_section");

    private BookingScripts() {
    }

    private static RedisScript<Long> longScript(String name) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/booking/" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }

    private static RedisScript<List> listScript(String name) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/booking/" + name + ".lua"));
        script.setResultType(List.class);
        return script;
    }
}
