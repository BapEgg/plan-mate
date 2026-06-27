package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDayEntity, Long> {
}
