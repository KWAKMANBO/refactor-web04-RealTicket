package com.realticket.domain.user.dto;

public record GuestCleanupResponse(int deletedUsers, int deletedReservations, int deletedReservedSeats) {
}
