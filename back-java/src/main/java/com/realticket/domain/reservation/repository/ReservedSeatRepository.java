package com.realticket.domain.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.realticket.domain.reservation.entity.ReservedSeat;

public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, Long> {
}
