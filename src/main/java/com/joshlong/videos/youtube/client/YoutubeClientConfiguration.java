package com.joshlong.videos.youtube.client;

import com.joshlong.videos.JobProperties;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.client.RestClient;

import java.util.Set;

@Configuration
@ImportRuntimeHints(YoutubeClientConfiguration.Hints.class)
class YoutubeClientConfiguration {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			Set.of(Video.class, Playlist.class, PlaylistVideos.class, Channel.class, ChannelVideos.class,
					ChannelPlaylists.class).forEach(c -> hints.reflection().registerType(c, mcs));
		}

	}

	@Bean
	YoutubeClient youtubeClient(RestClient http, JobProperties properties) {
		return new DefaultYoutubeClient(http, properties.youtube().apiKey());
	}

}
