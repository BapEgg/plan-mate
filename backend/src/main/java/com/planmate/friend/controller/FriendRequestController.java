package com.planmate.friend.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.friend.dto.CreateFriendRequestRequest;
import com.planmate.friend.dto.FriendRequestResponse;
import com.planmate.friend.dto.FriendResponse;
import com.planmate.friend.entity.FriendRequestEntity;
import com.planmate.friend.service.FriendRequestService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    public FriendRequestController(FriendRequestService friendRequestService) {
        this.friendRequestService = friendRequestService;
    }

    @PostMapping("/api/friend-requests")
    public ResponseEntity<FriendRequestResponse> send(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody CreateFriendRequestRequest request
    ) {
        FriendRequestEntity created = friendRequestService.send(
                user.userId(), request.addresseeUserId(), request.addresseeEmail()
        );
        return ResponseEntity.ok(FriendRequestResponse.from(created));
    }

    /** {@code direction}은 {@code incoming}(기본, 나에게 온 요청) 또는 {@code outgoing}(내가 보낸 요청). */
    @GetMapping("/api/friend-requests")
    public List<FriendRequestResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "incoming") String direction
    ) {
        List<FriendRequestEntity> requests = "outgoing".equals(direction)
                ? friendRequestService.listOutgoing(user.userId())
                : friendRequestService.listIncoming(user.userId());
        return requests.stream().map(FriendRequestResponse::from).toList();
    }

    @GetMapping("/api/friends")
    public List<FriendResponse> listFriends(@AuthenticationPrincipal AuthenticatedUser user) {
        return friendRequestService.listFriends(user.userId());
    }

    @PostMapping("/api/friend-requests/{requestId}/accept")
    public ResponseEntity<Void> accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long requestId) {
        friendRequestService.accept(user.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/friend-requests/{requestId}/decline")
    public ResponseEntity<Void> decline(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long requestId) {
        friendRequestService.decline(user.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/friend-requests/{requestId}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long requestId) {
        friendRequestService.cancel(user.userId(), requestId);
        return ResponseEntity.noContent().build();
    }
}
