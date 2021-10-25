package com.joshlong.blog;

import java.util.Date;
import java.util.List;

public record BlogPost(String title, Date date, String originalContent, String processedContent, boolean published,
		BlogPostContentType type, String path, List<String> images) {
}
