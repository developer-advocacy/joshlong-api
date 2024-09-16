package com.joshlong;

import com.fasterxml.jackson.databind.JsonNode;
import com.joshlong.videos.JobProperties;
import com.joshlong.videos.youtube.IngestJobInitiatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@SpringBootApplication
@ImportRuntimeHints(Application.Hints.class)
@EnableConfigurationProperties({ JobProperties.class, BlogProperties.class })
public class Application {

	public static void main(String[] args) {
		var environment = System.getenv();
		var creds = Map.of("pw", Objects.requireNonNull(environment.get("SPRING_R2DBC_PASSWORD")), "url",
				Objects.requireNonNull(environment.get("SPRING_R2DBC_URL")), "user",
				Objects.requireNonNull(environment.get("SPRING_R2DBC_PASSWORD")));
		log.info("credentials: {}", creds);

		SpringApplication.run(Application.class, args);
	}

	@Bean
	ApplicationRunner ingestJobInitiationEventListener(ApplicationEventPublisher publisher) {
		return e -> publisher.publishEvent(new IngestJobInitiatedEvent(Instant.now()));
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var values = MemberCategory.values();
			Set.of(BlogProperties.BlogRssFeed.class, Appearance.class, Podcast.class, BlogPostsOrderedEvent.class,
					BlogPostContentType.class, IndexRebuildStatus.class, Content.class, BlogPost.class, JsonNode.class)
					.forEach(c -> hints.reflection().registerType(c, values));
		}

	}

	@Bean
	WebClient webClient(WebClient.Builder builder) {
		return builder.build();
	}

	@Bean
	WebFluxConfigurer webFluxConfigurer(BlogProperties properties) {
		return new WebFluxConfigurer() {

			@Override
			public void addCorsMappings(CorsRegistry registry) {

				var hosts = new HashSet<String>();
				for (var ch : properties.corsHosts()) {
					if (ch.endsWith("{expand}")) {
						var base = ch.substring(0, ch.lastIndexOf("{expand}"));
						for (var prefix : Set.of("https://", "http://", "https://www.", "http://www."))
							hosts.add(prefix + base);
					}
					else {
						hosts.add(ch);
					}
				}

				var hostsArray = hosts.toArray(new String[0]);
				var methods = Stream.of(HttpMethod.values()).map(HttpMethod::name).toArray(String[]::new);
				log.info("the CORS methods are :{}", String.join(", ", methods));
				log.info("the CORS hosts are {}", Arrays.toString(hostsArray));
				registry.addMapping("/**").allowedOrigins(hostsArray).allowedMethods(methods).maxAge(3600);
			}
		};
	}

}
