package com.joshlong.blog.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.BlogProperties;
import com.joshlong.blog.ContentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.io.File;

@Configuration
class ContentConfiguration {

	private final ObjectMapper objectMapper;

	private final BlogProperties properties;

	ContentConfiguration(BlogProperties properties, ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Bean
	ContentService booksContentService() throws Exception {
		return this.buildContentService("books.json");
	}

	@Bean
	ContentService livelessonsContentService() throws Exception {
		return this.buildContentService("livelessons.json");
	}

	private ContentService buildContentService(String fn) throws Exception {
		var file = new File(this.properties.localCloneDirectory().getFile(), "content/" + fn);
		var fileResource = new FileSystemResource(file);
		return new DefaultContentService(fileResource, this.objectMapper);
	}

}
