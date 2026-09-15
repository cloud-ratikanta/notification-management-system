package com.interview.assessment.notification.domain;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.dto.RecipientDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DefaultChannelRouter implements ChannelRouter {

    private final RoutingPolicy routingPolicy;
    private final com.interview.assessment.notification.strategy.ChannelStrategyRegistry strategyRegistry;

    public DefaultChannelRouter(RoutingPolicy routingPolicy, com.interview.assessment.notification.strategy.ChannelStrategyRegistry strategyRegistry) {
        this.routingPolicy = routingPolicy;
        this.strategyRegistry = strategyRegistry;
    }

    @Override
    public RoutingOutcome route(RoutingInput input) {
        RecipientDto recipient = input.recipient();
        List<Channel> selected = input.requestedChannels() != null && !input.requestedChannels().isEmpty()
                ? new ArrayList<>(input.requestedChannels())
                : new ArrayList<>(routingPolicy.defaultChannelsFor(input.severity()));

        Set<Channel> supportedByAddress = supportedByAddress(recipient);
        selected.removeIf(channel -> !routingPolicy.enabledChannels().contains(channel) || !supportedByAddress.contains(channel));

        // remove channels for which we don't have a delivery strategy registered
        selected.removeIf(channel -> !strategyRegistry.has(channel));

        if (recipient.blockedChannels() != null && !recipient.blockedChannels().isEmpty()) {
            selected.removeAll(recipient.blockedChannels());
        }

        if (recipient.preferredChannels() != null && !recipient.preferredChannels().isEmpty()) {
            selected.sort((left, right) -> Integer.compare(
                    preferenceIndex(recipient.preferredChannels(), left),
                    preferenceIndex(recipient.preferredChannels(), right)
            ));
        }

        if (input.severity() == com.interview.assessment.notification.domain.enums.Severity.CRITICAL &&
                selected.stream().noneMatch(routingPolicy.fastChannels()::contains)) {
            List<Channel> candidateFast = routingPolicy.fastChannels().stream()
                    .filter(supportedByAddress::contains)
                    .filter(channel -> recipient.blockedChannels() == null || !recipient.blockedChannels().contains(channel))
                    .toList();
            selected.addAll(candidateFast);
        }

        return new RoutingOutcome(selected.stream().distinct().toList());
    }

    private Set<Channel> supportedByAddress(RecipientDto recipient) {
        Set<Channel> supported = new java.util.HashSet<>();
        if (recipient.email() != null && !recipient.email().isBlank()) {
            supported.add(Channel.EMAIL);
        }
        if (recipient.phone() != null && !recipient.phone().isBlank()) {
            supported.add(Channel.SMS);
        }
        if (recipient.slackUserOrChannel() != null && !recipient.slackUserOrChannel().isBlank()) {
            supported.add(Channel.SLACK);
        }
        return supported;
    }

    private int preferenceIndex(List<Channel> preferred, Channel channel) {
        int index = preferred.indexOf(channel);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }
}

