package com.joshlong.content;

import com.joshlong.ContentService;
import com.joshlong.index.IndexingFinishedEvent;
import org.springframework.context.event.EventListener;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

class HtmlPassthroughContentService implements ContentService<String> {

	private final Supplier<String> content;

	private final AtomicBoolean indexed = new AtomicBoolean(false);

	HtmlPassthroughContentService(Supplier<String> content) {
		this.content = content;
	}

	@EventListener
	void indexed(IndexingFinishedEvent finishedEvent) {
		this.indexed.set(true);
	}

	@Override
	public String getContent() {
		return this.indexed.get() ? this.content.get() : "";
	}

}
