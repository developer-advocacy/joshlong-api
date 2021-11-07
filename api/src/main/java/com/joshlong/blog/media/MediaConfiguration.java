package com.joshlong.blog.media;

import com.joshlong.blog.BlogProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Log4j2
@Configuration
class MediaConfiguration {

	@Bean
	MediaRestController mediaRestController(BlogProperties properties) throws Exception {
		var mediaRoot = new File(properties.localCloneDirectory().getFile(), "content/media/");
		return new MediaRestController(mediaRoot);
	}

}
