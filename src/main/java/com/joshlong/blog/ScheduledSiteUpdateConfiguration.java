package com.joshlong.blog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Just a kicker thread, to ensure that we periodically rebuild the site, even if there's
 * no new activity on the Github repository
 *
 * @author Josh Long
 */
@Slf4j
@Configuration
@EnableScheduling
class ScheduledSiteUpdateConfiguration {

	private final ApplicationEventPublisher publisher;

	ScheduledSiteUpdateConfiguration(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Scheduled(cron = "@daily")
	public void refresh() {
		log.info("about to kick off the daily rebuild of the site in " + getClass().getName());
		this.publisher.publishEvent(new SiteUpdatedEvent());
		log.info("finished kicking off the daily rebuild of the site in " + getClass().getName());
	}

}
