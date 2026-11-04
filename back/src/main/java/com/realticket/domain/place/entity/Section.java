package com.realticket.domain.place.entity;

import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "sections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "col_len", nullable = false)
    private int colLen;

    /** 좌석 존재 여부. true: 좌석 있음, false: 빈 칸 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seats", nullable = false)
    private List<Boolean> seats;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Builder
    private Section(String name, int colLen, List<Boolean> seats, Place place) {
        this.name = name;
        this.colLen = colLen;
        this.seats = seats;
        this.place = place;
    }
}
