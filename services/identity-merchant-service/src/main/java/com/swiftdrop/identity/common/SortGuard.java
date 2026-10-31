package com.swiftdrop.identity.common;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Rejects {@code ?sort=} fields a list endpoint doesn't support, instead of failing with a 500. */
public final class SortGuard {

    private SortGuard() {
    }

    public static void check(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw ApiException.validation("sort", "can only sort by " + String.join(", ", allowed.stream().sorted().toList()));
            }
        }
    }
}
