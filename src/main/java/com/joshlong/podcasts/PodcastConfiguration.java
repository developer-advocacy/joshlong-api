package com.joshlong.podcasts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.BlogProperties;
import com.joshlong.PodcastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
class PodcastConfiguration {

	private final String podcastServiceName = PodcastService.class.getName();

	@Bean
	DefaultPodcastService defaultPodcastService(BlogProperties properties, ObjectMapper om) throws Exception {
		log.info("{} online", this.podcastServiceName);
		return new DefaultPodcastService(properties, om);
	}

}
