package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.FailureClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryStrategy implements ChannelDeliveryStrategy {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryStrategy.class);

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public DeliveryResult deliver(DeliveryCommand command) {
        if (command.recipientEmail() == null || command.recipientEmail().isBlank()) {
            return DeliveryResult.failed(FailureClass.INVALID_RECIPIENT, "Missing email address");
        }
        log.info("Delivery stub EMAIL success deliveryId={} notificationId={} recipientId={}",
                command.deliveryId(), command.notificationId(), command.recipientId());
        return DeliveryResult.ok();
    }
}

