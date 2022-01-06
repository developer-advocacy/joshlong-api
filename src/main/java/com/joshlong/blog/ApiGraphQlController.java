package com.joshlong.blog;

import com.joshlong.blog.index.IndexingFinishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.text.DateFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

// todo to support this view of the blogs, we'll need to develop a feature on the server-side that shows us
//        the first paragraph or the first N characters, whichever is fewer, in a given blog. We can look at the <P>
//        tags, perhaps using the JSOUP parsing, take the first one, and then truncate all but the first N characters of that?
//        We need this for the 'recent-posts' section of the front page
@Slf4j
@Controller
class ApiGraphQlController {

	private final List<BlogPost> postsOrderedNewestToOldest = new CopyOnWriteArrayList<>();

	private final IndexService indexService;

	private final AppearanceService appearanceService;

	private final PodcastService podcastService;

	private final DateFormat isoDateFormat;

	private final SpringTipsService springTipsService;

	private final ContentService<String> abstractsContentService;

	private final ContentService<Collection<Content>> booksContentService;

	private final ContentService<Collection<Content>> livelessonsContentService;

	ApiGraphQlController(IndexService indexService,
			@Qualifier("abstractsContentService") ContentService<String> abstractsContentService,
			@Qualifier("booksContentService") ContentService<Collection<Content>> booksContentService,
			SpringTipsService springTipsService,
			@Qualifier("livelessonsContentService") ContentService<Collection<Content>> livelessonsContentService,
			AppearanceService appearanceService, PodcastService podcastService, DateFormat isoDateFormat) {
		this.indexService = indexService;
		this.appearanceService = appearanceService;
		this.podcastService = podcastService;
		this.isoDateFormat = isoDateFormat;
		this.abstractsContentService = abstractsContentService;
		this.booksContentService = booksContentService;
		this.livelessonsContentService = livelessonsContentService;
		this.springTipsService = springTipsService;
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

	@QueryMapping
	String abstracts() {
		return this.abstractsContentService.getContent();
	}

	@QueryMapping
	Collection<Appearance> appearances() {
		return this.appearanceService.getAppearances();
	}

	@QueryMapping
	BlogPostSearchResults recentBlogPosts(@Argument int offset, @Argument int pageSize) {
		var end = Math.min((offset + pageSize), this.postsOrderedNewestToOldest.size());
		var results = this.postsOrderedNewestToOldest.subList(offset, end);
		log.info("recentBlogPosts (" + offset + "," + pageSize + "): " + results.size());
		return new BlogPostSearchResults(this.postsOrderedNewestToOldest.size(), offset, pageSize, results);

	}

	@QueryMapping
	BlogPostSearchResults search(@Argument String query, @Argument int offset, @Argument int pageSize) {
		return this.indexService.search(query, offset, pageSize);
	}

	@QueryMapping
	Mono<BlogPost> blogPostByPath(@Argument String path) {
		var index = this.indexService.getIndex();
		var nk = path.toLowerCase(Locale.ROOT);

		if (index.containsKey(nk))
			return Mono.just(index.get(nk));

		nk = "/jl/blogpost/" + nk;
		if (index.containsKey(nk))
			return Mono.just(index.get(nk));

		return Mono.empty();
	}

	@QueryMapping
	Collection<Content> livelessons() {
		return this.livelessonsContentService.getContent();
	}

	@QueryMapping
	Collection<Content> books() {
		return this.booksContentService.getContent();
	}

	@QueryMapping
	Collection<Podcast> podcasts() {
		return this.podcastService.getPodcasts();
	}

	@SchemaMapping(typeName = "Podcast", field = "date")
	String date(Podcast p) {
		return null != p.date() ? this.isoDateFormat.format(p.date()) : null;
	}

	@SchemaMapping(typeName = "Appearance", field = "startDate")
	String startDate(Appearance bp) {
		return isoDateFormat.format(bp.startDate());
	}

	@SchemaMapping(typeName = "Appearance", field = "endDate")
	String endDate(Appearance bp) {
		return isoDateFormat.format(bp.endDate());
	}

	@SchemaMapping(typeName = "BlogPost", field = "date")
	String date(BlogPost bp) {
		return isoDateFormat.format(bp.date());
	}

	@SchemaMapping(typeName = "BlogPost")
	String heroImage(BlogPost blogPost) {
		return blogPost.images() != null && blogPost.images().size() > 0 ? blogPost.images().get(0) : null;
	}

	@SchemaMapping(typeName = "BlogPost")
	String heroParagraphs(BlogPost post) {
		Assert.state(post.paragraphs() != null && post.paragraphs().size() > 0,
				() -> "the paragraphs must be non-null");
		return String.join(" ", post.paragraphs());
	}

	@QueryMapping
	SpringTipsEpisode latestSpringTipsEpisode() {
		return this.springTipsService.getLatestSpringTipsEpisode();
	}

	@QueryMapping
	Collection<SpringTipsEpisode> springTipsEpisodes() {
		return this.springTipsService.getSpringTipsEpisodes();
	}

	@SchemaMapping(typeName = "SpringTipsEpisode")
	String date(SpringTipsEpisode springTipsEpisode) {
		return this.isoDateFormat.format(springTipsEpisode.date());
	}

	@SchemaMapping(typeName = "SpringTipsEpisode")
	String blogUrl(SpringTipsEpisode episode) {
		return episode.blogUrl().toString();
	}

	@SchemaMapping(typeName = "SpringTipsEpisode")
	String youtubeEmbedUrl(SpringTipsEpisode springTipsEpisode) {
		return springTipsEpisode.youtubeEmbedUrl().toString();
	}

}
