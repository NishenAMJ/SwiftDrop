package com.swiftdrop.identity.merchant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.swiftdrop.identity.approval.ApplicationStatus;

final class MerchantSearch {

    private MerchantSearch() {
    }

    static Specification<Merchant> matching(ApplicationStatus status, String text) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (text != null && !text.isBlank()) {
                String escaped = text.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("businessName")), "%" + escaped + "%", '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
