package com.interview.assessment.notification;

import com.interview.assessment.notification.domain.enums.FailureClass;
import com.interview.assessment.notification.strategy.*;
import com.interview.assessment.notification.strategy.SlackDeliveryStrategy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStrategyTest {

    @Test
    void emailStrategy_missingEmail_fails() {
        EmailDeliveryStrategy s = new EmailDeliveryStrategy();
        DeliveryResult r = s.deliver(new DeliveryCommand(
                UUID.randomUUID(), UUID.randomUUID(), "r1", null, null, null, null, 0
        ));
        assertThat(r.success()).isFalse();
        assertThat(r.failureClass()).isEqualTo(FailureClass.INVALID_RECIPIENT);
    }

    @Test
    void emailStrategy_withEmail_succeeds() {
        EmailDeliveryStrategy s = new EmailDeliveryStrategy();
        DeliveryResult r = s.deliver(new DeliveryCommand(
                UUID.randomUUID(), UUID.randomUUID(), "r1", "a@b.com", null, null, null, 0
        ));
        assertThat(r.success()).isTrue();
    }

    @Test
    void smsStrategy_missingPhone_fails() {
        SmsDeliveryStrategy s = new SmsDeliveryStrategy();
        DeliveryResult r = s.deliver(new DeliveryCommand(
                UUID.randomUUID(), UUID.randomUUID(), "r1", null, null, null, null, 0
        ));
        assertThat(r.success()).isFalse();
        assertThat(r.failureClass()).isEqualTo(FailureClass.INVALID_RECIPIENT);
    }

    @Test
    void smsStrategy_withPhone_succeeds() {
        SmsDeliveryStrategy s = new SmsDeliveryStrategy();
        DeliveryResult r = s.deliver(new DeliveryCommand(
                UUID.randomUUID(), UUID.randomUUID(), "r1", null, "+1", null, null, 0
        ));
        assertThat(r.success()).isTrue();
    }
    @Test
    void slackStrategy_missingTarget_fails() {
        SlackDeliveryStrategy s = new SlackDeliveryStrategy();
        DeliveryResult r = s.deliver(new DeliveryCommand(
                UUID.randomUUID(), UUID.randomUUID(), "r1", null, null, null, null, 0
        ));
        assertThat(r.success()).isFalse();
        assertThat(r.failureClass()).isEqualTo(com.interview.assessment.notification.domain.enums.FailureClass.INVALID_RECIPIENT);
    }

    @Test
    void slackStrategy_withTarget_succeeds() {
        SlackDeliveryStrategy s = new SlackDeliveryStrategy();
        DeliveryResult r = s.deliver(new DeliveryCommand(
                UUID.randomUUID(), UUID.randomUUID(), "r1", null, null, "#channel", null, 0
        ));
        assertThat(r.success()).isTrue();
    }
}

