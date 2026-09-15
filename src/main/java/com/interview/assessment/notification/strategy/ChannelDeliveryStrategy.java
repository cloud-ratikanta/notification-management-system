package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;

/**
 * Strategy interface for delivering a single delivery command.
 */
public interface ChannelDeliveryStrategy {
    /** the channel this strategy implements */
    Channel channel();

    /** attempt delivery for the given command */
    DeliveryResult deliver(DeliveryCommand command);
}

