package com.joshlong.blogs;

import com.joshlong.*;
import com.joshlong.index.IndexingFinishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/**
 * Provides finder-methods to search the content in the {@link IndexService}
 */
@Service
class DefaultBlogPostSearchService implements BlogPostSearchService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final List<BlogPost> postsOrderedNewestToOldest = new CopyOnWriteArrayList<>();

	private final IndexService indexService;

	private final ApplicationEventPublisher publisher;

	DefaultBlogPostSearchService(ApplicationEventPublisher publisher, IndexService indexService) {
		this.indexService = indexService;
		this.publisher = publisher;
	}

	@EventListener(IndexingFinishedEvent.class)
	public void refresh() {
		log.info("caching the blogPost collection newest to oldest.");
		var index = this.indexService.getIndex();
		var blogs = index.values();
		var results = blogs.stream() //
				.sorted(Comparator.comparingLong((ToLongFunction<BlogPost>) value -> value.date().getTime()).reversed()) //
				.toList();
		synchronized (this.postsOrderedNewestToOldest) {
			this.postsOrderedNewestToOldest.clear();
			this.postsOrderedNewestToOldest.addAll(results);
		}
		publisher.publishEvent(new BlogPostsOrderedEvent(new ArrayList<>(this.postsOrderedNewestToOldest)));
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
	public BlogPost blogPostByPath(@Argument String path) {
		var index = this.indexService.getIndex();
		var nk = path.toLowerCase(Locale.ROOT);
		var blogPosts = Stream//
				.of(nk, "/" + nk, "/jl/blogPost/" + nk)//
				.map(t -> t.toLowerCase(Locale.ROOT))//
				.filter(index::containsKey)//
				.map(index::get)//
				.toList();
		return !blogPosts.isEmpty() ? blogPosts.getFirst() : null;
	}

	@Override
	public List<BlogPost> getBlogPosts() {
		return this.postsOrderedNewestToOldest;
	}

}
