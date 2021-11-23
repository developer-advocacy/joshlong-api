package com.joshlong.blog;

import lombok.extern.log4j.Log4j2;
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
@Log4j2
@Controller
class ApiGraphQlController {

    private final int heroParagraphLength = 400;

    private final IndexService indexService;
    private final AppearanceService appearanceService;
    private final DateFormat isoDateFormat;


    ApiGraphQlController(IndexService indexService, AppearanceService appearanceService, DateFormat isoDateFormat) {
        this.indexService = indexService;
        this.appearanceService = appearanceService;
        this.isoDateFormat = isoDateFormat;
    }

    @QueryMapping
    Collection<Appearance> appearances() {
        return this.appearanceService.getAppearances();
    }

    @QueryMapping
    Collection<BlogPost> recentBlogPosts(@Argument int count) {
        var index = this.indexService.getIndex();
        var blogs = index.values();
        return blogs
                .stream()
                .sorted(Comparator.comparingLong((ToLongFunction<BlogPost>) value -> value.date().getTime()).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    @QueryMapping
    Collection<BlogPost> blogPosts() {
        return this.indexService.getIndex().values();
    }

    @QueryMapping
    Mono<BlogPost> blogPostByPath(@Argument String path) {
        var index = this.indexService.getIndex();
        var nk = path.toLowerCase(Locale.ROOT);
        return index.containsKey(nk) ? Mono.just(index.get(nk)) : Mono.empty();
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
        return blogPost.images() != null && blogPost.images().size() > 0 ?
                blogPost.images().get(0) : null;
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
        return (hold.size() == 0) ?
                post.paragraphs().get(0).substring(0, this.heroParagraphLength) :
                String.join("", hold);
    }

}
