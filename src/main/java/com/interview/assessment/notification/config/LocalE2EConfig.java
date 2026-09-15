package com.interview.assessment.notification.config;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.strategy.ChannelDeliveryStrategy;
import com.interview.assessment.notification.strategy.ChannelStrategyRegistry;
import com.interview.assessment.notification.strategy.DeliveryResult;
import com.interview.assessment.notification.strategy.DeliveryCommand;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Local profile configuration used for interactive E2E demos.
 * Activate with -Dspring.profiles.active=local-e2e (can be combined with acceptance)
 */
@Configuration
@Profile("local-e2e")
public class LocalE2EConfig {

    @Bean
    public ChannelDeliveryStrategy emailStub() {
        return new ChannelDeliveryStrategy() {
            @Override
            public Channel channel() {
                return Channel.EMAIL;
            }

            @Override
            public DeliveryResult deliver(DeliveryCommand command) {
                // Always succeed locally for demo purposes
                return DeliveryResult.ok();
            }
        };
    }

    @Bean
    public ChannelDeliveryStrategy smsStub() {
        return new ChannelDeliveryStrategy() {
            @Override
            public Channel channel() {
                return Channel.SMS;
            }

            @Override
            public DeliveryResult deliver(DeliveryCommand command) {
                return DeliveryResult.ok();
            }
        };
    }

    @Bean
    public ChannelDeliveryStrategy slackStub() {
        return new ChannelDeliveryStrategy() {
            @Override
            public Channel channel() {
                return Channel.SLACK;
            }

            @Override
            public DeliveryResult deliver(DeliveryCommand command) {
                return DeliveryResult.ok();
            }
        };
    }

    @Bean
    @Primary
    public ChannelStrategyRegistry localRegistry() {
        // Build a registry from the demo stubs only so local behavior is deterministic
        List<ChannelDeliveryStrategy> strategies = List.of(emailStub(), smsStub(), slackStub());
        return new ChannelStrategyRegistry(strategies);
    }
}

