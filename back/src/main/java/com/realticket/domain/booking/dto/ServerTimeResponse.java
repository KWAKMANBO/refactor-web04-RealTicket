package com.realticket.domain.booking.dto;

/** @param now 서버 현재 시각 (epoch ms) */
public record ServerTimeResponse(long now) {
}
