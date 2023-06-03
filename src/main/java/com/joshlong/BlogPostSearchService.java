package com.joshlong;

import org.springframework.graphql.data.method.annotation.Argument;

import java.util.List;

public interface BlogPostSearchService {

	BlogPostSearchResults recentBlogPosts(@Argument int offset, @Argument int pageSize);

	BlogPostSearchResults search(@Argument String query, @Argument int offset, @Argument int pageSize);

	BlogPost blogPostByPath(@Argument String path);

	List<BlogPost> getBlogPosts();

}
