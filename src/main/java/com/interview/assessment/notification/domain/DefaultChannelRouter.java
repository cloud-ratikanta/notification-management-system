package com.interview.assessment.notification.domain;

import com.interview.assessment.notification.domain.enums.Channel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultChannelRouter implements ChannelRouter {

    @Override
    public RoutingOutcome route(RoutingInput input) {
        List<Channel> selected = new ArrayList<>();
        if (input.requestedChannels() != null && !input.requestedChannels().isEmpty()) {
            selected.addAll(input.requestedChannels());
        } else if (input.recipient().email() != null && !input.recipient().email().isBlank()) {
            selected.add(Channel.EMAIL);
        }

        if (selected.isEmpty()) {
            selected.add(Channel.EMAIL);
        }
        return new RoutingOutcome(selected.stream().distinct().toList());
    }
}

