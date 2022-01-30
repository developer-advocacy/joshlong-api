package com.joshlong.blog.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.BlogProperties;
import com.joshlong.blog.Content;
import com.joshlong.blog.ContentService;
import com.joshlong.blog.IndexService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.Collection;

@Configuration
class ContentConfiguration {

	private final ObjectMapper objectMapper;

	private final BlogProperties properties;

	private final IndexService indexService;

	ContentConfiguration(BlogProperties properties, ObjectMapper objectMapper, IndexService indexService) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.indexService = indexService;
	}

	@Bean
	ContentService<String> abstractsContentService() throws Exception {
		return new HtmlPassthroughContentService(
				() -> indexService.getIndex().get("/abstracts.html").processedContent());
	}

	@Bean
	ContentService<Collection<Content>> booksContentService() throws Exception {
		return this.buildContentService("books.json");
	}

	@Bean
	ContentService<Collection<Content>> livelessonsContentService() throws Exception {
		return this.buildContentService("livelessons.json");
	}

	private ContentService<Collection<Content>> buildContentService(String fn) throws Exception {
		var file = new File(this.properties.localCloneDirectory().getFile(), "content/" + fn);
		var fileResource = new FileSystemResource(file);
		return new JsonContentService(fileResource, this.objectMapper);
	}

}
