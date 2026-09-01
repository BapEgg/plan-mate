package com.planmate.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.planmate.trip.repository.TripMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripMembershipQueryServiceTest {

    @Mock
    private TripMemberRepository tripMemberRepository;

    private TripMembershipQueryService service;

    @BeforeEach
    void setUp() {
        service = new TripMembershipQueryService(tripMemberRepository);
    }

    @Test
    void isMemberReturnsTrueWhenTripMemberExists() {
        given(tripMemberRepository.existsByTrip_IdAndUser_IdAndStatus(45L, 7L, com.planmate.trip.entity.MembershipStatus.ACTIVE)).willReturn(true);

        boolean result = service.isMember(7L, 45L);

        assertThat(result).isTrue();
        verify(tripMemberRepository).existsByTrip_IdAndUser_IdAndStatus(45L, 7L, com.planmate.trip.entity.MembershipStatus.ACTIVE);
    }

    @Test
    void isMemberReturnsFalseWhenTripMemberDoesNotExist() {
        given(tripMemberRepository.existsByTrip_IdAndUser_IdAndStatus(45L, 7L, com.planmate.trip.entity.MembershipStatus.ACTIVE)).willReturn(false);

        boolean result = service.isMember(7L, 45L);

        assertThat(result).isFalse();
        verify(tripMemberRepository).existsByTrip_IdAndUser_IdAndStatus(45L, 7L, com.planmate.trip.entity.MembershipStatus.ACTIVE);
    }
}
