package com.joshlong.videos.youtube;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 *
 * Published when it's time to ingest data from the Youtube REST API.
 *
 * @author Josh Long
 */
public class IngestJobInitiatedEvent extends ApplicationEvent {

	public IngestJobInitiatedEvent(Instant source) {
		super(source);
	}

}
