package com.interview.assessment.notification.domain;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RoutingPolicy {

    public List<Channel> defaultChannelsFor(Severity severity) {
        if (severity == Severity.CRITICAL) {
            return List.of(Channel.SMS, Channel.SLACK, Channel.EMAIL);
        }
        return List.of(Channel.EMAIL);
    }

    public Set<Channel> enabledChannels() {
        return Set.of(Channel.EMAIL, Channel.SMS, Channel.SLACK);
    }

    public Set<Channel> fastChannels() {
        return Set.of(Channel.SMS, Channel.SLACK);
    }
}

