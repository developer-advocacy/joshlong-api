package com.joshlong.blog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.core.io.Resource;

import java.net.URI;

@ConstructorBinding
@ConfigurationProperties("blog")
public record BlogProperties(URI gitRepository, Resource localCloneDirectory, boolean resetOnRebuild) {
}
