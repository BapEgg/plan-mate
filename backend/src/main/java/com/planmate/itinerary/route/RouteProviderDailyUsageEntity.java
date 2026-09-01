package com.planmate.itinerary.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * JPA 매핑만 담당한다 — 실제 원자적 증가는
 * {@link RouteProviderDailyUsageRepository#reserveCall} native query가 수행한다.
 */
@Entity
@Table(name = "route_provider_daily_usage")
public class RouteProviderDailyUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "operation", nullable = false, length = 40)
    private String operation;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    protected RouteProviderDailyUsageEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getOperation() {
        return operation;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public int getCallCount() {
        return callCount;
    }
}
