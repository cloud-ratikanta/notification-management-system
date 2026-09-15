package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ChannelStrategyRegistry {

    private final Map<Channel, ChannelDeliveryStrategy> strategies;

    public ChannelStrategyRegistry(List<ChannelDeliveryStrategy> strategies) {
        this.strategies = new EnumMap<>(Channel.class);
        for (ChannelDeliveryStrategy strategy : strategies) {
            this.strategies.put(strategy.channel(), strategy);
        }
    }

    public ChannelDeliveryStrategy get(Channel channel) {
        ChannelDeliveryStrategy strategy = strategies.get(channel);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy registered for channel: " + channel);
        }
        return strategy;
    }
}

