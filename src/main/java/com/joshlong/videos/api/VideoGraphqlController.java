package com.joshlong.videos.api;

import com.joshlong.videos.JobProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
class VideoGraphqlController {

	private final VideoService videoService;

	private final Map<String, String> ids = new ConcurrentHashMap<>();

	VideoGraphqlController(VideoService videoService, JobProperties properties) {
		this.videoService = videoService;
		this.ids.putAll(properties.channels());
	}

	@QueryMapping
	Collection<Playlist> playlistsByName(@Argument String name) {
		return this.videoService.playlistsByName(name);
	}

	@QueryMapping
	Collection<Channel> channels() {
		return this.videoService.channels();
	}

	@QueryMapping
	Collection<Video> videosByPlaylist(@Argument String playlistId) {
		var pl = this.videoService.playlistById(playlistId);
		return this.videoService.videosByPlaylist(pl);
	}

	@QueryMapping
	Collection<Video> videosByChannel(@Argument String channelId) {
		return videoService.videosByChannel(this.videoService.channelById(channelId));
	}

	@QueryMapping
	Collection<Video> springtipsVideos() {
		var springTips = this.videoService.playlistsByName("Spring Tips");
		if (springTips.isEmpty()) {
			return new ArrayList<>();
		}
		return this.videoService
				.videosByPlaylist(springTips.getFirst());
	}

	@QueryMapping
	Collection <Video> coffeesoftwareVideos() {
		return this.videoService.videosByChannel(
				this.videoService.channelById(this.ids.get("coffeesoftware")));
	}

}
