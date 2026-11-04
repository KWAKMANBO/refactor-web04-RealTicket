package com.realticket.domain.program.dto;

import com.realticket.domain.place.entity.Place;

public record PlaceResponse(Long id, String name) {

    public static PlaceResponse from(Place place) {
        return new PlaceResponse(place.getId(), place.getName());
    }
}
