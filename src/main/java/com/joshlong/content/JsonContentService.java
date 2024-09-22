package com.joshlong.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.Content;
import com.joshlong.ContentService;
import com.joshlong.index.IndexingFinishedEvent;
import com.joshlong.utils.JsonUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Slf4j
class JsonContentService implements ContentService<Collection<Content>> {

	private final Function<String, String> htmlRefResolver;

	private final Resource resource;

	private final ObjectMapper objectMapper;

	private final List<Content> contents = new CopyOnWriteArrayList<>();

	JsonContentService(Resource resource, Function<String, String> htmlRefResolver, ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.resource = resource;
		this.htmlRefResolver = htmlRefResolver;
	}

	@Override
	public Collection<Content> getContent() {
		return this.contents;
	}

	@SneakyThrows
	private URL buildUrlFrom(String url) {
		if (null == url)
			return null;
		return URI.create(url).toURL();
	}

	@EventListener(IndexingFinishedEvent.class)
	public void indexed() throws Exception {
		var file = this.resource.getFile();
		var values = this.objectMapper.readValue(file, new TypeReference<Collection<JsonNode>>() {
		});
		var content = values //
				.stream()//
				.map(json -> { //
					var title = JsonUtils.valueOrNull(json, "title");
					var html = JsonUtils.valueOrNull(json, "html");
					var htmlRef = JsonUtils.valueOrNull(json, "htmlRef");
					Assert.isTrue(StringUtils.hasText(html) || StringUtils.hasText(htmlRef),
							"you must provide an HTML description or a key which the HTML may be resolved");
					html = (!StringUtils.hasText(html)) ? this.htmlRefResolver.apply(htmlRef) : html;
					var imageUrl = buildUrlFrom(JsonUtils.valueOrNull(json, "imageUrl"));
					return new Content(title, html, imageUrl);
				}). //
				toList();

		synchronized (this.contents) {
			this.contents.clear();
			this.contents.addAll(content);
		}

	}

}
