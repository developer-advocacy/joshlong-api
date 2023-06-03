package com.joshlong.appearances;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.BlogProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
class AppearanceConfiguration {

	@Bean
	DefaultAppearanceService defaultAppearanceService(BlogProperties properties, ObjectMapper objectMapper)
			throws Exception {
		var root = properties.localCloneDirectory().getFile();
		var appearances = new File(new File(root, "content"), "appearances.json");
		return new DefaultAppearanceService(appearances, objectMapper);
	}

}
