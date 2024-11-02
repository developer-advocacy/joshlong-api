package com.joshlong.podcasts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.BlogProperties;
import com.joshlong.Podcast;
import com.joshlong.PodcastService;
import com.joshlong.index.IndexingFinishedEvent;
import com.joshlong.utils.JsonUtils;
import com.joshlong.utils.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * provides details on the latest-and-greatest podcasts from
 * <a href="https://api.bootifulpodcast.fm">api.bootifulpodcast.fm</a>.
 *
 * @author Josh Long
 */
class LegacyJsonPodcastService implements PodcastService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final URL uri;

	private final String rootUri;

	private final Collection<Podcast> podcasts = new CopyOnWriteArrayList<>();

	private final Object monitor = new Object();

	private final ObjectMapper objectMapper;

	LegacyJsonPodcastService(BlogProperties properties, ObjectMapper objectMapper)
			throws IOException, URISyntaxException {
		this.objectMapper = objectMapper;
		this.rootUri = properties.bootifulPodcastApiServerUri();
		this.uri = new URI(this.rootUri + "/site/podcasts").toURL();
	}

	@Override
	public Collection<Podcast> getPodcasts() {
		return this.podcasts;
	}

	private URL buildUrlFrom(String url) {
		return StringUtils.hasText(url) ? UrlUtils.url(url) : null;
	}

	@EventListener(IndexingFinishedEvent.class)
	void refresh() {
		try {
			log.info("refreshing {}", PodcastService.class.getName());
			var response = this.objectMapper.readValue(this.uri, new TypeReference<Collection<JsonNode>>() {
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
							var episodeUri = JsonUtils.valueOrNull(node, "episodeUri",
									u -> buildUrlFrom(this.rootUri + u));
							var description = JsonUtils.valueOrNull(node, "description");
							return new Podcast(id, uid, title, date, episodePhotoUri, episodeUri, description);
						})//
						.sorted(Comparator.comparing(Podcast::date).reversed())//
						.toList() //
				);
			}
		} //
		catch (Throwable t) {
			log.warn("we couldn't connect to the podcast feed! {}", this.uri);
		}
	}

}
