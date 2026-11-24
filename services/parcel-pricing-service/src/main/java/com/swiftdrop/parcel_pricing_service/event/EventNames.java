package com.swiftdrop.parcel_pricing_service.event;

/** RabbitMQ names agreed in contracts/events and used by the other SwiftDrop services. */
public final class EventNames {

    public static final String PRODUCER = "parcel-pricing-service";

    /** Our own exchange. Tracking & Reporting binds parcel.* here (contracts/events/parcel-pricing). */
    public static final String PARCEL_PRICING_EXCHANGE = "swiftdrop.parcel-pricing";
    /** Notification & Webhook listens here for parcel.status-changed (contracts/events/parcel). */
    public static final String PARCEL_EXCHANGE = "swiftdrop.parcel";
    /** Delivery Operations publishes delivery.* and warehouse parcel.* events here. */
    public static final String DELIVERY_EXCHANGE = "swiftdrop.delivery-operations";
    /** Payment & Settlement publishes payment.* events here. */
    public static final String PAYMENT_EXCHANGE = "swiftdrop.payment";

    public static final String PARCEL_BOOKED = "parcel.booked";
    public static final String PARCEL_CANCELLED = "parcel.cancelled";
    public static final String PARCEL_STATUS_CHANGED = "parcel.status-changed";

    public static final String DELIVERY_QUEUE = "parcel-pricing-service.delivery-events";
    public static final String PAYMENT_QUEUE = "parcel-pricing-service.payment-events";
    public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.parcel-pricing.dlx";
    public static final String DELIVERY_DEAD_LETTER_QUEUE = DELIVERY_QUEUE + ".dead-letter";
    public static final String PAYMENT_DEAD_LETTER_QUEUE = PAYMENT_QUEUE + ".dead-letter";

    private EventNames() {
    }
}
