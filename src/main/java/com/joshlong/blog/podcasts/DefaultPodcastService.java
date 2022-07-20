package com.joshlong.blog.podcasts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.Podcast;
import com.joshlong.blog.PodcastService;
import com.joshlong.blog.index.IndexingFinishedEvent;
import com.joshlong.blog.utils.JsonUtils;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
class DefaultPodcastService implements PodcastService {

	private final String rootUrl = "https://api.bootifulpodcast.fm";

	private final URL uri = new URL(this.rootUrl + "/site/podcasts");

	private final Collection<Podcast> podcasts = new CopyOnWriteArrayList<>();

	private final ObjectMapper objectMapper;

	private final Object monitor = new Object();

	DefaultPodcastService(ObjectMapper objectMapper) throws IOException {
		this.objectMapper = objectMapper;
		this.refresh();
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
		var response = objectMapper.readValue(this.uri, new TypeReference<Collection<JsonNode>>() {
		});
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
						var episodeUri = JsonUtils.valueOrNull(node, "episodeUri", u -> buildUrlFrom(this.rootUrl + u));
						var description = JsonUtils.valueOrNull(node, "description");
						return new Podcast(id, uid, title, date, episodePhotoUri, episodeUri, description);
					})//
					.sorted(Comparator.comparing(Podcast::date).reversed())//
					.toList() //
			);
		}
	}

}
