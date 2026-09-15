package com.interview.assessment.notification.domain;

public interface ChannelRouter {
    RoutingOutcome route(RoutingInput input);
}

