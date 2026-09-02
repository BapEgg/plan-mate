package com.planmate.vote.repository;

import com.planmate.vote.entity.ItineraryVoteVoterEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryVoteVoterRepository extends JpaRepository<ItineraryVoteVoterEntity, Long> {
    List<ItineraryVoteVoterEntity> findByVoteIdAndValidTrue(Long voteId);
    Optional<ItineraryVoteVoterEntity> findByVoteIdAndUserIdAndValidTrue(Long voteId, Long userId);
}
