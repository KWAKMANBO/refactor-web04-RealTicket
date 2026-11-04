package com.realticket.domain.event.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realticket.domain.event.dto.EventCreateRequest;
import com.realticket.domain.event.dto.EventDetailResponse;
import com.realticket.domain.event.entity.Event;
import com.realticket.domain.event.repository.EventRepository;
import com.realticket.domain.program.entity.Program;
import com.realticket.domain.program.repository.ProgramRepository;
import com.realticket.global.config.TimeConfig;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final ProgramRepository programRepository;

    public EventDetailResponse findDetail(Long eventId) {
        return eventRepository.findWithProgramAndPlaceById(eventId)
                .map(EventDetailResponse::from)
                .orElseThrow(() -> ApiException.notFound("해당 이벤트[" + eventId + "]는 존재하지 않습니다."));
    }

    @Transactional
    public Long create(EventCreateRequest request) {
        validateDates(request);
        Program program = programRepository.findWithPlaceById(request.programId())
                .orElseThrow(() -> ApiException.notFound("해당 프로그램[" + request.programId() + "]가 없습니다."));

        Event event = Event.builder()
                .runningDate(TimeConfig.toLocalDateTime(request.runningDate()))
                .reservationOpenDate(TimeConfig.toLocalDateTime(request.reservationOpenDate()))
                .reservationCloseDate(TimeConfig.toLocalDateTime(request.reservationCloseDate()))
                .program(program)
                .place(program.getPlace())
                .build();
        return eventRepository.save(event).getId();
    }

    @Transactional
    public void delete(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("해당 이벤트[" + eventId + "]가 없습니다."));
        try {
            eventRepository.delete(event);
            eventRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("해당 이벤트에 대한 예약이 존재합니다.");
        }
    }

    /** 예약 오픈 < 예약 마감 <= 공연 시작 */
    private void validateDates(EventCreateRequest request) {
        boolean valid = request.reservationOpenDate().isBefore(request.reservationCloseDate())
                && !request.reservationCloseDate().isAfter(request.runningDate());
        if (!valid) {
            throw ApiException.badRequest("예약오픈일자 < 예약마감일자 <= 시작날짜 형식이어야 합니다.");
        }
    }
}
