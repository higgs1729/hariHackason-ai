package com.hanamizuki.backend.api.user;

/** Write bodies for /api/users. */
public final class UserRequests {

    private UserRequests() {
    }

    /**
     * Both fields are partial: null leaves the column alone. An empty string
     * clears the profile, but not the name — an account with no display name
     * has nothing to show in a member list.
     */
    public record PatchMeRequest(String userName, String userProfile) {
    }
}
