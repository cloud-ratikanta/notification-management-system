package com.interview.assessment.notification;

import com.interview.assessment.notification.domain.DefaultChannelRouter;
import com.interview.assessment.notification.strategy.ChannelStrategyRegistry;
import com.interview.assessment.notification.strategy.EmailDeliveryStrategy;
import com.interview.assessment.notification.strategy.SmsDeliveryStrategy;
import com.interview.assessment.notification.domain.RoutingInput;
import com.interview.assessment.notification.dto.RecipientDto;
import com.interview.assessment.notification.domain.RoutingPolicy;
import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultChannelRouterTest {

    private final DefaultChannelRouter router = new DefaultChannelRouter(
            new RoutingPolicy(),
            new ChannelStrategyRegistry(List.of(new EmailDeliveryStrategy(), new SmsDeliveryStrategy()))
    );

    @Test
    void defaultChannelsForHighSeverity_chooseEmail() {
        RecipientDto r = new RecipientDto("user-1", "user1@example.com", null, null, null, null);
        var out = router.route(new RoutingInput(null, Severity.HIGH, r));
        assertThat(out.channels()).containsExactly(Channel.EMAIL);
    }

    @Test
    void criticalWithPhone_includesSms() {
        RecipientDto r = new RecipientDto("user-2", null, "+123", null, null, null);
        var out = router.route(new RoutingInput(null, Severity.CRITICAL, r));
        // CRITICAL default includes SMS first
        assertThat(out.channels()).contains(Channel.SMS);
    }

    @Test
    void blockedChannel_isRemoved() {
        RecipientDto r = new RecipientDto("user-3", "u@example.com", "+1", null, List.of(Channel.EMAIL), List.of(Channel.SMS));
        var out = router.route(new RoutingInput(null, Severity.CRITICAL, r));
        assertThat(out.channels()).doesNotContain(Channel.SMS);
    }

    @Test
    void noAddresses_returnsEmpty() {
        RecipientDto r = new RecipientDto("user-4", null, null, null, null, null);
        var out = router.route(new RoutingInput(null, Severity.HIGH, r));
        assertThat(out.channels()).isEmpty();
    }
}

