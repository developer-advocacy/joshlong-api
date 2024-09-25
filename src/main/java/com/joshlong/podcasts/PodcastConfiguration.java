package com.joshlong.podcasts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.BlogProperties;
import com.joshlong.PodcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PodcastConfiguration {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final String podcastServiceName = PodcastService.class.getName();

	@Bean
	DefaultPodcastService defaultPodcastService(BlogProperties properties, ObjectMapper om) throws Exception {
		log.info("{} online", this.podcastServiceName);
		return new DefaultPodcastService(properties, om);
	}

}
