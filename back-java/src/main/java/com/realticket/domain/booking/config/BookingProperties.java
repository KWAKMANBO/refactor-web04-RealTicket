package com.realticket.domain.booking.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 예매 관련 설정. 기본값은 application.yml, 개발용 값은 application-dev.yml 에 있다.
 *
 * @param inBookingDefaultMaxSize  좌석 선택 화면(입장 중 + 좌석 선택 중) 최대 인원 기본값
 * @param enteringSessionExpiry    입장 허가 후 좌석 화면에 들어오지 않으면 만료되는 시간
 * @param enteringGcInterval       만료된 입장 세션 정리 주기
 * @param seatsBroadcastInterval   좌석 변경 여부 확인(브로드캐스트) 주기
 * @param sseMaximumInterval       변경이 없어도 이 간격마다 전체 상태를 다시 보냄
 * @param sseHeartbeatInterval     끊긴 연결을 빨리 감지하기 위한 SSE 주석(heartbeat) 전송 주기
 * @param seatsSseRetry            좌석 SSE 재연결 대기 시간 (SSE retry 필드)
 * @param waitingBroadcastInterval 대기열 현황 전송 주기
 * @param waitingThroughputRate    예상 대기 시간 계산용 처리율 (프런트엔드가 사용)
 * @param maxBookingAmount         한 번에 예매할 수 있는 최대 좌석 수
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        int inBookingDefaultMaxSize,
        Duration enteringSessionExpiry,
        Duration enteringGcInterval,
        Duration seatsBroadcastInterval,
        Duration sseMaximumInterval,
        Duration sseHeartbeatInterval,
        Duration seatsSseRetry,
        Duration waitingBroadcastInterval,
        int waitingThroughputRate,
        int maxBookingAmount) {
}
