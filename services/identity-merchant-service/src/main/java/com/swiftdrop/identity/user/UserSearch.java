package com.swiftdrop.identity.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

/** Filters for the dispatcher's user list. Every filter is optional. */
final class UserSearch {

    private UserSearch() {
    }

    static Specification<User> matching(RoleName role, UserStatus status, String text) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (role != null) {
                // EXISTS subquery, so a user with several roles still appears once.
                Subquery<UUID> withRole = query.subquery(UUID.class);
                Root<User> u = withRole.from(User.class);
                Join<User, Role> r = u.join("roles");
                withRole.select(u.get("id"))
                    .where(cb.equal(u.get("id"), root.get("id")), cb.equal(r.get("name"), role));
                predicates.add(cb.exists(withRole));
            }
            if (text != null && !text.isBlank()) {
                String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(cb.lower(root.get("fullName")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
