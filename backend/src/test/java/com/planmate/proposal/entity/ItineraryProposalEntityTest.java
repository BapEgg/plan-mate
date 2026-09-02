package com.planmate.proposal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ItineraryProposalEntityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    void voteBoundProposalCannotReturnToDirectDecision() {
        ItineraryProposalEntity proposal = proposal();

        proposal.openVote(CREATED_AT.plusSeconds(1));

        assertThat(proposal.getStatus()).isEqualTo(ItineraryProposalStatus.VOTE_OPEN);
        assertThat(proposal.getDecisionMode()).isEqualTo(ProposalDecisionMode.VOTE);
        assertThatThrownBy(() -> proposal.selectDirect(CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appliedProposalKeepsItsDecisionAndRevisionReference() {
        ItineraryProposalEntity proposal = proposal();
        proposal.selectDirect(CREATED_AT.plusSeconds(1));

        proposal.markApplied(900L, CREATED_AT.plusSeconds(2));

        assertThat(proposal.getStatus()).isEqualTo(ItineraryProposalStatus.APPLIED);
        assertThat(proposal.getDecisionMode()).isEqualTo(ProposalDecisionMode.DIRECT);
        assertThat(proposal.getAppliedItineraryId()).isEqualTo(900L);
        assertThatThrownBy(() -> proposal.markCancelled(CREATED_AT.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    private ItineraryProposalEntity proposal() {
        return ItineraryProposalEntity.replaceItem(
                10L, 20L, 1, 30L, 2, 40L,
                "replacement-place", "새 장소", LocalTime.of(14, 30), 90,
                "fingerprint", CREATED_AT
        );
    }
}
