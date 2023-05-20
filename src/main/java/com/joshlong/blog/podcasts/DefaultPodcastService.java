package com.joshlong.blog.podcasts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.BlogProperties;
import com.joshlong.blog.Podcast;
import com.joshlong.blog.PodcastService;
import com.joshlong.blog.index.IndexingFinishedEvent;
import com.joshlong.blog.utils.JsonUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * provides details on the latest-and-greatest podcasts from
 * <a href="https://api.bootifulpodcast.fm">api.bootifulpodcast.fm</a>
 *
 * @author Josh Long
 */
@Slf4j
class DefaultPodcastService implements PodcastService {

	private final URL uri;

	private final String rootUri;

	private final Collection<Podcast> podcasts = new CopyOnWriteArrayList<>();

	private final ObjectMapper objectMapper;

	private final Object monitor = new Object();

	DefaultPodcastService(BlogProperties properties, ObjectMapper objectMapper) throws IOException {
		this.objectMapper = objectMapper;
		this.rootUri = properties.bootifulPodcastApiServerUri();
		this.uri = new URL(this.rootUri + "/site/podcasts");
	}

	@Override
	public Collection<Podcast> getPodcasts() {
		return this.podcasts;
	}

	@SneakyThrows
	private URL buildUrlFrom(String url) {
		return StringUtils.hasText(url) ? new URL(url) : null;
	}

	@EventListener(IndexingFinishedEvent.class)
	public void refresh() throws IOException {
		log.info("refreshing " + PodcastService.class.getName());
		var response = this.objectMapper
			.readValue(this.uri, new TypeReference<Collection<JsonNode>>() {});
		synchronized (this.monitor) {
			this.podcasts.clear();
			this.podcasts.addAll(response//
					.stream()//
					.map(node -> {
						var id = JsonUtils.valueOrNull(node, "id", Integer::parseInt);
						var uid = JsonUtils.valueOrNull(node, "uid");
						var title = JsonUtils.valueOrNull(node, "title");
						var date = new Date(node.get("date").longValue());
						var episodePhotoUri = JsonUtils.valueOrNull(node, "episodePhotoUri", this::buildUrlFrom);
						var episodeUri = JsonUtils.valueOrNull(node, "episodeUri", u -> buildUrlFrom(this.rootUri + u));
						var description = JsonUtils.valueOrNull(node, "description");
						return new Podcast(id, uid, title, date, episodePhotoUri, episodeUri, description);
					})//
					.sorted(Comparator.comparing(Podcast::date).reversed())//
					.toList() //
			);
		}
	}

}
