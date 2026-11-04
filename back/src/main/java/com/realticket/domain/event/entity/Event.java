package com.realticket.domain.event.entity;

import java.time.LocalDateTime;

import com.realticket.domain.place.entity.Place;
import com.realticket.domain.program.entity.Program;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "running_date", nullable = false)
    private LocalDateTime runningDate;

    @Column(name = "reservation_open_date", nullable = false)
    private LocalDateTime reservationOpenDate;

    @Column(name = "reservation_close_date", nullable = false)
    private LocalDateTime reservationCloseDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Builder
    private Event(LocalDateTime runningDate, LocalDateTime reservationOpenDate,
            LocalDateTime reservationCloseDate, Program program, Place place) {
        this.runningDate = runningDate;
        this.reservationOpenDate = reservationOpenDate;
        this.reservationCloseDate = reservationCloseDate;
        this.program = program;
        this.place = place;
    }
}
