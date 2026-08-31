-- Replace the sample ids with the generationIds recorded in result.json.

-- Outbox events created for this run.
SELECT id, aggregate_id, event_type, payload, created_at
FROM outbox_events
WHERE aggregate_id IN ('1117', '1118')
ORDER BY created_at;

-- Final generation states.
SELECT g.id AS generation_id,
       g.trip_id,
       t.title,
       g.status,
       g.failure_reason,
       g.created_at,
       g.updated_at
FROM itinerary_generations g
JOIN trips t ON t.id = g.trip_id
WHERE g.id IN (1117, 1118)
ORDER BY g.id;

-- Candidate rows saved by the Worker.
SELECT generation_id, count(*) AS candidate_count
FROM place_candidates
WHERE generation_id IN (1117, 1118)
GROUP BY generation_id
ORDER BY generation_id;
