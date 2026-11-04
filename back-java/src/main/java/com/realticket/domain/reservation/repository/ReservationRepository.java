package com.realticket.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realticket.domain.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** 아직 공연 전인 예매 내역 (좌석은 batch fetch 로 함께 읽는다) */
    @Query("""
            select r from Reservation r
            join fetch r.event e
            join fetch r.program
            join fetch e.place
            where r.user.id = :userId and e.runningDate >= :now
            order by e.runningDate, r.id
            """)
    List<Reservation> findUpcomingByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("select r from Reservation r join fetch r.event where r.id = :id and r.user.id = :userId")
    Optional<Reservation> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
