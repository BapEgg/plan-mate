package com.planmate.itinerary.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class RouteProviderQuotaServiceIntegrationTest {

    private static final String PROVIDER = "KAKAO_TEST";
    private static final String OPERATION = "ROLLBACK_TEST";
    private static final LocalDate USAGE_DATE = LocalDate.of(2099, 1, 1);

    @Autowired
    private RouteProviderQuotaService quotaService;

    @Autowired
    private RouteProviderDailyUsageRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanUsage() {
        repository.findByProviderAndOperationAndUsageDate(PROVIDER, OPERATION, USAGE_DATE)
                .ifPresent(repository::delete);
    }

    @Test
    void reservationSurvivesRollbackOfCallingTransaction() {
        TransactionTemplate callerTransaction = new TransactionTemplate(transactionManager);
        callerTransaction.executeWithoutResult(status -> {
            quotaService.reserve(PROVIDER, OPERATION, USAGE_DATE, 10);
            status.setRollbackOnly();
        });

        RouteProviderDailyUsageEntity usage = repository
                .findByProviderAndOperationAndUsageDate(PROVIDER, OPERATION, USAGE_DATE)
                .orElseThrow();
        assertThat(usage.getCallCount()).isEqualTo(1);
    }
}
