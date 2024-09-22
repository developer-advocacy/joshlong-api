package com.joshlong.blogs;

import com.joshlong.BlogPostService;
import com.joshlong.BlogProperties;
import com.joshlong.templates.MarkdownService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(RomeHints.class)
class BlogPostConfiguration {

	@Bean
	BlogPostService blogService(MarkdownService markdownService, BlogProperties properties) {
		return new DefaultBlogPostService(markdownService, properties.apiServerUri());
	}

}
