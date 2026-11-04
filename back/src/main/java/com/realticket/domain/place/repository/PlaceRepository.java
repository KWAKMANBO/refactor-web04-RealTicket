package com.realticket.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.realticket.domain.place.entity.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
