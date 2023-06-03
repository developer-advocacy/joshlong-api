package com.joshlong;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;

import java.text.DateFormat;
import java.util.Collection;

/**
 * @author Josh Long
 */
@Slf4j
@Controller
class ApiGraphQlController {

	private final BlogPostSearchService blogPostSearchService;

	private final AppearanceService appearanceService;

	private final PodcastService podcastService;

	private final DateFormat isoDateFormat;

	private final ContentService<String> abstractsContentService;

	private final ContentService<String> aboutContentService;

	private final ContentService<Collection<Content>> booksContentService;

	private final ContentService<Collection<Content>> livelessonsContentService;

	ApiGraphQlController(@Qualifier("aboutContentService") ContentService<String> aboutContentService, //
			@Qualifier("abstractsContentService") ContentService<String> abstractsContentService, //
			@Qualifier("booksContentService") ContentService<Collection<Content>> booksContentService, //
			@Qualifier("livelessonsContentService") ContentService<Collection<Content>> livelessonsContentService, //

			BlogPostSearchService blogPostSearchService, //
			AppearanceService appearanceService, //
			PodcastService podcastService, //
			DateFormat isoDateFormat) {

		this.blogPostSearchService = blogPostSearchService;
		this.appearanceService = appearanceService;
		this.aboutContentService = aboutContentService;
		this.podcastService = podcastService;
		this.isoDateFormat = isoDateFormat;
		this.abstractsContentService = abstractsContentService;
		this.booksContentService = booksContentService;
		this.livelessonsContentService = livelessonsContentService;
	}

	@QueryMapping
	String abstracts() {
		return this.abstractsContentService.getContent();
	}

	@QueryMapping
	String about() {
		return this.aboutContentService.getContent();
	}

	@QueryMapping
	BlogPostSearchResults search(@Argument String query, @Argument int offset, @Argument int pageSize) {
		return this.blogPostSearchService.search(query, offset, pageSize);
	}

	@QueryMapping
	BlogPostSearchResults recentBlogPosts(@Argument int offset, @Argument int pageSize) {
		return this.blogPostSearchService.recentBlogPosts(offset, pageSize);
	}

	@QueryMapping
	BlogPost blogPostByPath(@Argument String path) {
		return blogPostSearchService.blogPostByPath(path);
	}

	@QueryMapping
	Collection<Appearance> appearances() {
		return this.appearanceService.getAppearances();
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

}
