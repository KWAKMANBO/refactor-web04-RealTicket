package com.realticket.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realticket.domain.place.entity.Section;

public interface SectionRepository extends JpaRepository<Section, Long> {

    @Modifying
    @Query("delete from Section s where s.place.id = :placeId")
    int deleteAllByPlaceId(@Param("placeId") Long placeId);
}
