package com.joshlong.blog.podcasts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

@Configuration
class PodcastConfiguration {

    @Bean
    DefaultPodcastService defaultPodcastService(ObjectMapper om) throws IOException {
        return new DefaultPodcastService(om);
    }
}
