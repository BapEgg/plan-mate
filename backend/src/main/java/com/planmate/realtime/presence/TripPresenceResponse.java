package com.planmate.realtime.presence;

import java.util.List;

public record TripPresenceResponse(Long tripId, List<MemberPresenceResponse> members, long snapshotVersion) {
}
