package com.planmate.vote.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItineraryVoteResponseTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "2, 2",
            "3, 2",
            "4, 2",
            "5, 3",
            "6, 3"
    })
    void calculatesMinimumParticipationFromTheOpenTimeVoterSnapshot(int voters, int expected) {
        assertThat(ItineraryVoteResponse.minimumParticipation(voters)).isEqualTo(expected);
    }
}
