package com.joshlong.blog;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.util.Arrays;
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

    /* TODO this needs to be rationalized. how do we plugin a dynamic origin? or at least consider using profiles? */
    @Log4j2
    @Configuration
    @EnableWebFlux
    public static class CorsGlobalConfiguration implements WebFluxConfigurer {

        @Override
        public void addCorsMappings(CorsRegistry corsRegistry) {
            var methods = Stream.of(HttpMethod.values()).map(Enum::name).toArray(String[]::new);
            log.info("the methods are :" + String.join(", ", methods));
            corsRegistry
                    .addMapping("/**")
                    .allowedOrigins("http://192.168.4.218:8081", "http://127.0.0.1:8081", "http://localhost:8081")
                    .allowedMethods(methods)
                    .maxAge(3600);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(SiteApplication.class, args);
    }

}
