package com.planmate.trip.service;

import com.planmate.trip.config.TripScheduleProperties;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.exception.InvalidTripRequestException;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

@Component
public class SchedulePreferenceResolver {

    private final TripScheduleProperties properties;

    public SchedulePreferenceResolver(TripScheduleProperties properties) {
        this.properties = properties;
    }

    public ResolvedSchedulePreference resolve(TripCreateRequest.SchedulePreferenceRequest request) {
        LocalTime startTime = request.dailyStartTime() == null
                ? properties.getDefaultStartTime()
                : request.dailyStartTime();
        LocalTime endTime = request.dailyEndTime() == null
                ? properties.getDefaultEndTime()
                : request.dailyEndTime();

        if (!startTime.isBefore(endTime)) {
            throw new InvalidTripRequestException("하루 일정 시작 시간은 종료 시간보다 빨라야 합니다.");
        }
        return new ResolvedSchedulePreference(startTime, endTime);
    }
}
