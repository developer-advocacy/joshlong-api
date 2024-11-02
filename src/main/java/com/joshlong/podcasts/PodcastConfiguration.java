package com.joshlong.podcasts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URL;

@Configuration
class PodcastConfiguration {

	@Bean
	DomAtomPodcastService domAtomPodcastService(@Value("${blog.rss.feed}") URL url) {
		return new DomAtomPodcastService(url);
	}

}
