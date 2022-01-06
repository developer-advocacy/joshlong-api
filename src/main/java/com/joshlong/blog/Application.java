package com.joshlong.blog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.NativeDetector;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.boot.GraphQlSourceBuilderCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.ResourceHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.springframework.nativex.hint.TypeAccess.*;

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
@NativeHint(options = "-H:+AddAllCharsets")
@TypeHint( //
		access = { //
				PUBLIC_CLASSES, PUBLIC_CONSTRUCTORS, PUBLIC_FIELDS, PUBLIC_METHODS, //
				QUERY_DECLARED_CONSTRUCTORS, QUERY_PUBLIC_METHODS, QUERY_PUBLIC_CONSTRUCTORS, //
				RESOURCE, //
		}, //
		types = { Podcast.class, BlogPostContentType.class, IndexRebuildStatus.class, Content.class, BlogPost.class,
				Appearance.class, SpringTipsEpisode.class })
@Slf4j
@ResourceHint(patterns = {
	"graphql/schema.graphqls", "graphiql/index.html" })
@SpringBootApplication
@EnableConfigurationProperties(BlogProperties.class)
public class Application {

	@Bean
	ApplicationRunner runner() {
		return args -> System.getenv().forEach((k, v) -> System.out.println(k + "=" + v));
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
				var methods = Stream.of(HttpMethod.values()).map(Enum::name).toArray(String[]::new);
				log.info("the CORS methods are :" + String.join(", ", methods));
				log.info("the CORS hosts are " + Arrays.toString(properties.corsHosts()));
				registry.addMapping("/**").allowedOrigins(properties.corsHosts()).allowedMethods(methods).maxAge(3600);
			}
		};
	}

	@Bean
	GraphQlSourceBuilderCustomizer graphQlSourceBuilderCustomizer() {
		return builder -> {
			if (NativeDetector.inNativeImage())
				builder.schemaResources(new ClassPathResource("graphql/schema.graphqls"));
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
