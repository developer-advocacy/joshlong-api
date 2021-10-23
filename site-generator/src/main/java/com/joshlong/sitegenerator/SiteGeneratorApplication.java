package com.joshlong.sitegenerator;

import lombok.SneakyThrows;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ReflectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

@EnableBatchProcessing
@EnableConfigurationProperties(BlogProperties.class)
@SpringBootApplication
public class SiteGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiteGeneratorApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(BlogProperties properties, BlogService blogService) {
        return args -> {
            var file = blogService.buildBlogPostMetadataFrom(properties.contentRoot());

        };
    }
}

@ConstructorBinding
@ConfigurationProperties("blog")
record BlogProperties(File contentRoot) {
}

@Configuration
class BlogConfiguration {

    @Bean
    BlogService blogService(BlogProperties properties) {
        return new BlogService( properties.contentRoot());
    }
}

record BlogPostMetadata(String title, Date date, String[] tags,
                        String originalContent,
                        String processContent,
                        boolean published) {
}


class BlogService {


    private static final Log log = LogFactory.getLog(SiteGeneratorApplication.class);

    private final File root;

    BlogService(File root) {
        this.root = root;
        System.out.println(this.root.getAbsolutePath() + " is the path");
    }


    private static void exception(Exception e) throws RuntimeException {
        log.error(NestedExceptionUtils.buildMessage("an exception has occurred! ", e));
        ReflectionUtils.rethrowRuntimeException(e);
    }

    @SneakyThrows
    BlogPostMetadata buildBlogPostMetadataFrom(Resource r) {
        return buildBlogPostMetadataFrom(r.getInputStream());
    }

    @SneakyThrows
    BlogPostMetadata buildBlogPostMetadataFrom(InputStream file) {
        try (var f = new InputStreamReader(file)) {
            var contents = FileCopyUtils.copyToString(f);
            return buildBlogPostMetadataFrom(contents);
        }
    }

    @SneakyThrows
    BlogPostMetadata buildBlogPostMetadataFrom(File file) {
        return buildBlogPostMetadataFrom(new FileInputStream(file));
    }

    @SneakyThrows
    private static BlogPostMetadata buildBlogPostMetadataFrom(String contents) {
        return null;
    }

}