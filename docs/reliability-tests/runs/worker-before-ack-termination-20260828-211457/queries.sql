-- Experiment: worker-before-ack-termination-20260828-211457
-- Generation IDs: 1166, 1167, 1168, 1169, 1170, 1171, 1172, 1173, 1174, 1175
SELECT id, trip_id, status, collection_claim_version, created_at, updated_at
FROM itinerary_generations
WHERE id IN (1166,1167,1168,1169,1170,1171,1172,1173,1174,1175)
ORDER BY id;

SELECT generation_id, count(*) AS candidate_count,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id IN (1166,1167,1168,1169,1170,1171,1172,1173,1174,1175)
GROUP BY generation_id
ORDER BY generation_id;
