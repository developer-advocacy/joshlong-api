package com.joshlong.videos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.util.stream.Stream;

@Slf4j
@Configuration
class CorsConfiguration {

	@Bean
	WebFluxConfigurer webFluxConfigurer(JobProperties properties) {
		return new WebFluxConfigurer() {

			@Override
			public void addCorsMappings(CorsRegistry registry) {
				var methods = Stream.of(HttpMethod.values()).map(HttpMethod::name).toArray(String[]::new);
				var corsHosts = properties.api().corsHosts();
				log.info("the CORS methods are: " + String.join(", ", methods));
				log.info("the CORS hosts are: " + String.join(", ", corsHosts));
				registry.addMapping("/**").allowedOrigins(corsHosts).allowedMethods(methods).maxAge(3600);
			}
		};
	}

}
