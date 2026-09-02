package com.planmate.itinerary.route;

import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteProviderQuotaService {

    private final RouteProviderDailyUsageRepository usageRepository;

    public RouteProviderQuotaService(RouteProviderDailyUsageRepository usageRepository) {
        this.usageRepository = usageRepository;
    }

    /**
     * 외부 provider 호출 시도는 성공 여부와 무관하게 소모된다. 호출자의 이후 트랜잭션이
     * 실패하더라도 사용량이 되돌아가지 않도록 독립 트랜잭션에서 먼저 예약한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reserve(String provider, String operation, LocalDate usageDate, int dailyLimit) {
        return usageRepository.reserveCall(provider, operation, usageDate, dailyLimit)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.ROUTE_QUOTA_EXCEEDED));
    }
}
