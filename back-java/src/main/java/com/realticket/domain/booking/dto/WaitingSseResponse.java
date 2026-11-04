package com.realticket.domain.booking.dto;

/**
 * 대기열 SSE 데이터.
 *
 * @param headOrder      대기열 맨 앞 사용자의 대기 번호 (비어 있으면 마지막 번호 + 1)
 * @param totalWaiting   대기 인원
 * @param throughputRate 예상 대기 시간 계산용 처리율
 */
public record WaitingSseResponse(long headOrder, long totalWaiting, int throughputRate) {
}
