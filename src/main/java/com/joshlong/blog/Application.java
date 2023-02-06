package com.joshlong.blog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This the GraphQL API for the new joshlong.com. Most o the endpoints are GraphQ the
 * exception of a few endpoints intended to simplify integration, like one for Github's
 * webhooks.
 * <p>
 * It listens for webhooks from Github to know when to download and re-index the html
 * pages with a Spring Batch job.
 * <p>
 * It supports searching the blog posts with an in-memory Lucene index.
 *
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */

@Slf4j
@SpringBootApplication
@ImportRuntimeHints(Application.Hints.class)
@EnableConfigurationProperties(BlogProperties.class)
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var values = MemberCategory.values();

			Set.of(Podcast.class, BlogPostContentType.class, IndexRebuildStatus.class, Content.class, BlogPost.class,
					Appearance.class, SpringTipsEpisode.class).forEach(c -> hints.reflection().registerType(c, values));

			// todo see if i can drop these and have things still work?
			Set.of("graphql/schema.graphqls", "graphiql/index.html")
					.forEach(r -> hints.resources().registerResource(new ClassPathResource(r)));

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
				var methods = Stream.of(HttpMethod.values()).map(HttpMethod::name).toArray(String[]::new);
				log.info("the CORS methods are :" + String.join(", ", methods));
				log.info("the CORS hosts are " + Arrays.toString(properties.corsHosts()));
				registry.addMapping("/**").allowedOrigins(properties.corsHosts()).allowedMethods(methods).maxAge(3600);
			}
		};
	}

}
