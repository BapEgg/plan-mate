package com.planmate.vote.dto;

import com.planmate.vote.entity.BallotChoice;
import jakarta.validation.constraints.NotNull;

public record CastBallotRequest(@NotNull BallotChoice choice) {
}
