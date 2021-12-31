package com.joshlong.blog;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

// todo to support this view of the blogs, we'll need to develop a feature on the server-side that shows us
//        the first paragraph or the first N characters, whichever is fewer, in a given blog. We can look at the <P>
//        tags, perhaps using the JSOUP parsing, take the first one, and then truncate all but the first N characters of that?
//        We need this for the 'recent-posts' section of the front page
@Controller
class ApiGraphQlController {

    private final int heroParagraphLength = 400;

    private final IndexService indexService;

    private final AppearanceService appearanceService;

    private final PodcastService podcastService;

    private final DateFormat isoDateFormat;

    private final ContentService booksContentService;

    private final ContentService livelessonsContentService;

    ApiGraphQlController(IndexService indexService,
                         @Qualifier("booksContentService") ContentService booksContentService,
                         @Qualifier("livelessonsContentService") ContentService livelessonsContentService,
                         AppearanceService appearanceService, PodcastService podcastService, DateFormat isoDateFormat) {
        this.indexService = indexService;
        this.appearanceService = appearanceService;
        this.podcastService = podcastService;
        this.isoDateFormat = isoDateFormat;
        this.booksContentService = booksContentService;
        this.livelessonsContentService = livelessonsContentService;
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
        if (null != p.date())
            return this.isoDateFormat.format(p.date());
        return null;
    }

    @MutationMapping
    IndexRebuildStatus rebuildIndex() {
        return this.indexService.rebuildIndex();
    }

    @QueryMapping
    Collection<BlogPost> search(@Argument String query) {
        return this.indexService.search(query);
    }

    //
    @SchemaMapping(typeName = "Appearance", field = "startDate")
    String startDate(Appearance bp) {
        return isoDateFormat.format(bp.startDate());
    }

    @SchemaMapping(typeName = "Appearance", field = "endDate")
    String endDate(Appearance bp) {
        return isoDateFormat.format(bp.endDate());
    }
    //

    @SchemaMapping(typeName = "BlogPost", field = "date")
    String date(BlogPost bp) {
        return isoDateFormat.format(bp.date());
    }

    @SchemaMapping(typeName = "IndexRebuildStatus", field = "date")
    String indexRebuildStatusDate(IndexRebuildStatus rebuildStatus) {
        return isoDateFormat.format(rebuildStatus.date());
    }

    @SchemaMapping(typeName = "BlogPost")
    String heroImage(BlogPost blogPost) {
        return blogPost.images() != null && blogPost.images().size() > 0 ? blogPost.images().get(0) : null;
    }

    @SchemaMapping(typeName = "BlogPost")
    String heroParagraphs(BlogPost post) {
        Assert.state(post.paragraphs() != null, () -> "the paragraphs must be non-null");
        var ctr = 0;
        var hold = new ArrayList<String>();
        for (var p : post.paragraphs()) {
            if ((ctr + p.length()) <= (this.heroParagraphLength)) {
                hold.add(p);
            } //
            else {
                break;
            }
            ctr += p.length();
        }
        return (hold.size() == 0) ? post.paragraphs().get(0).substring(0, this.heroParagraphLength)
                : String.join("", hold);
    }

}
