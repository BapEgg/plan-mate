package com.planmate.vote.repository;

import com.planmate.vote.entity.BallotChoice;
import com.planmate.vote.entity.ItineraryVoteBallotEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryVoteBallotRepository extends JpaRepository<ItineraryVoteBallotEntity, Long> {
    List<ItineraryVoteBallotEntity> findByVoteIdAndValidTrue(Long voteId);
    Optional<ItineraryVoteBallotEntity> findByVoteIdAndUserId(Long voteId, Long userId);
    long countByVoteIdAndValidTrueAndChoice(Long voteId, BallotChoice choice);
}
