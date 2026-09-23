package com.hanamizuki.backend.api.friend;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.auth.AuthDtos.UserDto;
import com.hanamizuki.backend.api.friend.FriendVos.FriendRequestBody;
import com.hanamizuki.backend.api.friend.FriendVos.FriendRequestVo;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.FriendService;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    public List<UserDto> list(@AuthenticationPrincipal AuthUser principal) {
        return friendService.list(principal.userId());
    }

    /** The inbox behind {@code accept}: without it a request id is unreachable. */
    @GetMapping("/requests")
    public List<FriendRequestVo> incoming(@AuthenticationPrincipal AuthUser principal) {
        return friendService.incoming(principal.userId());
    }

    /** 204 whether this created a request or accepted the other side's. */
    @PostMapping("/requests")
    public ResponseEntity<Void> request(@RequestBody FriendRequestBody body,
                                        @AuthenticationPrincipal AuthUser principal) {
        friendService.request(principal.userId(), body.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long requestId,
                                       @AuthenticationPrincipal AuthUser principal) {
        friendService.accept(principal.userId(), requestId);
        return ResponseEntity.noContent().build();
    }
}
