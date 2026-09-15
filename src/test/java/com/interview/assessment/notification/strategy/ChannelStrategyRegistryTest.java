package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelStrategyRegistryTest {

    @Test
    void registry_resolves_registered_strategies_by_channel() {
        EmailDeliveryStrategy email = new EmailDeliveryStrategy();
        SmsDeliveryStrategy sms = new SmsDeliveryStrategy();

        ChannelStrategyRegistry registry = new ChannelStrategyRegistry(List.of(email, sms));

        assertThat(registry.get(Channel.EMAIL)).isSameAs(email);
        assertThat(registry.get(Channel.SMS)).isSameAs(sms);
    }

    @Test
    void registry_throws_for_unregistered_channel() {
        EmailDeliveryStrategy email = new EmailDeliveryStrategy();
        ChannelStrategyRegistry registry = new ChannelStrategyRegistry(List.of(email));

        assertThrows(IllegalArgumentException.class, () -> registry.get(Channel.SMS));
    }
}

