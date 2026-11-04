package com.realticket.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.realticket.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    /*
     * 게스트 정리용 native 쿼리.
     * 예약은 soft delete(@SQLRestriction) 대상이라 JPQL 로는 삭제된 행이 보이지 않으므로,
     * 외래 키에 걸리지 않도록 native 로 좌석 → 예약 → 사용자 순서대로 지운다.
     */
    @Modifying
    @Query(value = """
            DELETE rs FROM reserved_seats rs
            JOIN reservations r ON rs.reservation_id = r.id
            JOIN users u ON r.user_id = u.id
            WHERE u.is_guest = TRUE
            """, nativeQuery = true)
    int deleteGuestReservedSeats();

    @Modifying
    @Query(value = """
            DELETE r FROM reservations r
            JOIN users u ON r.user_id = u.id
            WHERE u.is_guest = TRUE
            """, nativeQuery = true)
    int deleteGuestReservations();

    @Modifying
    @Query(value = "DELETE FROM users WHERE is_guest = TRUE", nativeQuery = true)
    int deleteGuests();
}
