-- Experiment: rabbitmq-outage-recovery-20260828-233213
-- Generation IDs: 1216, 1217, 1218, 1219, 1220, 1221, 1222, 1223, 1224, 1225
SELECT id, trip_id, status, collection_claim_version, failure_reason
FROM itinerary_generations
WHERE id IN (1216,1217,1218,1219,1220,1221,1222,1223,1224,1225)
ORDER BY id;

SELECT generation_id, count(*) AS candidates,
       count(DISTINCT place_id) AS distinct_places,
       count(DISTINCT rank) AS distinct_ranks
FROM place_candidates
WHERE generation_id IN (1216,1217,1218,1219,1220,1221,1222,1223,1224,1225)
GROUP BY generation_id
ORDER BY generation_id;

SELECT slot_name, restart_lsn, confirmed_flush_lsn, active
FROM pg_replication_slots
WHERE slot_name = 'planmate_outbox_slot';
