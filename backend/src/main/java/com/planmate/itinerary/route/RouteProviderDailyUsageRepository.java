package com.planmate.itinerary.route;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteProviderDailyUsageRepository extends JpaRepository<RouteProviderDailyUsageEntity, Long> {

    /**
     * spec §10.5: DB의 (provider, operation, KST date) counter를 외부 호출 전에 원자적으로
     * 증가시켜 재시작·동시 instance에서도 일일 한도를 넘기지 않는다. {@code call_count}가 이미
     * 한도에 도달했으면 {@code DO UPDATE}의 WHERE 절이 막혀 행이 반환되지 않는다 — 그 경우
     * {@link Optional#empty()}가 "한도 도달"을 의미한다. INSERT..RETURNING이라 결과 row를
     * 반환하므로 {@code @Modifying}을 붙이지 않는다.
     */
    @Query(value = """
            INSERT INTO route_provider_daily_usage (provider, operation, usage_date, call_count)
            VALUES (:provider, :operation, :usageDate, 1)
            ON CONFLICT (provider, operation, usage_date)
            DO UPDATE SET call_count = route_provider_daily_usage.call_count + 1
            WHERE route_provider_daily_usage.call_count < :dailyLimit
            RETURNING call_count
            """, nativeQuery = true)
    Optional<Integer> reserveCall(
            @Param("provider") String provider,
            @Param("operation") String operation,
            @Param("usageDate") LocalDate usageDate,
            @Param("dailyLimit") int dailyLimit
    );
}
