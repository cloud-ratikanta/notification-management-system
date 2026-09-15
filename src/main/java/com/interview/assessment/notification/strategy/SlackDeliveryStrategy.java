package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "notification.channels.slack", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SlackDeliveryStrategy implements ChannelDeliveryStrategy {

    private static final Logger log = LoggerFactory.getLogger(SlackDeliveryStrategy.class);

    @Override
    public Channel channel() {
        return Channel.SLACK;
    }

    @Override
    public DeliveryResult deliver(DeliveryCommand command) {
        log.info("Delivery stub SLACK success deliveryId={} notificationId={} recipientId={}",
                command.deliveryId(), command.notificationId(), command.recipientId());
        return DeliveryResult.ok();
    }
}

