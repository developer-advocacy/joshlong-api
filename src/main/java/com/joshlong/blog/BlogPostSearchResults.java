package com.joshlong.blog;

import java.util.List;

/**
 *
 */
public record BlogPostSearchResults(int totalResultsSize, int offset, int pageSize, List<BlogPost> posts) {
}
