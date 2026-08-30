-- Experiment: stale-generation-recovery-20260828-225751
SELECT id, trip_id, status, collection_claim_version, collection_lease_expires_at, failure_reason, created_at, updated_at
FROM itinerary_generations
WHERE id IN (1196,1197,1198,1199,1200,1201,1202,1203,1204,1205)
ORDER BY id;

SELECT generation_id, count(*) AS candidate_count,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id IN (1196,1197,1198,1199,1200,1201,1202,1203,1204,1205)
GROUP BY generation_id
ORDER BY generation_id;
