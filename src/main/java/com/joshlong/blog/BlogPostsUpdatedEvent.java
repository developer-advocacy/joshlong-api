package com.joshlong.blog;

import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.List;

public class BlogPostsUpdatedEvent extends ApplicationEvent {

	public BlogPostsUpdatedEvent(List<BlogPost> posts) {
		super(posts);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<BlogPost> getSource() {
		var src = super.getSource();
		if (src instanceof List<?>)
			return (List<BlogPost>) src;
		return Collections.emptyList();
	}

}
