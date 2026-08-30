-- Experiment: worker-before-claim-termination-20260829-002951
-- Generation IDs: 1226, 1227, 1228, 1229, 1230, 1231, 1232, 1233, 1234, 1235
SELECT id, trip_id, status, collection_claim_version, created_at, updated_at
FROM itinerary_generations
WHERE id IN (1226,1227,1228,1229,1230,1231,1232,1233,1234,1235)
ORDER BY id;

SELECT generation_id, count(*) AS candidate_count,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id IN (1226,1227,1228,1229,1230,1231,1232,1233,1234,1235)
GROUP BY generation_id
ORDER BY generation_id;
