package com.swiftdrop.communication.notification;

public final class EmailTemplates {

    private EmailTemplates() {
    }

    public static EmailContent welcome(String fullName) {
        String subject = "Welcome to SwiftDrop";
        String body = "Hello " + fullName + ",\n\n"
                + "Welcome to SwiftDrop! Your account has been successfully registered.\n"
                + "You can now book and track parcel deliveries across our network.\n\n"
                + "Best regards,\nThe SwiftDrop Team";
        return new EmailContent(subject, body);
    }

    public static EmailContent merchantDecision(String eventType, String businessName, String reason) {
        return switch (eventType) {
            case "merchant.approved" -> new EmailContent(
                    "SwiftDrop Merchant Application Approved",
                    "Hello,\n\n"
                            + "We are pleased to inform you that your merchant application for '" + businessName + "' has been approved.\n"
                            + "You can now start using merchant services on SwiftDrop.\n\n"
                            + "Best regards,\nThe SwiftDrop Team"
            );
            case "merchant.rejected" -> {
                String reasonText = (reason != null && !reason.isBlank()) ? "\nReason: " + reason : "";
                yield new EmailContent(
                        "SwiftDrop Merchant Application Update",
                        "Hello,\n\n"
                                + "We regret to inform you that your merchant application for '" + businessName + "' was not approved."
                                + reasonText + "\n\n"
                                + "Best regards,\nThe SwiftDrop Team"
                );
            }
            case "merchant.suspended" -> {
                String reasonText = (reason != null && !reason.isBlank()) ? "\nReason: " + reason : "";
                yield new EmailContent(
                        "SwiftDrop Merchant Account Suspended",
                        "Hello,\n\n"
                                + "Your merchant account for '" + businessName + "' has been suspended."
                                + reasonText + "\n\n"
                                + "Please contact support for assistance.\n\n"
                                + "Best regards,\nThe SwiftDrop Team"
                );
            }
            default -> new EmailContent(
                    "SwiftDrop Merchant Notification",
                    "Hello,\n\n"
                            + "There is an update on your merchant account for '" + businessName + "'.\n\n"
                            + "Best regards,\nThe SwiftDrop Team"
            );
        };
    }

    public static EmailContent parcelStatus(String status, String trackingNumber, String trackingBaseUrl, String failureReason) {
        String baseUrl = trackingBaseUrl.endsWith("/") ? trackingBaseUrl : trackingBaseUrl + "/";
        String trackingUrl = baseUrl + trackingNumber;

        return switch (status.toUpperCase()) {
            case "CREATED" -> new EmailContent(
                    "SwiftDrop: Your Parcel Booking Confirmed (" + trackingNumber + ")",
                    "Hello,\n\nYour parcel with tracking number " + trackingNumber + " has been booked.\n"
                            + "Track your parcel: " + trackingUrl + "\n\nBest regards,\nThe SwiftDrop Team"
            );
            case "PICKED_UP" -> new EmailContent(
                    "SwiftDrop: Your Parcel Has Been Picked Up (" + trackingNumber + ")",
                    "Hello,\n\nYour parcel with tracking number " + trackingNumber + " has been picked up by the courier.\n"
                            + "Track your parcel: " + trackingUrl + "\n\nBest regards,\nThe SwiftDrop Team"
            );
            case "IN_TRANSIT" -> new EmailContent(
                    "SwiftDrop: Your Parcel Is In Transit (" + trackingNumber + ")",
                    "Hello,\n\nYour parcel with tracking number " + trackingNumber + " is now in transit to its destination facility.\n"
                            + "Track your parcel: " + trackingUrl + "\n\nBest regards,\nThe SwiftDrop Team"
            );
            case "OUT_FOR_DELIVERY" -> new EmailContent(
                    "SwiftDrop: Your Parcel Is Out for Delivery (" + trackingNumber + ")",
                    "Hello,\n\nGood news! Your parcel with tracking number " + trackingNumber + " is out for delivery today.\n"
                            + "Track your parcel: " + trackingUrl + "\n\nBest regards,\nThe SwiftDrop Team"
            );
            case "DELIVERED" -> new EmailContent(
                    "SwiftDrop: Your Parcel Has Been Delivered (" + trackingNumber + ")",
                    "Hello,\n\nYour parcel with tracking number " + trackingNumber + " has been successfully delivered. Thank you for choosing SwiftDrop!\n"
                            + "Track your parcel: " + trackingUrl + "\n\nBest regards,\nThe SwiftDrop Team"
            );
            case "FAILED" -> {
                String reasonText = (failureReason != null && !failureReason.isBlank()) ? "\nReason: " + failureReason : "";
                yield new EmailContent(
                        "SwiftDrop: Delivery Attempt Failed (" + trackingNumber + ")",
                        "Hello,\n\nWe could not complete the delivery of your parcel (" + trackingNumber + ")."
                                + reasonText + "\nTrack your parcel: " + trackingUrl + "\n\nBest regards,\nThe SwiftDrop Team"
                );
            }
            default -> new EmailContent(
                    "SwiftDrop: Parcel Status Update (" + trackingNumber + ")",
                    "Hello,\n\nYour parcel " + trackingNumber + " status is now " + status + ".\n"
                            + "Track your parcel: " + trackingUrl + "\n\nBest regards,\nThe SwiftDrop Team"
            );
        };
    }
}
