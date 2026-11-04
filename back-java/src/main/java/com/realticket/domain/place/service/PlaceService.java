package com.realticket.domain.place.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realticket.domain.place.dto.PlaceCreateRequest;
import com.realticket.domain.place.dto.PlaceSeatsResponse;
import com.realticket.domain.place.dto.SectionCreateRequest;
import com.realticket.domain.place.entity.Place;
import com.realticket.domain.place.entity.Section;
import com.realticket.domain.place.repository.PlaceRepository;
import com.realticket.domain.place.repository.SectionRepository;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final SectionRepository sectionRepository;
    private final ObjectMapper objectMapper;

    public PlaceSeatsResponse getSeats(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> ApiException.badRequest("해당 장소가 존재하지 않습니다."));
        return PlaceSeatsResponse.of(place, findSectionsInOrder(place));
    }

    /** Place.sectionIds 순서(= sectionIndex 순서)대로 구역을 반환한다. */
    public List<Section> findSectionsInOrder(Place place) {
        List<Long> sectionIds = place.getSectionIds();
        Map<Long, Section> byId = sectionRepository.findAllById(sectionIds).stream()
                .collect(Collectors.toMap(Section::getId, Function.identity()));
        return sectionIds.stream()
                .map(id -> {
                    Section section = byId.get(id);
                    if (section == null) {
                        throw ApiException.internal("장소[" + place.getId() + "]의 구역[" + id + "] 정보가 없습니다.");
                    }
                    return section;
                })
                .toList();
    }

    @Transactional
    public Long createPlace(PlaceCreateRequest request) {
        validateJson(request.overviewPoints());
        Place place = Place.builder()
                .name(request.name())
                .address(request.address())
                .overviewSvg(request.overviewSvg())
                .overviewHeight(request.overviewHeight())
                .overviewWidth(request.overviewWidth())
                .overviewPoints(request.overviewPoints())
                .build();
        return placeRepository.save(place).getId();
    }

    /**
     * 장소에 구역들을 추가하고, order 순서대로 구역 순서를 다시 지정한다.
     * 모든 구역은 같은 장소에 속해야 한다.
     */
    @Transactional
    public List<Long> createSections(List<SectionCreateRequest> requests) {
        Long placeId = requests.getFirst().placeId();
        if (requests.stream().anyMatch(request -> !placeId.equals(request.placeId()))) {
            throw ApiException.badRequest("섹션은 모두 동일한 place에 삽입해야 합니다.");
        }
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> ApiException.notFound("해당 장소[" + placeId + "]가 존재하지 않습니다."));

        List<Long> sectionIds = requests.stream()
                .sorted(Comparator.comparing(SectionCreateRequest::order))
                .map(request -> sectionRepository.save(Section.builder()
                        .name(request.name())
                        .colLen(request.colLen())
                        .seats(request.seats())
                        .place(place)
                        .build()))
                .map(Section::getId)
                .toList();

        place.replaceSectionIds(sectionIds);
        return sectionIds;
    }

    @Transactional
    public void deletePlace(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> ApiException.notFound("해당 장소[" + placeId + "]가 존재하지 않습니다."));
        try {
            sectionRepository.deleteAllByPlaceId(placeId);
            placeRepository.delete(place);
            placeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("해당 장소를 사용하는 프로그램 또는 이벤트가 존재합니다.");
        }
    }

    private void validateJson(String json) {
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("overviewPoints는 JSON 문자열이어야 합니다.");
        }
    }
}
