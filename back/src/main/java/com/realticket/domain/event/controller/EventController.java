package com.realticket.domain.event.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.realticket.domain.event.dto.EventCreateRequest;
import com.realticket.domain.event.dto.EventDetailResponse;
import com.realticket.domain.event.service.EventService;
import com.realticket.global.auth.RequireStatus;
import com.realticket.global.auth.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Event")
@RestController
@RequestMapping("/event")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "이벤트(회차) 상세 조회")
    @RequireStatus
    @GetMapping("/{eventId}")
    public EventDetailResponse findDetail(@PathVariable Long eventId) {
        return eventService.findDetail(eventId);
    }

    @Operation(summary = "이벤트 추가 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody EventCreateRequest request) {
        eventService.create(request);
    }

    @Operation(summary = "이벤트 삭제 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @DeleteMapping("/{eventId}")
    public void delete(@PathVariable Long eventId) {
        eventService.delete(eventId);
    }
}
