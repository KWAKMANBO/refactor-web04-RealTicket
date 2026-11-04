package com.realticket.domain.booking.service;

import com.realticket.domain.booking.repository.BookingKeys;

/**
 * 좌석 위치.
 *
 * @param sectionIndex 장소의 구역 순서 (0부터)
 * @param seatIndex    구역 안의 칸 번호 (0부터, 행 우선)
 */
public record Seat(int sectionIndex, int seatIndex) {

    public String member() {
        return BookingKeys.seatMember(sectionIndex, seatIndex);
    }

    public static Seat fromMember(String member) {
        int sep = member.indexOf(':');
        return new Seat(Integer.parseInt(member.substring(0, sep)), Integer.parseInt(member.substring(sep + 1)));
    }
}
