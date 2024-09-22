package com.joshlong.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.BlogPost;
import com.joshlong.BlogProperties;
import com.joshlong.IndexService;
import com.joshlong.index.IndexingFinishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Slf4j
@Configuration
class ContentConfiguration {

	private final ObjectMapper objectMapper;

	private final BlogProperties properties;

	private final BlogIndexContentResolver resolver;

	private final IndexService indexService;

	ContentConfiguration(BlogIndexContentResolver resolver, BlogProperties properties, ObjectMapper objectMapper,
			IndexService indexService) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.resolver = resolver;
		this.indexService = indexService;
	}

	@Bean
	HtmlPassthroughContentService aboutContentService() {
		return this.passthroughContentService("/about.html");
	}

	@Bean
	HtmlPassthroughContentService abstractsContentService() {
		return this.passthroughContentService("/abstracts.html");
	}

	private HtmlPassthroughContentService passthroughContentService(String key) {
		return new HtmlPassthroughContentService(() -> indexService.getIndex().get(key).processedContent());
	}

	@Bean
	JsonContentService booksContentService() throws Exception {
		return this.buildContentService("books.json");
	}

	@Bean
	JsonContentService livelessonsContentService() throws Exception {
		return this.buildContentService("livelessons.json");
	}

	private JsonContentService buildContentService(String fn) throws Exception {
		var file = new File(this.properties.localCloneDirectory().getFile(), "content/" + fn);
		log.info("the file is {} and it exists? {}", file.getAbsolutePath(), file.exists());
		var fileResource = new FileSystemResource(file);
		return new JsonContentService(fileResource, this.resolver, this.objectMapper);
	}

}

/**
 * given an HTML key, we need to load the content for a blog post here
 */
@Slf4j
@Component
class BlogIndexContentResolver implements Function<String, String> {

	private final AtomicReference<Map<String, BlogPost>> reference = new AtomicReference<>();

	@EventListener
	public void reset(IndexingFinishedEvent event) {
		var posts = event.getSource();
		var index = posts.index();
		this.reference.set(index);
		if (log.isDebugEnabled())
			index.forEach((k, v) -> log.debug(k + "=" + v.path()));
	}

	@Override
	public String apply(String key) {
		var index = this.reference.get();
		var post = index.getOrDefault(key, null);
		Assert.notNull(post, "the blog post could not be found with key " + key);
		return post.processedContent();
	}

}