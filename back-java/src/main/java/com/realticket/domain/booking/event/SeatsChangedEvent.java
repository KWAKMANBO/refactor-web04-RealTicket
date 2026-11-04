package com.realticket.domain.booking.event;

/** 좌석 상태가 바뀌었다. 다음 브로드캐스트 주기에 전체 좌석 상태를 다시 보낸다. */
public record SeatsChangedEvent(long eventId) {
}
