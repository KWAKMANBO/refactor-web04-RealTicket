package com.realticket.domain.program.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realticket.domain.event.repository.EventRepository;
import com.realticket.domain.place.entity.Place;
import com.realticket.domain.place.repository.PlaceRepository;
import com.realticket.domain.program.dto.ProgramCreateRequest;
import com.realticket.domain.program.dto.ProgramDetailResponse;
import com.realticket.domain.program.dto.ProgramSummaryResponse;
import com.realticket.domain.program.entity.Program;
import com.realticket.domain.program.repository.ProgramRepository;
import com.realticket.global.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramService {

    private final ProgramRepository programRepository;
    private final PlaceRepository placeRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    public List<ProgramSummaryResponse> findAll() {
        return programRepository.findAllWithPlace().stream()
                .map(ProgramSummaryResponse::from)
                .toList();
    }

    public ProgramDetailResponse findDetail(Long programId) {
        Program program = programRepository.findWithPlaceById(programId)
                .orElseThrow(() -> ApiException.notFound("해당 프로그램[" + programId + "]는 존재하지 않습니다."));
        return ProgramDetailResponse.of(program,
                eventRepository.findOpenEventsByProgramId(programId, LocalDateTime.now(clock)));
    }

    @Transactional
    public Long create(ProgramCreateRequest request) {
        Place place = placeRepository.findById(request.placeId())
                .orElseThrow(() -> ApiException.notFound("해당 장소[" + request.placeId() + "]는 존재하지 않습니다."));
        Program program = Program.builder()
                .name(request.name())
                .profileUrl(request.profileUrl())
                .runningTime(request.runningTime())
                .genre(request.genre())
                .actors(request.actors())
                .price(request.price())
                .place(place)
                .build();
        return programRepository.save(program).getId();
    }

    @Transactional
    public void delete(Long programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> ApiException.notFound("해당 프로그램[" + programId + "]가 없습니다."));
        if (eventRepository.existsByProgramId(programId)) {
            throw ApiException.conflict("해당 프로그램에 대한 이벤트가 존재합니다.");
        }
        try {
            programRepository.delete(program);
            programRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("해당 프로그램에 대한 예약이 존재합니다.");
        }
    }
}
