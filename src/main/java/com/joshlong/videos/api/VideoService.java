package com.joshlong.videos.api;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A service for working with the database.
 *
 * @author Josh Long
 */
public interface VideoService {

	Mono<Playlist> playlistById(String id);

	Flux<Video> videosByPlaylist(Playlist playlist);

	/**
	 * @return Returns a {@link Video } given its {@code id}.
	 */
	Mono<Channel> channelById(String id);

	/**
	 * @return all the channels in the database.
	 */
	Flux<Channel> channels();

	/**
	 * @return all the videos for a given channel
	 */
	Flux<Video> videosByChannel(Channel channel);

	/**
	 * @return playlists by their name
	 */
	Flux<Playlist> playlistsByName(String name);

}
