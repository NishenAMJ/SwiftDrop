package com.swiftdrop.parcel_pricing_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqPublisherConfig {

    public static final String PARCEL_PRICING_EXCHANGE = "swiftdrop.parcel-pricing";

    @Bean
    TopicExchange parcelPricingExchange() {
        return new TopicExchange(PARCEL_PRICING_EXCHANGE, true, false);
    }
}
