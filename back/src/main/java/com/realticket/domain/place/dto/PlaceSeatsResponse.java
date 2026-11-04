package com.realticket.domain.place.dto;

import java.util.List;

import com.realticket.domain.place.entity.Place;
import com.realticket.domain.place.entity.Section;

public record PlaceSeatsResponse(Long id, Layout layout) {

    public static PlaceSeatsResponse of(Place place, List<Section> orderedSections) {
        return new PlaceSeatsResponse(place.getId(), new Layout(
                place.getOverviewSvg(),
                orderedSections.stream().map(SectionResponse::from).toList(),
                place.getOverviewWidth(),
                place.getOverviewHeight(),
                place.getOverviewPoints()));
    }

    public record Layout(String overview, List<SectionResponse> sections, int overviewWidth, int overviewHeight,
            String overviewPoints) {
    }

    public record SectionResponse(Long id, String name, int colLen, List<Boolean> seats) {

        static SectionResponse from(Section section) {
            return new SectionResponse(section.getId(), section.getName(), section.getColLen(), section.getSeats());
        }
    }
}
