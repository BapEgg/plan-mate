package com.planmate.vote.repository;

import com.planmate.vote.entity.ItineraryVoteEntity;
import com.planmate.vote.entity.VoteStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryVoteRepository extends JpaRepository<ItineraryVoteEntity, Long> {

    List<ItineraryVoteEntity> findByTripIdOrderByStatusAscDeadlineAscIdAsc(Long tripId);

    Optional<ItineraryVoteEntity> findByProposalId(Long proposalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vote FROM ItineraryVoteEntity vote WHERE vote.id = :id AND vote.tripId = :tripId")
    Optional<ItineraryVoteEntity> findByIdAndTripIdForUpdate(@Param("id") Long id, @Param("tripId") Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vote FROM ItineraryVoteEntity vote WHERE vote.id = :id")
    Optional<ItineraryVoteEntity> findByIdForUpdate(@Param("id") Long id);

    List<ItineraryVoteEntity> findByStatusAndDeadlineLessThanEqual(VoteStatus status, Instant deadline);

    List<ItineraryVoteEntity> findByTripIdAndStatus(Long tripId, VoteStatus status);
}
