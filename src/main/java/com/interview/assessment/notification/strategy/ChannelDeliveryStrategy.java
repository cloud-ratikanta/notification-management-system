package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;

public interface ChannelDeliveryStrategy {
    Channel channel();

    DeliveryResult deliver(DeliveryCommand command);
}

