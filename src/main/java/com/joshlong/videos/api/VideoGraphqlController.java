package com.joshlong.videos.api;

import com.joshlong.videos.JobProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

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
	Flux<Playlist> playlistsByName(@Argument String name) {
		return this.videoService.playlistsByName(name);
	}

	@QueryMapping
	Flux<Channel> channels() {
		return this.videoService.channels();
	}

	@QueryMapping
	Flux<Video> videosByPlaylist(@Argument String playlistId) {
		return this.videoService.playlistById(playlistId).flatMapMany(this.videoService::videosByPlaylist);
	}

	@QueryMapping
	Flux<Video> videosByChannel(@Argument String channelId) {
		return this.videoService.channelById(channelId).flatMapMany(this.videoService::videosByChannel);
	}

	@QueryMapping
	Flux<Video> springtipsVideos() {
		return this.videoService.playlistsByName("Spring Tips").flatMap(this.videoService::videosByPlaylist);
	}

	@QueryMapping
	Flux<Video> coffeesoftwareVideos() {
		return this.videoService.channelById(this.ids.get("coffeesoftware"))
				.flatMapMany(this.videoService::videosByChannel);
	}

}
