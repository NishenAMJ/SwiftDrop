package com.swiftdrop.communication.notification;

public final class SmsTemplates {

    private static final int MAX_SMS_LENGTH = 160;

    private SmsTemplates() {
    }

    public static String parcelStatus(String status, String trackingNumber, String failureReason) {
        String message = switch (status.toUpperCase()) {
            case "OUT_FOR_DELIVERY" -> "SwiftDrop: Parcel " + trackingNumber + " is out for delivery today.";
            case "DELIVERED" -> "SwiftDrop: Parcel " + trackingNumber + " has been delivered. Thank you!";
            case "FAILED" -> {
                if (failureReason != null && !failureReason.isBlank()) {
                    yield "SwiftDrop: Delivery failed for " + trackingNumber + ". Reason: " + failureReason;
                }
                yield "SwiftDrop: Delivery failed for " + trackingNumber + ". Please check your tracking link.";
            }
            default -> "SwiftDrop: Parcel " + trackingNumber + " status is now " + status + ".";
        };

        if (message.length() > MAX_SMS_LENGTH) {
            return message.substring(0, MAX_SMS_LENGTH - 3) + "...";
        }
        return message;
    }
}
