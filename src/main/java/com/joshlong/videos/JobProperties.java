package com.joshlong.videos;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "bootiful")
public record JobProperties(Map<String, String> channels, Api api, Youtube youtube, Batch batch, Promotion promotion,
		Twitter twitter) {

	public record Api(String[] corsHosts) {
	}

	public record Twitter(String username, String clientId, String clientSecret) {
	}

	public record Batch(String[] channelIds, boolean run) {
	}

	public record Youtube(String apiKey) {
	}

	public record Promotion(String[] playlistIds) {
	}
}
