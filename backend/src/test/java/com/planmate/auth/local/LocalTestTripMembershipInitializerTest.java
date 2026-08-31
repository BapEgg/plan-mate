package com.planmate.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.entity.TripMemberRole;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalTestTripMembershipInitializerTest {

    private final LocalCredentialRepository localCredentialRepository = org.mockito.Mockito.mock(LocalCredentialRepository.class);
    private final TripRepository tripRepository = org.mockito.Mockito.mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository = org.mockito.Mockito.mock(TripMemberRepository.class);

    @Test
    void addsConfiguredAccountsAsMembers() throws Exception {
        TripEntity trip = org.mockito.Mockito.mock(TripEntity.class);
        UserEntity local1 = org.mockito.Mockito.mock(UserEntity.class);
        UserEntity local2 = org.mockito.Mockito.mock(UserEntity.class);
        LocalCredentialEntity credential1 = org.mockito.Mockito.mock(LocalCredentialEntity.class);
        LocalCredentialEntity credential2 = org.mockito.Mockito.mock(LocalCredentialEntity.class);
        given(trip.getId()).willReturn(1530L);
        given(local1.getId()).willReturn(2623L);
        given(local2.getId()).willReturn(2624L);
        given(credential1.getUser()).willReturn(local1);
        given(credential2.getUser()).willReturn(local2);
        given(tripRepository.findById(1530L)).willReturn(Optional.of(trip));
        given(localCredentialRepository.findByLoginId("local1")).willReturn(Optional.of(credential1));
        given(localCredentialRepository.findByLoginId("local2")).willReturn(Optional.of(credential2));

        initializer(properties(1530L, "local1", "local2")).run(null);

        ArgumentCaptor<TripMemberEntity> captor = ArgumentCaptor.forClass(TripMemberEntity.class);
        verify(tripMemberRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TripMemberEntity::getRole)
                .containsOnly(TripMemberRole.MEMBER);
        assertThat(captor.getAllValues())
                .extracting(TripMemberEntity::getUser)
                .containsExactly(local1, local2);
    }

    @Test
    void keepsExistingMembershipIdempotently() throws Exception {
        TripEntity trip = org.mockito.Mockito.mock(TripEntity.class);
        UserEntity local1 = org.mockito.Mockito.mock(UserEntity.class);
        LocalCredentialEntity credential = org.mockito.Mockito.mock(LocalCredentialEntity.class);
        given(trip.getId()).willReturn(1530L);
        given(local1.getId()).willReturn(2623L);
        given(credential.getUser()).willReturn(local1);
        given(tripRepository.findById(1530L)).willReturn(Optional.of(trip));
        given(localCredentialRepository.findByLoginId("local1")).willReturn(Optional.of(credential));
        given(tripMemberRepository.existsByTrip_IdAndUser_Id(1530L, 2623L)).willReturn(true);

        initializer(properties(1530L, "local1")).run(null);

        verify(tripMemberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateLoginIdsBeforeWriting() {
        assertThatThrownBy(() -> initializer(properties(1530L, "local1", "local1")).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate local test trip membership loginId");
        verify(tripRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    private LocalTestTripMembershipInitializer initializer(LocalTestTripMembershipProperties properties) {
        return new LocalTestTripMembershipInitializer(
                localCredentialRepository,
                tripRepository,
                tripMemberRepository,
                properties
        );
    }

    private LocalTestTripMembershipProperties properties(Long tripId, String... loginIds) {
        LocalTestTripMembershipProperties properties = new LocalTestTripMembershipProperties();
        properties.setEnabled(true);
        properties.setTripId(tripId);
        properties.setMemberLoginIds(List.of(loginIds));
        return properties;
    }
}
