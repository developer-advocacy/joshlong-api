package com.joshlong.blog.blogs;

import com.joshlong.blog.BlogPostService;
import com.joshlong.blog.BlogProperties;
import com.joshlong.blog.dates.SimpleDateDateFormat;
import com.joshlong.templates.MarkdownService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.DateFormat;

@Configuration
class BlogPostConfiguration {

	@Bean
	BlogPostService blogService(MarkdownService markdownService, @SimpleDateDateFormat DateFormat simpleDateFormat,
			BlogProperties properties) {
		return new DefaultBlogPostService(markdownService, simpleDateFormat, properties.apiServerUri());
	}

}
