package com.realticket.domain.place.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.realticket.domain.place.dto.PlaceCreateRequest;
import com.realticket.domain.place.dto.PlaceSeatsResponse;
import com.realticket.domain.place.dto.SectionCreateRequest;
import com.realticket.domain.place.service.PlaceService;
import com.realticket.global.auth.RequireStatus;
import com.realticket.global.auth.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@Tag(name = "Place")
@RestController
@RequestMapping("/place")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @Operation(summary = "장소의 좌석 정보 조회")
    @GetMapping("/seat/{placeId}")
    public PlaceSeatsResponse getSeats(@PathVariable Long placeId) {
        return placeService.getSeats(placeId);
    }

    @Operation(summary = "장소 추가 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createPlace(@Valid @RequestBody PlaceCreateRequest request) {
        placeService.createPlace(request);
    }

    @Operation(summary = "장소 삭제 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @DeleteMapping("/{placeId}")
    public void deletePlace(@PathVariable Long placeId) {
        placeService.deletePlace(placeId);
    }

    @Operation(summary = "구역 추가 [관리자]", description = "같은 장소의 구역들을 order 순서대로 추가한다.")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping("/section")
    @ResponseStatus(HttpStatus.CREATED)
    public void createSections(@RequestBody @NotEmpty List<@Valid SectionCreateRequest> requests) {
        placeService.createSections(requests);
    }
}
