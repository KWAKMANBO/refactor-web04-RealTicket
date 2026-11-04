package com.realticket.domain.reservation.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLRestriction;

import com.realticket.domain.event.entity.Event;

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
@Table(name = "reserved_seats")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservedSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "section_name", nullable = false)
    private String sectionName;

    @Column(name = "section_index", nullable = false)
    private int sectionIndex;

    @Column(name = "col_len", nullable = false)
    private int colLen;

    /** 1부터 시작하는 행 번호 */
    @Column(name = "`row`", nullable = false)
    private int row;

    /** 1부터 시작하는 열 번호 */
    @Column(name = "col", nullable = false)
    private int col;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Builder
    private ReservedSeat(String sectionName, int sectionIndex, int colLen, int row, int col, Event event) {
        this.sectionName = sectionName;
        this.sectionIndex = sectionIndex;
        this.colLen = colLen;
        this.row = row;
        this.col = col;
        this.event = event;
    }

    /** 좌석 선택 화면 비트맵의 index (0부터 시작) */
    public int seatIndex() {
        return (row - 1) * colLen + (col - 1);
    }

    void assignReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
