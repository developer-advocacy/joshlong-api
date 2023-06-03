package com.joshlong.content;

import com.joshlong.ContentService;

import java.util.function.Supplier;

record HtmlPassthroughContentService(Supplier<String> content) implements ContentService<String> {

	@Override
	public String getContent() {
		return this.content.get();
	}

}
