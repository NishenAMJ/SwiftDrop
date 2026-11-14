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
}
