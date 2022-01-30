package com.joshlong.blog.index;

import com.joshlong.blog.BlogPost;
import org.springframework.context.ApplicationEvent;

import java.util.Date;
import java.util.Map;

public class IndexingFinishedEvent extends ApplicationEvent {

	public record IndexSource(Map<String, BlogPost> index, Date date) {
	}

	public IndexingFinishedEvent(IndexSource date) {
		super(date);
	}

	public IndexingFinishedEvent(Map<String, BlogPost> postMap, Date date) {
		super(new IndexSource(postMap, date));
	}

	@Override
	public IndexSource getSource() {
		return (IndexSource) super.getSource();
	}

}
