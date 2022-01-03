package com.joshlong.blog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.text.DateFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

// todo to support this view of the blogs, we'll need to develop a feature on the server-side that shows us
//        the first paragraph or the first N characters, whichever is fewer, in a given blog. We can look at the <P>
//        tags, perhaps using the JSOUP parsing, take the first one, and then truncate all but the first N characters of that?
//        We need this for the 'recent-posts' section of the front page
@Slf4j
@Controller
class ApiGraphQlController {

	private final int heroParagraphLength = 400;

	private final IndexService indexService;

	private final AppearanceService appearanceService;

	private final PodcastService podcastService;

	private final DateFormat isoDateFormat;

	private final SpringTipsService springTipsService;

	private final ContentService booksContentService;

	private final ContentService livelessonsContentService;

	ApiGraphQlController(IndexService indexService,
			@Qualifier("booksContentService") ContentService booksContentService, SpringTipsService springTipsService,
			@Qualifier("livelessonsContentService") ContentService livelessonsContentService,
			AppearanceService appearanceService, PodcastService podcastService, DateFormat isoDateFormat) {
		this.indexService = indexService;
		this.appearanceService = appearanceService;
		this.podcastService = podcastService;
		this.isoDateFormat = isoDateFormat;
		this.booksContentService = booksContentService;
		this.livelessonsContentService = livelessonsContentService;
		this.springTipsService = springTipsService;
	}

	@QueryMapping
	Collection<Appearance> appearances() {
		return this.appearanceService.getAppearances();
	}

	@QueryMapping
	Collection<BlogPost> recentBlogPosts(@Argument int count) {
		var index = this.indexService.getIndex();
		var blogs = index.values();
		return blogs.stream()
				.sorted(Comparator.comparingLong((ToLongFunction<BlogPost>) value -> value.date().getTime()).reversed())
				.limit(count).collect(Collectors.toList());
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
	Collection<BlogPost> blogPosts() {
		return this.indexService.getIndex().values();
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
	Collection<Podcast> podcasts() {
		return this.podcastService.getPodcasts();
	}

	@SchemaMapping(typeName = "Podcast", field = "date")
	String date(Podcast p) {
		return null != p.date() ? this.isoDateFormat.format(p.date()) : null;
	}

	@QueryMapping
	Collection<BlogPost> search(@Argument String query) {
		return this.indexService.search(query);
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

	// private final Map<String, Boolean> truncated = new ConcurrentHashMap<>();
	//
	// @EventListener(IndexingFinishedEvent.class)
	// public void reset() {
	// this.truncated.clear();
	// }

	/*
	 * @SchemaMapping(typeName = "BlogPost") Boolean heroParagraphsTruncated(BlogPost
	 * post) { var result = this.truncated.getOrDefault(post.pathId(), false);
	 * log.info("are the hero paragraphs for " + post.title() + " truncated? " + result);
	 * return result; }
	 */

	/**
	 * add each word and check that the length of the current sentence plus the new word
	 * isn't longer than {@link this#heroParagraphLength}. Once it is, stop collecting
	 * words.
	 */
	@SchemaMapping(typeName = "BlogPost")
	String heroParagraphs(BlogPost post) {
		Assert.state(post.paragraphs() != null && post.paragraphs().size() > 0,
				() -> "the paragraphs must be non-null");
		return String.join(" ", post.paragraphs());
	}

	// new for the Spring Tips episodes

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
