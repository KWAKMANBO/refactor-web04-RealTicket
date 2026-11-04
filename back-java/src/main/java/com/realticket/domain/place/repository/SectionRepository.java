package com.realticket.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.realticket.domain.place.entity.Section;

public interface SectionRepository extends JpaRepository<Section, Long> {
}
