package com.flowerconnect.security.specification;

import com.flowerconnect.domain.User;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable query fragments for the admin user listing (plan task 2.8).
 *
 * <p>Both filters are optional, so the service composes only the ones supplied.
 * Combining them with {@code AND} means a request narrowed by both role and
 * status cannot accidentally widen into an OR.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /**
     * Matches users holding the named seeded role (for example {@code CUSTOMER},
     * {@code FLORIST} or {@code ADMIN}).
     */
    public static Specification<User> withRole(String roleName) {
        return (root, query, builder) ->
                builder.equal(root.get("role").get("name"), roleName);
    }

    /**
     * Matches users in the given account status.
     */
    public static Specification<User> withStatus(User.Status status) {
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }
}