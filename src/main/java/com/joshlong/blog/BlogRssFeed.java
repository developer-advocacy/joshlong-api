package com.joshlong.blog;

import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
public record BlogRssFeed(String title, String link, String description) {
}
