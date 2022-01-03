package com.joshlong.blog;

import java.util.Date;
import java.util.List;

/**
 * Meant to describe posts as they exist in the database
 */
public record BlogPost(String title, Date date, String originalContent, String processedContent, boolean published,
		BlogPostContentType type, String path,
		/* this is the unique bit without the /jl/blogPost/ */ String pathId, List<String> images,
		List<String> paragraphs, boolean heroParagraphsTruncated) {
}
