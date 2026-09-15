package com.interview.assessment.notification.dto;

import com.interview.assessment.notification.domain.enums.Channel;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RecipientDto(
        @NotBlank String recipientId,
        String email,
        String phone,
        String slackUserOrChannel,
        List<Channel> preferredChannels,
        List<Channel> blockedChannels
) {
}

