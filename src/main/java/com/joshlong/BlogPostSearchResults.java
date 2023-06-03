package com.joshlong;

import java.util.List;

/**
 *
 */
public record BlogPostSearchResults(int totalResultsSize, int offset, int pageSize, List<BlogPost> posts) {
}
