package com.realticket.domain.booking.event;

/** 사용자가 좌석 선택 화면(좌석 SSE)을 떠났다. Nest 의 'seats-sse-close' 이벤트에 대응한다. */
public record SeatsSubscriptionClosedEvent(long eventId, String sid) {
}
