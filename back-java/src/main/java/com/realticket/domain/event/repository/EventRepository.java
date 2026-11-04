package com.realticket.domain.event.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realticket.domain.event.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("select e from Event e join fetch e.program join fetch e.place where e.id = :id")
    Optional<Event> findWithProgramAndPlaceById(@Param("id") Long id);

    /** 예약 마감 전인 이벤트 (마감 시각 >= now), 공연 시작 순 */
    @Query("""
            select e from Event e
            where e.program.id = :programId and e.reservationCloseDate >= :now
            order by e.runningDate
            """)
    List<Event> findOpenEventsByProgramId(@Param("programId") Long programId, @Param("now") LocalDateTime now);

    boolean existsByProgramId(Long programId);
}
