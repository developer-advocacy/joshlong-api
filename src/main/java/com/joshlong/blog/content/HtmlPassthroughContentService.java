package com.joshlong.blog.content;

import com.joshlong.blog.ContentService;

import java.util.function.Supplier;

class HtmlPassthroughContentService implements ContentService<String> {

	private final Supplier<String> content;

	HtmlPassthroughContentService(Supplier<String> content) {
		this.content = content;
	}

	@Override
	public String getContent() {
		return this.content.get();
	}

}
