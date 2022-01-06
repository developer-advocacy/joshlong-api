package com.joshlong.blog.blogs;

import com.joshlong.blog.BlogPost;
import com.joshlong.blog.BlogPostSearchResults;
import com.joshlong.blog.BlogPostSearchService;
import com.joshlong.blog.IndexService;
import com.joshlong.blog.index.IndexingFinishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToLongFunction;

/**
 * Provides finder-methods to search the content in the {@link IndexService}
 */
@Service
@Slf4j
class DefaultBlogPostSearchService implements BlogPostSearchService {

	private final List<BlogPost> postsOrderedNewestToOldest = new CopyOnWriteArrayList<>();

	private final IndexService indexService;

	DefaultBlogPostSearchService(IndexService indexService) {
		this.indexService = indexService;
	}

	@EventListener(IndexingFinishedEvent.class)
	public void refresh() {
		log.info("caching the blogPost collection newest to oldest.");
		var index = this.indexService.getIndex();
		var blogs = index.values();
		var results = blogs.stream() //
				.sorted(Comparator.comparingLong((ToLongFunction<BlogPost>) value -> value.date().getTime()).reversed()) //
				.toList();
		this.postsOrderedNewestToOldest.addAll(results);
	}

	@Override
	public BlogPostSearchResults recentBlogPosts(@Argument int offset, @Argument int pageSize) {
		var all = this.postsOrderedNewestToOldest.stream().filter(BlogPost::listed).toList();
		var end = Math.min((offset + pageSize), all.size());
		var results = all.subList(offset, end);
		log.info("recentBlogPosts (" + offset + "," + pageSize + "): " + results.size());
		return new BlogPostSearchResults(all.size(), offset, pageSize, results);
	}

	@Override
	public BlogPostSearchResults search(@Argument String query, @Argument int offset, @Argument int pageSize) {
		return this.indexService.search(query, offset, pageSize, true);
	}

	@Override
	public Mono<BlogPost> blogPostByPath(@Argument String path) {
		var index = this.indexService.getIndex();
		var nk = path.toLowerCase(Locale.ROOT);
		var blogPosts = List.of(nk, "/" + nk, "/jl/blogPost/" + nk).stream().map(t -> t.toLowerCase(Locale.ROOT))
				.filter(index::containsKey).map(index::get).map(Mono::just).toList();
		return blogPosts.size() > 0 ? blogPosts.get(0) : Mono.empty();
	}

}
