package com.realticket.domain.program.dto;

import com.realticket.domain.program.entity.Program;

/** 메인 페이지 프로그램 목록 항목 */
public record ProgramSummaryResponse(Long id, String name, String genre, PlaceResponse place, String profileUrl,
        String actors) {

    public static ProgramSummaryResponse from(Program program) {
        return new ProgramSummaryResponse(
                program.getId(),
                program.getName(),
                program.getGenre(),
                PlaceResponse.from(program.getPlace()),
                program.getProfileUrl(),
                program.getActors());
    }
}
