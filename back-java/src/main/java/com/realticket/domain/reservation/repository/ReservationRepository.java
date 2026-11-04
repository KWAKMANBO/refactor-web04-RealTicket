package com.realticket.domain.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.realticket.domain.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
