-- Experiment: rabbitmq-outage-recovery-20260828-232549
-- Generation IDs: 1206, 1207, 1208, 1209, 1210, 1211, 1212, 1213, 1214, 1215
SELECT id, trip_id, status, collection_claim_version, failure_reason
FROM itinerary_generations
WHERE id IN (1206,1207,1208,1209,1210,1211,1212,1213,1214,1215)
ORDER BY id;

SELECT generation_id, count(*) AS candidates,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id IN (1206,1207,1208,1209,1210,1211,1212,1213,1214,1215)
GROUP BY generation_id
ORDER BY generation_id;

SELECT slot_name, restart_lsn, confirmed_flush_lsn, active
FROM pg_replication_slots
WHERE slot_name = 'planmate_outbox_slot';
