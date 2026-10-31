package com.swiftdrop.identity.merchant;

public enum BusinessType {
    INDIVIDUAL,
    SOLE_PROPRIETORSHIP,
    PARTNERSHIP,
    COMPANY;

    /** Partnerships and companies must give a business registration number. */
    public boolean requiresRegistrationNumber() {
        return this == PARTNERSHIP || this == COMPANY;
    }
}
