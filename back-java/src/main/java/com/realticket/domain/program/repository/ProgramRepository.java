package com.realticket.domain.program.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.realticket.domain.program.entity.Program;

public interface ProgramRepository extends JpaRepository<Program, Long> {
}
