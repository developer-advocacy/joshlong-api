package com.joshlong.blog.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
