package com.swiftdrop.communication.common;

public final class PhoneMasker {

    private PhoneMasker() {
    }

    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "****";
        }
        String trimmed = phoneNumber.trim();
        if (trimmed.length() <= 7) {
            return "****";
        }
        int suffixLen = 3;
        int prefixLen = Math.max(1, trimmed.length() - suffixLen - 4);
        String prefix = trimmed.substring(0, prefixLen);
        String suffix = trimmed.substring(trimmed.length() - suffixLen);
        return prefix + "****" + suffix;
    }
}
