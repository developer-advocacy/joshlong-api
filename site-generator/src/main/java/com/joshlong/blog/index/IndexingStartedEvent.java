package com.joshlong.blog.index;

import org.springframework.context.ApplicationEvent;

import java.util.Date;

public class IndexingStartedEvent extends ApplicationEvent {

	public IndexingStartedEvent(Date date) {
		super(date);
	}

	@Override
	public Date getSource() {
		return (Date) super.getSource();
	}

}
