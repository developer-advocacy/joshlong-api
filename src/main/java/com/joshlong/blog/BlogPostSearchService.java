package com.joshlong.blog;

import org.springframework.graphql.data.method.annotation.Argument;
import reactor.core.publisher.Mono;

public interface BlogPostSearchService {

	BlogPostSearchResults recentBlogPosts(@Argument int offset, @Argument int pageSize);

	BlogPostSearchResults search(@Argument String query, @Argument int offset, @Argument int pageSize);

	Mono<BlogPost> blogPostByPath(@Argument String path);

}
