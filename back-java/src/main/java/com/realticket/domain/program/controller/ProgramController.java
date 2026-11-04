package com.realticket.domain.program.controller;

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

import com.realticket.domain.program.dto.ProgramCreateRequest;
import com.realticket.domain.program.dto.ProgramDetailResponse;
import com.realticket.domain.program.dto.ProgramSummaryResponse;
import com.realticket.domain.program.service.ProgramService;
import com.realticket.global.auth.RequireStatus;
import com.realticket.global.auth.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Program")
@RestController
@RequestMapping("/program")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @Operation(summary = "프로그램 전체 목록 조회")
    @GetMapping
    public List<ProgramSummaryResponse> findAll() {
        return programService.findAll();
    }

    @Operation(summary = "프로그램 상세 조회", description = "예약 마감 전인 회차(events)를 함께 조회한다.")
    @GetMapping("/{programId}")
    public ProgramDetailResponse findDetail(@PathVariable Long programId) {
        return programService.findDetail(programId);
    }

    @Operation(summary = "프로그램 추가 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody ProgramCreateRequest request) {
        programService.create(request);
    }

    @Operation(summary = "프로그램 삭제 [관리자]")
    @RequireStatus(UserStatus.ADMIN)
    @DeleteMapping("/{programId}")
    public void delete(@PathVariable Long programId) {
        programService.delete(programId);
    }
}
