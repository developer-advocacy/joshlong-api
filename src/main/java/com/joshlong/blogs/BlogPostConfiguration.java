package com.joshlong.blogs;

import com.joshlong.BlogPostService;
import com.joshlong.BlogProperties;
import com.joshlong.dates.SimpleDateDateFormat;
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
