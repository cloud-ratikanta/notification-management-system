package com.interview.assessment.notification.strategy;

/** Runtime exception thrown by registry when a channel is unknown. */
public class UnknownChannelException extends RuntimeException {
    public UnknownChannelException(String channel) {
        super("Unknown channel: " + channel);
    }
}

