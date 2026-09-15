package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsDeliveryStrategy implements ChannelDeliveryStrategy {

    private static final Logger log = LoggerFactory.getLogger(SmsDeliveryStrategy.class);

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public DeliveryResult deliver(DeliveryCommand command) {
        log.info("Delivery stub SMS success deliveryId={} notificationId={} recipientId={}",
                command.deliveryId(), command.notificationId(), command.recipientId());
        return DeliveryResult.ok();
    }
}

