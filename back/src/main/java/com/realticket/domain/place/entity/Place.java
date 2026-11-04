package com.realticket.domain.place.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "overview_svg")
    private String overviewSvg;

    @Column(name = "overview_height", nullable = false)
    private int overviewHeight;

    @Column(name = "overview_width", nullable = false)
    private int overviewWidth;

    /** 구역 순서. index가 sectionIndex, 값이 Section id 이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", nullable = false)
    private List<Long> sectionIds = new ArrayList<>();

    @Column(name = "overview_points", nullable = false, columnDefinition = "TEXT")
    private String overviewPoints;

    @Builder
    private Place(String name, String address, String overviewSvg, int overviewHeight, int overviewWidth,
            String overviewPoints) {
        this.name = name;
        this.address = address;
        this.overviewSvg = overviewSvg;
        this.overviewHeight = overviewHeight;
        this.overviewWidth = overviewWidth;
        this.overviewPoints = overviewPoints;
    }

    /** 구역 순서를 새로 지정한다. */
    public void replaceSectionIds(List<Long> sectionIds) {
        this.sectionIds = new ArrayList<>(sectionIds);
    }
}
