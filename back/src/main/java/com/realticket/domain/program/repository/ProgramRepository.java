package com.realticket.domain.program.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realticket.domain.program.entity.Program;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    @Query("select p from Program p join fetch p.place order by p.id")
    List<Program> findAllWithPlace();

    @Query("select p from Program p join fetch p.place where p.id = :id")
    Optional<Program> findWithPlaceById(@Param("id") Long id);
}
