package com.planmate.auth.local;

import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.entity.UserEntity;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@Order(10)
@ConditionalOnProperty(
        name = "app.local-test-users.trip-membership.enabled",
        havingValue = "true"
)
public class LocalTestTripMembershipInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalTestTripMembershipInitializer.class);

    private final LocalCredentialRepository localCredentialRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final LocalTestTripMembershipProperties properties;

    public LocalTestTripMembershipInitializer(
            LocalCredentialRepository localCredentialRepository,
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            LocalTestTripMembershipProperties properties
    ) {
        this.localCredentialRepository = localCredentialRepository;
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Configuration configuration = validateConfiguration();
        TripEntity trip = tripRepository.findById(configuration.tripId())
                .orElseThrow(() -> new IllegalStateException(
                        "Local test membership trip not found: " + configuration.tripId()
                ));
        Instant now = Instant.now();

        for (String loginId : configuration.memberLoginIds()) {
            LocalCredentialEntity credential = localCredentialRepository.findByLoginId(loginId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Local test membership account not found: " + loginId
                    ));
            UserEntity user = credential.getUser();
            if (tripMemberRepository.existsByTrip_IdAndUser_Id(trip.getId(), user.getId())) {
                log.info("Local test trip membership already exists: tripId={}, loginId={}", trip.getId(), loginId);
                continue;
            }

            tripMemberRepository.save(TripMemberEntity.member(trip, user, now));
            log.info("Local test trip member added: tripId={}, loginId={}", trip.getId(), loginId);
        }
    }

    private Configuration validateConfiguration() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Local test trip membership initializer was loaded while disabled");
        }
        Long tripId = properties.getTripId();
        if (tripId == null || tripId <= 0) {
            throw new IllegalStateException("Local test trip membership tripId must be positive");
        }

        Set<String> loginIds = new LinkedHashSet<>();
        for (String configuredLoginId : properties.getMemberLoginIds()) {
            if (configuredLoginId == null || configuredLoginId.isBlank()) {
                throw new IllegalStateException("Local test trip membership loginId must not be blank");
            }
            String loginId = configuredLoginId.trim();
            if (!loginIds.add(loginId)) {
                throw new IllegalStateException("Duplicate local test trip membership loginId: " + loginId);
            }
        }
        if (loginIds.isEmpty()) {
            throw new IllegalStateException("At least one local test trip member must be configured");
        }
        return new Configuration(tripId, loginIds);
    }

    private record Configuration(Long tripId, Set<String> memberLoginIds) {
    }
}
