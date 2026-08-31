package com.planmate.auth.local;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.local-test-users.trip-membership")
public class LocalTestTripMembershipProperties {

    private boolean enabled;
    private Long tripId;
    private List<String> memberLoginIds = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public List<String> getMemberLoginIds() {
        return List.copyOf(memberLoginIds);
    }

    public void setMemberLoginIds(List<String> memberLoginIds) {
        this.memberLoginIds = memberLoginIds == null ? new ArrayList<>() : new ArrayList<>(memberLoginIds);
    }
}
