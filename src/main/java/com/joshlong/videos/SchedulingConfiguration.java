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

	// the first run is right after startup, on the scheduler's thread, so it doesn't hold
	// up the application becoming ready
	@Scheduled(fixedRate = 12, timeUnit = TimeUnit.HOURS)
	public void scheduled() {
		this.applicationEventPublisher.publishEvent(new IngestJobInitiatedEvent(Instant.now()));
	}

}
