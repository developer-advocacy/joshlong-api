package com.joshlong.blog;

import graphql.execution.instrumentation.Instrumentation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.graphql.boot.GraphQlProperties;
import org.springframework.graphql.boot.GraphQlSourceBuilderCustomizer;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.MissingSchemaException;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.ResourceHint;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

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
@ResourceHint(patterns = { "/graphql/schema.graphqls" })
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(BlogProperties.class)
public class SiteApplication {

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
		List<Resource> schemaResources = List.of(new ClassPathResource("/graphql/schema.graphqls"));
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

	public static void main(String[] args) {
		SpringApplication.run(SiteApplication.class, args);
	}

}
