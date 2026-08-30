-- Outbox events created for this experiment.
SELECT id, aggregate_id, event_type, payload, created_at
FROM outbox_events
WHERE aggregate_id IN ('1117', '1118', '1119', '1120', '1121', '1122', '1123', '1124', '1125', '1126')
ORDER BY created_at;

-- Final Generation states.
SELECT g.id AS generation_id,
       g.trip_id,
       t.title,
       g.status,
       g.failure_reason,
       g.created_at,
       g.updated_at
FROM itinerary_generations g
JOIN trips t ON t.id = g.trip_id
WHERE g.id IN (1117, 1118, 1119, 1120, 1121, 1122, 1123, 1124, 1125, 1126)
ORDER BY g.id;

-- Candidate rows saved by the Worker.
SELECT generation_id, count(*) AS candidate_count
FROM place_candidates
WHERE generation_id IN (1117, 1118, 1119, 1120, 1121, 1122, 1123, 1124, 1125, 1126)
GROUP BY generation_id
ORDER BY generation_id;
