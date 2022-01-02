package com.joshlong.blog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.core.io.Resource;

import java.net.URI;

@ConstructorBinding
@ConfigurationProperties("blog")
public record BlogProperties(URI gitRepository, Resource localCloneDirectory, boolean resetOnRebuild,

        /*
         * this key is stored in my lastpass. it's an environment variable for this app and configured
         * as the payload of the webhook in github for the joshlong.github.io-content repository
         */
         String indexRebuildKey,
     String[] corsHosts) {
}
