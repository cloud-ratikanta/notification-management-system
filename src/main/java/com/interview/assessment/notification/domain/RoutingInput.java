package com.interview.assessment.notification.domain;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.dto.RecipientDto;

import java.util.List;

public record RoutingInput(
        List<Channel> requestedChannels,
        RecipientDto recipient
) {
}

