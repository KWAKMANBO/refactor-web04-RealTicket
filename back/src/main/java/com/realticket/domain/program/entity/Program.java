package com.realticket.domain.program.entity;

import com.realticket.domain.place.entity.Place;

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
@Table(name = "programs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "profile_url", nullable = false)
    private String profileUrl;

    @Column(name = "running_time", nullable = false)
    private int runningTime;

    @Column(name = "genre", nullable = false)
    private String genre;

    @Column(name = "actors", nullable = false)
    private String actors;

    @Column(name = "price", nullable = false)
    private int price;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Builder
    private Program(String name, String profileUrl, int runningTime, String genre, String actors, int price,
            Place place) {
        this.name = name;
        this.profileUrl = profileUrl;
        this.runningTime = runningTime;
        this.genre = genre;
        this.actors = actors;
        this.price = price;
        this.place = place;
    }
}
