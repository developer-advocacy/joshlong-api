package com.joshlong.media;

import com.joshlong.BlogProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
class MediaConfiguration {

	@Bean
	MediaRestController mediaRestController(BlogProperties properties) throws Exception {
		var mediaRoot = new File(properties.localCloneDirectory().getFile(), "content/media/");
		return new MediaRestController(mediaRoot);
	}

}
