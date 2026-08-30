-- Experiment: retry-classification-dlq-20260828-223237
-- Retryable generation IDs: 1176, 1177, 1178, 1179, 1180, 1181, 1182, 1183, 1184, 1185
-- Non-Retryable generation IDs: 1186, 1187, 1188, 1189, 1190, 1191, 1192, 1193, 1194, 1195
SELECT id, trip_id, status, failure_reason, collection_claim_version, created_at, updated_at
FROM itinerary_generations
WHERE id IN (1176,1177,1178,1179,1180,1181,1182,1183,1184,1185,1186,1187,1188,1189,1190,1191,1192,1193,1194,1195)
ORDER BY id;

SELECT status, failure_reason, count(*) AS count
FROM itinerary_generations
WHERE id IN (1176,1177,1178,1179,1180,1181,1182,1183,1184,1185,1186,1187,1188,1189,1190,1191,1192,1193,1194,1195)
GROUP BY status, failure_reason
ORDER BY failure_reason;

SELECT generation_id, count(*) AS candidate_count
FROM place_candidates
WHERE generation_id IN (1176,1177,1178,1179,1180,1181,1182,1183,1184,1185,1186,1187,1188,1189,1190,1191,1192,1193,1194,1195)
GROUP BY generation_id
ORDER BY generation_id;
