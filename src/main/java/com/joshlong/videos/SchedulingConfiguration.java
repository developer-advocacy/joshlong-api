package com.joshlong.videos;

import com.joshlong.videos.youtube.IngestJobInitiatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Configuration
class SchedulingConfiguration {

	private final ApplicationEventPublisher applicationEventPublisher;

	SchedulingConfiguration(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Scheduled(fixedRate = 12, timeUnit = TimeUnit.HOURS)
	public void scheduled() {
		System.out.println("scheduled!");
		this.applicationEventPublisher.publishEvent(new IngestJobInitiatedEvent(Instant.now()));
	}

}
