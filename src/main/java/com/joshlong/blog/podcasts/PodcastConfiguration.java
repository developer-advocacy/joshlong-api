package com.joshlong.blog.podcasts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.BlogProperties;
import com.joshlong.blog.PodcastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.util.ArrayList;

@Slf4j
@Configuration
class PodcastConfiguration {

	private final String podcastServiceName = PodcastService.class.getName();

	@Bean
	DefaultPodcastService defaultPodcastService(BlogProperties properties, ObjectMapper om) throws IOException {
		log.info("{} online", this.podcastServiceName);
		return new DefaultPodcastService(properties, om);
	}

}
