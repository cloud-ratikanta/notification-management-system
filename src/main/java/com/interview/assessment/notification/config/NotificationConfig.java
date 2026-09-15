package com.interview.assessment.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.interview.assessment.notification.domain.RetryPolicy;

import java.util.Random;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

	@Bean
	public RetryPolicy retryPolicy(NotificationProperties props) {
		return new RetryPolicy(props.getRetry(), new Random());
	}

}
