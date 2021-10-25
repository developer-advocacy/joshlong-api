package com.joshlong.blog;

import lombok.extern.log4j.Log4j2;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

import java.text.DateFormat;
import java.util.Collection;
import java.util.Locale;

@Log4j2
@Controller
class ApiGraphQlController {

	private final IndexService indexService;

	private final DateFormat isoDateFormat;

	ApiGraphQlController(IndexService indexService, DateFormat isoDateFormat) {
		this.indexService = indexService;
		this.isoDateFormat = isoDateFormat;
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

	@SchemaMapping(typeName = "BlogPost", field = "date")
	String date(BlogPost bp) {
		return isoDateFormat.format(bp.date());
	}

	@SchemaMapping(typeName = "IndexRebuildStatus", field = "date")
	String indexRebuildStatusDate(IndexRebuildStatus rebuildStatus) {
		return isoDateFormat.format(rebuildStatus.date());
	}

}
