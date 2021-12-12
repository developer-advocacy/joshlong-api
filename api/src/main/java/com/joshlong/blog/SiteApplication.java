package com.joshlong.blog;

import graphql.execution.instrumentation.Instrumentation;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.graphql.boot.GraphQlProperties;
import org.springframework.graphql.boot.GraphQlSourceBuilderCustomizer;
import org.springframework.graphql.boot.InvalidSchemaLocationsException;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.MissingSchemaException;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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

@SpringBootApplication
@EnableConfigurationProperties(BlogProperties.class)
public class SiteApplication {

	/**
	 * TODO graalvm The autoconfiguration, as of Spring Native 1.0.0-SNAPSHOT in middle
	 * December 2021, uses a {@link ResourcePatternResolver} which requires us to scour
	 * the classpath for files. Trouble is, in a GraalVM application, there's no
	 * classpath, so that mechanism doesn't work. Hopefully we can remove this in the
	 * future. This works because we hardcode a single static {@link Resource}
	 */
	@Bean
	GraphQlSource graalvmCompatibleGraphqlSource(GraphQlProperties properties,
			ObjectProvider<DataFetcherExceptionResolver> exceptionResolversProvider,
			ObjectProvider<Instrumentation> instrumentationsProvider,
			ObjectProvider<GraphQlSourceBuilderCustomizer> sourceCustomizers,
			ObjectProvider<RuntimeWiringConfigurer> wiringConfigurers) {
		String location = properties.getSchema().getLocations()[0];
		List<Resource> schemaResources = List.of(new ClassPathResource(location));
		GraphQlSource.Builder builder = GraphQlSource.builder()
				.schemaResources(schemaResources.toArray(new Resource[0]))
				.exceptionResolvers(exceptionResolversProvider.orderedStream().collect(Collectors.toList()))
				.instrumentation(instrumentationsProvider.orderedStream().collect(Collectors.toList()));
		wiringConfigurers.orderedStream().forEach(builder::configureRuntimeWiring);
		sourceCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
		try {
			return builder.build();
		}
		catch (MissingSchemaException exc) {
			throw new IllegalArgumentException("we could not find the schema files!");
		}
	}

	/*
	 * TODO this needs to be rationalized. how do we plugin a dynamic origin? or at least
	 * consider using profiles?
	 */
	@Log4j2
	@Configuration
	@EnableWebFlux
	public static class CorsGlobalConfiguration implements WebFluxConfigurer {

		@Override
		public void addCorsMappings(CorsRegistry corsRegistry) {
			var methods = Stream.of(HttpMethod.values()).map(Enum::name).toArray(String[]::new);
			log.info("the methods are :" + String.join(", ", methods));
			corsRegistry.addMapping("/**")
					.allowedOrigins("http://192.168.4.218:8081", "http://127.0.0.1:8081", "http://localhost:8081")
					.allowedMethods(methods).maxAge(3600);
		}

	}

	public static void main(String[] args) {
		SpringApplication.run(SiteApplication.class, args);
	}

}
