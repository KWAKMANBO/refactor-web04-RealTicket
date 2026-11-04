package com.realticket.domain.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.realticket.domain.event.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {
}
