package com.joshlong.blog.content;

import com.joshlong.blog.ContentService;

import java.util.function.Supplier;

record HtmlPassthroughContentService(Supplier<String> content) implements ContentService<String> {

	@Override
	public String getContent() {
		return this.content.get();
	}

}
