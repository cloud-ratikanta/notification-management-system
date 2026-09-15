package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelDeliveryStrategyTest {

    @Test
    void anonymousStrategy_can_be_invoked_and_returns_ok() {
        ChannelDeliveryStrategy s = new ChannelDeliveryStrategy() {
            @Override
            public Channel channel() { return Channel.EMAIL; }

            @Override
            public DeliveryResult deliver(DeliveryCommand command) {
                return DeliveryResult.ok();
            }
        };

        DeliveryCommand cmd = new DeliveryCommand(UUID.randomUUID(), UUID.randomUUID(), "r1", "addr@example.com", null, null, Channel.EMAIL, 0);
        DeliveryResult r = s.deliver(cmd);

        assertThat(r.success()).isTrue();
        assertThat(r.failureClass()).isNull();
    }

    @Test
    void strategy_channel_method_returns_channel() {
        ChannelDeliveryStrategy s = new ChannelDeliveryStrategy() {
            @Override
            public Channel channel() { return Channel.SMS; }

            @Override
            public DeliveryResult deliver(DeliveryCommand command) {
                return DeliveryResult.ok();
            }
        };
        assertThat(s.channel()).isEqualTo(Channel.SMS);
    }
}

