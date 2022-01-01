package com.joshlong.blog;

import org.springframework.context.ApplicationEvent;

/**
 * Whenever we need to rebuild the index based on an updated git repository, we will
 * publish this event.
 *
 */
public class SiteUpdatedEvent extends ApplicationEvent {

	public SiteUpdatedEvent() {
		super(null);
	}

}
