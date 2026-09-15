package com.interview.assessment.notification.domain;

import com.interview.assessment.notification.domain.enums.Channel;

import java.util.List;

public record RoutingOutcome(List<Channel> channels) {
}

